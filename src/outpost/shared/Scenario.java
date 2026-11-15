package outpost.shared;

import java.time.LocalDate;

/**
 * A mission the player can start: a real period of Mars history, or a surprise date. The
 * home screen shows the helmet, the title, the month and year and {@code hard}. The
 * mission-start card shows the helmet, the title, the dates and the story.
 *
 * @param title  short name
 * @param start  Earth date of the mission's first sol
 * @param end    Earth date of the mission's last sol
 * @param hard   about six words on what makes this mission hard
 * @param story  what really happened on Mars then, in a few sentences for display
 * @param helmet what the mission's real weather and radiation do to a helmet: the picture
 *               of the mission
 */
public record Scenario(String title, LocalDate start, LocalDate end, String hard, String story, Helmet helmet) {}
