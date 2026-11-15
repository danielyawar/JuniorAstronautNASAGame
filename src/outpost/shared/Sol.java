package outpost.shared;

import java.time.LocalDate;

/** One game day. {@code number} starts at 1; {@code earthDate} keys the data archives. */
public record Sol(int number, LocalDate earthDate) {

    /** The following sol. Approximates one sol as one Earth day (a real sol is 24 h 39 min). */
    public Sol next() {
        return new Sol(number + 1, earthDate.plusDays(1));
    }
}
