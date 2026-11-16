import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Shrinks the artist's pictures to the size the game draws them at and writes them into
 * src/outpost/view/, where Build.java picks them up. Needs only a JDK (17 or newer). Run it
 * from the repository root, with the folder that holds the full-size originals:
 *
 *     java tools/ShrinkPictures.java <folder of originals>
 *     java Build.java
 *
 * Why: the originals are up to 3000 pixels wide and are drawn about 100 wide. Shipped as
 * they were, they made the jar 12 MB, took about 100 MB of memory once opened, and were
 * shrunk again for every frame.
 *
 * Each picture is written at a whole multiple of the size it is drawn at in the game's
 * 800 x 600 layout, and in the shape it is drawn in there (so a picture that the game
 * draws wider or taller than the artist made it is shipped in the game's shape). The game
 * shrinks it once more to fit the window, and that step is only clean up to 2 to 1, which
 * is why the files are not kept larger.
 *
 * To add a picture, or after changing the size one is drawn at (Site.java for the
 * buildings, Pictures.java for the ground), change the list below to match and run this
 * again on the originals. The originals are not in the repository's working files; keep
 * them somewhere safe.
 */
public class ShrinkPictures {

    /**
     * One picture: the original's file name, the name the game reads it under, the size it
     * is drawn at in the 800 x 600 layout, and how many times larger than that it is kept.
     * A name ending in .jpg is written as a JPEG, which suits a photo and cannot be
     * see-through; anything else is written as a PNG.
     */
    record Picture(String original, String shipped, int drawnWidth, int drawnHeight, double times) {}

    static final Picture[] PICTURES = {
        // Everything is kept at twice the size it is drawn, for large windows and for screens
        // that Java scales up. The ground is a photo and soft to begin with: at 1.5 times it
        // looks the same in a window up to 1200 x 900 and the browser opens it about half a
        // second sooner, if that is ever worth more.
        new Picture("background.png", "background.jpg", 800, 600, 2),
        new Picture("base.png", "base.png", 175, 150, 2),
        new Picture("panel.png", "panel.png", 80, 50, 2),
        new Picture("greenh.png", "greenh.png", 105, 85, 2),
        new Picture("drill.png", "drill.png", 60, 50, 2),
    };

    /** 0 to 1. At 0.8 the photo shows no marks at the size the game draws it. */
    static final float JPEG_QUALITY = 0.8f;
    static final File SHIPPED = new File("src/outpost/view");

    public static void main(String[] args) throws IOException {
        if (args.length != 1 || !new File(args[0]).isDirectory()) {
            System.err.println("Usage: java tools/ShrinkPictures.java <folder of originals>");
            System.exit(2);
        }
        if (!SHIPPED.isDirectory()) {
            System.err.println("Run this from the repository root: there is no " + SHIPPED + " here.");
            System.exit(2);
        }
        for (Picture picture : PICTURES) {
            File from = new File(args[0], picture.original());
            BufferedImage original = from.isFile() ? ImageIO.read(from) : null;
            if (original == null) {
                System.err.println("Could not read " + from + ": " + picture.shipped() + " is left as it is.");
                continue;
            }
            // Never larger than the original: enlarging adds bytes and no detail.
            int width = Math.min(original.getWidth(), (int) Math.round(picture.drawnWidth() * picture.times()));
            int height = Math.min(original.getHeight(), (int) Math.round(picture.drawnHeight() * picture.times()));
            boolean photo = picture.shipped().endsWith(".jpg");
            BufferedImage small = shrink(original, width, height, !photo);
            File to = new File(SHIPPED, picture.shipped());
            if (photo) {
                write(small, "jpeg", JPEG_QUALITY, to);
            } else {
                write(small, "png", 0f, to); // for a PNG, 0 means the smallest file; nothing is lost
            }
            System.out.printf("%-15s %4d x %4d, %,9d bytes  ->  %-15s %4d x %4d, %,7d bytes%n",
                    picture.original(), original.getWidth(), original.getHeight(), from.length(),
                    picture.shipped(), width, height, to.length());
        }
    }

    /**
     * A smaller copy of {@code source}, each new pixel the average of the old pixels it
     * covers. See-through pixels count for nothing in the average, so no dark or coloured
     * fringe appears round the edge of a building.
     */
    static BufferedImage shrink(BufferedImage source, int width, int height, boolean seeThrough) {
        int oldWidth = source.getWidth();
        int oldHeight = source.getHeight();
        Spans columns = new Spans(oldWidth, width);
        Spans rows = new Spans(oldHeight, height);

        // Across first: every old row becomes a row of the new width. Four numbers a pixel:
        // how solid it is, then red, green and blue, each already multiplied by how solid.
        float[] across = new float[width * oldHeight * 4];
        int[] row = new int[oldWidth];
        for (int y = 0; y < oldHeight; y++) {
            source.getRGB(0, y, oldWidth, 1, row, 0, oldWidth);
            for (int x = 0; x < width; x++) {
                double a = 0;
                double r = 0;
                double g = 0;
                double b = 0;
                for (int k = 0; k < columns.count[x]; k++) {
                    int argb = row[columns.first[x] + k];
                    double solid = (argb >>> 24) / 255.0 * columns.share[x][k];
                    a += solid;
                    r += solid * ((argb >> 16) & 255);
                    g += solid * ((argb >> 8) & 255);
                    b += solid * (argb & 255);
                }
                int at = (y * width + x) * 4;
                across[at] = (float) a;
                across[at + 1] = (float) r;
                across[at + 2] = (float) g;
                across[at + 3] = (float) b;
            }
        }

        // Then down.
        BufferedImage small = new BufferedImage(width, height,
                seeThrough ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double a = 0;
                double r = 0;
                double g = 0;
                double b = 0;
                for (int k = 0; k < rows.count[y]; k++) {
                    int at = ((rows.first[y] + k) * width + x) * 4;
                    double share = rows.share[y][k];
                    a += share * across[at];
                    r += share * across[at + 1];
                    g += share * across[at + 2];
                    b += share * across[at + 3];
                }
                int argb = 0;
                if (a > 0) {
                    argb = (seeThrough ? channel(a * 255) : 255) << 24
                            | channel(r / a) << 16 | channel(g / a) << 8 | channel(b / a);
                }
                small.setRGB(x, y, argb);
            }
        }
        return small;
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    /**
     * For each pixel of the new, shorter row: which old pixels it covers and what share of
     * it each one fills. The old pixels at the two ends are usually covered only in part.
     */
    static final class Spans {
        final int[] first;
        final int[] count;
        final double[][] share;

        Spans(int from, int to) {
            first = new int[to];
            count = new int[to];
            share = new double[to][];
            double each = from / (double) to;
            for (int i = 0; i < to; i++) {
                double start = i * each;
                double end = Math.min(from, (i + 1) * each);
                first[i] = (int) Math.floor(start);
                int last = Math.min(from - 1, (int) Math.ceil(end) - 1);
                count[i] = last - first[i] + 1;
                share[i] = new double[count[i]];
                for (int k = 0; k < count[i]; k++) {
                    int old = first[i] + k;
                    share[i][k] = (Math.min(old + 1, end) - Math.max(old, start)) / (end - start);
                }
            }
        }
    }

    /** Writes a picture with the given quality, 0 to 1. */
    static void write(BufferedImage image, String format, float quality, File to) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ImageWriteParam how = writer.getDefaultWriteParam();
        how.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        if (how.getCompressionTypes() != null && how.getCompressionType() == null) {
            how.setCompressionType(how.getCompressionTypes()[0]);
        }
        how.setCompressionQuality(quality);
        to.delete(); // an old, longer file would otherwise keep its tail
        try (ImageOutputStream out = ImageIO.createImageOutputStream(to)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), how);
        } finally {
            writer.dispose();
        }
    }
}
