package outpost.radiation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import outpost.shared.CsvResource;
import outpost.shared.RadiationConditions;
import outpost.shared.RadiationSource;
import outpost.shared.Sol;

/**
 * Radiation for each sol from real records: a measured everyday dose, plus a spike on the
 * dates when NASA's space weather model predicted a solar storm would reach Mars. The
 * storm dates come from the archive bundled in the jar (donki_mars_cme.csv, made by
 * tools/fetch_nasa_data.py).
 *
 * <p>Which sols have a storm is real data. How big the spike is on those sols is a GAME
 * ESTIMATE: see {@link #stormDoseMsv}.
 */
public final class ArchiveRadiationSource implements RadiationSource {

    /**
     * Everyday dose on the surface of Mars, in millisieverts per day, measured by
     * Curiosity's Radiation Assessment Detector: 0.64 +/- 0.12 mSv/day.
     * Hassler et al., "Mars' Surface Radiation Environment Measured with the Mars Science
     * Laboratory's Curiosity Rover", Science 343 (2014), doi:10.1126/science.1244797.
     */
    public static final double BACKGROUND_MSV = 0.64;

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private record Storm(int speedKms, LocalDate launched, boolean glancing) {}

    private final Map<LocalDate, Storm> storms = new HashMap<>();

    public ArchiveRadiationSource() {
        // Columns: arrival_date,speed_kms,cme_start_date,glancing
        for (String[] row : CsvResource.read(ArchiveRadiationSource.class, "donki_mars_cme.csv").rows()) {
            storms.put(LocalDate.parse(row[0]),
                    new Storm(Integer.parseInt(row[1]), LocalDate.parse(row[2]), row[3].equals("1")));
        }
    }

    /** Number of storm days in the archive. */
    public int stormDayCount() {
        return storms.size();
    }

    @Override
    public RadiationConditions radiationFor(Sol sol) {
        Storm storm = storms.get(sol.earthDate());
        if (storm == null) {
            return new RadiationConditions(BACKGROUND_MSV, false, null);
        }
        String detail = (storm.glancing() ? "The edge of a solar storm" : "A solar storm")
                + " that left the Sun on " + DATE.format(storm.launched()) + " at " + storm.speedKms()
                + " km/s was predicted to reach Mars today.";
        return new RadiationConditions(stormDoseMsv(storm.speedKms(), storm.glancing()), true, detail);
    }

    /**
     * GAME ESTIMATE of the dose on a storm sol. The archive says when a storm was predicted
     * to arrive and how fast it left the Sun, but not what dose it caused on the ground, so
     * the game scales the everyday dose by the storm's speed: twice the everyday dose at
     * 500 km/s or slower, up to six times at 2500 km/s or faster, and half the extra for a
     * glancing blow. These multipliers are chosen for the game and are not NASA figures.
     */
    static double stormDoseMsv(int speedKms, boolean glancing) {
        double extra = Math.max(1.0, Math.min(5.0, speedKms / 500.0));
        return BACKGROUND_MSV * (1.0 + (glancing ? extra / 2 : extra));
    }
}
