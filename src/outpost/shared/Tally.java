package outpost.shared;

/**
 * Running totals of what has happened to the outpost. {@code Outpost.tally} counts from the
 * start of the mission; the difference between two of them says what happened in between,
 * which is how the report on a night is made.
 *
 * @param radiation      health lost to radiation
 * @param cold           health lost to cold
 * @param hunger         health lost to hunger
 * @param rest           health recovered by resting
 * @param energyUsed     energy the machines drew
 * @param foodEaten      days of food eaten
 * @param mealsShort     days of food the astronaut needed but did not have
 * @param hoursHeaterOff Mars hours the heater was switched off
 * @param hoursNoPower   Mars hours the heater was switched on but had no energy to run on
 */
public record Tally(double radiation, double cold, double hunger, double rest, double energyUsed,
                    double foodEaten, double mealsShort, double hoursHeaterOff, double hoursNoPower) {

    /** Nothing has happened yet. */
    public static final Tally NONE = new Tally(0, 0, 0, 0, 0, 0, 0, 0, 0);

    /** What happened between {@code earlier} and this tally. */
    public Tally since(Tally earlier) {
        return new Tally(radiation - earlier.radiation, cold - earlier.cold, hunger - earlier.hunger,
                rest - earlier.rest, energyUsed - earlier.energyUsed, foodEaten - earlier.foodEaten,
                mealsShort - earlier.mealsShort, hoursHeaterOff - earlier.hoursHeaterOff,
                hoursNoPower - earlier.hoursNoPower);
    }

    /** Net change in health from every cause. */
    public double healthChange() {
        return rest - radiation - cold - hunger;
    }
}
