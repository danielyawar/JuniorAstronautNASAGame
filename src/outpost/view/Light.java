package outpost.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.util.Random;
import outpost.shared.GameState;

/**
 * The light of the day and the night, laid over the picture of the outpost: one see-through
 * colour for the hour and the dust in the air together, and on a solar storm sol a scatter
 * of short pink streaks. It is drawn over the ground, the buildings and the astronaut and
 * under every control, and it covers the whole panel.
 *
 * <p>Both are cheap: one filled rectangle, and 44 lines on storm sols. What is kept here is
 * drawing work only (the last colour, the streaks of this sol); no game state.
 */
final class Light {

    /** The colour of the night, and how thickly it lies over the picture when the Sun is down. */
    private static final Color NIGHT = new Color(10, 14, 44);
    private static final double NIGHT_ALPHA = 0.64;
    /** The colour of dust in the air, and how thickly it would lie if no sunlight got through at all. */
    private static final Color DUST = new Color(184, 130, 86);
    private static final double DUST_ALPHA = 0.8;

    private static final int STREAKS = 44;
    private static final BasicStroke STREAK = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    /** The colour last laid over the picture, kept so that a new one is made only when the light changes. */
    private Color tint;
    private int tintArgb;

    /** The streaks of a solar storm, worked out once for the sol in {@code streaksSol}. */
    private final double[][] streaks = new double[STREAKS][];
    private final Color[] streakColours = new Color[STREAKS];
    private int streaksSol = -1;
    private final Line2D.Double line = new Line2D.Double();

    /**
     * Lays the light of this moment over the picture. {@code g} is in the 800 x 600 layout;
     * the rectangle given is the whole panel in those units, which reaches outside the
     * layout when the panel is not the layout's shape.
     */
    void paint(Graphics2D g, GameState state, int left, int top, int wide, int high) {
        double dark = Math.pow(1 - Draw.clamp(state.now().sun()), 3);
        int argb = tint(dark, state.weather().sunlight());
        if (argb >>> 24 != 0) {
            if (tint == null || argb != tintArgb) {
                tint = new Color(argb, true);
                tintArgb = argb;
            }
            g.setColor(tint);
            g.fillRect(left, top, wide, high);
        }
        if (state.radiation().solarEvent()) {
            paintStorm(g, state.sol().number(), state.outpost().clock(), left, top, wide, high);
        }
    }

    /**
     * The one see-through colour that stands for both the time of day and the dust in the
     * air: night laid over dust. Nothing at all (alpha 0) in full daylight under a clear sky.
     *
     * @param dark     0.0 at midday to 1.0 at night
     * @param sunlight share of sunlight the sky lets through
     * @return the colour as alpha, red, green, blue in one number
     */
    private static int tint(double dark, double sunlight) {
        double night = NIGHT_ALPHA * dark;
        double dust = DUST_ALPHA * Math.pow(1 - Draw.clamp(sunlight), 1.3);
        double alpha = night + dust * (1 - night);
        if (alpha < 0.004) {
            return 0;
        }
        double fromDust = dust * (1 - night) / alpha;
        return (int) Math.round(255 * alpha) << 24
                | (int) Math.round(NIGHT.getRed() * (1 - fromDust) + DUST.getRed() * fromDust) << 16
                | (int) Math.round(NIGHT.getGreen() * (1 - fromDust) + DUST.getGreen() * fromDust) << 8
                | (int) Math.round(NIGHT.getBlue() * (1 - fromDust) + DUST.getBlue() * fromDust);
    }

    /** Streaks for the particles of a solar storm. The pattern depends only on the sol; the clock makes it fall. */
    private void paintStorm(Graphics2D g, int sol, double clock, int left, int top, int wide, int high) {
        if (streaksSol != sol) {
            Random random = new Random(sol * 7919L);
            for (int i = 0; i < STREAKS; i++) {
                streaks[i] = new double[] {random.nextDouble(), random.nextDouble(), 18 + random.nextDouble() * 30};
                streakColours[i] = new Color(255, 110 + random.nextInt(80), 215, 130 + random.nextInt(100));
            }
            streaksSol = sol;
        }
        double fallen = clock * 0.35;
        // A streak leans to the left as it falls, so they start from a strip wider than the panel.
        double across = wide + 200;
        g.setStroke(STREAK);
        for (int i = 0; i < STREAKS; i++) {
            double[] streak = streaks[i];
            double x = left - 100 + ((streak[0] - 0.5 * fallen * high / across) % 1 + 1) % 1 * across;
            double y = top + (streak[1] + fallen) % 1 * high;
            double length = streak[2];
            line.setLine(x, y, x - length * 0.5, y + length);
            g.setColor(streakColours[i]);
            g.draw(line);
        }
    }
}
