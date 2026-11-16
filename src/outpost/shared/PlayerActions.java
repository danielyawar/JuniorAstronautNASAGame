package outpost.shared;

/** What the player can do. Panels call these; the controller carries them out. */
public interface PlayerActions {

    /** From the home screen, chooses {@code scenario} and shows its mission-start card. */
    void start(Scenario scenario);

    /** From the home screen, chooses an Earth date at random and shows its mission-start card. */
    void startSurprise();

    /**
     * Starts the clock on the sol that is waiting: closes the mission-start card before sol 1,
     * or the report on the night before any later sol.
     */
    void startSol();

    /** By day, sends the astronaut to {@code place}. They walk there and then do its job. */
    void send(Place place);

    /** By day, flips the heater's switch. It works at once, wherever the astronaut is. */
    void switchHeater(boolean on);

    /** By day, flips the grow lamps' switch. It works at once, wherever the astronaut is. */
    void switchLamps(boolean on);

    /** By day, sends the astronaut to bed. The rest of the sol and the night pass quickly. */
    void sleep();

    /**
     * Stops the clock while {@code paused} is true. The view calls this when it opens or
     * closes a card, and when its lesson on how to play starts or ends, so that time stands
     * still while the player reads. The switches and {@code send} still work meanwhile.
     */
    void pause(boolean paused);

    /** Goes back to the mission-start card of the mission being played. Does nothing on the home screen. */
    void restart();

    /** Leaves the mission, if there is one, and shows the home screen. */
    void home();
}
