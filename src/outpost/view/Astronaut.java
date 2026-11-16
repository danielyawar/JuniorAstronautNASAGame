package outpost.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The astronaut, drawn in code: a white suit with a backpack, a dark visor and a shadow on
 * the ground. Large enough to pick out at once, and so far larger than life beside the
 * buildings. All its shapes are made once, around the point between the feet.
 */
final class Astronaut {

    /** From the ground to the top of the helmet, in layout units. The label goes above this. */
    static final double HEIGHT = 39;

    private static final Color SUIT = new Color(246, 246, 250);
    private static final Color SUIT_SHADE = new Color(204, 208, 220);
    private static final Color PACK = new Color(255, 140, 40);
    private static final Color BOOT = new Color(96, 100, 116);
    private static final Color VISOR = new Color(30, 38, 66);
    private static final Color GLINT = new Color(170, 210, 255, 210);
    private static final Color CHEST = new Color(84, 108, 150);
    private static final Color OUTLINE = new Color(38, 40, 58, 215);
    private static final Color SHADOW = new Color(0, 0, 0, 76);
    private static final BasicStroke EDGE = new BasicStroke(1.1f);

    private static final Shape SHADOW_SHAPE = new Ellipse2D.Double(-12, -3.5, 24, 7);
    private static final Shape PACK_SHAPE = new RoundRectangle2D.Double(-9.5, -28, 19, 17, 6, 6);
    private static final Shape LEG = new RoundRectangle2D.Double(-2.75, -11, 5.5, 11, 3, 3);
    private static final Shape BOOT_SHAPE = new RoundRectangle2D.Double(-3.25, -3.5, 6.5, 3.5, 2.5, 2.5);
    private static final Shape ARM = new RoundRectangle2D.Double(-2.25, 0, 4.5, 10.5, 4, 4);
    private static final Shape BODY = new RoundRectangle2D.Double(-7, -24, 14, 15, 6, 6);
    private static final Shape CHEST_SHAPE = new RoundRectangle2D.Double(-3, -19.5, 6, 4.5, 2, 2);
    private static final Shape HELMET = new Ellipse2D.Double(-8.5, -39, 17, 17);
    private static final Shape VISOR_SHAPE = new RoundRectangle2D.Double(-5.5, -34.5, 11, 7.5, 7, 7);
    private static final Shape GLINT_SHAPE = new Ellipse2D.Double(-3.8, -33.3, 3.2, 2);

    /**
     * Draws the astronaut with the point between their feet at (x, y) in the layout.
     *
     * @param step   -1 to 1 while walking: its sign says which foot is off the ground and its
     *               size how far; 0 while standing
     * @param facing -1 when heading left, 1 when heading right, 0 when facing the player
     */
    static void paint(Graphics2D g, double x, double y, double step, int facing) {
        AffineTransform before = g.getTransform();
        g.translate(x, y);
        g.setColor(SHADOW);
        g.fill(SHADOW_SHAPE);

        double lift = Math.abs(step);
        g.translate(0, -1.5 * lift); // a little bounce in the walk
        g.setStroke(EDGE);
        part(g, PACK_SHAPE, PACK, 0, 0);
        leg(g, -3.6, step > 0 ? 2.6 * lift : 0);
        leg(g, 3.6, step < 0 ? 2.6 * lift : 0);
        part(g, ARM, SUIT_SHADE, -9, -22.5 + 1.6 * step);
        part(g, ARM, SUIT_SHADE, 9, -22.5 - 1.6 * step);
        part(g, BODY, SUIT, 0, 0);
        g.setColor(CHEST);
        g.fill(CHEST_SHAPE);
        part(g, HELMET, SUIT, 0, 0);
        g.translate(2.2 * facing, 0);
        g.setColor(VISOR);
        g.fill(VISOR_SHAPE);
        g.setColor(GLINT);
        g.fill(GLINT_SHAPE);
        g.setTransform(before);
    }

    private static void leg(Graphics2D g, double x, double lifted) {
        part(g, LEG, SUIT_SHADE, x, -lifted);
        g.translate(x, -lifted);
        g.setColor(BOOT);
        g.fill(BOOT_SHAPE);
        g.translate(-x, lifted);
    }

    /** One part of the suit, filled and given a thin dark edge so it shows on pale ground. */
    private static void part(Graphics2D g, Shape shape, Color fill, double x, double y) {
        g.translate(x, y);
        g.setColor(fill);
        g.fill(shape);
        g.setColor(OUTLINE);
        g.draw(shape);
        g.translate(-x, -y);
    }

    private Astronaut() {}
}
