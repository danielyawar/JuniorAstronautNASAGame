package outpost.shared;

/**
 * Immutable snapshot of everything the panels may display. Only GameController creates these.
 *
 * @param allocation     energy spent on each use so far this sol
 * @param limit          most energy each use can take this sol; more would change nothing
 * @param phase          PLAYING, WON or LOST
 * @param missionSols    sols the crew must last
 * @param energy         energy in the battery, ready to spend
 * @param energyCapacity most energy the battery holds
 * @param solarOutput    energy the solar panels make this sol
 * @param collected      true once the player has collected this sol's solar energy
 * @param heatNeed       energy the habitat needs tonight to stay warm
 * @param crewHealth     0 to 100
 * @param foodDays       days of food in store
 * @param forecast       what happens if the sol ends with the current spending; once the
 *                       mission is over, what happened on its last sol
 */
public record GameState(Sol sol, WeatherConditions weather, RadiationConditions radiation,
                        PowerAllocation allocation, PowerAllocation limit, Phase phase, int missionSols,
                        int energy, int energyCapacity, int solarOutput, boolean collected,
                        int heatNeed, double crewHealth, double foodDays, SolReport forecast) {}
