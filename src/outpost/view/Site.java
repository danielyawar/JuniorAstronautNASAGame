package outpost.view;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import outpost.shared.Place;

/**
 * The one table of places on the play screen. For each of the four places the astronaut can
 * be sent to, it says which picture stands for it and where that picture is drawn, where the
 * astronaut stands to work there, and where its round button and its switch are. Every
 * number is in the 800 x 600 layout.
 *
 * <p>The pictures, the buttons, the switches, the clicks, the astronaut's walk and label and
 * the guide's ring all read this table. To move a place, change its row here and nothing
 * else. (A picture drawn at a new size should also be shrunk again: tools/ShrinkPictures.java.)
 *
 * <p>A click counts on a round button, on a switch, and on a picture wherever the picture
 * is not see-through (see {@link Pictures#solid}). So nothing that cannot be seen can be
 * clicked, and no click area has numbers of its own to keep in step.
 */
enum Site {

    // Place, its picture's file, where the picture is drawn (left, top, width, height),
    // where the astronaut's feet are (x, y), the round button's corner, the switch's corner.
    HABITAT(Place.HABITAT, "base.png", box(350, 375, 175, 150), 388, 502, null, toggle(326, 418)),
    GREENHOUSE(Place.GREENHOUSE, "greenh.png", box(625, 375, 105, 85), 677, 504, button(639, 302), toggle(594, 384)),
    PANELS(Place.PANELS, "panel.png", box(80, 470, 80, 50), 184, 508, button(82, 390), null),
    DIGGER(Place.DIGGER, "drill.png", box(327, 180, 60, 50), 424, 236, button(309, 100), null);

    /** Every round button is this wide and high. */
    static final double BUTTON_SIZE = 76;
    /** Every switch is this wide and high. */
    static final double SWITCH_WIDTH = 56;
    static final double SWITCH_HEIGHT = 16;
    /** The dark frame drawn round a switch reaches this far outside it. */
    static final double SWITCH_FRAME = 2;

    private final Place place;
    private final String file;
    private final Rectangle2D picture;
    private final double standX;
    private final double standY;
    private final Ellipse2D button;
    private final RoundRectangle2D toggle;
    private final RoundRectangle2D toggleFrame;

    Site(Place place, String file, Rectangle2D picture, double standX, double standY, Ellipse2D button,
         RoundRectangle2D toggle) {
        this.place = place;
        this.file = file;
        this.picture = picture;
        this.standX = standX;
        this.standY = standY;
        this.button = button;
        this.toggle = toggle;
        this.toggleFrame = toggle == null ? null : new RoundRectangle2D.Double(
                toggle.getX() - SWITCH_FRAME, toggle.getY() - SWITCH_FRAME,
                toggle.getWidth() + 2 * SWITCH_FRAME, toggle.getHeight() + 2 * SWITCH_FRAME, 8, 8);
    }

    private static Rectangle2D box(double left, double top, double width, double height) {
        return new Rectangle2D.Double(left, top, width, height);
    }

    private static Ellipse2D button(double left, double top) {
        return new Ellipse2D.Double(left, top, BUTTON_SIZE, BUTTON_SIZE);
    }

    private static RoundRectangle2D toggle(double left, double top) {
        return new RoundRectangle2D.Double(left, top, SWITCH_WIDTH, SWITCH_HEIGHT, 8, 8);
    }

    /** The row for {@code place}. */
    static Site of(Place place) {
        for (Site site : values()) {
            if (site.place == place) {
                return site;
            }
        }
        throw new IllegalArgumentException("No site for " + place);
    }

    /** The place the astronaut is sent to from here. */
    Place place() {
        return place;
    }

    /** The picture's file, beside this class in the jar. */
    String file() {
        return file;
    }

    /** Where the picture is drawn. Its see-through edges are inside this too. */
    Rectangle2D picture() {
        return picture;
    }

    /**
     * Where the astronaut's feet are while they work here, on open ground beside the
     * picture. For the habitat this is the door: the astronaut walks to it and goes in.
     */
    double standX() {
        return standX;
    }

    double standY() {
        return standY;
    }

    /** The round button, or null: the habitat has none. */
    Ellipse2D button() {
        return button;
    }

    /** The switch (the heater's at the habitat, the lamps' at the greenhouse), or null. */
    RoundRectangle2D toggle() {
        return toggle;
    }

    /** The dark frame round the switch, which is also where a click flips it. Null without a switch. */
    RoundRectangle2D toggleFrame() {
        return toggleFrame;
    }
}
