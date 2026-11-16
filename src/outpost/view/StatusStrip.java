package outpost.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Outpost;
import outpost.shared.Phase;
import outpost.shared.RadiationConditions;
import outpost.shared.Readings;

/**
 * The strip along the top of the outpost screen. It holds what the player is looking after:
 * which sol it is and how far the Sun has got, the astronaut's health and the energy in the
 * battery. Between those two are the day's two real readings, small: how cold it is outside
 * now and how much radiation there is today. Each of the two opens a card with the rest of
 * what the rover measured (OutpostView owns the click and the card). Food is shown on the
 * greenhouse's button instead. The strip draws from the GameState and changes nothing.
 */
final class StatusStrip {

    static final Rectangle2D AREA = new Rectangle2D.Double(12, 10, 776, 48);
    /** The battery's part of the strip. The view explains the moon mark when the mouse is over it. */
    static final Rectangle2D ENERGY = new Rectangle2D.Double(538, 10, 250, 48);
    /** The temperature outside now. As tall as the strip, so a finger can hit it; a click opens the weather card. */
    static final Rectangle2D WEATHER = new Rectangle2D.Double(380, 10, 74, 48);
    /** Today's radiation, pink on a solar storm sol. A click opens the radiation card. */
    static final Rectangle2D RADIATION = new Rectangle2D.Double(454, 10, 84, 48);
    /** The pink of a solar storm. */
    static final Color ALERT = new Color(255, 96, 160);
    private static final Color EMPTY_BAR = new Color(62, 66, 88);
    private static final Color BUBBLE = new Color(24, 28, 44, 222);
    private static final Color SUN = new Color(255, 205, 80);
    private static final Color DIVIDER = new Color(255, 255, 255, 40);
    private static final Color TRACK_DAY = Draw.alpha(Color.WHITE, 96);
    private static final Color TRACK_NIGHT = Draw.alpha(Color.WHITE, 46);
    /** What the dark part of a crescent moon is drawn in: on the energy bar, and in the Sun's track. */
    private static final Color MOON_SHADE = new Color(24, 28, 44);
    private static final Color TRACK_MOON_SHADE = BUBBLE.darker();
    private static final Color MARK_EDGE = new Color(16, 18, 30);
    private static final Font READING_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    /** One of the Glyphs drawing methods. */
    @FunctionalInterface
    interface Icon {
        void draw(Graphics2D g, double x, double y, double size, Color colour);
    }

    static void paint(Graphics2D g, GameState state) {
        Outpost o = state.outpost();
        double left = AREA.getX();
        double top = AREA.getY();
        Draw.box(g, left, top, AREA.getWidth(), AREA.getHeight(), BUBBLE, Draw.EDGE);

        g.setFont(Draw.BIG_FONT);
        g.setColor(Color.WHITE);
        g.drawString("Sol " + state.sol().number() + " of " + state.missionSols(), (float) left + 11, (float) top + 23);
        g.setFont(Draw.BODY_FONT);
        g.setColor(Draw.MUTED);
        g.drawString("Earth date " + DATE.format(state.sol().earthDate()), (float) left + 11, (float) top + 39);
        paintSunTrack(g, left + 124, top + 26, state);

        meter(g, left + 176, 192, Glyphs::heart, Glyphs.HEALTH, "Health",
                String.valueOf(Draw.health(o.health())), o.health() / 100);
        // Why health is falling, as the energy bar says "filling" or "draining".
        if (clockRuns(state)) {
            switch (state.now().harm()) {
                case COLD:
                    trend(g, left + 176 + TREND_LEFT, -1, "cold", Glyphs.BAD);
                    break;
                case RADIATION:
                    trend(g, left + 176 + TREND_LEFT, -1, "radiation", Glyphs.WARN);
                    break;
                default:
                    break;
            }
        }

        // The two readings open their cards only while a sol is being played.
        boolean opens = state.phase() == Phase.DAY;
        boolean dusty = state.weather().sunlight() < 0.5;
        reading(g, WEATHER, dusty ? "Dusty sky" : "Outside", dusty ? Glyphs.WARN : Color.WHITE,
                degrees(state.now().outsideC()), Color.WHITE, opens);
        RadiationConditions radiation = state.radiation();
        Color tone = radiation.solarEvent() ? ALERT : Color.WHITE;
        reading(g, RADIATION, "Radiation", tone,
                String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv()), tone, opens);
        paintEnergy(g, state);
    }

    /** A whole number of degrees, never "-0". */
    static String degrees(double celsius) {
        return Math.round(celsius) + "\u00b0C";
    }

    /** The small arrow after a reading's name, pointing down to where its card opens. Made once. */
    private static final Path2D.Double MORE = new Path2D.Double();

    static {
        MORE.moveTo(0, 0);
        MORE.lineTo(7, 0);
        MORE.lineTo(3.5, 4.5);
        MORE.closePath();
    }

    /**
     * One of the day's real readings: its name, and under it the reading itself. While
     * {@code opens}, a small arrow after the name says that a click brings more.
     */
    private static void reading(Graphics2D g, Rectangle2D area, String name, Color nameColour, String value,
                                Color valueColour, boolean opens) {
        float left = (float) area.getX() + 9;
        float top = (float) area.getY();
        g.setColor(DIVIDER);
        g.fillRect((int) area.getX(), (int) top + 7, 1, (int) area.getHeight() - 14);
        g.setFont(Draw.BODY_FONT);
        g.setColor(nameColour);
        g.drawString(name, left, top + 17);
        if (opens) {
            double x = left + g.getFontMetrics().stringWidth(name) + 5;
            g.translate(x, top + 10.5);
            g.setColor(Draw.MUTED);
            g.fill(MORE);
            g.translate(-x, -(top + 10.5));
        }
        g.setFont(READING_FONT);
        g.setColor(valueColour);
        g.drawString(value, left, top + 38);
    }

    /** How far into its section a bar's trend is written, after the bar's name. */
    private static final double TREND_LEFT = 74;

    /**
     * True while time is passing. While the clock waits (a box is up, a card is open or
     * the lesson is running) nothing is filling, draining or falling, so no trend is shown.
     */
    private static boolean clockRuns(GameState state) {
        return (state.phase() == Phase.DAY || state.phase() == Phase.NIGHT) && !state.paused();
    }

    /**
     * A word beside a bar's name saying which way the bar is going and why, with an arrow
     * that points up ({@code arrow} 1), down (-1) or is left out (0).
     */
    private static void trend(Graphics2D g, double left, int arrow, String word, Color colour) {
        double top = AREA.getY();
        double x = left;
        g.setColor(colour);
        if (arrow != 0) {
            Path2D.Double triangle = new Path2D.Double();
            double tip = arrow > 0 ? top + 9 : top + 17;
            double base = arrow > 0 ? top + 17 : top + 9;
            triangle.moveTo(x, base);
            triangle.lineTo(x + 8, base);
            triangle.lineTo(x + 4, tip);
            triangle.closePath();
            g.fill(triangle);
            x += 12;
        }
        g.setFont(Draw.BODY_FONT);
        g.drawString(word, (float) x, (float) top + 17);
    }

    /**
     * The Sun's day in small: an arc from sunrise on the left to sunset on the right, with
     * the Sun on it by day and the Moon crossing it by night.
     */
    private static void paintSunTrack(Graphics2D g, double left, double horizon, GameState state) {
        Readings now = state.now();
        boolean night = now.dayShare() >= 1 && state.phase() != Phase.READY;
        double width = 46;
        double height = 14;
        double centre = left + width / 2;
        g.setStroke(Draw.HAIRLINE);
        g.setColor(night ? TRACK_NIGHT : TRACK_DAY);
        g.draw(new Line2D.Double(left - 3, horizon + 0.5, left + width + 3, horizon + 0.5));
        g.draw(new Arc2D.Double(left, horizon - height, width, 2 * height, 0, 180, Arc2D.OPEN));
        double angle = Math.PI * (night ? now.nightShare() : now.dayShare());
        double x = centre - Math.cos(angle) * width / 2;
        double y = horizon - Math.sin(angle) * height;
        if (night) {
            g.setColor(Draw.MOON);
            g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
            g.setColor(TRACK_MOON_SHADE);
            g.fill(new Ellipse2D.Double(x - 1.5, y - 5, 7, 7));
        } else {
            g.setColor(SUN);
            g.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
        }
    }

    /** Starts a section of the strip at {@code left}: a faint line, an icon and a name. */
    private static void section(Graphics2D g, double left, Icon icon, Color colour, String name) {
        double top = AREA.getY();
        g.setColor(DIVIDER);
        g.fill(new Rectangle2D.Double(left, top + 7, 1, AREA.getHeight() - 14));
        icon.draw(g, left + 12, top + 7, 12, colour);
        g.setFont(Draw.BODY_FONT);
        g.setColor(Color.WHITE);
        g.drawString(name, (float) left + 30, (float) top + 17);
    }

    /** A bar in the strip, {@code share} full (0.0 to 1.0). */
    private static void bar(Graphics2D g, double left, double width, Color colour, double share) {
        double top = AREA.getY() + 32;
        g.setColor(EMPTY_BAR);
        g.fill(new Rectangle2D.Double(left, top, width, 8));
        g.setColor(colour);
        g.fill(new Rectangle2D.Double(left, top, width * Draw.clamp(share), 8));
    }

    /** Health: a name, a number and a bar. */
    private static void meter(Graphics2D g, double left, double width, Icon icon, Color colour, String name,
                              String value, double share) {
        section(g, left, icon, colour, name);
        g.setFont(Draw.TITLE_FONT);
        Draw.rightAligned(g, value, left + width - 12, AREA.getY() + 17);
        bar(g, left + 12, width - 24, colour, share);
    }

    /**
     * The battery: how much energy it holds, whether it is filling or draining while the
     * clock runs, and a moon mark at the level tonight will take with the switches as they
     * are. The mark turns red when the battery cannot hold that much, or when the afternoon
     * has come and it does not hold that much yet.
     */
    private static void paintEnergy(Graphics2D g, GameState state) {
        Outpost o = state.outpost();
        Readings now = state.now();
        double left = ENERGY.getX();
        double top = ENERGY.getY();
        double width = ENERGY.getWidth();
        double capacity = state.energyCapacity();
        section(g, left, Glyphs::bolt, Glyphs.POWER, "Energy");

        g.setFont(Draw.TITLE_FONT);
        g.setColor(Color.WHITE);
        Draw.rightAligned(g, String.format(Locale.ENGLISH, "%.0f", Math.floor(o.energy())), left + width - 12, top + 17);

        // Filling, draining, full or empty.
        boolean rising = now.energyIn() > now.energyOut();
        if (clockRuns(state)) {
            if (rising && o.energy() >= capacity - 0.5) {
                trend(g, left + TREND_LEFT, 0, "full", Glyphs.GOOD);
            } else if (!rising && o.energy() <= 0.5) {
                trend(g, left + TREND_LEFT, 0, "empty", Glyphs.BAD);
            } else if (rising) {
                trend(g, left + TREND_LEFT, 1, "filling", Glyphs.GOOD);
            } else {
                trend(g, left + TREND_LEFT, -1, "draining", Glyphs.WARN);
            }
        }

        double barLeft = left + 12;
        double barWidth = width - 24;
        bar(g, barLeft, barWidth, Glyphs.POWER, o.energy() / capacity);

        // The moon mark: what tonight will take.
        boolean late = now.dayShare() > 0.5;
        boolean tooLittle = now.nightNeed() > capacity || (late && o.energy() < now.nightNeed() - 0.5);
        Color mark = tooLittle ? Glyphs.BAD : Draw.MOON;
        double markX = Math.round(barLeft + barWidth * Draw.clamp(now.nightNeed() / capacity));
        g.setColor(MARK_EDGE);
        g.fill(new Rectangle2D.Double(markX - 2, top + 29, 4, 14));
        g.setColor(mark);
        g.fill(new Rectangle2D.Double(markX - 1, top + 29, 2, 14));
        moon(g, Math.max(barLeft + 4, Math.min(barLeft + barWidth - 4, markX)), top + 24.5, mark);
    }

    /** A small crescent moon centred at (x, y). */
    private static void moon(Graphics2D g, double x, double y, Color colour) {
        g.setColor(colour);
        g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
        g.setColor(MOON_SHADE);
        g.fill(new Ellipse2D.Double(x - 1.5, y - 5, 7, 7));
    }

    private StatusStrip() {}
}
