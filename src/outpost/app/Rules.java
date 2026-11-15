package outpost.app;

import outpost.shared.PowerAllocation;
import outpost.shared.RadiationConditions;
import outpost.shared.SolReport;
import outpost.shared.WeatherConditions;

/**
 * The game rules: how a sol's real weather and radiation, and the energy the player spends,
 * change food and crew health. Pure functions; GameController holds the state.
 *
 * <p>Every number in this class is a GAME RULE chosen so that the mission is playable and
 * the three uses of energy compete. None of them is a NASA figure. The inputs they work on
 * (temperature, sunlight level, radiation dose, storm dates) are the real data.
 */
public final class Rules {

    /** Sols the crew must last. */
    public static final int MISSION_SOLS = 30;
    public static final double START_HEALTH = 100;
    /** Days of food on the shelves when the mission starts. */
    public static final double START_FOOD = 5;
    /** Days of food the crew eats each sol. */
    public static final double FOOD_EATEN = 1;
    /** Most energy the battery holds. */
    public static final int ENERGY_CAPACITY = 20;
    /** Most energy the shield and the greenhouse can each use in a sol. */
    public static final int MAX_SHIELD = 6;
    public static final int MAX_GREENHOUSE = 6;

    /** Days of food one energy grows in a warm greenhouse. */
    static final double FOOD_PER_CELL = 0.35;
    /** Share of greenhouse growth lost for each energy of heating the outpost is short. */
    static final double GROWTH_LOST_PER_COLD_CELL = 0.3;
    /** Share of radiation one energy of shielding stops. */
    static final double BLOCKED_PER_CELL = 0.15;
    /** Crew health lost per millisievert that gets through. */
    static final double HEALTH_PER_MSV = 4;
    /** Crew health lost for each energy of heating the habitat is short. */
    static final double HEALTH_PER_COLD_CELL = 5;
    /** Crew health lost on a sol with not enough food. */
    static final double HEALTH_WHEN_HUNGRY = 12;
    /** Crew health recovered on a sol that is warm and fed. */
    static final double HEALTH_REST = 1;

    /** Energy the solar panels make in a sol: one for each tenth of full sunlight, at least one. */
    public static int solarOutput(WeatherConditions weather) {
        return Math.max(1, (int) Math.round(weather.sunlight() * 10));
    }

    /**
     * Energy the habitat needs to stay warm tonight: one at -60 C or warmer, one more for
     * each 10 C colder, up to four.
     */
    public static int heatNeed(WeatherConditions weather) {
        int cells = (int) Math.round((-weather.minTempC() - 50) / 10);
        return Math.max(1, Math.min(4, cells));
    }

    /** Most energy worth spending on each use in one sol; more would change nothing. */
    public static PowerAllocation limit(WeatherConditions weather) {
        return new PowerAllocation(MAX_SHIELD, MAX_GREENHOUSE, heatNeed(weather));
    }

    /** What ending the sol now would do. */
    public static SolReport forecast(WeatherConditions weather, RadiationConditions radiation,
                                     PowerAllocation allocation, double health, double food) {
        int heatShort = Math.max(0, heatNeed(weather) - allocation.heating());

        double blocked = Math.min(MAX_SHIELD, allocation.shielding()) * BLOCKED_PER_CELL;
        double doseTaken = radiation.doseMsv() * (1 - blocked);
        double grown = allocation.greenhouse() * FOOD_PER_CELL
                * Math.max(0, 1 - GROWTH_LOST_PER_COLD_CELL * heatShort);
        boolean hungry = food + grown < FOOD_EATEN;
        double foodAfter = Math.max(0, food + grown - FOOD_EATEN);

        double fromRadiation = -doseTaken * HEALTH_PER_MSV;
        double fromCold = -heatShort * HEALTH_PER_COLD_CELL;
        double fromHunger = hungry ? -HEALTH_WHEN_HUNGRY : 0;
        double rest = heatShort == 0 && !hungry ? HEALTH_REST : 0;
        double healthAfter = Math.max(0, Math.min(100, health + fromRadiation + fromCold + fromHunger + rest));
        return new SolReport(blocked, doseTaken, heatShort, grown, FOOD_EATEN, foodAfter, hungry,
                fromRadiation, fromCold, fromHunger, rest, healthAfter);
    }

    private Rules() {}
}
