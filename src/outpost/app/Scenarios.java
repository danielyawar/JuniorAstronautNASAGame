package outpost.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import outpost.shared.RadiationSource;
import outpost.shared.Scenario;
import outpost.shared.WeatherSource;

/**
 * The missions a player can start: four real periods of Mars history, and a surprise date.
 * {@link #LIST} is the one place the four are written down.
 */
final class Scenarios {

    /**
     * The first Earth date for which Curiosity's weather archive has temperatures. No
     * mission starts before this.
     */
    static final LocalDate FIRST_READINGS = LocalDate.of(2012, 8, 16);

    /** What is written by hand for a scenario. */
    private record Entry(String title, LocalDate start, String hard, String story) {}

    /**
     * Each start date is chosen so that the scenario's event falls inside a mission of
     * {@link Rules#MISSION_SOLS} sols; check them against the archives if that changes.
     * {@code hard} and the stories are the only claims about history the game makes.
     * Reword them freely for young readers, but add no fact without a NASA source for it.
     *
     * <p>The order is the order of the cards on the home screen: from the gentlest mission
     * to the hardest, by the health the careful scripted player has left at the end (see
     * Balance, which prints the order it finds and says so if this list disagrees). The
     * first is the one the home screen marks "Start here".
     */
    private static final List<Entry> LIST = List.of(
            new Entry("First days on Mars", FIRST_READINGS,
                    "Clear skies and a quiet Sun",
                    "The Curiosity rover landed in Gale Crater on 6 August 2012. This mission starts"
                    + " on the first day we have its temperature readings."),
            new Entry("The great dust storm", LocalDate.of(2018, 6, 8),
                    "Dust blocks the Sun",
                    "A dust storm grew until it covered the whole planet. The Opportunity rover ran"
                    + " on sunlight and went silent on 10 June 2018. Curiosity runs on nuclear"
                    + " power, so it kept measuring."),
            new Entry("Deep winter", LocalDate.of(2016, 1, 6),
                    "The coldest nights and weak sunlight",
                    "It was early winter at Gale Crater, the place on Mars where the Curiosity"
                    + " rover works. Mars was almost as far from the Sun as it ever gets."),
            new Entry("The stormy Sun", LocalDate.of(2024, 5, 10),
                    "Nine sols of solar storms",
                    "The Sun was near the peak of its 11-year cycle. On 20 May 2024, Curiosity's"
                    + " radiation detector measured its biggest jump since the rover landed."));

    /**
     * Every scenario, gentlest first, for a mission of {@code sols} sols from its start.
     * The two sources give each one its helmet.
     */
    static List<Scenario> all(int sols, WeatherSource weather, RadiationSource radiation) {
        List<Scenario> all = new ArrayList<>();
        for (Entry entry : LIST) {
            all.add(new Scenario(entry.title(), entry.start(), lastDate(entry.start(), sols),
                    entry.hard(), entry.story(), Helmets.of(entry.start(), sols, weather, radiation)));
        }
        return List.copyOf(all);
    }

    /**
     * A mission for "Surprise me": it starts on any date from {@link #FIRST_READINGS} on
     * that leaves a whole mission of {@code sols} sols on or before {@code archiveEnd}, each
     * as likely as the next.
     */
    static Scenario surprise(Random random, LocalDate archiveEnd, int sols, WeatherSource weather,
                             RadiationSource radiation) {
        LocalDate latest = archiveEnd.minusDays(sols - 1L);
        long choices = ChronoUnit.DAYS.between(FIRST_READINGS, latest) + 1;
        LocalDate start = choices <= 1 ? FIRST_READINGS : FIRST_READINGS.plusDays(random.nextInt((int) choices));
        return onDate(start, sols, weather, radiation);
    }

    /** The surprise mission that starts on {@code start}. Its helmet shows what those days are really like. */
    static Scenario onDate(LocalDate start, int sols, WeatherSource weather, RadiationSource radiation) {
        return new Scenario("Surprise mission", start, lastDate(start, sols),
                "A mystery day",
                "These are real days from the Curiosity rover's records, picked by chance."
                + " Watch the sky and the radiation detector to see what Mars does.",
                Helmets.of(start, sols, weather, radiation));
    }

    /** One sol is counted as one Earth day: see Sol. */
    private static LocalDate lastDate(LocalDate start, int sols) {
        return start.plusDays(sols - 1L);
    }

    private Scenarios() {}
}
