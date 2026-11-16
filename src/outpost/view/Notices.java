package outpost.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Phase;
import outpost.shared.Scenario;
import outpost.shared.Tally;

/**
 * The three boxes that stop the game to tell the player something: the mission-start card
 * before sol 1, the report on the night at each dawn, and the box at the end of the mission.
 * Each stands in the middle of the screen over the dimmed outpost and has one button.
 * OutpostView owns the mouse; this class draws the boxes and says where their buttons are.
 */
final class Notices {

    /** START on the mission-start card. */
    static final Rectangle2D START_BUTTON = new Rectangle2D.Double(320, 406, 160, 40);
    /** PLAY AGAIN on the box at the end of the mission. */
    static final Rectangle2D PLAY_AGAIN_BUTTON = new Rectangle2D.Double(330, 344, 140, 38);

    private static final Rectangle2D START_BOX = new Rectangle2D.Double(150, 102, 500, 362);
    /** Width and height of the helmet on the mission-start card. */
    private static final double START_HELMET = 116;
    private static final Rectangle2D ENDING_BOX = new Rectangle2D.Double(160, 196, 480, 206);

    private static final Font ROW_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font ROW_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_AND_YEAR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    /** Darkens the outpost behind a box, so the box is the one thing to read. */
    private static void dim(Graphics2D g) {
        g.setColor(new Color(10, 10, 20, 130));
        g.fillRect(-Draw.WIDTH, -Draw.HEIGHT, 3 * Draw.WIDTH, 3 * Draw.HEIGHT);
    }

    /**
     * Before sol 1: the mission's helmet, its name, its real dates and what really happened
     * then. The helmet is the one the home screen showed for the mission, standing still;
     * for a surprise mission it shows what the days that were picked are really like.
     */
    static void paintStart(Graphics2D g, GameState state, Component owner, boolean hot) {
        Scenario mission = state.mission();
        dim(g);
        Rectangle2D box = START_BOX;
        double centre = box.getCenterX();
        Draw.box(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), Draw.CARD_FILL, Glyphs.POWER);
        Helmet3D.paintStill(g, mission.helmet(), centre - START_HELMET / 2, box.getY() + 12, START_HELMET);
        g.setFont(Draw.HEADLINE_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, mission.title(), centre, box.getY() + 156);
        g.setFont(ROW_FONT);
        g.setColor(Draw.MUTED);
        Draw.centred(g, "Earth dates: " + dates(mission), centre, box.getY() + 177);

        g.setFont(Draw.STORY_FONT);
        g.setColor(Draw.CREAM);
        List<String> story = Draw.wrap(mission.story(), owner.getFontMetrics(Draw.STORY_FONT), box.getWidth() - 56);
        // The story is two to four lines; it sits in the middle of the room left for four.
        double y = box.getY() + 205 + Math.max(0, 4 - story.size()) * 9;
        for (String line : story) {
            Draw.centred(g, line, centre, y);
            y += 18;
        }
        g.setFont(ROW_BOLD);
        g.setColor(Glyphs.POWER);
        Draw.centred(g, "Keep your astronaut safe for " + state.missionSols() + " sols. A sol is one day on Mars.",
                centre, box.getY() + 288);
        Draw.button(g, START_BUTTON, "START", Draw.BUTTON_FONT, Glyphs.GOOD, hot);
    }

    /** The first and last Earth dates of a mission, with the year once if they share it. */
    private static String dates(Scenario mission) {
        LocalDate start = mission.start();
        LocalDate end = mission.end();
        return (start.getYear() == end.getYear() ? DAY : DAY_AND_YEAR).format(start)
                + " to " + DAY_AND_YEAR.format(end);
    }

    /**
     * At dawn: what the night did to the astronaut and why, one cause to a line, then what
     * was eaten and how much energy was used. Only the things that happened are listed.
     */
    static void paintNight(Graphics2D g, GameState state, boolean hot) {
        Tally night = state.night();
        dim(g);
        Rectangle2D box = nightBox(state);
        double centre = box.getCenterX();
        double left = box.getX() + 24;
        double right = box.getMaxX() - 24;
        Draw.box(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), Draw.CARD_FILL, Draw.MOON);
        g.setFont(Draw.HEADLINE_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, "Night " + (state.sol().number() - 1), centre, box.getY() + 40);

        int[] lines = healthLines(state);
        int change = lines[RADIATION] + lines[COLD] + lines[HUNGER] + lines[REST];
        g.setFont(ROW_BOLD);
        if (change < 0) {
            g.setColor(Glyphs.BAD);
            Draw.centred(g, "Health went down by " + -change, centre, box.getY() + 62);
        } else if (change > 0) {
            g.setColor(Glyphs.GOOD);
            Draw.centred(g, "Health went up by " + change, centre, box.getY() + 62);
        } else {
            g.setColor(Draw.MUTED);
            Draw.centred(g, "Health did not change", centre, box.getY() + 62);
        }

        double y = box.getY() + 92;
        if (lines[RADIATION] != 0) {
            double stopped = state.now().shieldBlocks();
            String why = stopped >= 0.005
                    ? String.format(Locale.ENGLISH, "Radiation (the shield stopped %.0f%%)", stopped * 100)
                    : "Radiation (no shield to stop it)";
            y = row(g, left, right, y, why, signed(lines[RADIATION]), Glyphs.WARN);
        }
        if (lines[COLD] != 0) {
            String why = night.hoursNoPower() > 0.5 && night.hoursNoPower() >= night.hoursHeaterOff()
                    ? "Cold: the battery ran empty"
                    : night.hoursHeaterOff() > 0.5 ? "Cold: the heater was off" : "Cold";
            y = row(g, left, right, y, why, signed(lines[COLD]), Glyphs.BAD);
        }
        if (lines[HUNGER] != 0) {
            y = row(g, left, right, y,
                    night.foodEaten() < 0.05 ? "Hunger: there was no food" : "Hunger: not enough food",
                    signed(lines[HUNGER]), Glyphs.BAD);
        }
        if (lines[REST] != 0) {
            y = row(g, left, right, y, "A warm sleep", signed(lines[REST]), Glyphs.GOOD);
        }
        y += 4;
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new Rectangle2D.Double(left, y - 12, right - left, 1));
        y += 8;
        String eaten = night.foodEaten() < 0.05 ? "none"
                : night.foodEaten() > 0.95 ? "1 day" : "less than 1 day";
        y = row(g, left, right, y, "Food eaten", eaten, Color.WHITE);
        row(g, left, right, y, "Energy used", String.format(Locale.ENGLISH, "%.0f", night.energyUsed()), Color.WHITE);

        Draw.button(g, nextSolButton(state), "START SOL " + state.sol().number(), Draw.BUTTON_FONT,
                Glyphs.GOOD, hot);
    }

    // The places of the four causes in the answer of healthLines.
    private static final int RADIATION = 0;
    private static final int COLD = 1;
    private static final int HUNGER = 2;
    private static final int REST = 3;

    /**
     * What each cause did to health in the night, as whole numbers for the report: what
     * radiation, cold and hunger took (0 or less) and what rest gave back (0 or more). A
     * young reader will add the lines up, so they are rounded together, to add up to the
     * change in the health number the top strip showed: its number at dawn less its number
     * at bedtime. A cause that comes to 0 gets no line.
     */
    private static int[] healthLines(GameState state) {
        Tally night = state.night();
        double[] exact = {-night.radiation(), -night.cold(), -night.hunger(), night.rest()};
        int[] whole = new int[exact.length];
        int sum = 0;
        for (int i = 0; i < exact.length; i++) {
            whole[i] = (int) Math.round(exact[i]);
            sum += whole[i];
        }
        double atDawn = state.outpost().health();
        int change = Draw.health(atDawn) - Draw.health(atDawn - night.healthChange());
        // Each line was rounded on its own, so together they can be out by one or two. The
        // line that rounding moved furthest the wrong way gives way first. A loss never
        // turns into a gain.
        while (sum != change) {
            int step = change > sum ? 1 : -1;
            int furthest = REST;
            double most = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < exact.length; i++) {
                boolean allowed = i == REST ? whole[i] + step >= 0 : whole[i] + step <= 0;
                double off = step * (exact[i] - whole[i]);
                if (allowed && off > most) {
                    furthest = i;
                    most = off;
                }
            }
            whole[furthest] += step;
            sum += step;
        }
        return whole;
    }

    /** The night's report, as tall as its lines need and in the middle of the screen. */
    private static Rectangle2D nightBox(GameState state) {
        int rows = 2;
        for (int line : healthLines(state)) {
            rows += line != 0 ? 1 : 0;
        }
        double height = 166 + rows * 22;
        return new Rectangle2D.Double(215, Math.round((Draw.HEIGHT - height) / 2), 370, height);
    }

    /** START SOL n on the night's report. */
    static Rectangle2D nextSolButton(GameState state) {
        Rectangle2D box = nightBox(state);
        return new Rectangle2D.Double(310, box.getMaxY() - 58, 180, 40);
    }

    private static String signed(int change) {
        return String.format(Locale.ENGLISH, "%+d", change);
    }

    /** The nearest whole number of days, for a young reader: "1 day", "3 days". */
    private static String days(double days) {
        long whole = Math.round(days);
        return whole == 1 ? "1 day" : whole + " days";
    }

    /** One line of the night's report. Returns where the next line goes. */
    private static double row(Graphics2D g, double left, double right, double y, String name, String value,
                              Color colour) {
        g.setFont(ROW_FONT);
        g.setColor(Draw.CREAM);
        g.drawString(name, (float) left, (float) y);
        g.setFont(ROW_BOLD);
        g.setColor(colour);
        Draw.rightAligned(g, value, right, y);
        return y + 22;
    }

    /** The end of the mission: how it went, what cost the most health, and a button to play again. */
    static void paintEnding(Graphics2D g, GameState state, boolean hot) {
        boolean won = state.phase() == Phase.WON;
        Tally all = state.outpost().tally();
        dim(g);
        Rectangle2D box = ENDING_BOX;
        double centre = box.getCenterX();
        Draw.box(g, box.getX(), box.getY(), box.getWidth(), box.getHeight(), Draw.CARD_FILL,
                won ? Glyphs.GOOD : Glyphs.BAD);
        g.setFont(Draw.HEADLINE_FONT);
        g.setColor(won ? Glyphs.GOOD : Glyphs.BAD);
        Draw.centred(g, won ? "Mission complete!" : "Mission over", centre, box.getY() + 40);
        g.setFont(ROW_BOLD);
        g.setColor(Color.WHITE);
        if (won) {
            Draw.centred(g, "Your astronaut stayed safe for all " + state.missionSols() + " sols.",
                    centre, box.getY() + 68);
            Draw.centred(g, "Health left: " + Draw.health(state.outpost().health()) + ".  Food left: "
                    + days(state.outpost().foodDays()) + ".", centre, box.getY() + 90);
        } else {
            Draw.centred(g, "Your astronaut's health ran out on sol " + state.sol().number() + ".",
                    centre, box.getY() + 68);
            Draw.centred(g, worst(all), centre, box.getY() + 90);
        }
        g.setFont(ROW_FONT);
        g.setColor(Draw.MUTED);
        Draw.centred(g, String.format(Locale.ENGLISH,
                "Health lost to:   radiation %.0f   cold %.0f   hunger %.0f",
                all.radiation(), all.cold(), all.hunger()), centre, box.getY() + 116);
        Draw.button(g, PLAY_AGAIN_BUTTON, "PLAY AGAIN", Draw.BUTTON_FONT, Glyphs.POWER, hot);
    }

    /** One sentence naming what took the most health over the whole mission. */
    private static String worst(Tally all) {
        if (all.cold() >= all.radiation() && all.cold() >= all.hunger()) {
            return "The cold took the most health. Keep the heater running.";
        }
        if (all.hunger() >= all.radiation()) {
            return "Hunger took the most health. Grow and pick more food.";
        }
        return "Radiation took the most health. Build the shield and rest inside.";
    }

    private Notices() {}
}
