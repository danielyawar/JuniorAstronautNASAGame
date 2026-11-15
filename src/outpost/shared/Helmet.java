package outpost.shared;

/**
 * The state of the astronaut's helmet that stands for a mission on its card. The helmet
 * shows at a glance what the mission is like. Each dial runs from 0.0 (none) to 1.0 (as
 * much as the game ever shows). The controller works the dials out from the mission's own
 * weather and radiation; the view only draws them.
 *
 * @param dust  orange dust on the shell and haze in the visor: how many of the mission's
 *              sols have a dusty sky
 * @param frost frost creeping in from the rim of the visor: how cold the mission's usual
 *              night is
 * @param storm a flaring Sun and pink streaks in the visor: how many of the mission's sols
 *              have a solar storm
 * @param wear  scuffs and scratches: how hard the mission is on the whole
 */
public record Helmet(double dust, double frost, double storm, double wear) {

    /** A helmet that nothing has touched. */
    public static final Helmet CLEAN = new Helmet(0, 0, 0, 0);
}
