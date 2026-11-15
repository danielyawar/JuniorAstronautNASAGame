package outpost.shared;

/** The player's split of a sol's power, in percent. The three shares total at most 100. */
public record PowerAllocation(int shielding, int greenhouse, int heating) {

    public PowerAllocation {
        if (shielding < 0 || greenhouse < 0 || heating < 0 || shielding + greenhouse + heating > 100) {
            throw new IllegalArgumentException(
                    "Bad power allocation: " + shielding + "/" + greenhouse + "/" + heating);
        }
    }
}
