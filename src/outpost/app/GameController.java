package outpost.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import outpost.shared.GameState;
import outpost.shared.Phase;
import outpost.shared.PlayerActions;
import outpost.shared.PowerAllocation;
import outpost.shared.PowerUse;
import outpost.shared.RadiationConditions;
import outpost.shared.RadiationSource;
import outpost.shared.Sol;
import outpost.shared.SolReport;
import outpost.shared.WeatherConditions;
import outpost.shared.WeatherSource;

/**
 * Owns the game state and is the only code that changes it. Panels receive each new
 * GameState through a listener and send user actions back through PlayerActions.
 * Call everything on the Swing event thread.
 */
public final class GameController implements PlayerActions {

    private final WeatherSource weather;
    private final RadiationSource radiation;
    private final Sol startSol;
    private final List<Consumer<GameState>> listeners = new ArrayList<>();
    private GameState state;

    public GameController(WeatherSource weather, RadiationSource radiation, Sol startSol) {
        this.weather = weather;
        this.radiation = radiation;
        this.startSol = startSol;
        this.state = newSol(startSol, 0, Rules.START_HEALTH, Rules.START_FOOD);
    }

    public GameState state() {
        return state;
    }

    /** Registers a listener and immediately sends it the current state. */
    public void addListener(Consumer<GameState> listener) {
        listeners.add(listener);
        listener.accept(state);
    }

    @Override
    public void collect() {
        if (state.phase() != Phase.PLAYING || state.collected()) {
            return;
        }
        int energy = Math.min(state.energyCapacity(), state.energy() + state.solarOutput());
        update(with(state.allocation(), energy, true));
    }

    @Override
    public void spend(PowerUse use, int delta) {
        if (state.phase() != Phase.PLAYING || (delta != 1 && delta != -1)) {
            return;
        }
        int spent = state.allocation().get(use);
        boolean allowed = delta > 0
                ? state.energy() > 0 && spent < state.limit().get(use)
                : spent > 0 && state.energy() < state.energyCapacity();
        if (allowed) {
            update(with(state.allocation().plus(use, delta), state.energy() - delta, state.collected()));
        }
    }

    /**
     * Ends the sol. The forecast becomes what happened; energy spent today is used up, and
     * energy left in the battery carries over. Solar energy that was not collected is lost.
     */
    @Override
    public void nextSol() {
        if (state.phase() != Phase.PLAYING) {
            return;
        }
        SolReport report = state.forecast();
        if (report.healthAfter() <= 0) {
            update(ended(Phase.LOST, report));
        } else if (state.sol().number() >= state.missionSols()) {
            update(ended(Phase.WON, report));
        } else {
            update(newSol(state.sol().next(), state.energy(), report.healthAfter(), report.foodAfter()));
        }
    }

    @Override
    public void restart() {
        update(newSol(startSol, 0, Rules.START_HEALTH, Rules.START_FOOD));
    }

    private GameState newSol(Sol sol, int energy, double health, double food) {
        WeatherConditions w = weather.weatherFor(sol);
        RadiationConditions r = radiation.radiationFor(sol);
        return new GameState(sol, w, r, PowerAllocation.NONE, Rules.limit(w), Phase.PLAYING, Rules.MISSION_SOLS,
                energy, Rules.ENERGY_CAPACITY, Rules.solarOutput(w), false, Rules.heatNeed(w),
                health, food, Rules.forecast(w, r, PowerAllocation.NONE, health, food));
    }

    /** The current sol with different spending, battery level and collected flag. */
    private GameState with(PowerAllocation allocation, int energy, boolean collected) {
        GameState s = state;
        return new GameState(s.sol(), s.weather(), s.radiation(), allocation, s.limit(), s.phase(),
                s.missionSols(),
                energy, s.energyCapacity(), s.solarOutput(), collected, s.heatNeed(),
                s.crewHealth(), s.foodDays(),
                Rules.forecast(s.weather(), s.radiation(), allocation, s.crewHealth(), s.foodDays()));
    }

    /** The mission over on the current sol, with the last night's report applied. */
    private GameState ended(Phase phase, SolReport report) {
        GameState s = state;
        return new GameState(s.sol(), s.weather(), s.radiation(), s.allocation(), s.limit(), phase,
                s.missionSols(),
                s.energy(), s.energyCapacity(), s.solarOutput(), s.collected(), s.heatNeed(),
                report.healthAfter(), report.foodAfter(), report);
    }

    private void update(GameState next) {
        state = next;
        for (Consumer<GameState> listener : listeners) {
            listener.accept(next);
        }
    }
}
