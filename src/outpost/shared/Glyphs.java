package outpost.shared;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * Small icons drawn in code, shared so the view and the controls use the same pictures and
 * colours for the same things. Each is drawn inside a square with its top-left corner at
 * (x, y).
 */
public final class Glyphs {

    public static final Color SHIELD = new Color(150, 150, 255);
    public static final Color GREENHOUSE = new Color(112, 214, 112);
    public static final Color HEAT = new Color(255, 160, 84);
    public static final Color POWER = new Color(255, 212, 84);
    public static final Color HEALTH = new Color(255, 104, 124);
    public static final Color GOOD = new Color(122, 224, 132);
    public static final Color WARN = new Color(255, 196, 92);
    public static final Color BAD = new Color(255, 112, 112);

    /** Lightning bolt: power. */
    public static void bolt(Graphics2D g, double x, double y, double size, Color colour) {
        fill(g, x, y, size, colour, polygon(0.62, 0.0, 0.14, 0.58, 0.46, 0.58, 0.36, 1.0,
                0.86, 0.38, 0.54, 0.38, 0.74, 0.0));
    }

    /** Heart: crew health. */
    public static void heart(Graphics2D g, double x, double y, double size, Color colour) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0.5, 0.96);
        path.curveTo(-0.25, 0.5, 0.1, -0.1, 0.5, 0.28);
        path.curveTo(0.9, -0.1, 1.25, 0.5, 0.5, 0.96);
        path.closePath();
        fill(g, x, y, size, colour, path);
    }

    /** Shield: radiation shielding. */
    public static void shield(Graphics2D g, double x, double y, double size, Color colour) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0.5, 0.0);
        path.lineTo(0.94, 0.16);
        path.lineTo(0.94, 0.5);
        path.quadTo(0.9, 0.86, 0.5, 1.0);
        path.quadTo(0.1, 0.86, 0.06, 0.5);
        path.lineTo(0.06, 0.16);
        path.closePath();
        fill(g, x, y, size, colour, path);
    }

    /** Leaf: greenhouse and food. */
    public static void leaf(Graphics2D g, double x, double y, double size, Color colour) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0.12, 0.88);
        path.quadTo(0.0, 0.1, 0.92, 0.08);
        path.quadTo(0.96, 0.96, 0.12, 0.88);
        path.closePath();
        fill(g, x, y, size, colour, path);
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke((float) (size * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x + size * 0.04, y + size * 0.98, x + size * 0.3, y + size * 0.7));
        g.setStroke(old);
    }

    /** Flame: heating. */
    public static void flame(Graphics2D g, double x, double y, double size, Color colour) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(0.5, 0.0);
        path.curveTo(0.62, 0.3, 0.98, 0.48, 0.84, 0.78);
        path.quadTo(0.72, 1.0, 0.5, 1.0);
        path.quadTo(0.26, 1.0, 0.16, 0.78);
        path.curveTo(0.04, 0.54, 0.26, 0.42, 0.3, 0.26);
        path.quadTo(0.4, 0.38, 0.42, 0.46);
        path.quadTo(0.52, 0.3, 0.5, 0.0);
        path.closePath();
        fill(g, x, y, size, colour, path);
    }

    /** Five-pointed star: mission rating. */
    public static void star(Graphics2D g, double x, double y, double size, Color colour) {
        double[] points = new double[20];
        for (int i = 0; i < 10; i++) {
            double radius = i % 2 == 0 ? 0.5 : 0.21;
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            points[2 * i] = 0.5 + radius * Math.cos(angle);
            points[2 * i + 1] = 0.52 + radius * Math.sin(angle);
        }
        fill(g, x, y, size, colour, polygon(points));
    }

    private static Path2D.Double polygon(double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.closePath();
        return path;
    }

    private static void fill(Graphics2D g, double x, double y, double size, Color colour, Path2D path) {
        AffineTransform place = AffineTransform.getTranslateInstance(x, y);
        place.scale(size, size);
        g.setColor(colour);
        g.fill(place.createTransformedShape(path));
    }

    private Glyphs() {}
}
