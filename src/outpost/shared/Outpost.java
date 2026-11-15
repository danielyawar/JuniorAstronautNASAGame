package outpost.shared;

/**
 * Everything about the outpost that changes as time passes. The rules turn one of these
 * into the next; nothing else changes it.
 *
 * @param clock       Mars local time in hours since midnight at the start of this sol. It
 *                    runs on past 24 during the night, until the next sunrise.
 * @param energy      energy in the battery
 * @param panelsClean how clean the solar panels are, 0.0 (covered in dust) to 1.0
 * @param shield      height of the soil bank around the habitat, 0.0 (none) to 1.0 (finished)
 * @param cropsReady  days of food grown in the greenhouse and waiting to be picked
 * @param foodDays    days of food in the store
 * @param health      the astronaut's health, 0 to 100
 * @param heaterOn    true while the heater's switch is on, whether or not it has energy
 * @param lampsOn     true while the grow lamps' switch is on, whether or not they have energy
 * @param place       where the astronaut is, or is walking to
 * @param walkLeft    Mars hours of walking left before the astronaut gets there; 0 once there
 * @param tally       running totals since the mission began
 */
public record Outpost(double clock, double energy, double panelsClean, double shield, double cropsReady,
                      double foodDays, double health, boolean heaterOn, boolean lampsOn,
                      Place place, double walkLeft, Tally tally) {

    /** True once the astronaut has reached {@code where} and is doing its job. */
    public boolean at(Place where) {
        return place == where && walkLeft <= 0;
    }

    /** True while the astronaut is still on the way to {@code place}. */
    public boolean walking() {
        return walkLeft > 0;
    }
}
