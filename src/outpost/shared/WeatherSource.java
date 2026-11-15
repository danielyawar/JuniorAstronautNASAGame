package outpost.shared;

/** Supplies the weather for a sol. Must return the same answer every time for the same sol. */
public interface WeatherSource {
    WeatherConditions weatherFor(Sol sol);
}
