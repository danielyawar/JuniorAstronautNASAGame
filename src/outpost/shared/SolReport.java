package outpost.shared;

/**
 * What one sol does to the outpost under a given spending of energy. The same record is used for
 * the forecast of the current sol and for the report on the sol just ended.
 *
 * @param shieldBlocked   share of the sol's radiation the shield stops, 0.0 to 1.0
 * @param doseTakenMsv    radiation the crew takes after shielding, in millisieverts
 * @param heatShort       energy the habitat is short of for heating; 0 when warm
 * @param foodGrown       days of food the greenhouse grows
 * @param foodEaten       days of food the crew needs this sol
 * @param foodAfter       days of food in store once the crew has eaten
 * @param hungry          true when there was not enough food for the crew
 * @param healthRadiation change in crew health from radiation; 0 or less
 * @param healthCold      change in crew health from a cold habitat; 0 or less
 * @param healthHunger    change in crew health from hunger; 0 or less
 * @param healthRest      health the crew recovers on a warm, fed sol; 0 or more
 * @param healthAfter     crew health at the end of the sol, 0 to 100
 */
public record SolReport(double shieldBlocked, double doseTakenMsv, int heatShort,
                        double foodGrown, double foodEaten, double foodAfter, boolean hungry,
                        double healthRadiation, double healthCold, double healthHunger,
                        double healthRest, double healthAfter) {

    /** Total change in crew health over the sol, before the 0 to 100 limits. */
    public double healthChange() {
        return healthRadiation + healthCold + healthHunger + healthRest;
    }
}
