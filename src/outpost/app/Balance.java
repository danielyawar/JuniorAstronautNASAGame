package outpost.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import outpost.shared.GameState;
import outpost.shared.Outpost;
import outpost.shared.Phase;
import outpost.shared.Place;
import outpost.shared.PlayerActions;
import outpost.shared.RadiationConditions;
import outpost.shared.RadiationSource;
import outpost.shared.Readings;
import outpost.shared.Scenario;
import outpost.shared.Sol;
import outpost.shared.Tally;
import outpost.shared.WeatherConditions;
import outpost.shared.WeatherSource;

/**
 * The balance simulation: plays whole missions with scripted players, with no window and
 * no timer, and prints how each one ends. Use it to check the numbers in Rules whenever
 * one changes. Run it with
 *
 * <pre>    java -jar SpaceGuy.jar balance</pre>
 *
 * <p>The scripted players press the same PlayerActions a person does and time moves only
 * through GameController.advance, so what is measured here is the game itself.
 */
final class Balance {

    /** Real seconds between a scripted player's decisions: 240 looks at the outpost each day. */
    private static final double DECISION_SECONDS = 0.25;
    /** Start dates in the random sample. The seed is fixed so the sample is the same every run. */
    private static final int SAMPLE = 300;
    private static final long SAMPLE_SEED = 20_261_115L;

    /** A way of playing. Called through each day; looks at the state and presses the controls. */
    private interface Policy {
        void play(GameState state, PlayerActions actions);
    }

    /** How a mission ended. {@code lowestDawn} is the least energy left in the battery at any sunrise. */
    private record Result(Phase phase, int sol, Outpost end, double lowestDawn, List<String> log) {

        boolean won() {
            return phase == Phase.WON;
        }

        String line() {
            Tally t = end.tally();
            return String.format(Locale.ENGLISH,
                    "%-4s sol %2d  health %5.1f  (radiation -%4.1f  cold -%5.1f  hunger -%4.1f  rest +%4.1f)"
                    + "  food %4.1f  shield %3.0f%%  soil pH %3.1f  lowest dawn energy %3.0f",
                    won() ? "WON" : "LOST", sol, end.health(), t.radiation(), t.cold(), t.hunger(), t.rest(),
                    end.foodDays(), end.shield() * 100, outpost.shared.Soil.ph(end.marsSoil()), lowestDawn);
        }
    }

    static void run(WeatherSource weather, RadiationSource radiation, LocalDate archiveEnd, List<String> options) {
        Map<String, Policy> policies = new LinkedHashMap<>();
        policies.put("Sensible play", Balance::sensible);
        policies.put("Sensible, but looks only every 6 seconds", slow(Balance::sensible, 6));
        policies.put("Does nothing", (state, actions) -> { });
        policies.put("Everything on, astronaut rests", Balance::everythingOn);
        policies.put("Sensible jobs, never switches off", Balance::neverSwitchesOff);

        System.out.println("Sunlight strength from distance (Rules.sunStrength), against the NASA Mars Fact Sheet:");
        System.out.printf(Locale.ENGLISH, "  nearest the Sun  (Ls 251): %.4f   fact sheet (227.956 / 206.650)^2 = %.4f%n",
                Rules.sunStrength(251), Math.pow(227.956 / 206.650, 2));
        System.out.printf(Locale.ENGLISH, "  farthest         (Ls  71): %.4f   fact sheet (227.956 / 249.261)^2 = %.4f%n",
                Rules.sunStrength(71), Math.pow(227.956 / 249.261, 2));

        GameController controller = new GameController(weather, radiation, archiveEnd);
        List<Scenario> scenarios = controller.state().scenarios();

        System.out.println();
        System.out.println("One step function, any step size (Sensible play, first scenario):");
        for (double seconds : new double[] {0.05, 0.25, 1.0}) {
            Result result = play(controller, scenarios.get(0), Balance::sensible, seconds, false);
            System.out.printf(Locale.ENGLISH, "  decisions every %.2f s: %s%n", seconds, result.line());
        }

        System.out.println();
        System.out.println("Each night's low, the energy the night takes with only the heater on, and the health"
                + " it costs with the heater off:");
        for (Scenario scenario : scenarios) {
            StringBuilder line = new StringBuilder(String.format(Locale.ENGLISH, "  %-22s", scenario.title()));
            Sol sol = new Sol(1, scenario.start());
            for (int i = 0; i < Rules.MISSION_SOLS; i++, sol = sol.next()) {
                WeatherConditions w = weather.weatherFor(sol);
                Outpost atSunset = Rules.switched(Rules.landed(w), true, false);
                Outpost unheated = new Outpost(Rules.sunsetHour(w), 0, 1, 0, 0, 0, 1000, false, false,
                        Place.HABITAT, 0, Tally.NONE, 0);
                double cold = Rules.step(unheated, w, new RadiationConditions(0, false),
                        Rules.nightEnd(w) - Rules.sunsetHour(w)).tally().cold();
                line.append(String.format(Locale.ENGLISH, "  %.0fC %.0f -%.0f", w.minTempC(),
                        Rules.nightNeed(atSunset, w), cold));
            }
            System.out.println(line);
        }

        for (Map.Entry<String, Policy> policy : policies.entrySet()) {
            System.out.println();
            System.out.println(policy.getKey());
            List<Scenario> gentlestFirst = new ArrayList<>(scenarios);
            Map<Scenario, Double> healthLeft = new LinkedHashMap<>();
            for (Scenario scenario : scenarios) {
                Result result = play(controller, scenario, policy.getValue(), DECISION_SECONDS,
                        options.contains("sols"));
                System.out.printf(Locale.ENGLISH, "  %-22s %s%n", scenario.title(), result.line());
                result.log().forEach(System.out::println);
                // A lost mission counts as harder than any won one, and harder the sooner it was lost.
                healthLeft.put(scenario, result.won() ? result.end().health() : result.sol() - 1000.0);
            }
            if (policy.getValue() == policies.get("Sensible play")) {
                // The home screen lists the missions in the order Scenarios writes them down,
                // which should be this one. The sort keeps that order between equals.
                gentlestFirst.sort((a, b) -> Double.compare(healthLeft.get(b), healthLeft.get(a)));
                StringBuilder order = new StringBuilder("  Gentlest to hardest by health left:");
                for (Scenario scenario : gentlestFirst) {
                    order.append(scenario == gentlestFirst.get(0) ? " " : ", ").append(scenario.title());
                }
                System.out.println(order);
                System.out.println(gentlestFirst.equals(scenarios)
                        ? "  The home screen shows them in this order."
                        : "  THE HOME SCREEN SHOWS A DIFFERENT ORDER. Change the order of Scenarios.LIST to match.");
            }
        }

        // The same range of dates "Surprise me" draws from.
        LocalDate latest = archiveEnd.minusDays(Rules.MISSION_SOLS - 1L);
        int choices = (int) ChronoUnit.DAYS.between(Scenarios.FIRST_READINGS, latest) + 1;
        Random random = new Random(SAMPLE_SEED);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < SAMPLE; i++) {
            dates.add(Scenarios.FIRST_READINGS.plusDays(random.nextInt(choices)));
        }
        System.out.println();
        System.out.println(SAMPLE + " random start dates, " + Scenarios.FIRST_READINGS + " to " + latest + ":");
        for (Map.Entry<String, Policy> policy : policies.entrySet()) {
            int wins = 0;
            double healthSum = 0;
            double lowest = Double.POSITIVE_INFINITY;
            List<String> losses = new ArrayList<>();
            Map<Double, String> hardest = new TreeMap<>();
            for (LocalDate date : dates) {
                Result result = play(controller, Scenarios.onDate(date, Rules.MISSION_SOLS, weather, radiation),
                        policy.getValue(), DECISION_SECONDS, false);
                if (result.won()) {
                    wins++;
                    healthSum += result.end().health();
                    lowest = Math.min(lowest, result.end().health());
                    hardest.put(result.end().health(), "    " + date + "  " + result.line());
                } else {
                    losses.add("    " + date + "  " + result.line());
                }
            }
            System.out.printf(Locale.ENGLISH, "  %-34s wins %3d of %d", policy.getKey(), wins, SAMPLE);
            if (wins > 0) {
                System.out.printf(Locale.ENGLISH, "   health when winning: average %.0f, lowest %.0f",
                        healthSum / wins, lowest);
            }
            System.out.println();
            if (policy.getValue() == policies.get("Sensible play")) {
                System.out.println(losses.isEmpty() ? "    No start date in the sample loses." : "    Dates that lose:");
                losses.forEach(System.out::println);
                System.out.println("    The five dates it wins with least health:");
                hardest.values().stream().limit(5).forEach(System.out::println);
            }
        }
    }

    /** Plays one mission from its mission-start card to its end. */
    private static Result play(GameController controller, Scenario scenario, Policy policy, double seconds,
                               boolean logSols) {
        controller.home();
        controller.start(scenario);
        double lowestDawn = Double.POSITIVE_INFINITY;
        double sunsetEnergy = 0;
        List<String> log = new ArrayList<>();
        while (true) {
            GameState state = controller.state();
            switch (state.phase()) {
                case READY:
                    controller.startSol();
                    break;
                case DAWN:
                    lowestDawn = Math.min(lowestDawn, state.outpost().energy());
                    if (logSols) {
                        log.add(solLine(state.sol().number() - 1, state, sunsetEnergy));
                    }
                    controller.startSol();
                    break;
                case DAY:
                    policy.play(state, controller);
                    controller.advance(seconds);
                    if (controller.state().phase() == Phase.NIGHT) {
                        sunsetEnergy = controller.state().outpost().energy();
                    }
                    break;
                case NIGHT:
                    controller.advance(seconds);
                    break;
                default:
                    if (logSols) {
                        log.add(solLine(state.sol().number(), state, sunsetEnergy));
                    }
                    return new Result(state.phase(), state.sol().number(), state.outpost(),
                            Math.min(lowestDawn, state.outpost().energy()), log);
            }
        }
    }

    /** One line on a sol that has just ended, for the "sols" option. */
    private static String solLine(int sol, GameState state, double sunsetEnergy) {
        Outpost o = state.outpost();
        Tally night = state.night() == null ? Tally.NONE : state.night();
        return String.format(Locale.ENGLISH,
                "      sol %2d  sunset energy %3.0f  dawn energy %3.0f  health %5.1f  food %4.1f  crops %3.1f"
                + "  clean %3.0f%%  shield %3.0f%%  night: radiation -%.1f cold -%.1f hunger -%.1f rest +%.1f",
                sol, sunsetEnergy, o.energy(), o.health(), o.foodDays(), o.cropsReady(), o.panelsClean() * 100,
                o.shield() * 100, night.radiation(), night.cold(), night.hunger(), night.rest());
    }

    // ---- The scripted players -------------------------------------------------------

    /**
     * What a careful child might do. The heater stays on. The lamps are on while the Sun
     * is making more than everything uses, or the battery is nearly full, and go off for
     * the night unless the battery can carry them. The astronaut brushes dusty panels,
     * picks food when there is a day's worth, digs while the battery is healthy, and
     * otherwise rests inside. On a solar storm sol they stay in except for short jobs.
     */
    private static void sensible(GameState state, PlayerActions actions) {
        Outpost o = state.outpost();
        Readings now = state.now();
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        boolean evening = now.dayShare() > 0.8;
        actions.switchHeater(true);
        actions.switchLamps(lampsWanted(state, evening));

        boolean storm = radiation.solarEvent();
        Place go;
        if (o.place() == Place.PANELS && o.panelsClean() < 0.995) {
            go = Place.PANELS; // finish the job
        } else if (o.place() == Place.GREENHOUSE && o.cropsReady() > 0.02) {
            go = Place.GREENHOUSE;
        } else if (o.panelsClean() < (storm ? 0.6 : 0.85)) {
            go = Place.PANELS;
        } else if (o.cropsReady() >= 1 || (evening && o.cropsReady() > 0.05 && o.foodDays() < 1)) {
            go = Place.GREENHOUSE;
        } else if (!storm && !evening && o.shield() < 1
                && o.energy() >= (o.place() == Place.DIGGER ? 35 : 55)
                && o.energy() >= Rules.nightNeed(Rules.switched(o, true, false), weather) * now.dayShare()) {
            go = Place.DIGGER;
        } else {
            go = Place.HABITAT;
        }
        actions.send(go);
    }

    /** Whether the careful player has the grow lamps on at this moment. */
    private static boolean lampsWanted(GameState state, boolean evening) {
        Outpost o = state.outpost();
        Readings now = state.now();
        int nightsLeft = state.missionSols() - state.sol().number() + 1;
        if (o.foodDays() + o.cropsReady() >= nightsLeft + 0.2 || o.cropsReady() >= Rules.CROPS_MAX - 0.01) {
            return false; // enough food to finish, or the plants are full
        }
        if (evening) {
            return o.energy() >= Rules.nightNeed(Rules.switched(o, true, true), state.weather()) + 5;
        }
        double otherDraws = now.energyOut() - (o.lampsOn() ? Rules.LAMP_DRAW : 0);
        return o.energy() >= 0.9 * state.energyCapacity()
                || (o.energy() >= 30 && now.energyIn() > otherDraws + Rules.LAMP_DRAW);
    }

    /**
     * {@code policy} played by someone who looks at the outpost only once every
     * {@code seconds} real seconds of each day, and so is late with everything.
     */
    private static Policy slow(Policy policy, double seconds) {
        double[] next = {0};
        return (state, actions) -> {
            double now = state.now().dayShare() * Rules.DAY_SECONDS;
            if (now < next[0] - seconds || now >= next[0]) {
                next[0] = now + seconds;
                policy.play(state, actions);
            }
        };
    }

    /** Both switches on, astronaut resting in the habitat, nothing else ever. */
    private static void everythingOn(GameState state, PlayerActions actions) {
        actions.switchHeater(true);
        actions.switchLamps(true);
        actions.send(Place.HABITAT);
    }

    /** The careful player's jobs, but with the heater and the lamps left on day and night. */
    private static void neverSwitchesOff(GameState state, PlayerActions actions) {
        sensible(state, actions);
        actions.switchLamps(true);
    }

    private Balance() {}
}
