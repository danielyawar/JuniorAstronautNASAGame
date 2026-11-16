package outpost.weather;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import outpost.shared.CsvResource;
import outpost.shared.Sol;
import outpost.shared.UvIndex;
import outpost.shared.WeatherConditions;
import outpost.shared.WeatherSource;

/**
 * Real weather measured by the Curiosity rover at Gale Crater, read from the archive
 * bundled in the jar (curiosity_weather.csv, made by tools/fetch_nasa_data.py).
 *
 * <p>A game sol is looked up by its Earth date. Dates the rover has no row for use the
 * most recent earlier row, and {@link WeatherConditions#measuredOn()} says which date was
 * used. Within a row, a value the rover did not report that sol is taken from the nearest
 * earlier sol that has it.
 *
 * <p>Sunlight is a game estimate, not a measurement: see {@link #sunlightFor(UvIndex)}.
 */
public final class ArchiveWeatherSource implements WeatherSource {

    private final TreeMap<LocalDate, WeatherConditions> byDate = new TreeMap<>();

    public ArchiveWeatherSource() {
        // Columns: earth_date,sol,ls,min_air_c,max_air_c,min_ground_c,max_ground_c,
        //          pressure_pa,uv_index,sunrise,sunset
        TreeMap<LocalDate, String[]> rows = new TreeMap<>();
        for (String[] row : CsvResource.read(ArchiveWeatherSource.class, "curiosity_weather.csv").rows()) {
            rows.put(LocalDate.parse(row[0]), row);
        }
        Double ls = null;
        Double min = null;
        Double max = null;
        Double pressure = null;
        UvIndex uv = null;
        LocalTime sunrise = null;
        LocalTime sunset = null;
        // The first pass finds starting values for the few sols before the rover's first
        // full reading. The second fills every row, carrying the last known value forward.
        for (String[] row : rows.values()) {
            ls = ls != null ? ls : number(row[2]);
            min = min != null ? min : number(row[3]);
            max = max != null ? max : number(row[4]);
            pressure = pressure != null ? pressure : number(row[7]);
            uv = uv != null ? uv : uv(row[8]);
            sunrise = sunrise != null ? sunrise : time(row[9]);
            sunset = sunset != null ? sunset : time(row[10]);
        }
        if (ls == null || min == null || max == null || pressure == null || uv == null) {
            throw new IllegalStateException("curiosity_weather.csv has no complete readings");
        }
        for (Map.Entry<LocalDate, String[]> entry : rows.entrySet()) {
            String[] row = entry.getValue();
            ls = number(row[2]) != null ? number(row[2]) : ls;
            min = number(row[3]) != null ? number(row[3]) : min;
            max = number(row[4]) != null ? number(row[4]) : max;
            pressure = number(row[7]) != null ? number(row[7]) : pressure;
            uv = uv(row[8]) != null ? uv(row[8]) : uv;
            sunrise = time(row[9]) != null ? time(row[9]) : sunrise;
            sunset = time(row[10]) != null ? time(row[10]) : sunset;
            byDate.put(entry.getKey(), new WeatherConditions(
                    min, max, sunlightFor(uv), Integer.parseInt(row[1]), pressure, uv,
                    sunrise, sunset, entry.getKey(), ls));
        }
    }

    /** First Earth date in the archive. */
    public LocalDate firstDate() {
        return byDate.firstKey();
    }

    /** Last Earth date in the archive. */
    public LocalDate lastDate() {
        return byDate.lastKey();
    }

    @Override
    public WeatherConditions weatherFor(Sol sol) {
        Map.Entry<LocalDate, WeatherConditions> entry = byDate.floorEntry(sol.earthDate());
        return entry != null ? entry.getValue() : byDate.firstEntry().getValue();
    }

    /**
     * GAME ESTIMATE of how much solar power gets through, from the rover's UV reading. The
     * feed's own sky field says "Sunny" on every sol, even during the 2018 global dust
     * storm, so it cannot be used; the UV reading does drop to "Low" in that storm. The
     * four shares below are chosen for the game and are not NASA figures.
     */
    static double sunlightFor(UvIndex uv) {
        switch (uv) {
            case VERY_HIGH:
                return 1.0;
            case HIGH:
                return 0.85;
            case LOW:
                return 0.35;
            default:
                return 0.65;
        }
    }

    private static Double number(String text) {
        return text.isEmpty() ? null : Double.valueOf(text);
    }

    private static LocalTime time(String text) {
        return text.isEmpty() ? null : LocalTime.parse(text);
    }

    private static UvIndex uv(String text) {
        return text.isEmpty() ? null : UvIndex.valueOf(text.toUpperCase(Locale.ROOT));
    }
}
