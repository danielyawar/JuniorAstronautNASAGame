package outpost.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The layout size, the colours and the small drawing helpers that every screen of the view
 * shares, so that the home screen and the outpost look like one game.
 */
final class Draw {

    /** Every screen is laid out for this size and scaled to fit the panel. */
    static final int WIDTH = 800;
    static final int HEIGHT = 600;

    static final Color EDGE = new Color(255, 255, 255, 80);
    static final Color MUTED = new Color(196, 200, 214);
    static final Color CREAM = new Color(255, 244, 232, 235);
    static final Color CARD_FILL = new Color(24, 28, 44);
    /** Fill of a card or button the mouse is over. */
    static final Color CARD_HOT = new Color(40, 46, 70);
    /** Dark text and edges on a bright button. */
    static final Color INK = new Color(24, 26, 38);
    /** The pale blue of the Moon: the night, and what it will take. */
    static final Color MOON = new Color(206, 216, 255);

    /** The fonts are made once here, never while painting. */
    static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    static final Font BUTTON_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font HEADLINE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 22);
    static final Font STORY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);

    static final BasicStroke HAIRLINE = new BasicStroke(1f);
    static final BasicStroke THICK = new BasicStroke(2f);

    /**
     * Moves and scales {@code g} so that drawing in the 800 x 600 layout fills a panel of
     * the given size, centred, with its shape kept.
     */
    static void fit(Graphics2D g, int panelWidth, int panelHeight) {
        double scale = scale(panelWidth, panelHeight);
        g.translate((panelWidth - WIDTH * scale) / 2, (panelHeight - HEIGHT * scale) / 2);
        g.scale(scale, scale);
    }

    /** Panel pixels for each unit of the 800 x 600 layout. */
    static double scale(int panelWidth, int panelHeight) {
        return Math.min(panelWidth / (double) WIDTH, panelHeight / (double) HEIGHT);
    }

    /** A square-cornered box with a one-pixel edge, snapped to whole pixels so it stays crisp. */
    static void box(Graphics2D g, double x, double y, double width, double height, Color fill, Color edge) {
        double left = Math.round(x);
        double top = Math.round(y);
        double w = Math.round(width);
        double h = Math.round(height);
        g.setColor(fill);
        g.fill(new Rectangle2D.Double(left, top, w, h));
        if (edge != null) {
            g.setStroke(HAIRLINE);
            g.setColor(edge);
            g.draw(new Rectangle2D.Double(left + 0.5, top + 0.5, w - 1, h - 1));
        }
    }

    /** A flat, bright button with dark text in {@code font}. It lights up while {@code hot}. */
    static void button(Graphics2D g, Rectangle2D area, String text, Font font, Color fill, boolean hot) {
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                hot ? shade(fill, 1.12) : fill, hot ? Color.WHITE : new Color(20, 22, 34));
        g.setFont(font);
        g.setColor(INK);
        centred(g, text, area.getCenterX(), area.getCenterY() + font.getSize() * 0.36);
    }

    /** The small triangle on top of a card that hangs below the thing it is about, pointing up at it. */
    static void pointerUp(Graphics2D g, double x, double top, Color colour) {
        Path2D.Double pointer = new Path2D.Double();
        pointer.moveTo(x - 6, top + 1);
        pointer.lineTo(x + 6, top + 1);
        pointer.lineTo(x, top - 7);
        pointer.closePath();
        g.setColor(colour);
        g.fill(pointer);
    }

    static Color alpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /** {@code colour} made darker (a factor under 1) or brighter (over 1). How see-through it is stays the same. */
    static Color shade(Color colour, double factor) {
        return new Color(channel(colour.getRed() * factor), channel(colour.getGreen() * factor),
                channel(colour.getBlue() * factor), colour.getAlpha());
    }

    /** The colour {@code share} of the way from {@code from} to {@code to}: 0.0 is the first and 1.0 the second. */
    static Color mix(Color from, Color to, double share) {
        double k = clamp(share);
        return new Color(channel(from.getRed() + (to.getRed() - from.getRed()) * k),
                channel(from.getGreen() + (to.getGreen() - from.getGreen()) * k),
                channel(from.getBlue() + (to.getBlue() - from.getBlue()) * k),
                channel(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * k));
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    static void centred(Graphics2D g, String text, double centreX, double baseline) {
        g.drawString(text, (float) (centreX - g.getFontMetrics().stringWidth(text) / 2.0), (float) baseline);
    }

    static void rightAligned(Graphics2D g, String text, double right, double baseline) {
        g.drawString(text, (float) (right - g.getFontMetrics().stringWidth(text)), (float) baseline);
    }

    /** Splits a sentence into lines no wider than {@code width}. */
    static List<String> wrap(String sentence, FontMetrics metrics, double width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : sentence.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(longer) > width) {
                lines.add(line);
                line = word;
            } else {
                line = longer;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * The astronaut's health as the number the game shows: a whole number, rounded up, so
     * that it reads 1 until the health has really run out.
     */
    static int health(double health) {
        return (int) Math.ceil(health);
    }

    private Draw() {}
}
