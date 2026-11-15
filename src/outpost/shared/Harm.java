package outpost.shared;

/**
 * What is taking the astronaut's health at this moment. The rules work it out, so that the
 * health bar can say why it is falling. Hunger is not here: it strikes once, at bedtime,
 * and the night's report tells of it.
 */
public enum Harm {
    /** Health is steady or coming back. */
    NONE,
    /** Health is falling, and radiation is taking the most. */
    RADIATION,
    /** Health is falling, and the cold is taking the most. */
    COLD
}
