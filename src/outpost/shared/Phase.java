package outpost.shared;

/** Where the player is in the game. */
public enum Phase {
    /** The home screen is showing. No mission has started; the player is choosing one. */
    BRIEFING,
    /** A mission is chosen and its mission-start card is showing. The clock waits for START. */
    READY,
    /** The Sun is up, the clock is running and the player is directing the astronaut. */
    DAY,
    /**
     * The astronaut is asleep and the clock is running fast to the next sunrise. Begins at
     * sunset, or earlier if the player presses SLEEP.
     */
    NIGHT,
    /** A new sol has dawned and the report on the night is showing. The clock waits. */
    DAWN,
    /** The astronaut finished the last sol. */
    WON,
    /** The astronaut's health ran out. */
    LOST
}
