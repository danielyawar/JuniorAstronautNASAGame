package outpost.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Helmet;
import outpost.shared.Scenario;

/**
 * The home screen, shown while the phase is BRIEFING: the game's name, one card for each
 * mission and a "Surprise me" button. A card is the button that starts its mission.
 * OutpostView owns the mouse and the PlayerActions. This class draws the screen and says
 * what is at a point; it holds no state and changes nothing.
 *
 * <p>A child should take the screen in at a glance, so the main thing on a card is a
 * picture: an astronaut's helmet in the state the mission would leave it in (HelmetArt).
 * Under it are only the mission's name, its month and year, and one line on what makes it
 * hard. The cards stand in a row in the order of the state's list, which runs from the
 * gentlest mission to the hardest, and the first is marked "Start here". The story of what
 * really happened is on the mission-start card that follows.
 *
 * <p>Nothing here moves: the screen is repainted only when the mouse goes onto or off a card.
 */
final class HomeScreen {

    private static final Font HEADING_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 30);
    private static final Font INTRO_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 15);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    private static final Font WHEN_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font HARD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font SURPRISE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 21);
    private static final Font MARK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FOOT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private static final Color SKY_TOP = new Color(54, 38, 52);
    private static final Color SKY_BOTTOM = new Color(204, 136, 92);
    private static final Color FAR_GROUND = new Color(150, 84, 58);
    private static final Color NEAR_GROUND = new Color(98, 54, 44);

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private static final double MARGIN = 20;
    private static final double GAP = 12;
    private static final double CARD_TOP = 126;
    private static final double CARD_HEIGHT = 270;
    /** Width and height of the helmet on a card. */
    private static final double HELMET = 136;
    /** Room each side between a card's edge and its words. */
    private static final double TEXT_PAD = 8;
    private static final double SURPRISE_WIDTH = 322;
    private static final double SURPRISE_HEIGHT = 66;
    private static final double SURPRISE_HELMET = 56;

    /** Where the card of scenario number {@code i} of {@code count} is, in the 800 x 600 layout: all in one row. */
    private static Rectangle2D card(int i, int count) {
        double width = Math.floor((Draw.WIDTH - 2 * MARGIN - (count - 1) * GAP) / Math.max(1, count));
        double left = (Draw.WIDTH - count * width - (count - 1) * GAP) / 2;
        return new Rectangle2D.Double(left + i * (width + GAP), CARD_TOP, width, CARD_HEIGHT);
    }

    /**
     * Where the "Surprise me" button is: under the row of cards, and below the place where
     * START will be on the mission-start card that a click here opens. A child who clicks
     * twice then does not start the mission before seeing its card. (The cards themselves
     * are clear of START too.)
     */
    private static Rectangle2D surprise() {
        double top = Math.max(CARD_TOP + CARD_HEIGHT + 14, Notices.START_BUTTON.getMaxY() + 6);
        return new Rectangle2D.Double((Draw.WIDTH - SURPRISE_WIDTH) / 2, top, SURPRISE_WIDTH, SURPRISE_HEIGHT);
    }

    /** The scenario whose card is at (x, y) in the 800 x 600 layout, or null if none is. */
    static Scenario scenarioAt(GameState state, double x, double y) {
        List<Scenario> scenarios = state.scenarios();
        for (int i = 0; i < scenarios.size(); i++) {
            if (card(i, scenarios.size()).contains(x, y)) {
                return scenarios.get(i);
            }
        }
        return null;
    }

    /** True if the "Surprise me" button is at (x, y) in the 800 x 600 layout. */
    static boolean surpriseAt(GameState state, double x, double y) {
        return surprise().contains(x, y);
    }

    /**
     * Draws the whole screen onto a panel of the given size. {@code hot} is the scenario
     * under the mouse, or null; {@code surpriseHot} says the mouse is over "Surprise me".
     */
    static void paint(Graphics2D g, GameState state, int panelWidth, int panelHeight, Scenario hot,
                      boolean surpriseHot) {
        g.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, panelHeight, SKY_BOTTOM));
        g.fillRect(0, 0, panelWidth, panelHeight);
        Draw.fit(g, panelWidth, panelHeight);
        paintGround(g);

        double centre = Draw.WIDTH / 2.0;
        g.setFont(HEADING_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, "Junior Astronaut: Mars Outpost", centre, 70);
        g.setFont(INTRO_FONT);
        g.setColor(Draw.CREAM);
        Draw.centred(g, "Pick a mission. Keep your astronaut safe for " + state.missionSols()
                + " sols, the days on Mars.", centre, 102);

        List<Scenario> scenarios = state.scenarios();
        for (int i = 0; i < scenarios.size(); i++) {
            // The list runs from the gentlest mission to the hardest, so the first is the one to start with.
            paintCard(g, card(i, scenarios.size()), scenarios.get(i), scenarios.get(i).equals(hot), i == 0);
        }
        paintSurprise(g, surprise(), surpriseHot);

        g.setFont(FOOT_FONT);
        g.setColor(Draw.CREAM);
        Draw.centred(g, "Real NASA weather and solar storms from the past. A saved copy, not live.",
                centre, 584);
    }

    /** Two plain ridges of Mars ground along the bottom, wide enough for a panel of any shape. */
    private static void paintGround(Graphics2D g) {
        ridge(g, FAR_GROUND, -800, 532, 90, 524, 210, 508, 300, 520, 430, 514, 560, 526, 660, 510,
                740, 518, 1600, 530);
        ridge(g, NEAR_GROUND, -800, 552, 150, 546, 340, 554, 560, 544, 800, 550, 1600, 548);
    }

    /** Fills everything below the line through the given x, y points. */
    private static void ridge(Graphics2D g, Color colour, double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], 3 * Draw.HEIGHT);
        for (int i = 0; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.lineTo(xy[xy.length - 2], 3 * Draw.HEIGHT);
        path.closePath();
        g.setColor(colour);
        g.fill(path);
    }

    /**
     * The whole card is the button. Under the mouse it lights up with a yellow edge. The
     * helmet is the mission's picture; the words below it are the same three as ever.
     */
    private static void paintCard(Graphics2D g, Rectangle2D area, Scenario scenario, boolean hot, boolean first) {
        button(g, area, hot);
        double centre = area.getCenterX();
        double top = area.getY();
        double room = area.getWidth() - 2 * TEXT_PAD;
        HelmetArt.paint(g, scenario.helmet(), false, centre - HELMET / 2, top + 14, HELMET);

        // The name has room for two lines; a short one sits in the middle of that room.
        g.setFont(NAME_FONT);
        g.setColor(Color.WHITE);
        lines(g, twoLines(scenario.title(), g.getFontMetrics(), room), centre, top + 174, 20);
        g.setFont(WHEN_FONT);
        g.setColor(Draw.MUTED);
        Draw.centred(g, MONTH.format(scenario.start()), centre, top + 215);
        g.setFont(HARD_FONT);
        g.setColor(Glyphs.POWER);
        lines(g, twoLines(scenario.hard(), g.getFontMetrics(), room), centre, top + 238, 16);

        if (first) {
            // A green tab on the top edge of the gentlest mission's card.
            String mark = "Start here";
            g.setFont(MARK_FONT);
            double width = g.getFontMetrics().stringWidth(mark) + 22;
            Draw.box(g, centre - width / 2, top - 10, width, 20, Glyphs.GOOD, Draw.INK);
            g.setColor(Draw.INK);
            Draw.centred(g, mark, centre, top + 4.5);
        }
    }

    /**
     * Draws one or two lines centred on {@code centre}. {@code firstBaseline} is where the
     * first of two lines goes; a single line is drawn halfway between the two.
     */
    private static void lines(Graphics2D g, List<String> lines, double centre, double firstBaseline, double step) {
        double y = lines.size() == 1 ? firstBaseline + step / 2 : firstBaseline;
        for (String line : lines) {
            Draw.centred(g, line, centre, y);
            y += step;
        }
    }

    /**
     * {@code text} on one line if it fits in {@code width}, and otherwise split between two
     * words into the two lines that are nearest each other in length.
     */
    private static List<String> twoLines(String text, FontMetrics metrics, double width) {
        if (metrics.stringWidth(text) <= width) {
            return List.of(text);
        }
        List<String> best = List.of(text);
        int bestWidth = Integer.MAX_VALUE;
        for (int space = text.indexOf(' '); space > 0; space = text.indexOf(' ', space + 1)) {
            String head = text.substring(0, space);
            String tail = text.substring(space + 1);
            int widest = Math.max(metrics.stringWidth(head), metrics.stringWidth(tail));
            if (widest < bestWidth) {
                bestWidth = widest;
                best = List.of(head, tail);
            }
        }
        return best;
    }

    /** The helmet here has a question mark in its visor: nobody knows what the days will bring. */
    private static void paintSurprise(Graphics2D g, Rectangle2D area, boolean hot) {
        button(g, area, hot);
        String text = "Surprise me";
        g.setFont(SURPRISE_FONT);
        double width = g.getFontMetrics().stringWidth(text);
        double left = area.getCenterX() - (SURPRISE_HELMET + 12 + width) / 2;
        HelmetArt.paint(g, Helmet.CLEAN, true, left, area.getCenterY() - SURPRISE_HELMET / 2, SURPRISE_HELMET);
        g.setColor(Color.WHITE);
        g.drawString(text, (float) (left + SURPRISE_HELMET + 12), (float) area.getCenterY() + 8);
    }

    private static void button(Graphics2D g, Rectangle2D area, boolean hot) {
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                hot ? Draw.CARD_HOT : Draw.CARD_FILL, hot ? Glyphs.POWER : Draw.EDGE);
        if (hot) {
            // A second line inside the edge, so the highlight is easy to see.
            g.setColor(Glyphs.POWER);
            g.draw(new Rectangle2D.Double(area.getX() + 1.5, area.getY() + 1.5,
                    area.getWidth() - 3, area.getHeight() - 3));
        }
    }

    private HomeScreen() {}
}
