package outpost.shared;

import java.util.List;

/**
 * Immutable snapshot of everything the panels may display. Only GameController creates these.
 *
 * <p>While {@code phase} is BRIEFING the home screen is showing and there is no mission:
 * {@code scenarios}, {@code missionSols} and {@code energyCapacity} are set and everything
 * else is null or false. In every other phase all of it is set except {@code night}, which
 * is null until the first night has passed.
 *
 * @param phase          where the player is: see {@link Phase}
 * @param missionSols    sols the astronaut must last
 * @param mission        the mission being played, for the mission-start card
 * @param sol            the sol being played. While the phase is DAWN it is the sol about to start.
 * @param weather        the sol's weather
 * @param radiation      the sol's radiation
 * @param outpost        the battery, the stores, the astronaut and the switches, as they are now
 * @param now            what the instruments read now
 * @param night          what the last night did, from the moment the astronaut went to bed
 *                       to sunrise. Once the mission is over, what its last night did.
 * @param energyCapacity most energy the battery holds
 * @param paused         true while the view has asked for the clock to stand still
 * @param scenarios      the missions the home screen offers, from the gentlest to the hardest,
 *                       so the first is the one to start with; empty once one is chosen
 */
public record GameState(Phase phase, int missionSols, Scenario mission, Sol sol,
                        WeatherConditions weather, RadiationConditions radiation,
                        Outpost outpost, Readings now, Tally night,
                        double energyCapacity, boolean paused, List<Scenario> scenarios) {}
