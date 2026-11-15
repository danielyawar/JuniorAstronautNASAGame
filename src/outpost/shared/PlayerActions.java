package outpost.shared;

/** What the player can do. Panels call these; the controller carries them out. */
public interface PlayerActions {

    /** Moves the energy the solar panels made today into the battery. Once per sol. */
    void collect();

    /** Spends one energy from the battery on {@code use} (delta +1), or takes one back (delta -1). */
    void spend(PowerUse use, int delta);

    /** Ends the sol: the crew eats, the night passes, and a new sol begins. */
    void nextSol();

    /** Starts the mission again from the first sol. */
    void restart();
}
