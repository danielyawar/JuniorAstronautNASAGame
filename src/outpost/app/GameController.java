package outpost.app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import outpost.shared.GameState;
import outpost.shared.Outpost;
import outpost.shared.Phase;
import outpost.shared.Place;
import outpost.shared.PlayerActions;
import outpost.shared.RadiationConditions;
import outpost.shared.RadiationSource;
import outpost.shared.Scenario;
import outpost.shared.Sol;
import outpost.shared.Tally;
import outpost.shared.WeatherConditions;
import outpost.shared.WeatherSource;

/**
 * Owns the game state and is the only code that changes it. Panels receive each new
 * GameState through a listener and send user actions back through PlayerActions.
 * Call everything on the Swing event thread.
 *
 * <p>The controller has no timer of its own. Whoever owns the clock (Main, a test, the
 * balance simulation) calls {@link #advance} with the real seconds that have passed.
 */
public final class GameController implements PlayerActions {

    private final WeatherSource weatherSource;
    private final RadiationSource radiationSource;
    /** Last Earth date the archives cover. A surprise mission ends on or before it. */
    private final LocalDate archiveEnd;
    /** What the home screen offers, gentlest mission first. */
    private final List<Scenario> scenarios;
    private final Random random = new Random();
    private final List<Consumer<GameState>> listeners = new ArrayList<>();

    // The mutable game state. Everything below is null or false on the home screen.
    private Phase phase = Phase.BRIEFING;
    private Scenario mission;
    private Sol sol;
    private WeatherConditions weather;
    private RadiationConditions radiation;
    private Outpost outpost;
    /** The outpost's tally at the moment the astronaut last went to bed. */
    private Tally atBedtime;
    /** What the last night did. */
    private Tally night;
    private boolean paused;
    /** The snapshot of all of the above that the listeners were last sent. */
    private GameState state;

    /** Opens on the home screen. {@code archiveEnd} is the last Earth date the weather source covers. */
    public GameController(WeatherSource weather, RadiationSource radiation, LocalDate archiveEnd) {
        this.weatherSource = weather;
        this.radiationSource = radiation;
        this.archiveEnd = archiveEnd;
        this.scenarios = Scenarios.all(Rules.MISSION_SOLS, weather, radiation);
        this.state = snapshot();
    }

    public GameState state() {
        return state;
    }

    /** Registers a listener and immediately sends it the current state. */
    public void addListener(Consumer<GameState> listener) {
        listeners.add(listener);
        listener.accept(state);
    }

    /**
     * Moves the game on by {@code seconds} of real time. By day a sol's daylight takes
     * {@link Rules#DAY_SECONDS}; while the astronaut sleeps the clock runs at
     * {@link Rules#NIGHT_HOURS_PER_SECOND}. Does nothing while the clock is paused or a
     * card is waiting for the player. All the change comes from {@link Rules#step}.
     */
    public void advance(double seconds) {
        if (paused || seconds <= 0 || !clockRuns()) {
            return;
        }
        double left = seconds;
        while (left > 1e-9 && clockRuns()) {
            boolean day = phase == Phase.DAY;
            double sunset = Rules.sunsetHour(weather);
            double rate = day ? (sunset - Rules.sunriseHour(weather)) / Rules.DAY_SECONDS
                    : Rules.NIGHT_HOURS_PER_SECOND;
            double end = day ? sunset : Rules.nightEnd(weather);
            double span = Math.max(0, Math.min(left * rate, end - outpost.clock()));
            outpost = Rules.step(outpost, weather, radiation, span);
            left -= span / rate;
            if (outpost.health() <= 0) {
                lose();
            } else if (outpost.clock() >= end - 1e-9) {
                if (day) {
                    goToBed();
                } else {
                    wake();
                }
            }
        }
        publish();
    }

    private boolean clockRuns() {
        return phase == Phase.DAY || phase == Phase.NIGHT;
    }

    @Override
    public void start(Scenario scenario) {
        if (phase == Phase.BRIEFING) {
            ready(scenario);
        }
    }

    /** The date is chosen here, when the player asks, and stays the same if the mission is restarted. */
    @Override
    public void startSurprise() {
        if (phase == Phase.BRIEFING) {
            ready(Scenarios.surprise(random, archiveEnd, Rules.MISSION_SOLS, weatherSource, radiationSource));
        }
    }

    @Override
    public void startSol() {
        if (phase == Phase.READY || phase == Phase.DAWN) {
            phase = Phase.DAY;
            publish();
        }
    }

    @Override
    public void send(Place place) {
        if (phase == Phase.DAY && place != null && place != outpost.place()) {
            outpost = Rules.sent(outpost, place);
            publish();
        }
    }

    @Override
    public void switchHeater(boolean on) {
        if (phase == Phase.DAY && on != outpost.heaterOn()) {
            outpost = Rules.switched(outpost, on, outpost.lampsOn());
            publish();
        }
    }

    @Override
    public void switchLamps(boolean on) {
        if (phase == Phase.DAY && on != outpost.lampsOn()) {
            outpost = Rules.switched(outpost, outpost.heaterOn(), on);
            publish();
        }
    }

    @Override
    public void sleep() {
        if (phase == Phase.DAY) {
            goToBed();
            publish();
        }
    }

    @Override
    public void pause(boolean nowPaused) {
        if (phase != Phase.BRIEFING && nowPaused != paused) {
            paused = nowPaused;
            publish();
        }
    }

    @Override
    public void restart() {
        if (phase != Phase.BRIEFING) {
            ready(mission);
        }
    }

    @Override
    public void home() {
        phase = Phase.BRIEFING;
        mission = null;
        sol = null;
        weather = null;
        radiation = null;
        outpost = null;
        atBedtime = null;
        night = null;
        paused = false;
        publish();
    }

    /** Sets up sol 1 of {@code scenario} and shows its mission-start card. The clock waits for startSol. */
    private void ready(Scenario scenario) {
        mission = scenario;
        dawn(new Sol(1, scenario.start()));
        outpost = Rules.landed(weather);
        atBedtime = null;
        night = null;
        paused = false;
        phase = Phase.READY;
        publish();
    }

    /** Loads the real conditions for {@code next}, which becomes the sol being played. */
    private void dawn(Sol next) {
        sol = next;
        weather = weatherSource.weatherFor(next);
        radiation = radiationSource.radiationFor(next);
    }

    /** Sunset, or the SLEEP button: the astronaut goes in, eats, and the night begins. */
    private void goToBed() {
        atBedtime = outpost.tally();
        outpost = Rules.bedtime(outpost);
        phase = Phase.NIGHT;
        if (outpost.health() <= 0) {
            lose();
        }
    }

    /** Sunrise: the mission is won if that was the last sol; otherwise the next sol waits behind the night's report. */
    private void wake() {
        night = outpost.tally().since(atBedtime);
        if (sol.number() >= Rules.MISSION_SOLS) {
            phase = Phase.WON;
        } else {
            dawn(sol.next());
            outpost = Rules.newSol(outpost, weather);
            phase = Phase.DAWN;
        }
    }

    /** Health has reached 0. If it happened in the night, the report covers the night so far. */
    private void lose() {
        if (phase == Phase.NIGHT) {
            night = outpost.tally().since(atBedtime);
        }
        phase = Phase.LOST;
    }

    private GameState snapshot() {
        if (phase == Phase.BRIEFING) {
            return new GameState(phase, Rules.MISSION_SOLS, null, null, null, null, null, null, null,
                    Rules.ENERGY_CAPACITY, false, scenarios);
        }
        return new GameState(phase, Rules.MISSION_SOLS, mission, sol, weather, radiation, outpost,
                Rules.readings(outpost, weather, radiation), night, Rules.ENERGY_CAPACITY, paused, List.of());
    }

    private void publish() {
        state = snapshot();
        for (Consumer<GameState> listener : listeners) {
            listener.accept(state);
        }
    }
}
