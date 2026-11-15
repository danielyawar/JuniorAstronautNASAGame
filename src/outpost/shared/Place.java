package outpost.shared;

/**
 * The four places the astronaut can be sent. Each has one job, which the astronaut does for
 * as long as they stay there.
 */
public enum Place {
    /** Inside the habitat: rest, so health recovers. The soil bank shields the astronaut here. */
    HABITAT,
    /** Inside the greenhouse: tend the crops, moving what has grown into the food store. */
    GREENHOUSE,
    /** Outside, at the solar panels: brush the dust off them. */
    PANELS,
    /** Outside, with the digger: pile soil onto the bank around the habitat. */
    DIGGER
}
