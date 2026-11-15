package outpost.shared;

/**
 * What the instruments read at this moment. Worked out by the rules from the outpost, the
 * sol's weather and radiation and the clock, so that the view never has to do sums of its
 * own.
 *
 * @param sun           height of the Sun, 0.0 (night, sunrise, sunset) to 1.0 (midday)
 * @param dayShare      how far the day has run from sunrise (0.0) to sunset (1.0); 1.0 all night
 * @param nightShare    how far the night has run from sunset (0.0) to the next sunrise (1.0);
 *                      0.0 all day
 * @param outsideC      temperature outside now, in degrees Celsius
 * @param sunStrength   how strong sunlight is at Mars's distance from the Sun today, as a
 *                      share of its strength at Mars's average distance (about 0.84 to 1.22)
 * @param energyIn      energy the solar panels are making now, per Mars hour
 * @param energyOut     energy everything that is running asks for now, per Mars hour
 * @param nightNeed     energy the coming night will take with the switches as they are now;
 *                      during the night, what the rest of it will take
 * @param heaterWorking true when the heater is on and has energy
 * @param lampsWorking  true when the grow lamps are on and have energy
 * @param diggerWorking true when the astronaut is digging and the digger has energy
 * @param shieldBlocks  share of the radiation the soil bank stops while the astronaut is in
 *                      the habitat, 0.0 to 1.0
 * @param harm          why the astronaut's health is falling now, or NONE if it is not
 */
public record Readings(double sun, double dayShare, double nightShare, double outsideC, double sunStrength,
                       double energyIn, double energyOut, double nightNeed, boolean heaterWorking,
                       boolean lampsWorking, boolean diggerWorking, double shieldBlocks, Harm harm) {}
