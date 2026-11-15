package outpost.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import outpost.shared.Glyphs;
import outpost.shared.Helmet;

/**
 * The picture of an astronaut's helmet that stands for a mission. There is one drawing,
 * and four dials change it: dust cakes the shell orange and turns the sky in the visor to
 * brown haze, frost creeps in from the rim of the visor, a solar storm makes the Sun in the
 * visor flare among pink streaks, and wear scuffs the shell. With every dial at 0 the
 * helmet is clean and its visor shows a butterscotch sky and a small Sun.
 *
 * <p>Drawing a helmet takes gradients and clipping, which are slow in the browser, so each
 * helmet is drawn once into a picture and the picture is drawn after that (as Scene does
 * with the ground). The pictures are a cache of drawing work; no game state lives here.
 * Scuffs, frost and streaks come from fixed seeds, so a helmet looks the same every time.
 */
final class HelmetArt {

    /** Most pictures kept. The home screen needs five and the mission-start card one, at one size each. */
    private static final int MOST_PICTURES = 24;

    /** What a cached picture shows: which helmet, and at what size in screen pixels. */
    private record Key(Helmet helmet, boolean mystery, int width, int height) {}

    /** The pictures, least recently used first. Only ever touched on the Swing event thread. */
    private static final Map<Key, BufferedImage> PICTURES = new LinkedHashMap<>(32, 0.75f, true);

    // The helmet is drawn in a square 100 units across.
    private static final Ellipse2D SHELL = new Ellipse2D.Double(10, 4, 80, 82);
    private static final RoundRectangle2D COLLAR = new RoundRectangle2D.Double(26, 79, 48, 16, 9, 9);
    private static final RoundRectangle2D LEFT_POD = new RoundRectangle2D.Double(4.5, 37, 11, 22, 6, 6);
    private static final RoundRectangle2D RIGHT_POD = new RoundRectangle2D.Double(84.5, 37, 11, 22, 6, 6);
    private static final RoundRectangle2D VISOR = new RoundRectangle2D.Double(20, 23, 60, 42, 38, 38);
    /** Where the Sun is reflected in the visor. */
    private static final double SUN_X = 40;
    private static final double SUN_Y = 38;

    private static final Color OUTLINE = new Color(34, 38, 56);
    private static final Color SHELL_LIGHT = new Color(253, 253, 255);
    private static final Color SHELL_MID = new Color(232, 235, 243);
    private static final Color SHELL_SHADE = new Color(180, 188, 208);
    private static final Color METAL = new Color(150, 156, 172);
    private static final Color METAL_DARK = new Color(96, 102, 122);
    private static final Color FRAME = new Color(78, 84, 104);

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

    private static final Font QUESTION_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 38);

    /**
     * Draws the helmet in a square of the given size with its top-left corner at (x, y).
     * With {@code mystery} set the visor shows a question mark instead of the sky. The
     * picture is made in the screen's own pixels, so it is sharp at any size of window.
     */
    static void paint(Graphics2D g, Helmet helmet, boolean mystery, double x, double y, double size) {
        AffineTransform device = g.getTransform();
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0;
        if (!plain) {
            // A turned or mirrored drawing surface: let Java stretch a picture of about the right size.
            int pixels = Math.max(1, (int) Math.round(size));
            Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform place = AffineTransform.getTranslateInstance(x, y);
            place.scale(size / pixels, size / pixels);
            g.drawImage(picture(helmet, mystery, pixels, pixels), place, null);
            if (old != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
            }
            return;
        }
        int width = Math.max(1, (int) Math.round(size * device.getScaleX()));
        int height = Math.max(1, (int) Math.round(size * device.getScaleY()));
        Point2D corner = device.transform(new Point2D.Double(x, y), null);
        g.setTransform(new AffineTransform());
        g.drawImage(picture(helmet, mystery, width, height),
                (int) Math.round(corner.getX()), (int) Math.round(corner.getY()), null);
        g.setTransform(device);
    }

    /** The picture of this helmet at this size in pixels, from the cache if it has been drawn before. */
    private static BufferedImage picture(Helmet helmet, boolean mystery, int width, int height) {
        Key key = new Key(helmet, mystery, width, height);
        BufferedImage picture = PICTURES.get(key);
        if (picture == null) {
            picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = picture.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.scale(width / 100.0, height / 100.0);
                draw(g, helmet, mystery);
            } finally {
                g.dispose();
            }
            PICTURES.put(key, picture);
            // A window being dragged to a new size asks for a new picture at every size on the way.
            Iterator<Key> oldest = PICTURES.keySet().iterator();
            while (PICTURES.size() > MOST_PICTURES && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        return picture;
    }

    // ---- The drawing ----------------------------------------------------------------

    /** The one helmet, in a square 100 units across. */
    private static void draw(Graphics2D g, Helmet helmet, boolean mystery) {
        double dust = Draw.clamp(helmet.dust());
        double frost = Draw.clamp(helmet.frost());
        double storm = Draw.clamp(helmet.storm());
        double wear = Draw.clamp(helmet.wear());

        // The parts behind the shell: the ear pieces and the neck ring.
        Color metal = mix(METAL, DUST_DARK, 0.5 * dust);
        for (Shape pod : new Shape[] {LEFT_POD, RIGHT_POD}) {
            g.setColor(metal);
            g.fill(pod);
        }
        g.setPaint(new GradientPaint(0, 79, mix(METAL, FROST_TINT, 0.3 * frost), 0, 95, METAL_DARK));
        g.fill(COLLAR);
        g.setColor(Draw.alpha(Color.WHITE, 90));
        g.fill(new RoundRectangle2D.Double(30, 88.5, 40, 1.6, 1.6, 1.6));

        // The shell: white, lit from the upper left, and a little bluer in the cold.
        Color light = mix(SHELL_LIGHT, FROST, 0.5 * frost);
        Color mid = mix(SHELL_MID, FROST_TINT, 0.45 * frost);
        Color shade = mix(SHELL_SHADE, new Color(140, 168, 214), 0.5 * frost);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(36, 24), 74,
                new float[] {0f, 0.5f, 1f}, new Color[] {light, mid, shade}));
        g.fill(SHELL);

        Area body = new Area(SHELL);
        body.add(new Area(COLLAR));
        body.add(new Area(LEFT_POD));
        body.add(new Area(RIGHT_POD));
        Shape whole = g.getClip();
        g.clip(body);
        if (frost > 0) {
            shellFrost(g, frost);
        }
        if (dust > 0) {
            shellDust(g, dust);
        }
        if (wear > 0) {
            shellWear(g, wear);
        }
        if (storm > 0) {
            // The flaring Sun lights the edge of the shell pink.
            g.setStroke(new BasicStroke(7f));
            g.setPaint(new GradientPaint(14, 10, Draw.alpha(STORM_PINK, (int) Math.round(190 * storm)),
                    56, 50, Draw.alpha(STORM_PINK, 0)));
            g.draw(SHELL);
        }
        g.setClip(whole);

        // Outlines, so the white helmet stands out from a pale or a dark card alike.
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(OUTLINE);
        g.draw(LEFT_POD);
        g.draw(RIGHT_POD);
        g.draw(COLLAR);
        g.draw(SHELL);

        // The visor: a dark frame, and glass that mirrors what is in front of the astronaut.
        g.setColor(mix(FRAME, DUST_DARK, 0.45 * dust));
        g.fill(new RoundRectangle2D.Double(VISOR.getX() - 3, VISOR.getY() - 3, VISOR.getWidth() + 6,
                VISOR.getHeight() + 6, 43, 43));
        g.clip(VISOR);
        if (mystery) {
            question(g);
        } else {
            reflection(g, dust, frost, storm);
            if (storm > 0) {
                streaks(g, storm);
            }
            if (dust > 0) {
                visorDust(g, dust);
            }
            if (wear > 0) {
                visorWear(g, wear);
            }
        }
        glass(g);
        if (frost > 0 && !mystery) {
            visorFrost(g, frost);
        }
        g.setClip(whole);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(OUTLINE);
        g.draw(VISOR);
        g.draw(new RoundRectangle2D.Double(VISOR.getX() - 3, VISOR.getY() - 3, VISOR.getWidth() + 6,
                VISOR.getHeight() + 6, 43, 43));
    }

    /** The sky, the ground and the Sun as the curved visor mirrors them. */
    private static void reflection(Graphics2D g, double dust, double frost, double storm) {
        double left = VISOR.getX();
        double top = VISOR.getY();
        double right = VISOR.getMaxX();
        double bottom = VISOR.getMaxY();
        double dark = 0.7 * storm;
        Color high = mix(mix(mix(SKY_HIGH, COLD_HIGH, frost), HAZE_HIGH, dust), STORM_HIGH, dark);
        Color low = mix(mix(mix(SKY_LOW, COLD_LOW, frost), HAZE_LOW, dust), STORM_LOW, dark);
        g.setPaint(new GradientPaint(0, (float) top, high, 0, (float) top + 30, low));
        g.fill(VISOR);

        // The ground, with its horizon bowed by the curve of the glass. Thick dust hides it.
        Color far = mix(mix(mix(GROUND_FAR, GROUND_COLD, 0.55 * frost), HAZE_LOW, 0.85 * dust), STORM_HIGH, 0.4 * dark);
        Color near = mix(mix(mix(GROUND_NEAR, GROUND_COLD, 0.3 * frost), HAZE_HIGH, 0.7 * dust), STORM_HIGH, 0.5 * dark);
        Path2D.Double hills = new Path2D.Double();
        hills.moveTo(left, top + 31);
        hills.quadTo(left + 14, top + 24, left + 27, top + 28);
        hills.quadTo(left + 40, top + 31, left + 48, top + 26);
        hills.quadTo(left + 55, top + 24, right, top + 30);
        hills.lineTo(right, bottom);
        hills.lineTo(left, bottom);
        hills.closePath();
        g.setColor(far);
        g.fill(hills);
        Path2D.Double plain = new Path2D.Double();
        plain.moveTo(left, top + 35);
        plain.quadTo(left + 30, top + 30, right, top + 36);
        plain.lineTo(right, bottom);
        plain.lineTo(left, bottom);
        plain.closePath();
        g.setColor(near);
        g.fill(plain);

        // The Sun. Small and sharp on a clear day; white in the cold; a dim smudge in dust;
        // large, with rays and a pink ring, in a solar storm.
        double shows = 1 - 0.35 * dust;
        Color disc = mix(mix(SUN, SUN_COLD, frost), SUN_DIM, 0.8 * dust);
        Color glow = mix(mix(SUN_GLOW, SUN_COLD, frost), SUN_DIM, dust);
        if (storm > 0) {
            double ring = 15 + 13 * storm;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(SUN_X, SUN_Y), (float) ring,
                    new float[] {0f, 0.45f, 1f}, new Color[] {
                        Draw.alpha(STORM_PINK, (int) Math.round(235 * storm)),
                        Draw.alpha(STORM_PINK, (int) Math.round(130 * storm)),
                        Draw.alpha(STORM_PINK, 0)}));
            g.fill(new Ellipse2D.Double(SUN_X - ring, SUN_Y - ring, 2 * ring, 2 * ring));
            // Rays, long and short by turns.
            g.setColor(Draw.alpha(mix(SUN, Color.WHITE, 0.4), (int) Math.round(140 + 115 * storm)));
            int rays = 12;
            for (int k = 0; k < rays; k++) {
                double angle = k * 2 * Math.PI / rays + 0.2;
                double length = (k % 2 == 0 ? 20 : 12) * (0.35 + 0.65 * storm) + 4;
                double halfWidth = 1.1 + 1.2 * storm;
                Path2D.Double ray = new Path2D.Double();
                ray.moveTo(SUN_X + Math.cos(angle + Math.PI / 2) * halfWidth, SUN_Y + Math.sin(angle + Math.PI / 2) * halfWidth);
                ray.lineTo(SUN_X + Math.cos(angle) * length, SUN_Y + Math.sin(angle) * length);
                ray.lineTo(SUN_X + Math.cos(angle - Math.PI / 2) * halfWidth, SUN_Y + Math.sin(angle - Math.PI / 2) * halfWidth);
                ray.closePath();
                g.fill(ray);
            }
        }
        double halo = 8 + 6 * dust + 4 * storm;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(SUN_X, SUN_Y), (float) halo,
                new float[] {0f, 1f}, new Color[] {
                    Draw.alpha(glow, (int) Math.round(210 * shows)), Draw.alpha(glow, 0)}));
        g.fill(new Ellipse2D.Double(SUN_X - halo, SUN_Y - halo, 2 * halo, 2 * halo));
        double radius = 3.6 + 3.4 * storm - 0.6 * dust;
        g.setColor(Draw.alpha(mix(disc, Color.WHITE, 0.6 * storm), (int) Math.round(255 * shows)));
        g.fill(new Ellipse2D.Double(SUN_X - radius, SUN_Y - radius, 2 * radius, 2 * radius));
    }

    /** The pink streaks of a solar storm, slanting the way they do over the outpost. */
    private static void streaks(Graphics2D g, double storm) {
        Random random = new Random(7919);
        int count = (int) Math.round(18 * storm);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < count; i++) {
            double x = VISOR.getX() + 4 + random.nextDouble() * (VISOR.getWidth() + 2);
            double y = VISOR.getY() - 2 + random.nextDouble() * (VISOR.getHeight() - 6);
            double length = 7 + random.nextDouble() * 9;
            g.setColor(new Color(255, 120 + random.nextInt(80), 220, 170 + random.nextInt(80)));
            g.draw(new Line2D.Double(x, y, x - length * 0.5, y + length));
        }
    }

    /** The glass itself: darker toward its edge, with a streak of shine, so it reads as a curved visor. */
    private static void glass(Graphics2D g) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 42), 40,
                new float[] {0f, 0.62f, 1f}, new Color[] {
                    new Color(8, 10, 30, 0), new Color(8, 10, 30, 0), new Color(8, 10, 30, 150)}));
        g.fill(VISOR);
        Path2D.Double shine = new Path2D.Double();
        shine.moveTo(27, 40);
        shine.quadTo(29, 28, 44, 26.5);
        shine.quadTo(33, 31, 30.5, 41);
        shine.closePath();
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(shine);
        g.setColor(new Color(255, 255, 255, 105));
        g.fill(new Ellipse2D.Double(66, 29.5, 4.2, 3));
    }

    /** "Surprise me": nobody knows what the visor will show. */
    private static void question(Graphics2D g) {
        g.setPaint(new GradientPaint(0, (float) VISOR.getY(), new Color(52, 60, 102),
                0, (float) VISOR.getMaxY(), new Color(20, 24, 46)));
        g.fill(VISOR);
        g.setFont(QUESTION_FONT);
        g.setColor(Glyphs.POWER);
        String mark = "?";
        double width = g.getFontMetrics().getStringBounds(mark, g).getWidth();
        g.drawString(mark, (float) (50 - width / 2), 57.5f);
    }

    /** Frost growing in from the rim of the visor: a white edge, then crystals reaching for the middle. */
    private static void visorFrost(Graphics2D g, double frost) {
        double reach = 2.5 + 8.5 * frost;
        // Bands of white along the rim, each narrower than the last, so the frost thins inward.
        int bands = 7;
        for (int band = 1; band <= bands; band++) {
            g.setStroke(new BasicStroke((float) (2 * reach * band / bands), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Draw.alpha(FROST, (int) Math.round(24 + 28 * frost)));
            g.draw(VISOR);
        }
        // Crystals: feathered spikes from the rim toward the middle of the glass.
        Random random = new Random(1601);
        double cx = VISOR.getCenterX();
        double cy = VISOR.getCenterY();
        int spikes = (int) Math.round(18 + 40 * frost);
        g.setStroke(new BasicStroke(0.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < spikes; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double length = reach * (0.6 + 0.7 * random.nextDouble());
            double turn = (random.nextDouble() - 0.5) * 0.7;
            // Start on the rim: walk out from the middle until the point leaves the visor.
            double edge = 12;
            while (VISOR.contains(cx + Math.cos(angle) * (edge + 1), cy + Math.sin(angle) * (edge + 1)) && edge < 40) {
                edge += 1;
            }
            double x0 = cx + Math.cos(angle) * edge;
            double y0 = cy + Math.sin(angle) * edge;
            double inward = angle + Math.PI + turn;
            double x1 = x0 + Math.cos(inward) * length;
            double y1 = y0 + Math.sin(inward) * length;
            g.setColor(Draw.alpha(Color.WHITE, 150 + random.nextInt(90)));
            g.draw(new Line2D.Double(x0, y0, x1, y1));
            for (int twig = 1; twig <= 3; twig++) {
                double along = twig / 4.0;
                double bx = x0 + (x1 - x0) * along;
                double by = y0 + (y1 - y0) * along;
                double twigLength = length * 0.3 * (1 - along * 0.5);
                for (int side = -1; side <= 1; side += 2) {
                    double twigAngle = inward + side * 0.9;
                    g.draw(new Line2D.Double(bx, by, bx + Math.cos(twigAngle) * twigLength,
                            by + Math.sin(twigAngle) * twigLength));
                }
            }
        }
    }

    /** A film of dust on the glass, thickest at the bottom, and grains stuck to it. */
    private static void visorDust(Graphics2D g, double dust) {
        g.setPaint(new GradientPaint(0, (float) VISOR.getY(), Draw.alpha(DUST, (int) Math.round(30 * dust)),
                0, (float) VISOR.getMaxY(), Draw.alpha(DUST_DARK, (int) Math.round(215 * dust))));
        g.fill(VISOR);
        Random random = new Random(4057);
        int grains = (int) Math.round(46 * dust);
        for (int i = 0; i < grains; i++) {
            double x = VISOR.getX() + random.nextDouble() * VISOR.getWidth();
            double y = VISOR.getY() + Math.sqrt(random.nextDouble()) * VISOR.getHeight();
            double size = 0.5 + random.nextDouble() * 1.1;
            g.setColor(Draw.alpha(random.nextBoolean() ? DUST_GRAIN : DUST, 150 + random.nextInt(80)));
            g.fill(new Ellipse2D.Double(x, y, size, size));
        }
    }

    /** Fine scratches across the glass. */
    private static void visorWear(Graphics2D g, double wear) {
        Random random = new Random(2203);
        int scratches = (int) Math.round(7 * wear);
        g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < scratches; i++) {
            double x = VISOR.getX() + 6 + random.nextDouble() * (VISOR.getWidth() - 12);
            double y = VISOR.getY() + 5 + random.nextDouble() * (VISOR.getHeight() - 10);
            double angle = -0.5 + random.nextDouble() * 1.0;
            double length = 6 + random.nextDouble() * 10;
            g.setColor(new Color(255, 240, 220, 110));
            g.draw(new Line2D.Double(x, y, x + Math.cos(angle) * length, y + Math.sin(angle) * length));
        }
    }

    /** A cold shell: rime round its edge and a sparkle of ice on it. */
    private static void shellFrost(Graphics2D g, double frost) {
        g.setStroke(new BasicStroke((float) (5 + 5 * frost)));
        g.setColor(Draw.alpha(FROST, (int) Math.round(150 * frost)));
        g.draw(SHELL);
        Random random = new Random(907);
        int sparkles = (int) Math.round(26 * frost);
        g.setStroke(new BasicStroke(0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < sparkles; i++) {
            double x = 12 + random.nextDouble() * 76;
            double y = 6 + random.nextDouble() * 84;
            double arm = 0.9 + random.nextDouble() * 1.3;
            g.setColor(Draw.alpha(new Color(150, 190, 240), 150 + random.nextInt(90)));
            g.draw(new Line2D.Double(x - arm, y, x + arm, y));
            g.draw(new Line2D.Double(x, y - arm, x, y + arm));
            g.draw(new Line2D.Double(x - arm * 0.6, y - arm * 0.6, x + arm * 0.6, y + arm * 0.6));
            g.draw(new Line2D.Double(x - arm * 0.6, y + arm * 0.6, x + arm * 0.6, y - arm * 0.6));
        }
    }

    /** Orange dust caked on the shell: a coat that is thickest low down, clumps, and grains. */
    private static void shellDust(Graphics2D g, double dust) {
        g.setPaint(new GradientPaint(0, 10, Draw.alpha(DUST, (int) Math.round(64 * dust)),
                0, 92, Draw.alpha(DUST_DARK, (int) Math.round(240 * dust))));
        g.fillRect(0, 0, 100, 100);
        Random random = new Random(3331);
        int clumps = (int) Math.round(30 * dust);
        for (int i = 0; i < clumps; i++) {
            double x = 6 + random.nextDouble() * 88;
            double y = 6 + Math.sqrt(random.nextDouble()) * 88;
            double w = 4 + random.nextDouble() * 11;
            double h = 3 + random.nextDouble() * 6;
            g.setColor(Draw.alpha(random.nextInt(3) == 0 ? DUST_DARK : DUST, 60 + random.nextInt(80)));
            g.fill(new Ellipse2D.Double(x - w / 2, y - h / 2, w, h));
        }
        int grains = (int) Math.round(90 * dust);
        for (int i = 0; i < grains; i++) {
            double x = 6 + random.nextDouble() * 88;
            double y = 6 + Math.sqrt(random.nextDouble()) * 88;
            double size = 0.5 + random.nextDouble() * 1.0;
            g.setColor(Draw.alpha(DUST_GRAIN, 120 + random.nextInt(100)));
            g.fill(new Ellipse2D.Double(x, y, size, size));
        }
    }

    /** Scuffs and scratches on the shell. */
    private static void shellWear(Graphics2D g, double wear) {
        Random random = new Random(5521);
        int scuffs = (int) Math.round(8 * wear);
        for (int i = 0; i < scuffs; i++) {
            double x = 14 + random.nextDouble() * 72;
            double y = 8 + random.nextDouble() * 76;
            double w = 5 + random.nextDouble() * 9;
            double h = 1.6 + random.nextDouble() * 2.6;
            AffineTransform turn = AffineTransform.getRotateInstance(-0.9 + random.nextDouble() * 1.8, x, y);
            g.setColor(new Color(70, 70, 86, 60 + random.nextInt(50)));
            g.fill(turn.createTransformedShape(new Ellipse2D.Double(x - w / 2, y - h / 2, w, h)));
        }
        int scratches = (int) Math.round(18 * wear);
        for (int i = 0; i < scratches; i++) {
            double x = 14 + random.nextDouble() * 72;
            double y = 8 + random.nextDouble() * 76;
            double angle = -1.2 + random.nextDouble() * 2.4;
            double length = 4 + random.nextDouble() * 10;
            double dx = Math.cos(angle) * length;
            double dy = Math.sin(angle) * length;
            g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(52, 50, 64, 170));
            g.draw(new Line2D.Double(x, y, x + dx, y + dy));
            g.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 255, 255, 150));
            g.draw(new Line2D.Double(x + 0.5, y + 0.7, x + dx + 0.5, y + dy + 0.7));
        }
    }

    /** The colour {@code share} of the way from {@code from} to {@code to}. */
    private static Color mix(Color from, Color to, double share) {
        double t = Draw.clamp(share);
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    private HelmetArt() {}
}
