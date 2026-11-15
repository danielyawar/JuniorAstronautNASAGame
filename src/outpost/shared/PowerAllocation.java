package outpost.shared;

/** Energy the player has spent on each use during the current sol, in whole units of energy. */
public record PowerAllocation(int shielding, int greenhouse, int heating) {

    /** Nothing spent yet. */
    public static final PowerAllocation NONE = new PowerAllocation(0, 0, 0);

    public PowerAllocation {
        if (shielding < 0 || greenhouse < 0 || heating < 0) {
            throw new IllegalArgumentException(
                    "Bad power allocation: " + shielding + "/" + greenhouse + "/" + heating);
        }
    }

    public int get(PowerUse use) {
        switch (use) {
            case SHIELD:
                return shielding;
            case GREENHOUSE:
                return greenhouse;
            default:
                return heating;
        }
    }

    /** This allocation with {@code delta} more energy on {@code use}. */
    public PowerAllocation plus(PowerUse use, int delta) {
        return new PowerAllocation(
                shielding + (use == PowerUse.SHIELD ? delta : 0),
                greenhouse + (use == PowerUse.GREENHOUSE ? delta : 0),
                heating + (use == PowerUse.HEAT ? delta : 0));
    }
}
