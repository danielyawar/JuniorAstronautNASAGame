package outpost.shared;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Weather for one sol.
 *
 * @param sunlight   share of clear-sky solar power reaching the panels, 0.0 to 1.0
 * @param missionSol the Curiosity mission sol the reading comes from, or 0 if it is not a
 *                   rover reading
 * @param pressurePa air pressure in pascals, or NaN if unknown
 * @param uv         ultraviolet strength; UNKNOWN if not reported
 * @param sunrise    local Mars time of sunrise, or null if unknown
 * @param sunset     local Mars time of sunset, or null if unknown
 * @param measuredOn Earth date of the reading used, or null if it is not a rover reading.
 *                   Differs from the sol's own date when the rover has no reading for it.
 */
public record WeatherConditions(double minTempC, double maxTempC, double sunlight,
                                int missionSol, double pressurePa, UvIndex uv,
                                LocalTime sunrise, LocalTime sunset, LocalDate measuredOn) {

    /** Weather with only the three core values known. */
    public WeatherConditions(double minTempC, double maxTempC, double sunlight) {
        this(minTempC, maxTempC, sunlight, 0, Double.NaN, UvIndex.UNKNOWN, null, null, null);
    }
}
