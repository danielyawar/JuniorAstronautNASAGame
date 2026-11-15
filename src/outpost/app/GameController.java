package outpost.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import outpost.shared.GameState;
import outpost.shared.PowerAllocation;
import outpost.shared.RadiationSource;
import outpost.shared.Sol;
import outpost.shared.WeatherSource;

/**
 * Owns the game state and is the only code that changes it. Panels receive each new
 * GameState through a listener and send user actions back through the public methods.
 * Call everything on the Swing event thread.
 */
public final class GameController {

    private final WeatherSource weather;
    private final RadiationSource radiation;
    private final Sol startSol;
    private final PowerAllocation startAllocation;
    private final List<Consumer<GameState>> listeners = new ArrayList<>();
    private GameState state;

    public GameController(WeatherSource weather, RadiationSource radiation,
                          Sol startSol, PowerAllocation startAllocation) {
        this.weather = weather;
        this.radiation = radiation;
        this.startSol = startSol;
        this.startAllocation = startAllocation;
        this.state = stateFor(startSol, startAllocation);
    }

    public GameState state() {
        return state;
    }

    /** Registers a listener and immediately sends it the current state. */
    public void addListener(Consumer<GameState> listener) {
        listeners.add(listener);
        listener.accept(state);
    }

    /** Advances one sol. The power allocation carries over. */
    public void nextSol() {
        update(stateFor(state.sol().next(), state.allocation()));
    }

    /** Returns to the first sol and the starting allocation. */
    public void restart() {
        update(stateFor(startSol, startAllocation));
    }

    /** Changes the power split for the current sol. */
    public void setAllocation(PowerAllocation allocation) {
        update(new GameState(state.sol(), state.weather(), state.radiation(), allocation));
    }

    private GameState stateFor(Sol sol, PowerAllocation allocation) {
        return new GameState(sol, weather.weatherFor(sol), radiation.radiationFor(sol), allocation);
    }

    private void update(GameState next) {
        state = next;
        for (Consumer<GameState> listener : listeners) {
            listener.accept(next);
        }
    }
}
