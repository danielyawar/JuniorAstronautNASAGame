package outpost.app;

import java.time.LocalTime;
import outpost.shared.Harm;
import outpost.shared.Outpost;
import outpost.shared.Place;
import outpost.shared.RadiationConditions;
import outpost.shared.Readings;
import outpost.shared.Soil;
import outpost.shared.Tally;
import outpost.shared.WeatherConditions;

/**
 * The game rules: how the outpost changes as Mars time passes. Pure functions;
 * GameController holds the state.
 *
 * <p>{@link #step} is the one place where time moves. The live clock, the night and the
 * balance simulation (Balance) all call it, so they cannot disagree.
 *
 * <p>Every number in this class is a GAME RULE chosen so that the mission is playable,
 * unless its comment says it is real. The inputs the rules work on (temperatures, sunrise
 * and sunset, sunlight level, solar longitude, radiation dose, storm dates) are the real
 * data. Three curves are MODELS drawn between real numbers: the Sun's height between the
 * real sunrise and sunset ({@link #sunHeight}), the temperature between the real low and
 * high ({@link #outsideC}), and the even spread of the sol's radiation dose through the sol.
 */
public final class Rules {

    // ---- The mission ----------------------------------------------------------------

    /** Sols the astronaut must last. */
    public static final int MISSION_SOLS = 12;
    public static final double START_HEALTH = 100;
    /** Days of food in the store when the mission starts. */
    public static final double START_FOOD = 4;
    /** Energy in the battery when the mission starts. */
    public static final double START_ENERGY = 50;
    /** Most energy the battery holds. */
    public static final double ENERGY_CAPACITY = 100;

    // ---- The clock ------------------------------------------------------------------

    /** Real seconds a day takes, from sunrise to sunset. */
    public static final double DAY_SECONDS = 60;
    /** Mars hours that pass in each real second while the astronaut sleeps. */
    public static final double NIGHT_HOURS_PER_SECOND = 3;
    /** A sol is divided into 24 Mars hours, as the rover's sunrise and sunset times are. */
    static final double HOURS_PER_SOL = 24;
    /** {@link #step} never moves time by more than this at once, however long the span asked for. */
    static final double LONGEST_TICK = 0.05;
    /** Used only if the weather has no sunrise or sunset time. */
    static final double USUAL_SUNRISE = 6;
    static final double USUAL_SUNSET = 18;

    // ---- Energy in ------------------------------------------------------------------

    /** Energy per Mars hour from clean panels at midday, under a clear sky, at Mars's average distance. */
    static final double SOLAR_PEAK = 29;
    /**
     * REAL: how stretched Mars's path round the Sun is (its eccentricity), and the solar
     * longitude at which Mars is nearest the Sun. NASA Mars Fact Sheet.
     */
    static final double ORBIT_ECCENTRICITY = 0.0934;
    static final double LS_NEAREST_SUN = 251;
    /** Share of the panels that dust covers in a sol under a clear sky. */
    static final double DUST_PER_SOL = 0.05;
    /** Extra share that dust covers in a sol when the sky lets no sunlight through; less when it lets some. */
    static final double DUST_EXTRA_PER_SOL = 0.25;
    /** Share of the panels the astronaut brushes clean in a Mars hour. */
    static final double BRUSH_PER_HOUR = 0.3;

    // ---- Energy out -----------------------------------------------------------------

    /** Energy per Mars hour that life support always draws. */
    static final double LIFE_SUPPORT = 0.8;
    /** Temperature the heater holds inside the habitat, in degrees Celsius. */
    static final double ROOM_C = 20;
    /** Energy per Mars hour the heater draws for each degree the outside is colder than the room. */
    static final double HEATER_PER_DEGREE = 0.03;
    /** Energy per Mars hour the grow lamps draw. */
    static final double LAMP_DRAW = 5;
    /** Energy per Mars hour the digger draws while the astronaut is using it. */
    static final double DIGGER_DRAW = 5;

    // ---- The astronaut --------------------------------------------------------------

    /** Mars hours it takes the astronaut to walk to any place. */
    public static final double WALK_HOURS = 0.5;
    /** Share of the finished soil bank the digger piles up in a Mars hour. */
    static final double DIG_PER_HOUR = 0.04;
    /** Share of the radiation a finished soil bank stops. */
    static final double SHIELD_BLOCKS = 0.8;
    /** Days of food the crops grow in a Mars hour under the lamps. */
    static final double GROW_PER_HOUR = 0.11;
    /** Most food that can wait on the plants, in days. They stop growing until it is picked. */
    public static final double CROPS_MAX = 2;
    /** Days of food the astronaut picks in a Mars hour. */
    static final double PICK_PER_HOUR = 2;
    /** Days of food the astronaut eats each night. */
    static final double FOOD_PER_NIGHT = 1;
    /** Share of the Mars soil a day of food grown uses up Earth soil for: Mars soil takes its place. */
    static final double SOIL_PER_FOOD_DAY = 0.10;
    /** Share of the garden soil that returns to Earth-like soil for each day of food picked (plant clippings as compost). */
    static final double COMPOST_PER_FOOD_DAY = 0.03;
    /** Share of the garden soil that Mars soil creeps into in a Mars hour, even with nothing growing: the pH slowly climbs. */
    static final double SOIL_DRIFT_PER_HOUR = 0.010;
    /** Share of the garden soil the astronaut swaps back to fresh Earth soil in a Mars hour spent in the greenhouse. */
    static final double SOIL_ADDED_PER_HOUR = 0.12;
    /** Share of the garden soil that is Mars soil when the mission starts: all Earth soil. */
    public static final double START_MARS_SOIL = 0;

    // ---- Health ---------------------------------------------------------------------

    /** Health lost per millisievert of radiation taken. */
    static final double HEALTH_PER_MSV = 5;
    /** Health lost per Mars hour without heating, for each degree the outside is colder than the room. */
    static final double COLD_PER_DEGREE_HOUR = 0.022;
    /** Health lost on a night with nothing to eat. */
    static final double HUNGER_HEALTH = 15;
    /** Health recovered per Mars hour of rest in a warm habitat. */
    static final double REST_PER_HOUR = 0.04;

    // ---- The sky --------------------------------------------------------------------

    /** Mars local time of sunrise, in hours. REAL where the weather has it. */
    public static double sunriseHour(WeatherConditions weather) {
        return hours(weather.sunrise(), USUAL_SUNRISE);
    }

    /** Mars local time of sunset, in hours. REAL where the weather has it. */
    public static double sunsetHour(WeatherConditions weather) {
        double sunset = hours(weather.sunset(), USUAL_SUNSET);
        return sunset > sunriseHour(weather) ? sunset : USUAL_SUNSET;
    }

    /**
     * The clock time at which the night ends: this sol's sunrise time, a whole sol later.
     * The next sol's own sunrise is at most a minute different.
     */
    public static double nightEnd(WeatherConditions weather) {
        return sunriseHour(weather) + HOURS_PER_SOL;
    }

    private static double hours(LocalTime time, double otherwise) {
        return time == null ? otherwise : time.toSecondOfDay() / 3600.0;
    }

    /**
     * MODEL of the Sun's height: zero at the real sunrise and sunset and all night, 1.0
     * halfway between them, along half a sine wave.
     */
    public static double sunHeight(WeatherConditions weather, double clock) {
        double sunrise = sunriseHour(weather);
        double sunset = sunsetHour(weather);
        if (clock <= sunrise || clock >= sunset) {
            return 0;
        }
        return Math.sin(Math.PI * (clock - sunrise) / (sunset - sunrise));
    }

    /** Share of the daylight hours gone by when the air is warmest. */
    static final double WARMEST_AT = 0.7;

    /**
     * MODEL of the temperature outside. It is at the sol's real low at sunrise, climbs to
     * the real high in the afternoon, and falls back to the low by the next sunrise. The
     * two ends are measured; the smooth curve between them is not.
     */
    public static double outsideC(WeatherConditions weather, double clock) {
        double sunrise = sunriseHour(weather);
        double warmest = sunrise + (sunsetHour(weather) - sunrise) * WARMEST_AT;
        double t = clock < sunrise ? clock + HOURS_PER_SOL : clock;
        double share = t <= warmest
                ? (1 - Math.cos(Math.PI * (t - sunrise) / (warmest - sunrise))) / 2
                : (1 + Math.cos(Math.PI * Math.min(1, (t - warmest) / (sunrise + HOURS_PER_SOL - warmest)))) / 2;
        return weather.minTempC() + (weather.maxTempC() - weather.minTempC()) * share;
    }

    /**
     * REAL PHYSICS: how strong sunlight is at Mars's distance from the Sun, as a share of
     * its strength at Mars's average distance. Light spreads out with the square of the
     * distance, and Mars's distance follows from where it is on its path ({@code ls}, the
     * solar longitude in degrees). The answer runs from about 0.84 at the farthest point
     * to about 1.22 at the nearest. With {@code ls} unknown the answer is 1.
     *
     * <p>Checked against the distances in the NASA Mars Fact Sheet: average 227.956,
     * nearest 206.650 and farthest 249.261 million km give (227.956 / 206.650)^2 = 1.217
     * and (227.956 / 249.261)^2 = 0.836. Balance prints the same check.
     */
    public static double sunStrength(double ls) {
        if (Double.isNaN(ls)) {
            return 1;
        }
        double e = ORBIT_ECCENTRICITY;
        double nearness = (1 + e * Math.cos(Math.toRadians(ls - LS_NEAREST_SUN))) / (1 - e * e);
        return nearness * nearness;
    }

    /** Energy per Mars hour the panels make: Sun's height x sky x distance x clean panels. */
    static double solarRate(double panelsClean, WeatherConditions weather, double clock) {
        return SOLAR_PEAK * sunHeight(weather, clock) * clamp(weather.sunlight(), 0, 1)
                * sunStrength(weather.ls()) * panelsClean;
    }

    /** Energy per Mars hour the heater draws when it is {@code outside} degrees Celsius out. */
    static double heaterDraw(double outside) {
        return HEATER_PER_DEGREE * Math.max(0, ROOM_C - outside);
    }

    /** Share of the panels that dust covers in a Mars hour: more under a dustier sky. */
    static double dustRate(WeatherConditions weather) {
        return (DUST_PER_SOL + DUST_EXTRA_PER_SOL * (1 - clamp(weather.sunlight(), 0, 1))) / HOURS_PER_SOL;
    }

    /** Share of the radiation a soil bank of this height stops while the astronaut is in the habitat. */
    static double shieldBlocks(double shield) {
        return SHIELD_BLOCKS * clamp(shield, 0, 1);
    }

    /** Health per Mars hour that this sol's radiation takes from an astronaut with nothing to stop it. */
    static double radiationHarm(RadiationConditions radiation) {
        return HEALTH_PER_MSV * radiation.doseMsv() / HOURS_PER_SOL;
    }

    /** Health per Mars hour that the cold takes with no heating, when it is {@code outside} degrees Celsius out. */
    static double coldHarm(double outside) {
        return COLD_PER_DEGREE_HOUR * Math.max(0, ROOM_C - outside);
    }

    // ---- Time -----------------------------------------------------------------------

    /**
     * The outpost after {@code hours} more Mars hours of this sol's weather and radiation.
     * This is the only place where time moves. It does not know about real time, timers or
     * phases: the caller decides how far to step and what happens at sunset and sunrise.
     * Stops early if the astronaut's health reaches 0.
     */
    public static Outpost step(Outpost outpost, WeatherConditions weather, RadiationConditions radiation,
                               double hours) {
        Outpost now = outpost;
        double left = hours;
        while (left > 1e-12 && now.health() > 0) {
            double tick = Math.min(left, LONGEST_TICK);
            now = tick(now, weather, radiation, tick);
            left -= tick;
        }
        return now;
    }

    /** One short stretch of time, short enough that nothing changes much during it. */
    private static Outpost tick(Outpost o, WeatherConditions weather, RadiationConditions radiation, double dt) {
        double middle = o.clock() + dt / 2;
        double outside = outsideC(weather, middle);
        double walked = Math.min(o.walkLeft(), dt);
        double there = dt - walked; // hours at the place, doing its job
        Place place = o.place();

        // Energy. Everything that is running shares what there is. With none, nothing runs.
        boolean digging = place == Place.DIGGER && o.shield() < 1;
        double made = solarRate(o.panelsClean(), weather, middle) * dt;
        double asked = LIFE_SUPPORT * dt
                + (o.heaterOn() ? heaterDraw(outside) * dt : 0)
                + (o.lampsOn() ? LAMP_DRAW * dt : 0)
                + (digging ? DIGGER_DRAW * there : 0);
        double have = o.energy() + made;
        double running = asked <= have ? 1 : have / asked; // share of this tick the machines ran
        double energy = Math.min(ENERGY_CAPACITY, have - asked * running);

        // Jobs.
        double clean = clamp(o.panelsClean() - dustRate(weather) * dt
                + (place == Place.PANELS ? BRUSH_PER_HOUR * there : 0), 0, 1);
        double shield = Math.min(1, o.shield() + (digging ? DIG_PER_HOUR * there * running : 0));
        double crops = Math.min(CROPS_MAX, o.cropsReady()
                + (o.lampsOn() ? GROW_PER_HOUR * dt * running * Soil.growth(o.marsSoil()) : 0));
        double grown = crops - o.cropsReady();
        double picked = place == Place.GREENHOUSE ? Math.min(crops, PICK_PER_HOUR * there) : 0;
        // The pH slowly climbs as Mars soil creeps in, and faster as growing food uses up Earth soil.
        // Picking returns clippings as compost. Time spent in the greenhouse adds fresh Earth soil,
        // which neutralises the soil and can bring the pH all the way back to where it started.
        double addedSoil = place == Place.GREENHOUSE ? SOIL_ADDED_PER_HOUR * there : 0;
        double marsSoil = Soil.clamp(o.marsSoil() + SOIL_PER_FOOD_DAY * grown + SOIL_DRIFT_PER_HOUR * dt
                - COMPOST_PER_FOOD_DAY * picked - addedSoil);

        // Health. Only the habitat has the soil bank round it, so only there is any radiation stopped.
        double sheltered = place == Place.HABITAT ? there : 0;
        double warm = o.heaterOn() ? running : 0; // share of this tick the heater ran
        double fromRadiation = radiationHarm(radiation) * (dt - sheltered * shieldBlocks(o.shield()));
        double fromCold = coldHarm(outside) * (1 - warm) * dt;
        double health = Math.max(0, o.health() - fromRadiation - fromCold);
        double fromRest = health <= 0 ? 0
                : Math.min(START_HEALTH - health, REST_PER_HOUR * sheltered * warm);

        Tally t = o.tally();
        Tally tally = new Tally(t.radiation() + fromRadiation, t.cold() + fromCold, t.hunger(),
                t.rest() + fromRest, t.energyUsed() + asked * running, t.foodEaten(), t.mealsShort(),
                t.hoursHeaterOff() + (o.heaterOn() ? 0 : dt),
                t.hoursNoPower() + (o.heaterOn() ? (1 - running) * dt : 0));
        return new Outpost(o.clock() + dt, energy, clean, shield, crops - picked, o.foodDays() + picked,
                health + fromRest, o.heaterOn(), o.lampsOn(), place, o.walkLeft() - walked, tally, marsSoil);
    }

    // ---- Moments --------------------------------------------------------------------

    /**
     * The outpost at sunrise on the first sol of a mission. Sunrise is the coldest moment
     * of the sol, so the heater is already on; the grow lamps are off.
     */
    public static Outpost landed(WeatherConditions weather) {
        return new Outpost(sunriseHour(weather), START_ENERGY, 1, 0, 0, START_FOOD, START_HEALTH,
                true, false, Place.HABITAT, 0, Tally.NONE, START_MARS_SOIL);
    }

    /**
     * Bedtime: the astronaut goes into the habitat and eats supper. With less than a day's
     * food in the store they eat what there is and lose health for the rest.
     */
    public static Outpost bedtime(Outpost o) {
        double eaten = Math.min(o.foodDays(), FOOD_PER_NIGHT);
        double shortBy = FOOD_PER_NIGHT - eaten;
        double fromHunger = Math.min(o.health(), HUNGER_HEALTH * shortBy / FOOD_PER_NIGHT);
        Tally t = o.tally();
        Tally tally = new Tally(t.radiation(), t.cold(), t.hunger() + fromHunger, t.rest(), t.energyUsed(),
                t.foodEaten() + eaten, t.mealsShort() + shortBy, t.hoursHeaterOff(), t.hoursNoPower());
        return new Outpost(o.clock(), o.energy(), o.panelsClean(), o.shield(), o.cropsReady(),
                o.foodDays() - eaten, o.health() - fromHunger, o.heaterOn(), o.lampsOn(),
                Place.HABITAT, 0, tally, o.marsSoil());
    }

    /** The same outpost at sunrise on a new sol: only the clock changes. */
    public static Outpost newSol(Outpost o, WeatherConditions weather) {
        return new Outpost(sunriseHour(weather), o.energy(), o.panelsClean(), o.shield(), o.cropsReady(),
                o.foodDays(), o.health(), o.heaterOn(), o.lampsOn(), o.place(), o.walkLeft(), o.tally(), o.marsSoil());
    }

    /** The outpost with the astronaut setting off for {@code place}. No change if already going there. */
    public static Outpost sent(Outpost o, Place place) {
        if (place == o.place()) {
            return o;
        }
        return new Outpost(o.clock(), o.energy(), o.panelsClean(), o.shield(), o.cropsReady(), o.foodDays(),
                o.health(), o.heaterOn(), o.lampsOn(), place, WALK_HOURS, o.tally(), o.marsSoil());
    }

    /** The outpost with its two switches set. */
    public static Outpost switched(Outpost o, boolean heaterOn, boolean lampsOn) {
        return new Outpost(o.clock(), o.energy(), o.panelsClean(), o.shield(), o.cropsReady(), o.foodDays(),
                o.health(), heaterOn, lampsOn, o.place(), o.walkLeft(), o.tally(), o.marsSoil());
    }

    // ---- Readings -------------------------------------------------------------------

    /** What the instruments read at the outpost's clock time. */
    public static Readings readings(Outpost o, WeatherConditions weather, RadiationConditions radiation) {
        double clock = o.clock();
        double sunrise = sunriseHour(weather);
        double sunset = sunsetHour(weather);
        double outside = outsideC(weather, clock);
        boolean digging = o.at(Place.DIGGER) && o.shield() < 1;
        double in = solarRate(o.panelsClean(), weather, clock);
        double out = LIFE_SUPPORT + (o.heaterOn() ? heaterDraw(outside) : 0)
                + (o.lampsOn() ? LAMP_DRAW : 0) + (digging ? DIGGER_DRAW : 0);
        boolean power = o.energy() > 1e-9 || in >= out;
        return new Readings(sunHeight(weather, clock),
                clamp((clock - sunrise) / (sunset - sunrise), 0, 1),
                clamp((clock - sunset) / (nightEnd(weather) - sunset), 0, 1),
                outside, sunStrength(weather.ls()), in, out, nightNeed(o, weather),
                o.heaterOn() && power, o.lampsOn() && power, digging && power, shieldBlocks(o.shield()),
                harm(o, radiation, outside, power ? 1 : in / out));
    }

    /**
     * Why health is falling at this moment: the same sums as one tick of {@link #step},
     * as rates. {@code running} is the share of what the machines ask for that they are
     * getting: 1 unless the battery is empty.
     */
    private static Harm harm(Outpost o, RadiationConditions radiation, double outside, double running) {
        boolean sheltered = o.at(Place.HABITAT);
        double warm = o.heaterOn() ? running : 0;
        double fromRadiation = radiationHarm(radiation) * (1 - (sheltered ? shieldBlocks(o.shield()) : 0));
        double fromCold = coldHarm(outside) * (1 - warm);
        double fromRest = sheltered ? REST_PER_HOUR * warm : 0;
        if (fromRadiation + fromCold <= fromRest + 1e-9) {
            return Harm.NONE;
        }
        return fromCold >= fromRadiation ? Harm.COLD : Harm.RADIATION;
    }

    /**
     * Energy the night will take, from sunset (or from now, if it is already night) to
     * sunrise, with the switches as they are. Nothing comes in at night, so this is how
     * much the battery must hold at sunset.
     */
    public static double nightNeed(Outpost o, WeatherConditions weather) {
        double from = Math.max(o.clock(), sunsetHour(weather));
        double to = nightEnd(weather);
        int slices = 48;
        double slice = (to - from) / slices;
        double need = 0;
        for (int i = 0; i < slices && slice > 0; i++) {
            double outside = outsideC(weather, from + (i + 0.5) * slice);
            need += (LIFE_SUPPORT + (o.heaterOn() ? heaterDraw(outside) : 0) + (o.lampsOn() ? LAMP_DRAW : 0)) * slice;
        }
        return need;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private Rules() {}
}
