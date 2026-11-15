package outpost.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of flat-shaded faces making up one low-poly object. Points are {u, v, h} in the
 * view's ground coordinates (see {@link Projection}). Faces pointing away from the camera
 * are dropped as they are added, so a closed convex shape can be added in any order;
 * otherwise add faces back to front, because they are painted in the order added.
 */
final class Mesh {

    /** Direction the light comes from, as a unit vector in {u, v, h}. */
    private static final double[] LIGHT = unit(-0.30, 0.55, 0.78);
    private static final double AMBIENT = 0.58;
    private static final double DIFFUSE = 0.52;

    private record Face(double[][] points, Color colour) {}

    private final List<Face> faces = new ArrayList<>();
    private final Projection projection;
    private final double brightness;

    /** @param brightness multiplies every face colour; 1.0 is full daylight */
    Mesh(Projection projection, double brightness) {
        this.projection = projection;
        this.brightness = brightness;
    }

    static double[] p(double u, double v, double h) {
        return new double[] {u, v, h};
    }

    /**
     * Adds a face of a solid. {@code inside} is any point inside the solid; it decides
     * which side of the face is the outside. Faces whose outside the camera cannot see are
     * skipped.
     */
    void face(double[] inside, Color colour, double[]... points) {
        double[] n = normal(points);
        double[] c = centroid(points);
        if (n[0] * (c[0] - inside[0]) + n[1] * (c[1] - inside[1]) + n[2] * (c[2] - inside[2]) < 0) {
            n = new double[] {-n[0], -n[1], -n[2]};
        }
        if (projection.facesCamera(n)) {
            faces.add(new Face(points, lit(colour, n)));
        }
    }

    /** Adds a face that is always drawn and shaded as if it faced up and toward the camera. */
    void sheet(Color colour, double[]... points) {
        double[] n = normal(points);
        if (!projection.facesCamera(n)) {
            n = new double[] {-n[0], -n[1], -n[2]};
        }
        faces.add(new Face(points, lit(colour, n)));
    }

    /** Adds an axis-aligned box with one corner at (u, v, h). */
    void box(double u, double v, double h, double du, double dv, double dh, Color colour) {
        double[] inside = p(u + du / 2, v + dv / 2, h + dh / 2);
        double u1 = u + du;
        double v1 = v + dv;
        double h1 = h + dh;
        face(inside, colour, p(u, v, h1), p(u1, v, h1), p(u1, v1, h1), p(u, v1, h1));
        face(inside, colour, p(u1, v, h), p(u1, v1, h), p(u1, v1, h1), p(u1, v, h1));
        face(inside, colour, p(u, v1, h), p(u1, v1, h), p(u1, v1, h1), p(u, v1, h1));
    }

    void paint(Graphics2D g) {
        for (Face face : faces) {
            Path2D.Double path = new Path2D.Double();
            for (int i = 0; i < face.points().length; i++) {
                double[] q = face.points()[i];
                double x = projection.x(q[0], q[1]);
                double y = projection.y(q[0], q[1], q[2]);
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.closePath();
            g.setColor(face.colour());
            g.fill(path);
            if (face.colour().getAlpha() == 255) {
                g.draw(path); // hides the hairline gaps antialiasing leaves between faces
            }
        }
    }

    private Color lit(Color colour, double[] n) {
        double towardLight = Math.max(0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
        return scale(colour, (AMBIENT + DIFFUSE * towardLight) * brightness);
    }

    static Color scale(Color colour, double factor) {
        return new Color(channel(colour.getRed() * factor), channel(colour.getGreen() * factor),
                channel(colour.getBlue() * factor), colour.getAlpha());
    }

    static Color mix(Color a, Color b, double t) {
        double k = Math.max(0, Math.min(1, t));
        return new Color(channel(a.getRed() + (b.getRed() - a.getRed()) * k),
                channel(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                channel(a.getBlue() + (b.getBlue() - a.getBlue()) * k),
                channel(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k));
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double[] normal(double[][] q) {
        double ax = q[1][0] - q[0][0];
        double ay = q[1][1] - q[0][1];
        double az = q[1][2] - q[0][2];
        double bx = q[2][0] - q[0][0];
        double by = q[2][1] - q[0][1];
        double bz = q[2][2] - q[0][2];
        return unit(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
    }

    private static double[] centroid(double[][] q) {
        double[] c = new double[3];
        for (double[] point : q) {
            c[0] += point[0] / q.length;
            c[1] += point[1] / q.length;
            c[2] += point[2] / q.length;
        }
        return c;
    }

    private static double[] unit(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return length == 0 ? new double[] {0, 0, 1} : new double[] {x / length, y / length, z / length};
    }
}
