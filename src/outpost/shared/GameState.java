package outpost.shared;

/** Immutable snapshot of everything the panels may display. Only GameController creates these. */
public record GameState(Sol sol, WeatherConditions weather, RadiationConditions radiation,
                        PowerAllocation allocation) {}
