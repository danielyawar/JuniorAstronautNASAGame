package outpost.shared;

/** Strength of ultraviolet sunlight at the surface, as Curiosity's weather feed reports it. */
public enum UvIndex {
    UNKNOWN("not measured"),
    LOW("low"),
    MODERATE("moderate"),
    HIGH("high"),
    VERY_HIGH("very high");

    private final String label;

    UvIndex(String label) {
        this.label = label;
    }

    /** Lower-case wording for display. */
    public String label() {
        return label;
    }
}
