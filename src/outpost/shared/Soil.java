package outpost.shared;

/**
 * The greenhouse soil. It starts as Earth soil, and as Earth soil runs out, Mars soil takes
 * its place, which pushes the pH up. {@code Outpost.marsSoil} is the share that is Mars soil
 * (0.0 to 1.0); everything here works out what that share means.
 *
 * <p>REAL: Mojave Mars Simulant MMS-1, ryegrass grown on it for 14 days, NASA OSDR OSD-581
 * (Berni et al. 2023, Science of the Total Environment 854:158774). pH 9.25 at the start
 * and 8.55 at day 14. The plants grew, but with yellow, downward-bent leaves and root air
 * gaps. That study has no healthy Mars-soil reading.
 *
 * <p>BORROWED: a healthy pH of about 6.3 for amended MMS-1, from a related Mars-simulant
 * study on soybean (reported by the Naples group; check the citation before showing one).
 * It is not NASA and not ryegrass, and the Earth soil that the game starts with is not
 * measured: the study's own potting soil was about pH 5.
 *
 * <p>GAME RULES: that pH moves in a straight line with the Mars share (real soil does not),
 * where the zones begin, how much slower plants grow in each zone, and how fast Earth soil
 * is used up and compost puts it back.
 */
public final class Soil {

    /** How the garden soil is doing. */
    public enum Zone {
        HEALTHY("Healthy"),
        TIRED("Tired"),
        STRESSED("Stressed");

        private final String label;

        Zone(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** BORROWED: pH of the soil when it is all Earth soil. */
    public static final double EARTH_PH = 6.3;
    /** REAL: pH of Mars soil simulant MMS-1 at the start of OSD-581. */
    public static final double MARS_PH = 9.25;
    /** Game rule: at or below this pH the soil is healthy. */
    static final double HEALTHY_MAX_PH = 7.0;
    /** Game rule: food production stops at or above this pH. */
    public static final double FOOD_STOP_PH = 8.0;
    /** Game rule: above this pH the soil is stressed (the study saw stress at 8.55 and 9.25). */
    static final double STRESSED_ABOVE_PH = 8.5;

    /** Game rule: how fast plants grow in each zone, as a share of full speed. */
    static final double GROWTH_HEALTHY = 1.0;
    static final double GROWTH_TIRED = 0.75;
    static final double GROWTH_STRESSED = 0.5;

    /** The soil's pH. A straight line between Earth soil and Mars soil: a game simplification. */
    public static double ph(double marsShare) {
        return EARTH_PH + (MARS_PH - EARTH_PH) * clamp(marsShare);
    }

    public static Zone zone(double marsShare) {
        double ph = ph(marsShare);
        if (ph <= HEALTHY_MAX_PH) {
            return Zone.HEALTHY;
        }
        return ph <= STRESSED_ABOVE_PH ? Zone.TIRED : Zone.STRESSED;
    }

    /** How fast plants grow in this soil, as a share of full speed. */
    public static double growth(double marsShare) {
        switch (zone(marsShare)) {
            case HEALTHY:
                return GROWTH_HEALTHY;
            case TIRED:
                return GROWTH_TIRED;
            default:
                return GROWTH_STRESSED;
        }
    }

    public static double clamp(double share) {
        return Math.max(0, Math.min(1, share));
    }

    private Soil() {}
}
