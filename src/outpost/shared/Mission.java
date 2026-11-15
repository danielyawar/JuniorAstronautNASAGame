package outpost.shared;

/**
 * The game side of the state: how the mission is going and what today asks of the player.
 * Power figures are in percent of full-sun power, like {@link PowerAllocation}; one power
 * cell on screen is {@value #CELL} percent.
 *
 * @param lengthSols      sols the crew must last
 * @param crewHealth      0 to 100
 * @param foodDays        days of food in store
 * @param powerBudget     power the panels make today, in percent; the allocation cannot
 *                        total more than this
 * @param heatNeed        heating power the habitat needs tonight, in percent
 * @param powerTrimmed    true when today's budget was too small for yesterday's split and
 *                        the game cut it down
 * @param forecast        what happens if the sol ends with the current split
 * @param lastReport      what happened on the sol just ended, or null on the first sol
 * @param lostToRadiation crew health lost to radiation so far this mission
 * @param lostToCold      crew health lost to cold so far this mission
 * @param lostToHunger    crew health lost to hunger so far this mission
 * @param stormsFaced     solar storm sols ended so far this mission
 */
public record Mission(Phase phase, int lengthSols, double crewHealth, double foodDays,
                      int powerBudget, int heatNeed, boolean powerTrimmed,
                      SolReport forecast, SolReport lastReport,
                      double lostToRadiation, double lostToCold, double lostToHunger,
                      int stormsFaced) {

    /** Percent of full-sun power in one power cell. */
    public static final int CELL = 10;
}
