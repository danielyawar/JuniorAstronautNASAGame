package outpost.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import outpost.shared.Helmet;
import outpost.shared.RadiationSource;
import outpost.shared.Sol;
import outpost.shared.WeatherConditions;
import outpost.shared.WeatherSource;

/**
 * Works out the helmet that stands for a mission from the mission's own data: the share of
 * its sols with a dusty sky, how cold its usual night is, and the share of its sols with a
 * solar storm. The numbers here are GAME ESTIMATES chosen so that the four missions on the
 * home screen look different at a glance; they are not NASA figures.
 */
final class Helmets {

    /** A sky that lets through less than this share of the sunlight is a dusty one. */
    static final double DUSTY_BELOW = 0.5;
    /** Share of dusty sols at which the helmet is as dusty as it gets: 5 sols of 12. */
    static final double DUSTY_SHARE_FULL = 0.4;
    /** The usual night's low, in degrees Celsius, at which frost first shows and at which it is thickest. */
    static final double FROST_STARTS_C = -79;
    static final double FROST_FULL_C = -87;
    /** Share of solar storm sols at which the helmet's visor is as stormy as it gets: 9 sols of 12. */
    static final double STORMY_SHARE_FULL = 0.75;
    /** How much each of the three adds to the helmet's scuffs. */
    static final double WEAR_FROM_DUST = 0.75;
    static final double WEAR_FROM_STORM = 0.25;
    static final double WEAR_FROM_FROST = 0.15;

    /** The helmet for a mission of {@code sols} sols that starts on {@code start}. */
    static Helmet of(LocalDate start, int sols, WeatherSource weather, RadiationSource radiation) {
        List<Double> lows = new ArrayList<>();
        int dusty = 0;
        int stormy = 0;
        Sol sol = new Sol(1, start);
        for (int i = 0; i < sols; i++, sol = sol.next()) {
            WeatherConditions conditions = weather.weatherFor(sol);
            lows.add(conditions.minTempC());
            dusty += conditions.sunlight() < DUSTY_BELOW ? 1 : 0;
            stormy += radiation.radiationFor(sol).solarEvent() ? 1 : 0;
        }
        double dust = share(dusty / (double) sols / DUSTY_SHARE_FULL);
        double frost = share((FROST_STARTS_C - usual(lows)) / (FROST_STARTS_C - FROST_FULL_C));
        double storm = share(stormy / (double) sols / STORMY_SHARE_FULL);
        double wear = share(WEAR_FROM_DUST * dust + WEAR_FROM_STORM * storm + WEAR_FROM_FROST * frost);
        return new Helmet(dust, frost, storm, wear);
    }

    /**
     * The usual value: the middle one. A mission can have one or two nights far colder than
     * the rest (the 2024 archive has single nights near -98 among nights near -69), and
     * they should not frost the helmet over.
     */
    private static double usual(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double share(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private Helmets() {}
}
