package outpost.shared;

/**
 * Radiation for one sol, before shielding.
 *
 * @param doseMsv    unshielded dose for the sol, in millisieverts
 * @param solarEvent true when a solar event raised the dose on this sol
 * @param detail     one plain sentence about the solar event for display, or null if there
 *                   is none
 */
public record RadiationConditions(double doseMsv, boolean solarEvent, String detail) {

    /** Radiation with no event description. */
    public RadiationConditions(double doseMsv, boolean solarEvent) {
        this(doseMsv, solarEvent, null);
    }
}
