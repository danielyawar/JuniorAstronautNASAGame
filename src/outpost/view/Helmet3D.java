package outpost.view;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import outpost.shared.Glyphs;
import outpost.shared.Helmet;

/**
 * The astronaut's helmet that stands for a mission, drawn as a solid object that can turn:
 * on the home screen it turns and changes from one mission's look to the next, and on the
 * mission-start card it stands still ({@link #paintStill}). Four dials change it: dust cakes
 * the shell orange and turns the sky in the visor to brown haze, frost grows in from the rim
 * of the visor, a solar storm makes the Sun in the visor flare among pink streaks, and wear
 * scuffs the shell. With every dial at 0 the helmet is clean and its visor mirrors a
 * butterscotch sky and a small Sun.
 *
 * <p><b>How it is drawn.</b> Nothing here is a 3D library. The shell (a slightly tall ball)
 * and the neck ring are shapes turned on a lathe about the one axis the helmet turns on, so
 * turning it never changes its outline on the screen, nor which way any point of that outline
 * faces. Three things are therefore worked out once and kept:
 * <ul>
 * <li>a {@code Solid}, for each size of picture: for every pixel, how much of it the helmet
 *     covers, the light that falls there, and which point of the helmet's surface shows there
 *     when the helmet faces the front (its longitude and its height);</li>
 * <li>a {@code Skin}, for each look: the helmet's surface unwrapped into a flat picture
 *     (longitude across, height down): the visor's frame, the stripe, the vent at the back,
 *     and whatever sits ON the helmet (dust, scuffs, frost); and a second small picture of
 *     what the glass mirrors (sky, ground and Sun);</li>
 * <li>for a skin at a size, the mirror picture laid out on the screen's pixels.</li>
 * </ul>
 * A frame is then one pass over the pixels: look the skin up at (longitude less the turn,
 * height), multiply by the light, and where the skin is clear glass show the mirror picture.
 * The mirror picture stays still on the screen while the visor slides across it, which is
 * how a mirrored ball really looks. While the look is changing, two skins are looked up and
 * mixed. The two ear pieces are not lathe shapes; they are worked out afresh for each
 * frame, in the same pass. No Graphics2D shape is drawn in a frame: in the browser one
 * such shape costs as much as a thousand pixels of the pass.
 *
 * <p>All of this is drawing work, kept so that a frame is cheap in the browser. No game
 * state lives here, and every mark comes from a fixed seed, so a look is the same each time.
 * Only ever used on the Swing event thread.
 */
final class Helmet3D {

    /**
     * Most pixels along a side of the picture a turning helmet is worked out in. A helmet
     * shown larger than this is that picture stretched, so the work of a frame never grows
     * past this. (On desktop Java stretching costs more than the pixels it saves; lower this
     * only if frames are slow in the browser on a large page.)
     */
    static final int MOST_PIXELS = 512;
    /** Seconds a change from one look to another takes. */
    static final double BLEND_SECONDS = 0.4;
    /** How far the still picture on the mission-start card is turned, in radians. More than 0 is to the right. */
    static final double STILL_TURN = Math.toRadians(-22);

    /**
     * What a helmet looks like: the four dials of {@link Helmet}, each from 0 to 1, and
     * whether the visor holds a question mark in place of the sky ("Surprise me").
     */
    record Look(double dust, double frost, double storm, double wear, boolean mystery) {

        /** Nobody knows what the days will bring: a clean helmet with a question mark in its visor. */
        static final Look MYSTERY = new Look(0, 0, 0, 0, true);

        static Look of(Helmet helmet) {
            return new Look(Draw.clamp(helmet.dust()), Draw.clamp(helmet.frost()), Draw.clamp(helmet.storm()),
                    Draw.clamp(helmet.wear()), false);
        }
    }

    // ---- The model, in units of the shell's radius ------------------------------------

    /** The shell is a ball this much taller than it is wide. */
    private static final double TALL = 1.05;
    /** The camera looks down on the helmet a little, so that rings round it show as curves. */
    private static final double PITCH = Math.toRadians(10);
    private static final double SIN_P = Math.sin(PITCH);
    private static final double COS_P = Math.cos(PITCH);
    /** The height at which the shell goes into the neck ring. */
    private static final double CUT = -0.80;
    /** The neck ring's outline from the shell down, as {radius, height}: each pair of points is turned into a band. */
    private static final double[][] RING = {
        {0.60, -0.80}, {0.705, -0.80}, {0.735, -0.83}, {0.735, -0.905}, {0.70, -0.92}, {0.70, -0.965},
        {0.765, -0.985}, {0.765, -1.07}, {0.72, -1.10}};
    /** The shell's radius as a share of the side of the square the helmet is drawn in, and where its middle is. */
    static final double RADIUS = 0.385;
    private static final double CENTRE_X = 0.5;
    private static final double CENTRE_Y = 0.473;
    /** The ear pieces: a short round stub on each side of the helmet, with a narrower knob on its end. */
    private static final double POD_HEIGHT = -0.06;
    /**
     * Each step of an ear piece: its radius, and where it starts and ends, in shell radii from
     * the helmet's axis. The first starts a hair outside the shell's surface, and no deeper:
     * seen from the side, an ear piece and its outline then lie wholly outside the shell's
     * outline, so nothing jumps at the moment it passes from the near side of the helmet to
     * the far side.
     */
    private static final double[][] POD_STEPS = {{0.215, 1.005, 1.10}, {0.13, 1.10, 1.165}};

    /** Where the light comes from, as the camera sees it: above and to the left, and from the front. */
    private static final double[] LIGHT = unit(-0.50, 0.62, 0.60);
    /** Halfway between the light and the camera: where a shine shows. */
    private static final double[] HALF = unit(LIGHT[0], LIGHT[1], LIGHT[2] + 1);

    // ---- The skin: the surface unwrapped ------------------------------------------------

    /** Columns: once round the helmet, with the front in the middle. Must be a power of two. */
    private static final int SKIN_W = 1024;
    /**
     * Rows: the shell from its top down to the neck ring, then the neck ring (which ends at
     * row 504). A skin is then just under two megabytes; desktop Java keeps an array that is
     * a little over two in twice the room.
     */
    private static final int SKIN_H = 506;
    /** One more column than SKIN_W, a copy of the first, so that a look-up can read its right-hand neighbour. */
    private static final int STRIDE = SKIN_W + 1;
    private static final int U_MASK = (SKIN_W << 8) - 1;
    private static final double PER_DEGREE = SKIN_W / 360.0;
    /** Texels to one radius of the shell, measured along the surface. */
    private static final double PER_UNIT = SKIN_W / (2 * Math.PI);
    private static final int SHELL_ROWS = 410;
    private static final int RING_ROW = 420;

    /** The visor on the skin: a window with round ends, wrapped round the front. */
    private static final double VISOR_X = SKIN_W / 2.0;
    private static final double VISOR_Y = row(9);
    private static final double VISOR_HALF_W = 54 * PER_DEGREE;
    private static final double VISOR_HALF_H = 30 * PER_DEGREE;
    private static final double VISOR_ARC = 2 * VISOR_HALF_H * 0.9;
    private static final double FRAME_WIDTH = 11;
    private static final RoundRectangle2D GLASS = new RoundRectangle2D.Double(VISOR_X - VISOR_HALF_W,
            VISOR_Y - VISOR_HALF_H, 2 * VISOR_HALF_W, 2 * VISOR_HALF_H, VISOR_ARC, VISOR_ARC);
    private static final RoundRectangle2D FRAME = new RoundRectangle2D.Double(GLASS.getX() - FRAME_WIDTH,
            GLASS.getY() - FRAME_WIDTH, GLASS.getWidth() + 2 * FRAME_WIDTH, GLASS.getHeight() + 2 * FRAME_WIDTH,
            VISOR_ARC + 2 * FRAME_WIDTH, VISOR_ARC + 2 * FRAME_WIDTH);
    /** Texels on the skin to one unit of the measure the marks are sized in: the shell is about 80 of them across. */
    private static final double MARK = 4.5;
    /** Half the width of the stripe over the top, in shell radii. */
    private static final double STRIPE_HALF = 0.085;

    /** The mirror picture: what a mirrored ball would show the camera, {@code CAP} pixels across. */
    private static final int CAP = 256;

    // ---- Colours ------------------------------------------------------------------------

    private static final Color OUTLINE = new Color(34, 38, 56);
    private static final Color SHELL = new Color(253, 253, 255);
    private static final Color SEAM = new Color(150, 156, 172);
    private static final Color METAL = new Color(150, 156, 172);
    private static final Color METAL_LIGHT = new Color(190, 196, 210);
    private static final Color METAL_DARK = new Color(96, 102, 122);
    private static final Color FRAME_GREY = new Color(78, 84, 104);
    private static final Color GLASS_SHADE = new Color(8, 10, 30);
    private static final Color STRIPE = new Color(52, 104, 196);

    // The sky and the ground in the visor: on a clear day, in deep cold, in thick dust and under a solar storm.
    private static final Color SKY_HIGH = new Color(204, 138, 84);
    private static final Color SKY_LOW = new Color(248, 214, 164);
    private static final Color COLD_HIGH = new Color(116, 148, 198);
    private static final Color COLD_LOW = new Color(204, 222, 242);
    private static final Color HAZE_HIGH = new Color(118, 80, 54);
    private static final Color HAZE_LOW = new Color(150, 104, 68);
    private static final Color STORM_HIGH = new Color(74, 40, 84);
    private static final Color STORM_LOW = new Color(214, 132, 140);
    private static final Color GROUND_FAR = new Color(158, 90, 60);
    private static final Color GROUND_NEAR = new Color(104, 58, 46);
    private static final Color GROUND_COLD = new Color(176, 186, 206);
    private static final Color MYSTERY_HIGH = new Color(52, 60, 102);
    private static final Color MYSTERY_LOW = new Color(20, 24, 46);

    private static final Color SUN = new Color(255, 246, 216);
    private static final Color SUN_GLOW = new Color(255, 226, 150);
    private static final Color SUN_COLD = new Color(250, 252, 255);
    private static final Color SUN_DIM = new Color(240, 196, 136);
    /** The pink of a solar storm, as in the outpost scene. */
    private static final Color STORM_PINK = new Color(255, 96, 160);

    private static final Color DUST = new Color(214, 126, 62);
    private static final Color DUST_DARK = new Color(160, 84, 42);
    private static final Color DUST_GRAIN = new Color(112, 60, 34);
    private static final Color FROST = new Color(240, 248, 255);
    private static final Color FROST_TINT = new Color(196, 222, 250);

    // ---- What is kept ---------------------------------------------------------------------

    /** Skins kept: the five looks of the home screen and one to spare. */
    private static final int MOST_SKINS = 6;
    private static final Map<Look, Skin> SKINS = new LinkedHashMap<>(16, 0.75f, true);
    /** Solids kept: the home screen's size and the still picture's. */
    private static final int MOST_SOLIDS = 2;
    private static final Map<Integer, Solid> SOLIDS = new LinkedHashMap<>(8, 0.75f, true);
    /** The shell with nothing on it and no visor yet: where every skin starts. Made when first wanted. */
    private static int[] bareShell;

    /** Still pictures kept, for the mission-start card. */
    private static final int MOST_STILLS = 4;

    private record StillKey(Look look, int pixels) {}

    private static final Map<StillKey, BufferedImage> STILLS = new LinkedHashMap<>(8, 0.75f, true);

    // ---- One helmet on the screen ---------------------------------------------------------

    /** What is showing: {@code to}, or while {@code share} is under 1 a mix of the two. */
    private Skin from;
    private Skin to;
    private double share = 1;
    /** A skin of this helmet's own, to hold a mix when the look changes again before a change is over. */
    private Skin scratch;

    /** The picture of the last frame, and what it was a frame of, so that the same frame is not worked out twice. */
    private BufferedImage picture;
    private int[] pixels;
    private Solid pictured;
    private Skin picturedFrom;
    private Skin picturedTo;
    private int picturedMix = -1;
    private int picturedTurn;
    private int changes;
    private int picturedChanges = -1;
    /** The boxes of the picture that hold the ear pieces of the last frame (left, top, right, bottom), to be wiped before the next. */
    private final int[][] podBox = new int[2 * POD_STEPS.length][4];
    private int podBoxes;

    /** Shows this look at once. */
    void show(Look look) {
        to = skin(look, true);
        from = to;
        share = 1;
        changes++;
    }

    /**
     * For when the helmet will not be drawn for a while: ends any change of look at once,
     * and lets go of every skin that is kept but the one showing (each is some megabytes,
     * and is painted again in a moment when wanted). What is slow to make again stays: the
     * solids, the showing skin and the tables all skins start from. So the helmet can be
     * drawn again at once, and {@link #prepare} brings the other skins back.
     */
    void rest() {
        if (to == null) {
            return;
        }
        if (share < 1 || scratch != null) {
            from = to;
            share = 1;
            scratch = null;
            changes++;
        }
        if (SKINS.size() > 1) {
            SKINS.values().retainAll(Set.of(to));
        }
    }

    /** Starts changing to this look from whatever is showing now. {@link #advance} moves the change on. */
    void aim(Look look) {
        if (to == null) {
            show(look);
            return;
        }
        if (to.look.equals(look)) {
            return;
        }
        from = share < 1 ? mixed() : to;
        to = skin(look, true);
        share = 0;
        changes++;
    }

    /** Moves a change of look on by this many seconds. True if the picture is now different. */
    boolean advance(double seconds) {
        if (share >= 1) {
            return false;
        }
        share = Math.min(1, share + seconds / BLEND_SECONDS);
        return true;
    }

    /** True while a change of look is under way. */
    boolean changing() {
        return share < 1;
    }

    /** Makes the skin of this look now if it has not been made, so that a later change to it starts at once. True if it had to be made. */
    static boolean prepare(Look look) {
        if (SKINS.containsKey(look)) {
            return false;
        }
        skin(look, true);
        return true;
    }

    /**
     * Draws the helmet in a square of the given size with its top-left corner at (x, y),
     * turned by {@code turn} radians (more than 0 turns its front to the right). The picture
     * is worked out in the screen's own pixels, so it is sharp at any size of window, up to
     * {@link #MOST_PIXELS}.
     */
    void paint(Graphics2D g, double x, double y, double size, double turn) {
        if (to == null) {
            return;
        }
        AffineTransform device = g.getTransform();
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0;
        Object smooth = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try {
            if (plain) {
                int across = Math.max(8, (int) Math.round(size * device.getScaleX()));
                int count = Math.min(across, MOST_PIXELS);
                frame(solid(count), turn);
                Point2D corner = device.transform(new Point2D.Double(x, y), null);
                g.setTransform(new AffineTransform());
                g.translate(Math.round(corner.getX()), Math.round(corner.getY()));
                if (across != count) {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.scale(across / (double) count, across / (double) count);
                }
                g.drawImage(picture, 0, 0, null);
            } else {
                // A turned or mirrored drawing surface: let Java stretch a picture of about the right size.
                int count = Math.max(8, Math.min((int) Math.round(size), MOST_PIXELS));
                frame(solid(count), turn);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.translate(x, y);
                g.scale(size / count, size / count);
                g.drawImage(picture, 0, 0, null);
            }
        } finally {
            g.setTransform(device);
            if (smooth != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth);
            }
        }
    }

    /**
     * The helmet as it stands on the mission-start card: the same helmet as on the home
     * screen, still, turned a little to one side. Each picture is worked out once and kept.
     */
    static void paintStill(Graphics2D g, Helmet helmet, double x, double y, double size) {
        Look look = Look.of(helmet);
        AffineTransform device = g.getTransform();
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0;
        if (!plain) {
            int count = Math.max(8, (int) Math.round(size));
            Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform place = AffineTransform.getTranslateInstance(x, y);
            place.scale(size / count, size / count);
            g.drawImage(still(look, count), place, null);
            if (old != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
            }
            return;
        }
        int count = Math.max(8, (int) Math.round(size * device.getScaleX()));
        Point2D corner = device.transform(new Point2D.Double(x, y), null);
        g.setTransform(new AffineTransform());
        g.drawImage(still(look, count), (int) Math.round(corner.getX()), (int) Math.round(corner.getY()), null);
        g.setTransform(device);
    }

    /** The still picture of this look, {@code count} pixels across, from the ones kept if it has been drawn before. */
    private static BufferedImage still(Look look, int count) {
        StillKey key = new StillKey(look, count);
        BufferedImage still = STILLS.get(key);
        if (still == null) {
            // Worked out at twice the size and halved, which smooths the fine marks of a small helmet.
            int twice = count <= MOST_PIXELS / 2 ? 2 : 1;
            Helmet3D helmet = new Helmet3D();
            helmet.to = skin(look, false);
            helmet.from = helmet.to;
            helmet.frame(solid(count * twice), STILL_TURN);
            still = new BufferedImage(count, count, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = still.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.scale(1.0 / twice, 1.0 / twice);
                g.drawImage(helmet.picture, 0, 0, null);
            } finally {
                g.dispose();
            }
            STILLS.put(key, still);
            trim(STILLS, MOST_STILLS);
        }
        return still;
    }

    /** Drops the entries used longest ago until no more than {@code most} are left. */
    private static <K, V> void trim(Map<K, V> kept, int most) {
        Iterator<K> oldest = kept.keySet().iterator();
        while (kept.size() > most && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    // ---- A frame --------------------------------------------------------------------------

    /** Works out the picture of the helmet at this turn, unless the picture already shows just that. */
    private void frame(Solid solid, double turn) {
        // The skin is looked up at the longitude a pixel shows less the turn, in 256ths of a texel.
        double columns = -turn / (2 * Math.PI) * SKIN_W;
        int turn8 = (int) Math.round((columns - Math.floor(columns / SKIN_W) * SKIN_W) * 256) & U_MASK;
        int mix = share >= 1 ? 256 : (int) Math.round(ease(share) * 256);
        Skin a = mix >= 256 ? to : from;
        Skin b = mix >= 256 || mix <= 0 ? null : to;
        if (solid == pictured && a == picturedFrom && b == picturedTo && mix == picturedMix
                && turn8 == picturedTurn && changes == picturedChanges) {
            return;
        }
        if (picture == null || picture.getWidth() != solid.size) {
            picture = new BufferedImage(solid.size, solid.size, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) picture.getRaster().getDataBuffer()).getData();
            podBoxes = 0;
        }
        // Where the ear pieces were last time is wiped; the shell's own pixels are all written afresh.
        for (int box = 0; box < podBoxes; box++) {
            int[] area = podBox[box];
            for (int y = area[1]; y < area[3]; y++) {
                Arrays.fill(pixels, y * solid.size + area[0], y * solid.size + area[2], 0);
            }
        }
        podBoxes = 0;
        a.fit(solid);
        if (b == null) {
            render(solid, a, turn8, pixels);
        } else {
            b.fit(solid);
            render(solid, a, b, mix, turn8, pixels);
        }
        // The ear pieces are placed by the same turn as the skin, to the same 256th of a texel.
        double blend = mix / 256.0;
        Look p = b == null ? a.look : from.look;
        Look q = b == null ? a.look : to.look;
        pods(solid, -turn8 / 256.0 / SKIN_W * 2 * Math.PI, p.dust() + (q.dust() - p.dust()) * blend,
                p.frost() + (q.frost() - p.frost()) * blend);
        pictured = solid;
        picturedFrom = a;
        picturedTo = b;
        picturedMix = mix;
        picturedTurn = turn8;
        picturedChanges = changes;
    }

    /** One skin: the frame that is drawn again and again while the helmet turns. */
    private static void render(Solid s, Skin skin, int turn8, int[] out) {
        final int[] tex = skin.texels;
        final int[] glass = skin.glass;
        final int[] over = skin.over;
        final int[] texU = s.texU;
        final int[] texV = s.texV;
        final int[] light = s.light;
        final byte[] cover = s.cover;
        final int size = s.size;
        for (int y = 0; y < size; y++) {
            int end = y * size + s.last[y];
            for (int i = y * size + s.first[y]; i < end; i++) {
                int c = cover[i] & 0xFF;
                if (c == 0) {
                    out[i] = 0;
                    continue;
                }
                int u = (texU[i] + turn8) & U_MASK;
                int v = texV[i];
                int paint = sample(tex, (v >> 8) * STRIDE + (u >> 8), u & 0xFF, v & 0xFF);
                int alpha = paint >>> 24;
                int l = light[i];
                int rgb = ((((paint >> 16) & 0xFF) * (l >>> 18)) >> 8) << 16
                        | ((((paint >> 8) & 0xFF) * ((l >> 9) & 0x1FF)) >> 8) << 8
                        | (((paint & 0xFF) * (l & 0x1FF)) >> 8);
                if (alpha < 255) {
                    rgb += scaled(glass[i], 256 - alpha - (alpha >> 7));
                }
                int o = over[i];
                int oa = o >>> 24;
                if (oa != 0) {
                    rgb = scaled(rgb, 256 - oa - (oa >> 7)) + (o & 0xFFFFFF);
                }
                out[i] = c << 24 | rgb;
            }
        }
    }

    /** Two skins, {@code mix} 256ths of the way from the first to the second: the frame while the look is changing. */
    private static void render(Solid s, Skin first, Skin second, int mix, int turn8, int[] out) {
        final int[] texA = first.texels;
        final int[] texB = second.texels;
        final int[] glassA = first.glass;
        final int[] glassB = second.glass;
        final int[] overA = first.over;
        final int[] overB = second.over;
        final int[] texU = s.texU;
        final int[] texV = s.texV;
        final int[] light = s.light;
        final byte[] cover = s.cover;
        final int size = s.size;
        for (int y = 0; y < size; y++) {
            int end = y * size + s.last[y];
            for (int i = y * size + s.first[y]; i < end; i++) {
                int c = cover[i] & 0xFF;
                if (c == 0) {
                    out[i] = 0;
                    continue;
                }
                int u = (texU[i] + turn8) & U_MASK;
                int v = texV[i];
                int at = (v >> 8) * STRIDE + (u >> 8);
                int paint = between(sample(texA, at, u & 0xFF, v & 0xFF), sample(texB, at, u & 0xFF, v & 0xFF), mix);
                int alpha = paint >>> 24;
                int l = light[i];
                int rgb = ((((paint >> 16) & 0xFF) * (l >>> 18)) >> 8) << 16
                        | ((((paint >> 8) & 0xFF) * ((l >> 9) & 0x1FF)) >> 8) << 8
                        | (((paint & 0xFF) * (l & 0x1FF)) >> 8);
                if (alpha < 255) {
                    rgb += scaled(between(glassA[i], glassB[i], mix) & 0xFFFFFF, 256 - alpha - (alpha >> 7));
                }
                int o = between(overA[i], overB[i], mix);
                int oa = o >>> 24;
                if (oa != 0) {
                    rgb = scaled(rgb, 256 - oa - (oa >> 7)) + (o & 0xFFFFFF);
                }
                out[i] = c << 24 | rgb;
            }
        }
    }

    /**
     * The skin's colour between four texels: {@code at} is the top-left one, and (fu, fv) is
     * how far toward the right-hand and the lower ones the point lies, in 256ths. Colours on
     * a skin are already multiplied by their own opacity, which is what makes this sum right.
     */
    private static int sample(int[] tex, int at, int fu, int fv) {
        int a = tex[at];
        int b = tex[at + 1];
        int c = tex[at + STRIDE];
        int d = tex[at + STRIDE + 1];
        if (a == b && a == c && a == d) {
            return a;
        }
        return between(between(a, b, fu), between(c, d, fu), fv);
    }

    /** The colour {@code share} 256ths of the way from one to the other, all four channels at once. */
    private static int between(int from, int to, int share) {
        int keep = 256 - share;
        int high = ((from >>> 8) & 0xFF00FF) * keep + ((to >>> 8) & 0xFF00FF) * share;
        int low = (from & 0xFF00FF) * keep + (to & 0xFF00FF) * share;
        return (high & 0xFF00FF00) | ((low >>> 8) & 0xFF00FF);
    }

    /** The red, green and blue of {@code rgb}, each multiplied by {@code share} 256ths. */
    private static int scaled(int rgb, int share) {
        return ((((rgb & 0xFF00FF) * share) >>> 8) & 0xFF00FF) | ((((rgb & 0x00FF00) * share) >>> 8) & 0x00FF00);
    }

    /** A slow start and a slow stop for a change that runs from 0 to 1. */
    private static double ease(double t) {
        double x = Draw.clamp(t);
        return x * x * (3 - 2 * x);
    }

    /** What is showing in the middle of a change, as a skin of its own, so that a new change can start from it. */
    private Skin mixed() {
        int mix = (int) Math.round(ease(share) * 256);
        if (scratch == null) {
            scratch = new Skin(to.look, new int[to.texels.length], new int[to.cap.length]);
        }
        Skin a = from;
        Skin b = to;
        Skin out = scratch;
        for (int i = 0; i < out.texels.length; i++) {
            out.texels[i] = between(a.texels[i], b.texels[i], mix);
        }
        for (int i = 0; i < out.cap.length; i++) {
            out.cap[i] = between(a.cap[i], b.cap[i], mix);
        }
        double t = mix / 256.0;
        Look p = a.look;
        Look q = b.look;
        out.look = new Look(p.dust() + (q.dust() - p.dust()) * t, p.frost() + (q.frost() - p.frost()) * t,
                p.storm() + (q.storm() - p.storm()) * t, p.wear() + (q.wear() - p.wear()) * t, q.mystery());
        out.solid = null; // its mirror picture is laid out afresh, from the mixed one, when next drawn
        return out;
    }

    // ---- The ear pieces -------------------------------------------------------------------

    /**
     * Draws the two ear pieces into the picture, pixel by pixel. (Drawn as Graphics2D shapes
     * they took more of a frame in the browser than the whole shell did.) An ear piece on the
     * near side of the helmet is laid over the shell's pixels and one on the far side under
     * them, so the shell hides what it should.
     */
    private void pods(Solid solid, double turn, double dust, double frost) {
        int size = solid.size;
        double scale = solid.radius;
        double line = Math.max(1, scale * 0.011);
        Color dark = Draw.mix(METAL_DARK, DUST_DARK, 0.4 * dust);
        Color bright = Draw.mix(Draw.mix(METAL_LIGHT, FROST, 0.4 * frost), DUST, 0.45 * dust);
        Color metal = Draw.mix(Draw.mix(METAL, FROST_TINT, 0.3 * frost), DUST_DARK, 0.5 * dust);
        Color socket = Draw.mix(FRAME_GREY, DUST_DARK, 0.4 * dust);
        double lineR = OUTLINE.getRed();
        double lineG = OUTLINE.getGreen();
        double lineB = OUTLINE.getBlue();
        double brightR = bright.getRed();
        double brightG = bright.getGreen();
        double brightB = bright.getBlue();
        double darkR = dark.getRed();
        double darkG = dark.getGreen();
        double darkB = dark.getBlue();
        // The stubs start from the helmet's axis, at the ear pieces' height.
        double originX = solid.cx;
        double originY = solid.cy - POD_HEIGHT * COS_P * scale;
        for (int side = -1; side <= 1; side += 2) {
            double longitude = side * Math.PI / 2 + turn;
            // The way the stub points, as the camera sees it: x to the right, y up, z toward the camera.
            double ax = Math.sin(longitude);
            double ay = -Math.cos(longitude) * SIN_P;
            double az = Math.cos(longitude) * COS_P;
            boolean near = az > 0;
            double flat = Math.sqrt(ax * ax + ay * ay);
            // On the picture (y down): the direction along the stub, away from the shell. Across it is (-ey, ex).
            double ex = flat < 1e-9 ? 1 : ax / flat;
            double ey = flat < 1e-9 ? 0 : -ay / flat;
            // The light comes from the upper left; which edge of the stub that is depends on its slant.
            boolean lightAbove = ey * LIGHT[0] + ex * LIGHT[1] > 0;
            double facing = Draw.clamp(0.55 + 0.45 * (ax * LIGHT[0] + ay * LIGHT[1] + az * LIGHT[2]));
            Color end = Draw.shade(Draw.mix(metal, bright, 0.6), 0.55 + 0.45 * facing);
            // The step next the shell first. Near the camera each step covers the one before;
            // on the far side each goes under the one before, and under the shell.
            for (int n = 0; n < POD_STEPS.length; n++) {
                double wide = POD_STEPS[n][0] * scale;
                double thin = Math.max(0.35, wide * Math.abs(az));
                double x0 = POD_STEPS[n][1] * flat * scale;
                double x1 = POD_STEPS[n][2] * flat * scale;
                boolean last = n == POD_STEPS.length - 1;
                int left = size;
                int top = size;
                int right = 0;
                int bottom = 0;
                for (int corner = 0; corner < 4; corner++) {
                    double lx = corner < 2 ? x0 - thin - 2 : x1 + thin + 2;
                    double ly = corner % 2 == 0 ? -wide - 2 : wide + 2;
                    double sx = originX + ex * lx - ey * ly;
                    double sy = originY + ey * lx + ex * ly;
                    left = Math.min(left, (int) Math.floor(sx));
                    top = Math.min(top, (int) Math.floor(sy));
                    right = Math.max(right, (int) Math.ceil(sx));
                    bottom = Math.max(bottom, (int) Math.ceil(sy));
                }
                left = Math.max(0, left);
                top = Math.max(0, top);
                right = Math.min(size, right);
                bottom = Math.min(size, bottom);
                int[] box = podBox[podBoxes++];
                box[0] = left;
                box[1] = top;
                box[2] = Math.max(left, right);
                box[3] = bottom;
                for (int y = top; y < bottom; y++) {
                    // Along the stub and across it, for the first pixel of the row; then a step at a time.
                    double dx = left + 0.5 - originX;
                    double dy = y + 0.5 - originY;
                    double lx = dx * ex + dy * ey;
                    double ly = dy * ex - dx * ey;
                    for (int x = left; x < right; x++, lx += ex, ly -= ey) {
                        int i = y * size + x;
                        if (Math.abs(ly) > wide + 0.5 || lx < x0 - thin - 0.5 || lx > x1 + thin + 0.5
                                || !near && solid.cover[i] == -1) {
                            continue; // outside the stub, or on the far side and wholly behind the shell
                        }
                        // The stub from the side: straight edges, and half an ellipse at each end.
                        double inside = lx < x0 ? insideEllipse(lx - x0, ly, thin, wide)
                                : lx > x1 ? insideEllipse(lx - x1, ly, thin, wide) : wide - Math.abs(ly);
                        if (inside <= -0.5) {
                            continue;
                        }
                        double across = Draw.clamp((ly / wide + 1) / 2);
                        double shade = lightAbove ? across : 1 - across;
                        double fill = Draw.clamp(inside - line + 0.5); // 0 on the dark outline, 1 inside it
                        double r = lineR + (brightR + (darkR - brightR) * shade - lineR) * fill;
                        double g = lineG + (brightG + (darkG - brightG) * shade - lineG) * fill;
                        double b = lineB + (brightB + (darkB - brightB) * shade - lineB) * fill;
                        if (near) {
                            // The step's flat end faces the camera: a lighter disc, and in the last one a dark socket.
                            double disc = insideEllipse(lx - x1, ly, thin, wide);
                            if (disc > -0.5) {
                                double discFill = Draw.clamp(disc - line + 0.5);
                                double cr = lineR + (end.getRed() - lineR) * discFill;
                                double cg = lineG + (end.getGreen() - lineG) * discFill;
                                double cb = lineB + (end.getBlue() - lineB) * discFill;
                                if (last) {
                                    double hole = Draw.clamp(insideEllipse(lx - x1, ly, thin / 2, wide / 2) + 0.5);
                                    cr += (socket.getRed() - cr) * hole;
                                    cg += (socket.getGreen() - cg) * hole;
                                    cb += (socket.getBlue() - cb) * hole;
                                }
                                double over = Math.min(1, disc + 0.5);
                                r += (cr - r) * over;
                                g += (cg - g) * over;
                                b += (cb - b) * over;
                            }
                        }
                        lay(pixels, i, r, g, b, Math.min(1, inside + 0.5), near);
                    }
                }
            }
        }
    }

    /**
     * About how far inside an ellipse a point is, in the units of its radii {@code a} and
     * {@code b}: more than 0 inside, less outside. Close to the true distance near the edge,
     * which is where it is used (to smooth the edge and to draw an outline along it).
     */
    private static double insideEllipse(double x, double y, double a, double b) {
        double level = 1 - x * x / (a * a) - y * y / (b * b);
        double steep = 2 * Math.sqrt(x * x / (a * a * a * a) + y * y / (b * b * b * b));
        return steep < 1e-9 ? Math.min(a, b) : level / steep;
    }

    /**
     * Lays a colour with this much opacity (0 to 1) on one pixel of the picture: over what
     * is there, or, with {@code over} false, under it.
     */
    private static void lay(int[] out, int i, double r, double g, double b, double alpha, boolean over) {
        int there = out[i];
        double below = (there >>> 24) / 255.0;
        if (!over && below >= 1) {
            return;
        }
        double total = over ? alpha + below * (1 - alpha) : below + alpha * (1 - below);
        if (total <= 0) {
            return;
        }
        double keep = (over ? below * (1 - alpha) : below) / total;
        out[i] = whole(total * 255) << 24
                | whole(((there >> 16) & 0xFF) * keep + r * (1 - keep)) << 16
                | whole(((there >> 8) & 0xFF) * keep + g * (1 - keep)) << 8
                | whole((there & 0xFF) * keep + b * (1 - keep));
    }

    /**
     * The whole number nearest to a value that is 0 or more. (Math.round gives a 64-bit
     * number, which the browser handles slowly: too slowly for work done pixel by pixel.)
     */
    private static int whole(double value) {
        return (int) (value + 0.5);
    }

    // ---- A solid: the helmet's shape at one size of picture --------------------------------

    /** The solid for a picture {@code count} pixels across, from the ones kept if it has been worked out before. */
    private static Solid solid(int count) {
        Solid solid = SOLIDS.get(count);
        if (solid == null) {
            solid = new Solid(count);
            SOLIDS.put(count, solid);
            trim(SOLIDS, MOST_SOLIDS);
        }
        return solid;
    }

    /** What one ray from the camera meets. */
    private static final class Hit {
        static final int NOTHING = 0;
        static final int SHELL_PART = 1;
        static final int RING_PART = 2;
        int part;
        /** Where on the skin, in texels. */
        double u;
        double v;
        /** The light there, red, green and blue, from 0 to 1. */
        double r;
        double g;
        double b;
        /** The shine, and how near the helmet's edge the point is: on the side the Sun is, and all round. */
        double shine;
        double sunRim;
        double allRim;
        /** Where on the mirror picture, in its pixels. */
        double capX;
        double capY;
    }

    /**
     * For every pixel of a square picture: how much of it the helmet covers, where on the
     * skin it shows when the helmet faces the front, the light there, and where the mirror
     * picture is seen in it. Worked out once for each size.
     */
    private static final class Solid {
        /** Rays to a side of an edge pixel: 16 rays for each such pixel. */
        private static final int FINE = 4;

        // The neck ring's bands, worked out once: how far along the ring's outline each
        // starts, its slope (radius gained for each unit of height) and its length for each
        // unit of height.
        private static final int BANDS = RING.length - 1;
        private static final double[] BAND_STARTS = new double[BANDS];
        private static final double[] BAND_SLOPE = new double[BANDS];
        private static final double[] BAND_SLANT = new double[BANDS];
        /** The box on the screen that holds the neck ring, in shell radii from the shell's middle. */
        private static final double RING_HALF_WIDTH;
        private static final double RING_TOP;
        private static final double RING_BOTTOM;

        static {
            double before = 0;
            for (int k = 0; k < BANDS; k++) {
                double wider = RING[k + 1][0] - RING[k][0];
                double higher = RING[k + 1][1] - RING[k][1];
                BAND_STARTS[k] = before;
                BAND_SLOPE[k] = higher == 0 ? 0 : wider / higher;
                BAND_SLANT[k] = Math.sqrt(1 + BAND_SLOPE[k] * BAND_SLOPE[k]);
                before += Math.sqrt(wider * wider + higher * higher);
            }
            double widest = 0;
            double top = -9;
            double bottom = 9;
            for (double[] point : RING) {
                widest = Math.max(widest, point[0]);
                top = Math.max(top, point[1] * COS_P + point[0] * SIN_P);
                bottom = Math.min(bottom, point[1] * COS_P - point[0] * SIN_P);
            }
            RING_HALF_WIDTH = widest + 0.01;
            RING_TOP = top + 0.01;
            RING_BOTTOM = bottom - 0.01;
        }

        final int size;
        /** The shell's radius and middle, in pixels. */
        final double radius;
        final double cx;
        final double cy;
        /** How much of each pixel the helmet covers, 0 to 255. */
        final byte[] cover;
        /** The first pixel the helmet covers in each row, and one past the last. */
        final int[] first;
        final int[] last;
        /** The column of the skin (at no turn) and its row, in 256ths of a texel. */
        final int[] texU;
        final int[] texV;
        /** The light: red, green and blue in 9 bits each, 256 being full light. */
        final int[] light;
        /** Where the mirror picture shows: its x and y in 64ths of a pixel, 16 bits each. */
        final int[] cap;
        /** The shine, as white already multiplied by its own opacity: laid over shell and glass alike. */
        final int[] shine;
        /** How near the edge each pixel is, 0 to 255: on the Sun's side, and all round. */
        final byte[] sunRim;
        final byte[] allRim;

        Solid(int size) {
            this.size = size;
            radius = size * RADIUS;
            cx = size * CENTRE_X;
            cy = size * CENTRE_Y;
            int count = size * size;
            cover = new byte[count];
            first = new int[size];
            last = new int[size];
            texU = new int[count];
            texV = new int[count];
            light = new int[count];
            cap = new int[count];
            shine = new int[count];
            sunRim = new byte[count];
            allRim = new byte[count];

            // One ray through the middle of every pixel.
            byte[] part = new byte[count];
            Hit hit = new Hit();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    trace((x + 0.5 - cx) / radius, (cy - y - 0.5) / radius, hit);
                    int i = y * size + x;
                    part[i] = (byte) hit.part;
                    if (hit.part != Hit.NOTHING) {
                        store(i, hit, 255);
                    }
                }
            }
            // Sixteen rays through each pixel on an edge, so that edges are smooth.
            int rays = FINE * FINE;
            int[] parts = new int[rays];
            double[] us = new double[rays];
            double[] vs = new double[rays];
            double[] capXs = new double[rays];
            double[] capYs = new double[rays];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int i = y * size + x;
                    if (!onEdge(part, x, y)) {
                        continue;
                    }
                    int hits = 0;
                    int shellHits = 0;
                    double r = 0;
                    double g = 0;
                    double b = 0;
                    double shine = 0;
                    double sunRim = 0;
                    double allRim = 0;
                    for (int k = 0; k < rays; k++) {
                        double px = x + (k % FINE + 0.5) / FINE;
                        double py = y + (k / FINE + 0.5) / FINE;
                        trace((px - cx) / radius, (cy - py) / radius, hit);
                        parts[k] = hit.part;
                        if (hit.part == Hit.NOTHING) {
                            continue;
                        }
                        hits++;
                        shellHits += hit.part == Hit.SHELL_PART ? 1 : 0;
                        r += hit.r;
                        g += hit.g;
                        b += hit.b;
                        shine += hit.shine;
                        sunRim += hit.sunRim;
                        allRim += hit.allRim;
                        us[k] = hit.u;
                        vs[k] = hit.v;
                        capXs[k] = hit.capX;
                        capYs[k] = hit.capY;
                    }
                    if (hits == 0) {
                        cover[i] = 0;
                        continue;
                    }
                    // The skin is looked up where the ray nearest the pixel's middle met the part most rays met.
                    int wanted = 2 * shellHits >= hits ? Hit.SHELL_PART : Hit.RING_PART;
                    int chosen = 0;
                    double nearest = Double.MAX_VALUE;
                    for (int k = 0; k < rays; k++) {
                        double dx = (k % FINE + 0.5) / FINE - 0.5;
                        double dy = (k / FINE + 0.5) / FINE - 0.5;
                        if (parts[k] == wanted && dx * dx + dy * dy < nearest) {
                            nearest = dx * dx + dy * dy;
                            chosen = k;
                        }
                    }
                    hit.part = wanted;
                    hit.u = us[chosen];
                    hit.v = vs[chosen];
                    hit.capX = capXs[chosen];
                    hit.capY = capYs[chosen];
                    hit.r = r / hits;
                    hit.g = g / hits;
                    hit.b = b / hits;
                    hit.shine = shine / hits;
                    hit.sunRim = sunRim / hits;
                    hit.allRim = allRim / hits;
                    store(i, hit, (int) Math.round(255.0 * hits / rays));
                }
            }
            for (int y = 0; y < size; y++) {
                int from = size;
                int to = 0;
                for (int x = 0; x < size; x++) {
                    if (cover[y * size + x] != 0) {
                        from = Math.min(from, x);
                        to = x + 1;
                    }
                }
                first[y] = Math.min(from, to);
                last[y] = to;
            }
        }

        /** True if the pixel or one of the eight round it shows a different part of the helmet, or none. */
        private boolean onEdge(byte[] part, int x, int y) {
            byte here = part[y * size + x];
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    byte there = nx < 0 || ny < 0 || nx >= size || ny >= size ? Hit.NOTHING : part[ny * size + nx];
                    if (there != here) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void store(int i, Hit hit, int covered) {
            cover[i] = (byte) covered;
            double column = hit.u - Math.floor(hit.u / SKIN_W) * SKIN_W;
            texU[i] = (int) (column * 256) & U_MASK;
            texV[i] = (int) (Math.max(0, Math.min(SKIN_H - 1.01, hit.v)) * 256);
            light[i] = whole(Draw.clamp(hit.r) * 256) << 18 | whole(Draw.clamp(hit.g) * 256) << 9
                    | whole(Draw.clamp(hit.b) * 256);
            cap[i] = (int) (Math.max(0, Math.min(CAP - 1.02, hit.capX)) * 64) << 16
                    | (int) (Math.max(0, Math.min(CAP - 1.02, hit.capY)) * 64);
            int white = whole(Draw.clamp(hit.shine) * 255);
            shine[i] = white << 24 | white << 16 | white << 8 | white;
            sunRim[i] = (byte) whole(Draw.clamp(hit.sunRim) * 255);
            allRim[i] = (byte) whole(Draw.clamp(hit.allRim) * 255);
        }

        /**
         * Follows the ray through the point (sx, sy) of the picture, measured from the middle
         * of the shell in shell radii with y up, and says what it meets first.
         */
        private static void trace(double sx, double sy, Hit hit) {
            // The ray in the helmet's own space: x to the right, y up its axis, z out of its front.
            double oy = sy * COS_P + 4 * SIN_P;
            double oz = -sy * SIN_P + 4 * COS_P;
            double dy = -SIN_P;
            double dz = -COS_P;
            double best = Double.MAX_VALUE;
            hit.part = Hit.NOTHING;
            double nx = 0;
            double ny = 0;
            double nz = 0;
            double shade = 1;
            double along = 0;

            // The shell: a ball stretched to TALL along its axis.
            double ey = oy / TALL;
            double edy = dy / TALL;
            double a = edy * edy + dz * dz;
            double b = ey * edy + oz * dz;
            double c = sx * sx + ey * ey + oz * oz - 1;
            double root = b * b - a * c;
            if (root > 0) {
                double t = (-b - Math.sqrt(root)) / a;
                double y = oy + t * dy;
                if (y >= CUT) {
                    best = t;
                    hit.part = Hit.SHELL_PART;
                    nx = sx;
                    ny = y / (TALL * TALL);
                    nz = oz + t * dz;
                    // The neck ring shades the shell just above it.
                    double clear = Draw.clamp((y - CUT) / 0.2);
                    shade = 0.52 + 0.48 * clear * (2 - clear);
                }
            }
            // The neck ring: each stretch of its outline, turned about the axis, is a band.
            // It is low and narrow, so most rays cannot meet it and are not tried against it.
            boolean nearRing = sy < RING_TOP && sy > RING_BOTTOM && Math.abs(sx) < RING_HALF_WIDTH;
            for (int k = 0; nearRing && k < BANDS; k++) {
                double r0 = RING[k][0];
                double y0 = RING[k][1];
                double r1 = RING[k + 1][0];
                double y1 = RING[k + 1][1];
                if (y0 == y1) {
                    // A flat band: it is seen only if it faces up, toward the camera.
                    double t = (y0 - oy) / dy;
                    double z = oz + t * dz;
                    double squared = sx * sx + z * z;
                    if (r1 > r0 && t < best && squared >= r0 * r0 && squared <= r1 * r1) {
                        double r = Math.sqrt(squared);
                        best = t;
                        hit.part = Hit.RING_PART;
                        nx = 0;
                        ny = 1;
                        nz = 0;
                        along = BAND_STARTS[k] + (r - r0);
                        shade = 0.55 + 0.45 * Draw.clamp((r - 0.648) / 0.05);
                    }
                } else {
                    // A band that slopes: radius = q0 + q1 * t along the ray.
                    double slope = BAND_SLOPE[k];
                    double q0 = r0 + slope * (oy - y0);
                    double q1 = slope * dy;
                    double qa = dz * dz - q1 * q1;
                    double qb = oz * dz - q0 * q1;
                    double qc = sx * sx + oz * oz - q0 * q0;
                    double under = qb * qb - qa * qc;
                    if (under >= 0 && Math.abs(qa) > 1e-12) {
                        double spread = Math.sqrt(under);
                        for (int which = -1; which <= 1; which += 2) {
                            double t = (-qb + which * spread) / qa;
                            double y = oy + t * dy;
                            double r = q0 + q1 * t;
                            if (t >= best || r <= 0 || y > Math.max(y0, y1) || y < Math.min(y0, y1)) {
                                continue;
                            }
                            double z = oz + t * dz;
                            double slant = BAND_SLANT[k];
                            double mx = sx / r / slant;
                            double my = -slope / slant;
                            double mz = z / r / slant;
                            if (my * SIN_P + mz * COS_P <= 0) {
                                continue; // faces away from the camera
                            }
                            best = t;
                            hit.part = Hit.RING_PART;
                            nx = mx;
                            ny = my;
                            nz = mz;
                            along = BAND_STARTS[k] + Math.abs(y - y0) * slant;
                            shade = k == 3 || k == 4 ? 0.8 : 1;
                        }
                    }
                }
            }
            if (hit.part == Hit.NOTHING) {
                return;
            }
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= length;
            ny /= length;
            nz /= length;
            double z = oz + best * dz;
            double y = oy + best * dy;
            hit.u = (Math.atan2(sx, z) / (2 * Math.PI) + 0.5) * SKIN_W;
            hit.v = hit.part == Hit.SHELL_PART
                    ? row(Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, y / TALL)))))
                    : RING_ROW + along * PER_UNIT;

            // The way the surface faces, as the camera sees it.
            double fx = nx;
            double fy = ny * COS_P - nz * SIN_P;
            double fz = ny * SIN_P + nz * COS_P;
            double lit = Draw.clamp((fx * LIGHT[0] + fy * LIGHT[1] + fz * LIGHT[2] + 0.42) / 1.42);
            lit = lit * (1.1 - 0.1 * lit);
            // The sky lights the top a cool colour and the ground warms the underside.
            double under = Math.max(0, -ny) * (1 - lit);
            double edge = 1 - Math.max(0, fz);
            double limb = 1 - 0.34 * edge * edge * edge * edge;
            hit.r = (0.50 + 0.50 * lit + 0.20 * under) * limb * shade;
            hit.g = (0.55 + 0.435 * lit + 0.09 * under) * limb * shade;
            hit.b = (0.70 + 0.26 * lit + 0.00 * under) * limb * shade;
            // The shine: a small hard spot (the 110th power) in a wide soft one (the 12th). The
            // powers are multiplied out, because Math.pow is very slow in the browser.
            double toward = Math.max(0, fx * HALF[0] + fy * HALF[1] + fz * HALF[2]);
            double t2 = toward * toward;
            double t4 = t2 * t2;
            double t8 = t4 * t4;
            double t12 = t8 * t4;
            double t32 = t8 * t8 * t8 * t8;
            hit.shine = 0.80 * (t32 * t32 * t32 * t12 * t2) + 0.10 * t12;
            double flat = Math.sqrt(fx * fx + fy * fy);
            double sunSide = flat < 1e-9 ? 0 : Draw.clamp(0.5 + 0.5 * (fx * -0.62 + fy * 0.78) / flat);
            hit.sunRim = hit.part == Hit.SHELL_PART ? edge * edge * sunSide * sunSide : 0;
            hit.allRim = edge * edge * edge;
            hit.capX = (fx * 0.5 + 0.5) * (CAP - 1);
            hit.capY = (0.5 - fy * 0.5) * (CAP - 1);
        }
    }

    // ---- A skin: what is painted on the helmet, and what its glass mirrors -------------------

    /** The skin of this look, from the ones kept if it has been painted before. */
    private static Skin skin(Look look, boolean keep) {
        Skin skin = SKINS.get(look);
        if (skin == null) {
            skin = new Skin(look, paintSkin(look), paintCap(look));
            if (keep) {
                SKINS.put(look, skin);
                trim(SKINS, MOST_SKINS);
            }
        }
        return skin;
    }

    private static final class Skin {
        /** The look this is the skin of; for a mix, the dials in between. */
        Look look;
        /**
         * The surface unwrapped, STRIDE texels by SKIN_H + 1: alpha is how much paint (or
         * frost, or dust) covers the point, and the colour is already multiplied by it.
         * Where alpha is 0 the surface is clear glass.
         */
        final int[] texels;
        /** The mirror picture, CAP by CAP. */
        final int[] cap;
        /** For one size of picture: the mirror picture as it lies on the screen, and what is laid over everything. */
        Solid solid;
        int[] glass;
        int[] over;

        Skin(Look look, int[] texels, int[] cap) {
            this.look = look;
            this.texels = texels;
            this.cap = cap;
        }

        /** Lays the mirror picture out for this size of picture, if it is not laid out for it already. */
        void fit(Solid s) {
            if (solid == s) {
                return;
            }
            int count = s.size * s.size;
            if (glass == null || glass.length != count) {
                glass = new int[count];
            }
            for (int i = 0; i < count; i++) {
                if (s.cover[i] == 0) {
                    continue;
                }
                int at = s.cap[i];
                int x = at >>> 16;
                int y = at & 0xFFFF;
                int p = (y >> 6) * CAP + (x >> 6);
                int fx = (x & 63) << 2;
                int fy = (y & 63) << 2;
                glass[i] = between(between(cap[p], cap[p + 1], fx), between(cap[p + CAP], cap[p + CAP + 1], fx), fy)
                        & 0xFFFFFF;
            }
            // A solar storm lights the edge nearest the Sun pink; frost whitens the edge all
            // round; and a coat of dust takes most of the shine off.
            int pink = (int) Math.round(205 * Draw.clamp(look.storm()));
            int white = (int) Math.round(170 * Draw.clamp(look.frost()));
            int gloss = (int) Math.round(256 * (1 - 0.8 * Draw.clamp(look.dust())));
            if (pink == 0 && white == 0 && gloss == 256) {
                over = s.shine;
            } else {
                over = new int[count];
                for (int i = 0; i < count; i++) {
                    if (s.cover[i] == 0) {
                        continue;
                    }
                    int rim = tinted(STORM_PINK, (s.sunRim[i] & 0xFF) * pink / 255);
                    int ice = tinted(FROST, (s.allRim[i] & 0xFF) * white / 255);
                    over[i] = above(between(0, s.shine[i], gloss), above(ice, rim));
                }
            }
            solid = s;
        }

        /** The colour at this opacity, multiplied by it. */
        private static int tinted(Color colour, int alpha) {
            return alpha << 24 | (colour.getRed() * alpha / 255) << 16 | (colour.getGreen() * alpha / 255) << 8
                    | colour.getBlue() * alpha / 255;
        }

        /** One see-through colour laid over another; both are already multiplied by their opacity. */
        private static int above(int top, int bottom) {
            int alpha = top >>> 24;
            int keep = 256 - alpha - (alpha >> 7);
            int high = ((bottom >>> 8) & 0xFF00FF) * keep;
            int low = (bottom & 0xFF00FF) * keep;
            return ((high & 0xFF00FF00) | ((low >>> 8) & 0xFF00FF)) + top;
        }
    }

    /** The row of the skin that shows the shell at this latitude, in degrees up from its middle. */
    private static double row(double latitude) {
        return (90 - latitude) * PER_DEGREE;
    }

    /** The column of the skin that shows this longitude, in degrees to the right of the front. */
    private static double column(double longitude) {
        return SKIN_W / 2.0 + longitude * PER_DEGREE;
    }

    private static Graphics2D pen(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    /** Paints the unwrapped surface of a helmet with this look. */
    private static int[] paintSkin(Look look) {
        BufferedImage image = new BufferedImage(STRIDE, SKIN_H + 1, BufferedImage.TYPE_INT_ARGB_PRE);
        int[] texels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        if (bareShell == null) {
            bareShell = paintBareShell();
        }
        System.arraycopy(bareShell, 0, texels, 0, texels.length);
        double dust = look.dust();
        double frost = look.frost();
        double wear = look.wear();
        // The big even coats of colour are laid straight into the texels, and the small
        // marks are drawn with Graphics2D onto the same texels: both land at once, in order.
        Graphics2D g = pen(image);
        try {
            if (frost > 0) {
                shellFrost(g, texels, frost);
            }
            if (dust > 0) {
                shellDust(g, texels, dust);
            }
            if (wear > 0) {
                shellWear(g, wear);
            }
            // The visor: a dark frame, and a window of clear glass cut through the paint.
            g.setColor(Draw.mix(FRAME_GREY, DUST_DARK, 0.45 * dust));
            g.fill(FRAME);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(OUTLINE);
            g.draw(FRAME);
            g.setComposite(AlphaComposite.Clear);
            g.fill(GLASS);
            g.setComposite(AlphaComposite.SrcOver);
            // The glass is darker toward its rim, so that it reads as curved.
            rimFade(texels, GLASS_SHADE, 38, 0.57, 0.4);
            Shape whole = g.getClip();
            g.clip(GLASS);
            if (look.mystery()) {
                question(g);
            } else {
                if (dust > 0) {
                    visorDust(g, texels, dust);
                }
                if (wear > 0) {
                    visorWear(g, wear);
                }
                if (frost > 0) {
                    visorFrost(g, texels, frost);
                }
            }
            g.setClip(whole);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(OUTLINE);
            g.draw(GLASS);
        } finally {
            g.dispose();
        }
        closeRound(texels);
        return texels;
    }

    /**
     * Lays a see-through colour over whole rows of a skin, straight into its texels: rows
     * {@code fromRow} up to {@code toRow}, with the colour and its opacity changing evenly
     * from row {@code first} to row {@code last} and staying as they are beyond those. (A
     * gradient fill does the same, far more slowly in the browser.)
     */
    private static void wash(int[] texels, int fromRow, int toRow, double first, double last,
                             Color firstColour, double firstAlpha, Color lastColour, double lastAlpha) {
        for (int y = fromRow; y < toRow; y++) {
            double t = last <= first ? 0 : Draw.clamp((y - first) / (last - first));
            int alpha = (int) Math.round(firstAlpha + (lastAlpha - firstAlpha) * t);
            if (alpha <= 0) {
                continue;
            }
            int colour = Skin.tinted(Draw.mix(firstColour, lastColour, t), Math.min(255, alpha));
            int end = (y + 1) * STRIDE;
            for (int i = y * STRIDE; i < end; i++) {
                texels[i] = Skin.above(colour, texels[i]);
            }
        }
    }

    /**
     * How far inside the glass of the visor the middle of a texel is, measured from the
     * rim, in texels; less than 0 outside it.
     */
    private static double insideGlass(int x, int y) {
        double round = VISOR_ARC / 2;
        double qx = Math.abs(x + 0.5 - VISOR_X) - (VISOR_HALF_W - round);
        double qy = Math.abs(y + 0.5 - VISOR_Y) - (VISOR_HALF_H - round);
        double ox = Math.max(qx, 0);
        double oy = Math.max(qy, 0);
        return round - Math.sqrt(ox * ox + oy * oy) - Math.min(Math.max(qx, qy), 0);
    }

    /**
     * Lays a colour over the glass along its rim, fading toward the middle: {@code peak} of
     * it at the rim, none {@code reach} texels in, and a little more than a straight fade in
     * between ({@code bulge}). Worked out texel by texel, so the fade has no steps.
     */
    private static void rimFade(int[] texels, Color colour, double reach, double peak, double bulge) {
        for (int y = (int) GLASS.getY(); y <= (int) GLASS.getMaxY(); y++) {
            for (int x = (int) GLASS.getX(); x <= (int) GLASS.getMaxX(); x++) {
                double inside = insideGlass(x, y);
                if (inside <= 0 || inside >= reach) {
                    continue;
                }
                double near = 1 - inside / reach;
                double alpha = peak * near * (1 + bulge * (1 - near));
                texels[y * STRIDE + x] = Skin.above(Skin.tinted(colour, whole(255 * alpha)),
                        texels[y * STRIDE + x]);
            }
        }
    }

    /** Lays a see-through colour over all the glass, changing evenly from its top to its bottom. */
    private static void glassWash(int[] texels, Color topColour, double topAlpha, Color bottomColour, double bottomAlpha) {
        for (int y = (int) GLASS.getY(); y <= (int) GLASS.getMaxY(); y++) {
            double t = Draw.clamp((y - GLASS.getY()) / GLASS.getHeight());
            int colour = Skin.tinted(Draw.mix(topColour, bottomColour, t),
                    (int) Math.round(topAlpha + (bottomAlpha - topAlpha) * t));
            for (int x = (int) GLASS.getX(); x <= (int) GLASS.getMaxX(); x++) {
                if (insideGlass(x, y) > 0) {
                    texels[y * STRIDE + x] = Skin.above(colour, texels[y * STRIDE + x]);
                }
            }
        }
    }

    /** Copies the first column into the spare one and the last row into the spare one, for the look-up's neighbours. */
    private static void closeRound(int[] texels) {
        for (int y = 0; y <= SKIN_H; y++) {
            texels[y * STRIDE + SKIN_W] = texels[y * STRIDE];
        }
        System.arraycopy(texels, (SKIN_H - 1) * STRIDE, texels, SKIN_H * STRIDE, STRIDE);
    }

    /** Fills a shape on the skin, and again a whole turn away if it crosses the join at the back of the helmet. */
    private static void fill(Graphics2D g, Shape shape) {
        g.fill(shape);
        Rectangle2D bounds = shape.getBounds2D();
        if (bounds.getMinX() < 0) {
            g.translate(SKIN_W, 0);
            g.fill(shape);
            g.translate(-SKIN_W, 0);
        }
        if (bounds.getMaxX() > SKIN_W) {
            g.translate(-SKIN_W, 0);
            g.fill(shape);
            g.translate(SKIN_W, 0);
        }
    }

    /** Draws a line on the skin, and again a whole turn away if it comes near the join at the back. */
    private static void draw(Graphics2D g, Shape shape) {
        g.draw(shape);
        Rectangle2D bounds = shape.getBounds2D();
        if (bounds.getMinX() < 8) {
            g.translate(SKIN_W, 0);
            g.draw(shape);
            g.translate(-SKIN_W, 0);
        }
        if (bounds.getMaxX() > SKIN_W - 8) {
            g.translate(-SKIN_W, 0);
            g.draw(shape);
            g.translate(SKIN_W, 0);
        }
    }

    /**
     * Marks of one colour gathered into two shapes, the fainter marks and the stronger, so
     * that some hundreds of small marks cost two drawing calls: in the browser every call is
     * slow, however small the mark, and a call that covers the whole skin slower still.
     */
    private static final class Marks {
        private static final int STRENGTHS = 2;
        private final Path2D.Double[] shapes = new Path2D.Double[STRENGTHS];
        private final Color colour;
        private final int faintest;
        private final int strongest;

        /** Marks of this colour whose opacities, of 255, run from {@code faintest} to {@code strongest}. */
        Marks(Color colour, int faintest, int strongest) {
            this.colour = colour;
            this.faintest = faintest;
            this.strongest = strongest;
            for (int k = 0; k < STRENGTHS; k++) {
                shapes[k] = new Path2D.Double();
            }
        }

        /** Adds a mark of about this opacity, and its copy a whole turn away if it reaches the join at the back of the helmet. */
        void add(Shape mark, int alpha) {
            int strength = strongest <= faintest ? 0
                    : Math.max(0, Math.min(STRENGTHS - 1, (alpha - faintest) * STRENGTHS / (strongest - faintest + 1)));
            Path2D.Double shape = shapes[strength];
            shape.append(mark, false);
            Rectangle2D bounds = mark.getBounds2D();
            if (bounds.getMinX() < 8) {
                shape.append(mark.getPathIterator(AffineTransform.getTranslateInstance(SKIN_W, 0)), false);
            }
            if (bounds.getMaxX() > SKIN_W - 8) {
                shape.append(mark.getPathIterator(AffineTransform.getTranslateInstance(-SKIN_W, 0)), false);
            }
        }

        /**
         * Adds a straight line of this width as a mark: a thin four-sided shape, to be filled
         * with the rest. (Lines drawn with a stroke are many times slower in the browser.)
         */
        void line(double x0, double y0, double x1, double y1, double width, int alpha) {
            double length = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
            if (length < 1e-6) {
                return;
            }
            // Half the width along the line (so that the ends are square and lines that meet overlap) and across it.
            double ax = (x1 - x0) / length * width / 2;
            double ay = (y1 - y0) / length * width / 2;
            Path2D.Double sliver = new Path2D.Double();
            sliver.moveTo(x0 - ax - ay, y0 - ay + ax);
            sliver.lineTo(x1 + ax - ay, y1 + ay + ax);
            sliver.lineTo(x1 + ax + ay, y1 + ay - ax);
            sliver.lineTo(x0 - ax + ay, y0 - ay - ax);
            sliver.closePath();
            add(sliver, alpha);
        }

        /** Fills every mark. */
        void fill(Graphics2D g) {
            for (int k = 0; k < STRENGTHS; k++) {
                if (shapes[k].getCurrentPoint() != null) {
                    g.setColor(colour(k));
                    g.fill(shapes[k]);
                }
            }
        }

        private Color colour(int strength) {
            return Draw.alpha(colour, faintest + (strongest - faintest) * (2 * strength + 1) / (2 * STRENGTHS));
        }
    }

    /**
     * How much wider a mark must be painted at this row of the skin to come out its true
     * width on the helmet: the rows near the top go round a much smaller circle.
     */
    private static double widen(double row) {
        double latitude = Math.toRadians(90 - row / PER_DEGREE);
        return Math.min(3, 1 / Math.max(0.05, Math.cos(latitude)));
    }

    /** The helmet before any mission has touched it, without its visor: white shell, stripe, seams, vent, neck ring. */
    private static int[] paintBareShell() {
        BufferedImage image = new BufferedImage(STRIDE, SKIN_H + 1, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = pen(image);
        try {
            g.setColor(SHELL);
            g.fillRect(0, 0, STRIDE, RING_ROW);

            // A blue stripe over the top, from the visor to the vent at the back.
            g.setColor(STRIPE);
            double capRow = row(Math.toDegrees(Math.acos(STRIPE_HALF)));
            g.fill(new Rectangle2D.Double(0, 0, STRIDE, capRow + 0.5));
            g.fill(stripe(column(0), 9));
            g.fill(stripe(column(-180), 12));
            g.fill(stripe(column(180), 12));

            // The seam where the shell goes into its collar.
            g.setStroke(new BasicStroke(3f));
            g.setColor(Draw.alpha(SEAM, 190));
            g.draw(new Line2D.Double(0, row(-40), STRIDE, row(-40)));

            // The vent at the back: a dark panel with slats.
            RoundRectangle2D vent = new RoundRectangle2D.Double(column(-180) - 50, row(10), 100, row(-18) - row(10), 22, 22);
            g.setColor(FRAME_GREY);
            fill(g, vent);
            g.setStroke(new BasicStroke(1.6f));
            g.setColor(OUTLINE);
            draw(g, vent);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int slat = 0; slat < 4; slat++) {
                double y = row(10) + (row(-18) - row(10)) * (slat + 1) / 5.0;
                g.setColor(new Color(40, 44, 62));
                draw(g, new Line2D.Double(column(-180) - 34, y, column(-180) + 34, y));
            }
            // Two round sockets low on the back, one each side of the vent.
            for (int side = -1; side <= 1; side += 2) {
                double x = column(side * 138);
                double y = row(-27);
                double wide = 19 * widen(y);
                g.setColor(METAL);
                g.fill(new Ellipse2D.Double(x - wide, y - 19, 2 * wide, 38));
                g.setStroke(new BasicStroke(1.6f));
                g.setColor(OUTLINE);
                g.draw(new Ellipse2D.Double(x - wide, y - 19, 2 * wide, 38));
                g.setColor(FRAME_GREY);
                g.fill(new Ellipse2D.Double(x - wide * 0.55, y - 10.5, 2 * wide * 0.55, 21));
            }
            // A small grille under the visor, where the astronaut's voice comes out.
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Draw.alpha(FRAME_GREY, 220));
            for (int line = 0; line < 3; line++) {
                double y = row(-29.5) + line * 7;
                double half = 22 - line * 5;
                g.draw(new Line2D.Double(column(0) - half, y, column(0) + half, y));
            }

            // The neck ring: bands of metal, a dark groove and a row of bolts.
            Color[] bands = {new Color(168, 174, 190), METAL_LIGHT, METAL, new Color(104, 110, 130),
                new Color(86, 92, 112), new Color(172, 178, 194), METAL, new Color(110, 116, 136)};
            double top = RING_ROW;
            double[] tops = new double[RING.length];
            for (int k = 0; k + 1 < RING.length; k++) {
                tops[k] = top;
                double stretch = Math.hypot(RING[k + 1][0] - RING[k][0], RING[k + 1][1] - RING[k][1]) * PER_UNIT;
                g.setColor(bands[k]);
                g.fill(new Rectangle2D.Double(0, top - 0.5, STRIDE, stretch + 1));
                top += stretch;
            }
            tops[RING.length - 1] = top;
            g.setColor(METAL_DARK);
            g.fill(new Rectangle2D.Double(0, top, STRIDE, SKIN_H + 1 - top));
            g.setColor(Draw.alpha(Color.WHITE, 110));
            g.fill(new Rectangle2D.Double(0, tops[2], STRIDE, 2));
            g.fill(new Rectangle2D.Double(0, tops[6], STRIDE, 2));
            double boltRow = (tops[6] + tops[7]) / 2;
            for (int bolt = 0; bolt < 16; bolt++) {
                double x = (bolt + 0.5) * SKIN_W / 16.0;
                g.setColor(new Color(70, 76, 96));
                g.fill(new Ellipse2D.Double(x - 5.5, boltRow - 4, 11, 8));
                g.setColor(Draw.alpha(Color.WHITE, 120));
                g.fill(new Ellipse2D.Double(x - 3.2, boltRow - 2.8, 4, 2.6));
            }
        } finally {
            g.dispose();
        }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /**
     * The stripe as it lies on the unwrapped shell: a band of even width on the helmet,
     * centred on the longitude at column {@code centre}, from near the top down to
     * {@code toLatitude} degrees. Near the top it has to be painted much wider.
     */
    private static Path2D stripe(double centre, double toLatitude) {
        Path2D.Double path = new Path2D.Double();
        double fromLatitude = Math.toDegrees(Math.acos(STRIPE_HALF));
        boolean started = false;
        for (double latitude = fromLatitude; latitude > toLatitude - 0.25; latitude -= 0.5) {
            double at = Math.max(latitude, toLatitude);
            double half = Math.toDegrees(Math.asin(Math.min(1, STRIPE_HALF / Math.cos(Math.toRadians(at))))) * PER_DEGREE;
            if (started) {
                path.lineTo(centre - half, row(at));
            } else {
                path.moveTo(centre - half, row(at));
                started = true;
            }
        }
        for (double latitude = toLatitude; latitude < fromLatitude + 0.25; latitude += 0.5) {
            double at = Math.min(latitude, fromLatitude);
            double half = Math.toDegrees(Math.asin(Math.min(1, STRIPE_HALF / Math.cos(Math.toRadians(at))))) * PER_DEGREE;
            path.lineTo(centre + half, row(at));
        }
        path.closePath();
        return path;
    }

    /** A cold shell: a blue cast, and a sparkle of ice all over it. */
    private static void shellFrost(Graphics2D g, int[] texels, double frost) {
        wash(texels, 0, RING_ROW, 0, 0, FROST_TINT, 120 * frost, FROST_TINT, 120 * frost);
        wash(texels, RING_ROW, SKIN_H + 1, 0, 0, FROST_TINT, 90 * frost, FROST_TINT, 90 * frost);
        Random random = new Random(907);
        int sparkles = (int) Math.round(80 * frost);
        Marks stars = new Marks(new Color(150, 190, 240), 150, 239);
        for (int i = 0; i < sparkles; i++) {
            double x = random.nextDouble() * SKIN_W;
            double y = 30 + random.nextDouble() * (SHELL_ROWS - 40);
            double arm = (0.9 + random.nextDouble() * 1.3) * MARK;
            double wide = widen(y);
            int alpha = 150 + random.nextInt(90);
            stars.line(x - arm * wide, y, x + arm * wide, y, 2.2, alpha);
            stars.line(x, y - arm, x, y + arm, 2.2, alpha);
            stars.line(x - arm * 0.6 * wide, y - arm * 0.6, x + arm * 0.6 * wide, y + arm * 0.6, 2.2, alpha);
            stars.line(x - arm * 0.6 * wide, y + arm * 0.6, x + arm * 0.6 * wide, y - arm * 0.6, 2.2, alpha);
        }
        stars.fill(g);
    }

    /** Orange dust caked on the shell: a coat that is thickest low down, clumps, and grains. */
    private static void shellDust(Graphics2D g, int[] texels, double dust) {
        wash(texels, 0, RING_ROW, 20, SHELL_ROWS - 20, DUST, 64 * dust, DUST_DARK, 240 * dust);
        wash(texels, RING_ROW, SKIN_H + 1, 0, 0, DUST_DARK, 185 * dust, DUST_DARK, 185 * dust);
        Random random = new Random(3331);
        int clumps = (int) Math.round(80 * dust);
        Marks pale = new Marks(DUST, 60, 139);
        Marks deep = new Marks(DUST_DARK, 60, 139);
        for (int i = 0; i < clumps; i++) {
            double x = random.nextDouble() * SKIN_W;
            double y = 30 + Math.sqrt(random.nextDouble()) * (SHELL_ROWS - 40);
            double w = (4 + random.nextDouble() * 11) * MARK * widen(y);
            double h = (3 + random.nextDouble() * 6) * MARK;
            Marks kind = random.nextInt(3) == 0 ? deep : pale;
            kind.add(new Ellipse2D.Double(x - w / 2, y - h / 2, w, h), 60 + random.nextInt(80));
        }
        pale.fill(g);
        deep.fill(g);
        int grains = (int) Math.round(240 * dust);
        Marks specks = new Marks(DUST_GRAIN, 120, 219);
        for (int i = 0; i < grains; i++) {
            double x = random.nextDouble() * SKIN_W;
            double y = 30 + Math.sqrt(random.nextDouble()) * (SKIN_H - 40);
            double size = (0.5 + random.nextDouble() * 1.0) * MARK;
            int alpha = 120 + random.nextInt(100);
            specks.add(new Ellipse2D.Double(x, y, size * widen(y), size), alpha);
        }
        specks.fill(g);
    }

    /** Scuffs and scratches on the shell. */
    private static void shellWear(Graphics2D g, double wear) {
        Random random = new Random(5521);
        int scuffs = (int) Math.round(22 * wear);
        Marks smudges = new Marks(new Color(70, 70, 86), 60, 109);
        for (int i = 0; i < scuffs; i++) {
            double x = random.nextDouble() * SKIN_W;
            double y = 50 + random.nextDouble() * (SHELL_ROWS - 80);
            double w = (5 + random.nextDouble() * 9) * MARK;
            double h = (1.6 + random.nextDouble() * 2.6) * MARK;
            AffineTransform place = AffineTransform.getTranslateInstance(x, y);
            place.scale(widen(y), 1);
            place.rotate(-0.9 + random.nextDouble() * 1.8);
            int alpha = 60 + random.nextInt(50);
            smudges.add(place.createTransformedShape(new Ellipse2D.Double(-w / 2, -h / 2, w, h)), alpha);
        }
        smudges.fill(g);
        // Each scratch is a dark line with a thin bright one beside it, where the light catches its edge.
        int scratches = (int) Math.round(48 * wear);
        Marks cuts = new Marks(new Color(52, 50, 64), 170, 170);
        Marks glints = new Marks(Color.WHITE, 150, 150);
        for (int i = 0; i < scratches; i++) {
            double x = random.nextDouble() * SKIN_W;
            double y = 50 + random.nextDouble() * (SHELL_ROWS - 80);
            double angle = -1.2 + random.nextDouble() * 2.4;
            double length = (3 + random.nextDouble() * 8) * MARK;
            double dx = Math.cos(angle) * length * widen(y);
            double dy = Math.sin(angle) * length;
            cuts.line(x, y, x + dx, y + dy, 2.8, 170);
            glints.line(x + 1.6, y + 2.4, x + dx + 1.6, y + dy + 2.4, 1.5, 150);
        }
        cuts.fill(g);
        glints.fill(g);
    }

    /**
     * "Surprise me": nobody knows what the visor will show. The question mark is drawn as a
     * hook and a dot, not taken from a font, so it is the same shape on every computer.
     */
    private static void question(Graphics2D g) {
        Path2D.Double hook = new Path2D.Double();
        hook.moveTo(VISOR_X - 25, VISOR_Y - 30);
        hook.curveTo(VISOR_X - 25, VISOR_Y - 62, VISOR_X + 25, VISOR_Y - 62, VISOR_X + 25, VISOR_Y - 30);
        hook.curveTo(VISOR_X + 25, VISOR_Y - 8, VISOR_X, VISOR_Y - 8, VISOR_X, VISOR_Y + 16);
        g.setColor(Glyphs.POWER);
        g.setStroke(new BasicStroke(20f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(hook);
        g.fill(new Ellipse2D.Double(VISOR_X - 12, VISOR_Y + 36, 24, 24));
    }

    /** Frost growing in from the rim of the visor: a white edge, then crystals reaching for the middle. */
    private static void visorFrost(Graphics2D g, int[] texels, double frost) {
        double reach = (2.5 + 8.5 * frost) * MARK;
        // White along the rim, thinning inward.
        rimFade(texels, FROST, reach, 0.5 + 0.29 * frost, 0.75);
        // Crystals: feathered spikes from the rim toward the middle of the glass.
        Random random = new Random(1601);
        double cx = GLASS.getCenterX();
        double cy = GLASS.getCenterY();
        int spikes = (int) Math.round(22 + 56 * frost);
        Marks crystals = new Marks(Color.WHITE, 150, 239);
        for (int i = 0; i < spikes; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double length = reach * (0.6 + 0.7 * random.nextDouble());
            double turn = (random.nextDouble() - 0.5) * 0.7;
            // Start on the rim: walk out from the middle until the point leaves the glass.
            double edge = 40;
            while (GLASS.contains(cx + Math.cos(angle) * (edge + 2), cy + Math.sin(angle) * (edge + 2)) && edge < 400) {
                edge += 2;
            }
            double x0 = cx + Math.cos(angle) * edge;
            double y0 = cy + Math.sin(angle) * edge;
            double inward = angle + Math.PI + turn;
            double x1 = x0 + Math.cos(inward) * length;
            double y1 = y0 + Math.sin(inward) * length;
            int alpha = 150 + random.nextInt(90);
            crystals.line(x0, y0, x1, y1, 2.6, alpha);
            for (int twig = 1; twig <= 3; twig++) {
                double along = twig / 4.0;
                double bx = x0 + (x1 - x0) * along;
                double by = y0 + (y1 - y0) * along;
                double twigLength = length * 0.3 * (1 - along * 0.5);
                for (int side = -1; side <= 1; side += 2) {
                    double twigAngle = inward + side * 0.9;
                    crystals.line(bx, by, bx + Math.cos(twigAngle) * twigLength, by + Math.sin(twigAngle) * twigLength,
                            2.6, alpha);
                }
            }
        }
        crystals.fill(g);
    }

    /** A film of dust on the glass, thickest at the bottom, and grains stuck to it. */
    private static void visorDust(Graphics2D g, int[] texels, double dust) {
        glassWash(texels, DUST, 30 * dust, DUST_DARK, 215 * dust);
        Random random = new Random(4057);
        int grains = (int) Math.round(70 * dust);
        Marks deep = new Marks(DUST_GRAIN, 150, 229);
        Marks pale = new Marks(DUST, 150, 229);
        for (int i = 0; i < grains; i++) {
            double x = GLASS.getX() + random.nextDouble() * GLASS.getWidth();
            double y = GLASS.getY() + Math.sqrt(random.nextDouble()) * GLASS.getHeight();
            double size = (0.5 + random.nextDouble() * 1.1) * MARK;
            Marks kind = random.nextBoolean() ? deep : pale;
            kind.add(new Ellipse2D.Double(x, y, size, size), 150 + random.nextInt(80));
        }
        deep.fill(g);
        pale.fill(g);
    }

    /** Fine scratches across the glass. */
    private static void visorWear(Graphics2D g, double wear) {
        Random random = new Random(2203);
        int scratches = (int) Math.round(11 * wear);
        Marks cuts = new Marks(new Color(255, 240, 220), 110, 110);
        for (int i = 0; i < scratches; i++) {
            double x = GLASS.getX() + 30 + random.nextDouble() * (GLASS.getWidth() - 60);
            double y = GLASS.getY() + 22 + random.nextDouble() * (GLASS.getHeight() - 44);
            double angle = -0.5 + random.nextDouble() * 1.0;
            double length = (6 + random.nextDouble() * 10) * MARK;
            cuts.line(x, y, x + Math.cos(angle) * length, y + Math.sin(angle) * length, 2, 110);
        }
        cuts.fill(g);
    }

    // ---- The mirror picture ----------------------------------------------------------------

    /**
     * What a mirrored ball shows the camera from each point of its face, worked out once: the
     * direction a ray from the camera bounces off in. The middle of the ball mirrors what is
     * behind the camera and its edge what is behind the ball, so the whole sky and ground
     * are squeezed into the disc, with the horizon bowed across it.
     */
    private static final class Mirror {
        /** How far above the far hills' crest the bounced ray points, and above the near ground's edge. */
        final float[] aboveHills = new float[CAP * CAP];
        final float[] aboveGround = new float[CAP * CAP];
        /** The sine of the bounced ray's height above the level. */
        final float[] height = new float[CAP * CAP];
        /** The angle between the bounced ray and the Sun, and the angle round the Sun, in radians. */
        final float[] fromSun = new float[CAP * CAP];
        final float[] roundSun = new float[CAP * CAP];
        /**
         * How squarely the glass faces the camera: 1 in the middle of the ball, 0 at its edge.
         * Less than 0 marks a part of the picture that is never seen and is left unpainted.
         */
        final float[] facing = new float[CAP * CAP];
        /**
         * The visor only ever slides sideways across the picture, so the glass shows no more
         * than a band of it (from about 0.50 below its middle to 0.62 above, where the whole
         * is 1): only this far above and below the middle is worked out and painted.
         */
        private static final double SEEN = 0.70;

        private static Mirror made;

        static Mirror get() {
            if (made == null) {
                made = new Mirror();
            }
            return made;
        }

        private Mirror() {
            // The Sun is where the ball's face at (-0.30, 0.25) mirrors it: up and to the left in the visor.
            double[] sun = bounce(-0.30, 0.25);
            double[] across = unit(sun[2], 0, -sun[0]);
            double[] upward = {sun[1] * across[2] - sun[2] * across[1], sun[2] * across[0] - sun[0] * across[2],
                sun[0] * across[1] - sun[1] * across[0]};
            for (int y = 0; y < CAP; y++) {
                for (int x = 0; x < CAP; x++) {
                    int i = y * CAP + x;
                    double fx = x / (CAP - 1.0) * 2 - 1;
                    double fy = 1 - y / (CAP - 1.0) * 2;
                    double far = Math.sqrt(fx * fx + fy * fy);
                    if (Math.abs(fy) > SEEN || far > 1.03) {
                        facing[i] = -1; // the glass never shows this part of the picture
                        continue;
                    }
                    if (far > 0.995) {
                        fx *= 0.995 / far;
                        fy *= 0.995 / far;
                    }
                    double[] ray = bounce(fx, fy);
                    double bearing = Math.atan2(ray[0], ray[2]);
                    double hills = 0.07 + 0.075 * Math.sin(1.9 * bearing + 0.6) + 0.04 * Math.sin(4.3 * bearing + 2.1)
                            + 0.014 * Math.sin(9.7 * bearing + 0.3);
                    double ground = -0.15 + 0.035 * Math.sin(2.9 * bearing + 4.0);
                    height[i] = (float) ray[1];
                    aboveHills[i] = (float) (ray[1] - hills);
                    aboveGround[i] = (float) (ray[1] - ground);
                    double cos = ray[0] * sun[0] + ray[1] * sun[1] + ray[2] * sun[2];
                    fromSun[i] = (float) Math.acos(Math.max(-1, Math.min(1, cos)));
                    roundSun[i] = (float) Math.atan2(ray[0] * upward[0] + ray[1] * upward[1] + ray[2] * upward[2],
                            ray[0] * across[0] + ray[1] * across[1] + ray[2] * across[2]);
                    facing[i] = (float) Math.sqrt(Math.max(0, 1 - fx * fx - fy * fy));
                }
            }
        }

        /**
         * The direction, in the world (y up, the camera to the front), that a ray from the
         * camera bounces off in when it meets the ball where its face is turned (fx, fy)
         * from the camera.
         */
        private static double[] bounce(double fx, double fy) {
            double fz = Math.sqrt(Math.max(0, 1 - fx * fx - fy * fy));
            double bx = 2 * fz * fx;
            double by = 2 * fz * fy;
            double bz = 2 * fz * fz - 1;
            return new double[] {bx, by * COS_P + bz * SIN_P, -by * SIN_P + bz * COS_P};
        }
    }

    /** Paints what the glass of a helmet with this look mirrors: the sky, the ground and the Sun. */
    private static int[] paintCap(Look look) {
        BufferedImage image = new BufferedImage(CAP, CAP, BufferedImage.TYPE_INT_RGB);
        int[] out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Mirror mirror = Mirror.get();
        double dust = look.dust();
        double frost = look.frost();
        double storm = look.storm();
        double dark = 0.7 * storm;
        Color high = Draw.mix(Draw.mix(Draw.mix(SKY_HIGH, COLD_HIGH, frost), HAZE_HIGH, dust), STORM_HIGH, dark);
        Color low = Draw.mix(Draw.mix(Draw.mix(SKY_LOW, COLD_LOW, frost), HAZE_LOW, dust), STORM_LOW, dark);
        // Thick dust hides the ground.
        Color far = Draw.mix(Draw.mix(Draw.mix(GROUND_FAR, GROUND_COLD, 0.55 * frost), HAZE_LOW, 0.85 * dust),
                STORM_HIGH, 0.4 * dark);
        Color near = Draw.mix(Draw.mix(Draw.mix(GROUND_NEAR, GROUND_COLD, 0.3 * frost), HAZE_HIGH, 0.7 * dust),
                STORM_HIGH, 0.5 * dark);
        // The Sun. Small and sharp on a clear day; white in the cold; a dim smudge in dust;
        // large, with rays and a pink ring, in a solar storm.
        double shows = 1 - 0.35 * dust;
        Color disc = Draw.mix(Draw.mix(Draw.mix(SUN, SUN_COLD, frost), SUN_DIM, 0.8 * dust), Color.WHITE, 0.6 * storm);
        Color glow = Draw.mix(Draw.mix(SUN_GLOW, SUN_COLD, frost), SUN_DIM, dust);
        Color ray = Draw.mix(SUN, Color.WHITE, 0.4);
        double discRadius = 0.15 + 0.14 * storm - 0.025 * dust;
        double haloRadius = 0.34 + 0.25 * dust + 0.17 * storm;
        double ringRadius = 0.65 + 0.55 * storm;
        double rayAlpha = (140 + 115 * storm) / 255;
        int rays = 12;
        double[] c = new double[3];
        for (int i = 0; i < out.length; i++) {
            if (mirror.facing[i] < 0) {
                continue; // never seen through the glass
            }
            if (look.mystery()) {
                double t = Draw.clamp(0.5 - mirror.height[i] * 0.6);
                set(c, MYSTERY_HIGH, 1);
                blend(c, MYSTERY_LOW, t);
            } else {
                double up = Draw.clamp(mirror.height[i] / 0.8);
                set(c, low, 1);
                blend(c, high, up * (2 - up));
                blend(c, far, Draw.clamp(0.5 - mirror.aboveHills[i] / 0.03));
                blend(c, near, Draw.clamp(0.5 - mirror.aboveGround[i] / 0.03));
                double angle = mirror.fromSun[i];
                if (storm > 0) {
                    double inRing = angle / ringRadius;
                    if (inRing < 1) {
                        double pink = inRing < 0.45 ? 235 - (235 - 130) * inRing / 0.45 : 130 * (1 - inRing) / 0.55;
                        blend(c, STORM_PINK, pink / 255 * storm);
                    }
                    // Rays, long and short by turns.
                    if (angle < 0.86 * (0.35 + 0.65 * storm) + 0.17) {
                        double step = 2 * Math.PI / rays;
                        double round = mirror.roundSun[i] - 0.2;
                        int which = (int) Math.floor(round / step + 0.5);
                        double off = Math.abs(Math.sin(round - which * step)) * angle;
                        double length = ((Math.floorMod(which, 2) == 0 ? 0.86 : 0.52) * (0.35 + 0.65 * storm) + 0.17);
                        double halfWidth = (0.048 + 0.052 * storm) * Math.max(0, 1 - angle / length);
                        if (angle < length) {
                            blend(c, ray, rayAlpha * Draw.clamp((halfWidth - off) / 0.012 + 0.5));
                        }
                    }
                }
                double halo = Draw.clamp(1 - angle / haloRadius);
                blend(c, glow, halo * halo * shows * 0.82);
                blend(c, disc, shows * Draw.clamp((discRadius - angle) / 0.02 + 0.5));
            }
            // Glass that curves away from the camera mirrors less and looks darker.
            double depth = 0.50 + 0.50 * Math.min(1, mirror.facing[i] / 0.75);
            out[i] = whole(c[0] * depth) << 16 | whole(c[1] * depth) << 8 | whole(c[2] * depth);
        }
        Graphics2D g = pen(image);
        try {
            if (storm > 0 && !look.mystery()) {
                streaks(g, storm);
            }
            // A streak of shine and a small glint: fixed on the screen, as shine on glass is.
            Path2D.Double shine = new Path2D.Double();
            shine.moveTo(capX(27), capY(40));
            shine.quadTo(capX(29), capY(28), capX(44), capY(26.5));
            shine.quadTo(capX(33), capY(31), capX(30.5), capY(41));
            shine.closePath();
            double gloss = 1 - 0.6 * dust; // dust dulls the glass
            g.setColor(new Color(255, 255, 255, (int) Math.round(120 * gloss)));
            g.fill(shine);
            g.setColor(new Color(255, 255, 255, (int) Math.round(105 * gloss)));
            g.fill(new Ellipse2D.Double(capX(66), capY(29.5), capX(70.2) - capX(66), capY(32.5) - capY(29.5)));
        } finally {
            g.dispose();
        }
        return out;
    }

    /** The pink streaks of a solar storm, slanting the way they do over the outpost. */
    private static void streaks(Graphics2D g, double storm) {
        Random random = new Random(7919);
        int count = (int) Math.round(34 * storm);
        g.setStroke(new BasicStroke(CAP * 0.02f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double x = CAP * (0.04 + random.nextDouble() * 0.96);
            double y = CAP * (0.2 + random.nextDouble() * 0.5);
            double length = CAP * (0.045 + random.nextDouble() * 0.06);
            g.setColor(new Color(255, 120 + random.nextInt(80), 220, 170 + random.nextInt(80)));
            g.draw(new Line2D.Double(x, y, x - length * 0.5, y + length));
        }
    }

    /**
     * A point given on a grid where the visor, facing the front, runs from 20 to 80 across and
     * from 23 to 65 down, as a place on the mirror picture (whose middle that visor covers).
     */
    private static double capX(double flatX) {
        return ((flatX - 50) / 30 * 0.76 * 0.5 + 0.5) * (CAP - 1);
    }

    private static double capY(double flatY) {
        return ((flatY - 44) / 21 * 0.5 * 0.5 + 0.5) * (CAP - 1);
    }

    private static void set(double[] c, Color colour, double share) {
        c[0] = colour.getRed() * share;
        c[1] = colour.getGreen() * share;
        c[2] = colour.getBlue() * share;
    }

    /** Moves the colour in {@code c} {@code share} of the way to {@code colour}. */
    private static void blend(double[] c, Color colour, double share) {
        double t = Draw.clamp(share);
        c[0] += (colour.getRed() - c[0]) * t;
        c[1] += (colour.getGreen() - c[1]) * t;
        c[2] += (colour.getBlue() - c[2]) * t;
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }
}
