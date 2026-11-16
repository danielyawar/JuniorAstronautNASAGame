package outpost.view;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * The picture of the outpost: a photo of the ground with the four building pictures on it,
 * each where {@link Site} says. Nothing in it changes while the game is played.
 *
 * <p>The view repaints about twenty times a second, so the pictures are not scaled for each
 * frame. They are put together once into one image the size of the panel, in the screen's
 * own pixels, and that image is drawn as it is after that. It is made again only when the
 * panel changes size. The image is a cache of drawing work; no game state lives here.
 *
 * <p>The photo covers the 800 x 600 layout exactly, so a building stands on the same ground
 * whatever the shape of the window. Where the panel is wider or taller than the layout, the
 * margins are filled with the same photo, blurred and a little darker, and mirrored outward
 * from each edge so that its colours carry on from the layout's: no black bar and no
 * stretching.
 *
 * <p>The files are made by tools/ShrinkPictures.java from the artist's full-size pictures.
 */
final class Pictures {

    /** The photo of the ground. It covers the whole layout. */
    private static final String GROUND = "background.jpg";
    /** How solid a picture must be at a point, out of 255, to count as something to see there. */
    private static final int SOLID = 64;
    /**
     * A click this near to a solid part of a picture still counts, in layout units, so that
     * a thin picture such as the digger is not hard to hit.
     */
    private static final double REACH = 4;
    /** Laid over the margins outside the layout, so that the layout stands out a little. */
    private static final Color MARGIN_SHADE = new Color(20, 12, 16, 70);

    /**
     * One building's picture.
     *
     * @param image the picture, ready to draw
     * @param solid for each pixel of it, row by row, whether there is something to see there
     * @param seen  the smallest rectangle of the layout that holds everything to see
     */
    private record Building(BufferedImage image, boolean[] solid, Rectangle2D seen) {}

    private final BufferedImage ground;
    private final Map<Site, Building> buildings = new EnumMap<>(Site.class);

    // The cache of drawing work. See the class comment.
    /**
     * The photo made very small, which blurs it when it is drawn large again. For the
     * margins, so it is made only once a panel that has margins turns up.
     */
    private BufferedImage blurred;
    private BufferedImage built;
    private int builtWidth;
    private int builtHeight;
    private double builtScaleX;
    private double builtScaleY;

    /** Reads the five files. Slow in the browser (about a second), so it is done once, when the game opens. */
    Pictures() {
        ground = read(GROUND, BufferedImage.TYPE_INT_RGB);
        for (Site site : Site.values()) {
            buildings.put(site, building(read(site.file(), BufferedImage.TYPE_INT_ARGB_PRE), site.picture()));
        }
    }

    /**
     * Reads one picture from beside this class in the jar. This is the only place a picture
     * file is opened, whatever its format. The picture is copied into the kind of image that
     * Java draws fastest, and the copy that came out of the file is let go.
     */
    private static BufferedImage read(String file, int type) {
        URL url = Pictures.class.getResource(file);
        if (url == null) {
            throw new IllegalStateException("The picture " + file + " is missing from the jar");
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(url);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the picture " + file, e);
        }
        if (decoded == null) {
            throw new IllegalStateException("Java cannot read the format of the picture " + file);
        }
        BufferedImage fast = new BufferedImage(decoded.getWidth(), decoded.getHeight(), type);
        Graphics2D g = fast.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.drawImage(decoded, 0, 0, null);
        } finally {
            g.dispose();
        }
        return fast;
    }

    /** A copy of {@code image} about 40 pixels wide, made by halving it again and again so that every pixel counts. */
    private static BufferedImage verySmall(BufferedImage image) {
        BufferedImage small = image;
        while (small.getWidth() > 64 && small.getHeight() > 2) {
            BufferedImage half = new BufferedImage(small.getWidth() / 2, small.getHeight() / 2,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = half.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(small, 0, 0, half.getWidth(), half.getHeight(), null);
            } finally {
                g.dispose();
            }
            small = half;
        }
        return small;
    }

    /** Works out where a building's picture, drawn at {@code drawnAt} in the layout, has something to see. */
    private static Building building(BufferedImage image, Rectangle2D drawnAt) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] alpha = image.getAlphaRaster().getSamples(0, 0, width, height, 0, (int[]) null);
        boolean[] solid = new boolean[alpha.length];
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (alpha[y * width + x] >= SOLID) {
                    solid[y * width + x] = true;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            return new Building(image, solid, drawnAt); // nothing to see: an empty picture
        }
        double acrossEach = drawnAt.getWidth() / width;
        double downEach = drawnAt.getHeight() / height;
        return new Building(image, solid, new Rectangle2D.Double(
                drawnAt.getX() + left * acrossEach, drawnAt.getY() + top * downEach,
                (right + 1 - left) * acrossEach, (bottom + 1 - top) * downEach));
    }

    /** The part of a place's picture that can be seen, in the layout: the picture without its see-through edges. */
    Rectangle2D seen(Site site) {
        return buildings.get(site).seen();
    }

    /**
     * True if (x, y) in the layout is on a place's picture: on a part of it that is not
     * see-through, or within {@link #REACH} of one. Open ground inside the picture's
     * rectangle does not count.
     */
    boolean solid(Site site, double x, double y) {
        Rectangle2D at = site.picture();
        if (x < at.getX() - REACH || x > at.getMaxX() + REACH || y < at.getY() - REACH || y > at.getMaxY() + REACH) {
            return false;
        }
        Building building = buildings.get(site);
        int width = building.image().getWidth();
        int height = building.image().getHeight();
        for (int down = -1; down <= 1; down++) {
            for (int across = -1; across <= 1; across++) {
                int column = (int) Math.floor((x + across * REACH - at.getX()) / at.getWidth() * width);
                int row = (int) Math.floor((y + down * REACH - at.getY()) / at.getHeight() * height);
                if (column >= 0 && column < width && row >= 0 && row < height
                        && building.solid()[row * width + column]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Draws the ground and the buildings over the whole panel, from the cache. {@code g}
     * must still be in panel coordinates: this is the one thing drawn before the layout is
     * scaled.
     */
    void paint(Graphics2D g, int width, int height) {
        AffineTransform device = g.getTransform();
        // On a screen that Java scales (a laptop set to 150%, say), keep the image as sharp
        // as the text by making it in the screen's own pixels.
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0;
        double scaleX = plain ? device.getScaleX() : 1;
        double scaleY = plain ? device.getScaleY() : 1;
        refresh(width, height, scaleX, scaleY);
        if (plain) {
            g.setTransform(AffineTransform.getTranslateInstance(device.getTranslateX(), device.getTranslateY()));
            g.drawImage(built, 0, 0, null);
            g.setTransform(device);
        } else {
            g.drawImage(built, 0, 0, null);
        }
    }

    /** Makes the cached image again if the panel is not the size it was made for. */
    private void refresh(int width, int height, double scaleX, double scaleY) {
        if (built != null && width == builtWidth && height == builtHeight
                && scaleX == builtScaleX && scaleY == builtScaleY) {
            return;
        }
        int pixelsWide = Math.max(1, (int) Math.ceil(width * scaleX));
        int pixelsHigh = Math.max(1, (int) Math.ceil(height * scaleY));
        built = new BufferedImage(pixelsWide, pixelsHigh, BufferedImage.TYPE_INT_RGB);
        builtWidth = width;
        builtHeight = height;
        builtScaleX = scaleX;
        builtScaleY = scaleY;
        Graphics2D c = built.createGraphics();
        try {
            c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // The layout's rectangle in the image's own pixels, on whole pixels so that the
            // copies of the photo meet without a gap.
            double layout = Draw.scale(width, height);
            int left = (int) Math.round((width - Draw.WIDTH * layout) / 2 * scaleX);
            int top = (int) Math.round((height - Draw.HEIGHT * layout) / 2 * scaleY);
            int wide = Math.max(1, (int) Math.round(Draw.WIDTH * layout * scaleX));
            int high = Math.max(1, (int) Math.round(Draw.HEIGHT * layout * scaleY));
            if (left > 0 || top > 0 || left + wide < pixelsWide || top + high < pixelsHigh) {
                // The margins: the blurred photo, mirrored outward from each edge of the
                // layout so that its colours carry on, and a little darker than the layout.
                if (blurred == null) {
                    blurred = verySmall(ground);
                }
                c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                for (int down = -copiesBefore(top, high); top + down * high < pixelsHigh; down++) {
                    for (int across = -copiesBefore(left, wide); left + across * wide < pixelsWide; across++) {
                        if (down == 0 && across == 0) {
                            continue; // the layout itself: the photo goes there
                        }
                        int x = left + across * wide;
                        int y = top + down * high;
                        boolean flipAcross = across % 2 != 0;
                        boolean flipDown = down % 2 != 0;
                        c.drawImage(blurred, flipAcross ? x + wide : x, flipDown ? y + high : y,
                                flipAcross ? x : x + wide, flipDown ? y : y + high,
                                0, 0, blurred.getWidth(), blurred.getHeight(), null);
                    }
                }
                c.setColor(MARGIN_SHADE);
                c.fillRect(0, 0, pixelsWide, pixelsHigh);
                c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            c.drawImage(ground, left, top, wide, high, null);

            c.scale(scaleX, scaleY);
            Draw.fit(c, width, height);
            for (Site site : Site.values()) {
                Rectangle2D at = site.picture();
                BufferedImage image = buildings.get(site).image();
                AffineTransform place = AffineTransform.getTranslateInstance(at.getX(), at.getY());
                place.scale(at.getWidth() / image.getWidth(), at.getHeight() / image.getHeight());
                c.drawImage(image, place, null);
            }
        } finally {
            c.dispose();
        }
    }

    /** How many copies of the photo, each {@code size} long, it takes to cover the {@code margin} before the layout. */
    private static int copiesBefore(int margin, int size) {
        return margin <= 0 ? 0 : (margin + size - 1) / size;
    }
}
