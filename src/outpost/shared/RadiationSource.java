package outpost.shared;

/** Supplies the radiation for a sol. Must return the same answer every time for the same sol. */
public interface RadiationSource {
    RadiationConditions radiationFor(Sol sol);
}
