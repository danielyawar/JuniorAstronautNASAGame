package outpost.shared;

/** Where the player is in a mission. */
public enum Phase {
    /** The mission has been set up and the briefing is showing. Nothing can be changed yet. */
    BRIEFING,
    /** The player is sharing out power and ending sols. */
    PLAYING,
    /** The crew reached the last sol. */
    WON,
    /** Crew health ran out. */
    LOST
}
