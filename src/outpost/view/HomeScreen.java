package outpost.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.Timer;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Phase;
import outpost.shared.PlayerActions;
import outpost.shared.Scenario;

/**
 * The home screen, shown while the phase is BRIEFING: one large astronaut's helmet, and
 * down the right-hand side a list of the missions with "Surprise me" at its foot.
 *
 * <p>The helmet is the picture of the mission that is picked in the list: clean, caked in
 * dust, frosted or under a flaring Sun (Helmet3D draws it, from the dials the controller
 * worked out). It sways gently, and the player can drag it right round. Under it are only
 * the mission's name, its month and year, one line on what makes it hard, and the button
 * that goes on to the mission-start card, where the story is. The list runs from the
 * gentlest mission to the hardest, as the state's list does, and the first is marked
 * "Start here".
 *
 * <p>A click on a row of the list picks that mission; so do the mouse wheel and the up and
 * down keys. Picking starts nothing: only the button does. Which row is picked, how far the
 * helmet is turned and what the mouse is over are this screen's own display state. The game
 * hears of them only through {@code start(scenario)} and {@code startSurprise()}.
 *
 * <p>OutpostView owns the panel. While the home screen is showing it passes every mouse and
 * key event here, with the mouse's place given in the 800 x 600 layout, and asks this class
 * to paint. The helmet's movement runs on a timer of this class's own, which runs only
 * while the home screen is on a screen that is showing and something is moving. Everything
 * that moves is changed in {@link #tick} or by the mouse and keys, never while painting.
 */
final class HomeScreen {

    // ---- For whoever tunes the cost in the browser ---------------------------------------

    /** Milliseconds between frames while the helmet is moving: 25 frames a second. */
    static final int TICK_MILLIS = 40;
    /**
     * True: the helmet sways by itself, so the timer runs all the while the home screen
     * shows. False: the helmet turns only when it is dragged, and the timer runs only
     * during a drag, the swing back after it, and a change from one mission's helmet to
     * the next.
     */
    static final boolean TURNS_BY_ITSELF = true;
    /** The sway: this many degrees to each side of facing the player, once there and back in this many seconds. */
    static final double SWAY_DEGREES = 27;
    static final double SWAY_SECONDS = 9;
    /** After a drag the helmet swings back to its sway; in this many seconds it covers about two thirds of the way. */
    static final double SETTLE_SECONDS = 0.55;
    /** Most real time one tick may count for, so that a slow frame does not make the helmet jump. */
    private static final double LONGEST_TICK_SECONDS = 0.12;
    /**
     * The still parts of the screen are kept as pictures (two, of the panel's size) if the
     * panel has no more pixels than this; a bigger panel is drawn afresh each time, which
     * a computer with such a screen does quickly.
     */
    private static final long MOST_BACKDROP_PIXELS = 3_200_000;

    // ---- The layout, at 800 x 600 ------------------------------------------------------------

    private static final Font HEADING_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 28);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 26);
    private static final Font WHEN_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private static final Font HARD_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    private static final Font PICK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private static final Font ROW_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    private static final Font MARK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font FOOT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /** The sky at dusk: dark behind the helmet, so that any helmet stands out, and bright only near the ground. */
    private static final Color SKY_TOP = new Color(46, 32, 50);
    private static final Color SKY_MIDDLE = new Color(96, 60, 66);
    private static final Color SKY_BOTTOM = new Color(204, 136, 92);
    /** How far down the panel the sky is SKY_MIDDLE. */
    private static final float SKY_MIDDLE_AT = 0.6f;
    private static final Color FAR_GROUND = new Color(150, 84, 58);
    private static final Color NEAR_GROUND = new Color(98, 54, 44);
    /** Fill of the row that is picked. */
    private static final Color ROW_PICKED = new Color(58, 62, 92);

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private static final String PICK = "PICK THIS MISSION";
    private static final String SURPRISE = "Surprise me";
    /** The one line under "Surprise me": the words a surprise mission carries. */
    private static final String SURPRISE_HARD = "A mystery day";

    /** The middle of the left-hand part of the screen, which holds the helmet and its words. */
    private static final double CENTRE = 282;
    /** The square the helmet is drawn in. */
    private static final Rectangle2D HELMET = new Rectangle2D.Double(CENTRE - 166, 46, 332, 332);
    private static final double NAME_BASELINE = 404;
    private static final double WHEN_BASELINE = 425;
    private static final double HARD_BASELINE = 447;
    private static final double PICK_WIDTH = 248;
    private static final double PICK_HEIGHT = 44;
    /** The list: its rows are big enough for a finger. */
    private static final double LIST_LEFT = 558;
    private static final double LIST_TOP = 102;
    private static final double ROW_WIDTH = 222;
    private static final double ROW_HEIGHT = 62;
    private static final double ROW_GAP = 10;
    /** "Surprise me" stands a little apart from the missions above it. */
    private static final double SURPRISE_GAP = 10;

    /** What the mouse can be over, besides a row of the list (0 and up). */
    private static final int NOTHING = -1;
    private static final int PICK_BUTTON = -2;

    private final JComponent owner;
    private final PlayerActions actions;
    private final Helmet3D helmet = new Helmet3D();
    private final Timer timer;
    private long lastTick;

    /** The state while the home screen is showing, and null while it is not. */
    private GameState state;
    /** The row of the list that is picked: a mission, or one more for "Surprise me". The gentlest to begin with. */
    private int picked;
    /** What the mouse is over: a row, PICK_BUTTON or NOTHING. */
    private int hot = NOTHING;
    /** Seconds the sway has run, and how far a drag has turned the helmet from it, in radians. */
    private double swayTime;
    private double dragTurn;
    private boolean dragging;
    private double dragX;
    /** Wheel notches not yet used: a touchpad sends many small parts of one. */
    private double wheelLeft;
    /** False until the skin of every row's helmet has been painted, a row at each tick, so that picking one starts at once. */
    private boolean helmetsReady;

    /** The sky, the ground, the game's name and the foot line as a picture in the screen's own pixels, and the scale it was made at. */
    private BufferedImage scenery;
    private double sceneryScaleX;
    private double sceneryScaleY;
    /** The scenery with the words and the list on it: everything but the helmet. And what it is a picture of. */
    private BufferedImage backdrop;
    private GameState backdropState;
    private int backdropPicked;
    private int backdropHot;

    /** @param owner the panel this screen is painted on, asked to repaint when the screen changes */
    HomeScreen(JComponent owner, PlayerActions actions) {
        this.owner = owner;
        this.actions = actions;
        timer = new Timer(TICK_MILLIS, event -> {
            long now = System.nanoTime();
            double seconds = Math.min(LONGEST_TICK_SECONDS, (now - lastTick) / 1e9);
            lastTick = now;
            tick(seconds);
        });
        // The timer stops when the window is closed or hidden, and starts again when it comes back.
        owner.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                keepTime();
            }
        });
    }

    /** Called with every new state. The home screen is showing while the phase is BRIEFING. */
    void show(GameState newState) {
        if (newState.phase() != Phase.BRIEFING) {
            state = null;
            hot = NOTHING;
            dragging = false;
            dragTurn = 0;
            wheelLeft = 0;
            scenery = null;
            backdrop = null;
            if (newState.phase() != Phase.READY) {
                // A sol is being played. The skins of the helmets that are not picked are some
                // megabytes each and are painted again in a moment, so they are not kept until
                // the player comes back; what is needed to show the home screen at once is.
                // (On the mission-start card all are kept: "Change mission" is one click away.)
                helmet.rest();
            }
            keepTime();
            return;
        }
        boolean arrived = state == null || !state.scenarios().equals(newState.scenarios());
        state = newState;
        if (arrived) {
            picked = Math.min(picked, newState.scenarios().size());
            helmet.show(look(picked));
            helmetsReady = false;
        }
        keepTime();
    }

    // ---- Movement --------------------------------------------------------------------------

    /**
     * Moves everything on by {@code seconds}: the sway, the swing back after a drag and the
     * change from one mission's helmet to the next. The timer calls this; a test can too.
     */
    void tick(double seconds) {
        if (state == null) {
            return;
        }
        boolean moved = helmet.advance(seconds);
        if (!dragging) {
            if (TURNS_BY_ITSELF) {
                swayTime += seconds;
                moved = true;
            }
            if (dragTurn != 0) {
                dragTurn *= Math.exp(-seconds / SETTLE_SECONDS);
                if (Math.abs(dragTurn) < 0.004) {
                    dragTurn = 0;
                }
                moved = true;
            }
        }
        if (!helmetsReady) {
            helmetsReady = !prepareOne();
        }
        if (moved) {
            repaintHelmet();
        }
        keepTime();
    }

    /** Starts the timer if the screen is showing and something is moving, and stops it if not. */
    private void keepTime() {
        boolean moving = TURNS_BY_ITSELF || dragging || dragTurn != 0 || helmet.changing() || !helmetsReady;
        boolean wanted = state != null && moving && owner.isShowing();
        if (wanted && !timer.isRunning()) {
            lastTick = System.nanoTime();
            timer.start();
        } else if (!wanted && timer.isRunning()) {
            timer.stop();
        }
    }

    /** Paints the skin of one row's helmet that has not been painted yet. False when all of them have been. */
    private boolean prepareOne() {
        for (int row = 0; row <= state.scenarios().size(); row++) {
            if (Helmet3D.prepare(look(row))) {
                return true;
            }
        }
        return false;
    }

    /** How far the helmet is turned now, in radians: more than 0 turns its front to the right. */
    private double turn() {
        double sway = TURNS_BY_ITSELF
                ? Math.toRadians(SWAY_DEGREES) * Math.sin(2 * Math.PI * swayTime / SWAY_SECONDS) : 0;
        return sway + dragTurn;
    }

    /** The look of the helmet for a row of the list. */
    private Helmet3D.Look look(int row) {
        List<Scenario> scenarios = state.scenarios();
        return row < scenarios.size() ? Helmet3D.Look.of(scenarios.get(row).helmet()) : Helmet3D.Look.MYSTERY;
    }

    /** Picks a row of the list; past either end, the end. The helmet changes to match over a moment. */
    private void pick(int row) {
        int wanted = Math.max(0, Math.min(state.scenarios().size(), row));
        if (wanted == picked) {
            return;
        }
        picked = wanted;
        helmet.aim(look(picked));
        owner.repaint();
        keepTime();
    }

    private void repaintHelmet() {
        int width = owner.getWidth();
        int height = owner.getHeight();
        double scale = Draw.scale(width, height);
        double left = (width - Draw.WIDTH * scale) / 2 + HELMET.getX() * scale;
        double top = (height - Draw.HEIGHT * scale) / 2 + HELMET.getY() * scale;
        owner.repaint((int) Math.floor(left) - 2, (int) Math.floor(top) - 2,
                (int) Math.ceil(HELMET.getWidth() * scale) + 5, (int) Math.ceil(HELMET.getHeight() * scale) + 5);
    }

    // ---- The mouse and the keys ----------------------------------------------------------------

    /** The mouse was pressed at (x, y) in the 800 x 600 layout. */
    void pressed(double x, double y) {
        if (state == null) {
            return;
        }
        int at = at(x, y);
        if (at == PICK_BUTTON) {
            if (picked < state.scenarios().size()) {
                actions.start(state.scenarios().get(picked));
            } else {
                actions.startSurprise();
            }
        } else if (at != NOTHING) {
            pick(at);
        } else if (onHelmet(x, y)) {
            dragging = true;
            dragX = x;
            keepTime();
        }
    }

    /** The mouse moved to (x, y) with its button held down: a drag that began on the helmet turns it. */
    void dragged(double x, double y) {
        if (state == null || !dragging) {
            return;
        }
        // The helmet's front follows the finger: across the shell's radius is one radian.
        dragTurn += (x - dragX) / (HELMET.getWidth() * Helmet3D.RADIUS);
        dragX = x;
        repaintHelmet();
    }

    /** The mouse button was let go at (x, y). The helmet swings back to its sway by the shorter way round. */
    void released(double x, double y) {
        if (!dragging) {
            return;
        }
        dragging = false;
        dragTurn = Math.IEEEremainder(dragTurn, 2 * Math.PI);
        keepTime();
    }

    /** The mouse moved to (x, y) with no button held. */
    void moved(double x, double y) {
        if (state == null) {
            return;
        }
        int at = at(x, y);
        if (at != hot) {
            hot = at;
            owner.repaint();
        }
    }

    /** The mouse left the panel. */
    void exited() {
        if (hot != NOTHING) {
            hot = NOTHING;
            owner.repaint();
        }
    }

    /** The mouse wheel turned by {@code notches}; more than 0 is toward the player, and moves down the list. */
    void wheel(double notches) {
        if (state == null || notches == 0) {
            return;
        }
        if (notches > 0 != wheelLeft > 0) {
            wheelLeft = 0;
        }
        wheelLeft += notches;
        int rows = (int) wheelLeft;
        if (rows != 0) {
            wheelLeft -= rows;
            pick(picked + rows);
        }
    }

    /** A key went down: {@code keyCode} is one of KeyEvent's VK numbers. Up and down move through the list. */
    void key(int keyCode) {
        if (state == null) {
            return;
        }
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_KP_UP) {
            pick(picked - 1);
        } else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_KP_DOWN) {
            pick(picked + 1);
        }
    }

    /** True if a press at (x, y) would do something, so the mouse shows a hand there. */
    boolean clickable(double x, double y) {
        return state != null && (at(x, y) != NOTHING || onHelmet(x, y));
    }

    /** The row of the list at (x, y) in the 800 x 600 layout, or PICK_BUTTON, or NOTHING. */
    private int at(double x, double y) {
        int missions = state.scenarios().size();
        for (int row = 0; row <= missions; row++) {
            if (row(row, missions).contains(x, y)) {
                return row;
            }
        }
        return pickButton().contains(x, y) ? PICK_BUTTON : NOTHING;
    }

    /** True if (x, y) is on the helmet, where a drag turns it. */
    private static boolean onHelmet(double x, double y) {
        return Math.hypot(x - HELMET.getCenterX(), y - HELMET.getCenterY()) < HELMET.getWidth() * 0.46;
    }

    /**
     * Where row number {@code row} of the list is, in the 800 x 600 layout. The rows after
     * the {@code missions} rows of missions (there is one: "Surprise me") stand a little apart.
     */
    private static Rectangle2D row(int row, int missions) {
        double top = LIST_TOP + row * (ROW_HEIGHT + ROW_GAP) + (row >= missions ? SURPRISE_GAP : 0);
        return new Rectangle2D.Double(LIST_LEFT, top, ROW_WIDTH, ROW_HEIGHT);
    }

    /**
     * Where the button that goes on to the mission-start card is: under the helmet's words,
     * and wholly below the place where START will be on that card. A child who clicks twice
     * then does not start the clock before seeing the card.
     */
    private static Rectangle2D pickButton() {
        double top = Math.max(HARD_BASELINE + 13, Notices.START_BUTTON.getMaxY() + 6);
        return new Rectangle2D.Double(CENTRE - PICK_WIDTH / 2, top, PICK_WIDTH, PICK_HEIGHT);
    }

    // ---- Painting ------------------------------------------------------------------------------

    /** Draws the whole screen onto a panel of the given size. {@code g} is still in panel pixels. */
    void paint(Graphics2D g, int panelWidth, int panelHeight) {
        if (state == null) {
            return;
        }
        AffineTransform device = g.getTransform();
        int width = (int) Math.ceil(panelWidth * device.getScaleX() - 1e-6);
        int height = (int) Math.ceil(panelHeight * device.getScaleY() - 1e-6);
        // A picture in the screen's own pixels can stand in for the drawing only if the panel
        // starts on a whole pixel and is not turned: otherwise it is drawn afresh.
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0
                && Math.abs(device.getTranslateX() - Math.rint(device.getTranslateX())) < 1e-6
                && Math.abs(device.getTranslateY() - Math.rint(device.getTranslateY())) < 1e-6;
        if (plain && (long) width * height <= MOST_BACKDROP_PIXELS) {
            // Everything but the helmet keeps still, so it is kept as a picture in the
            // screen's own pixels, and a frame of the helmet's movement costs only the helmet.
            // The picture is made in two steps: the scenery, which changes only with the
            // panel's size, and on a copy of it the words and the list, which change with
            // every pick and with the mouse.
            boolean resized = scenery == null || scenery.getWidth() != width || scenery.getHeight() != height
                    || sceneryScaleX != device.getScaleX() || sceneryScaleY != device.getScaleY();
            if (resized) {
                scenery = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                backdrop = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                sceneryScaleX = device.getScaleX();
                sceneryScaleY = device.getScaleY();
                Graphics2D pen = pen(scenery, device);
                try {
                    paintScenery(pen, panelWidth, panelHeight);
                } finally {
                    pen.dispose();
                }
            }
            if (resized || backdropState != state || backdropPicked != picked || backdropHot != hot) {
                Graphics2D pen = pen(backdrop, device);
                try {
                    AffineTransform scaled = pen.getTransform();
                    pen.setTransform(new AffineTransform());
                    pen.drawImage(scenery, 0, 0, null);
                    pen.setTransform(scaled);
                    Draw.fit(pen, panelWidth, panelHeight);
                    paintChoice(pen);
                } finally {
                    pen.dispose();
                }
                backdropState = state;
                backdropPicked = picked;
                backdropHot = hot;
            }
            g.setTransform(new AffineTransform());
            g.drawImage(backdrop, (int) Math.round(device.getTranslateX()), (int) Math.round(device.getTranslateY()),
                    null);
            g.setTransform(device);
            Draw.fit(g, panelWidth, panelHeight);
        } else {
            scenery = null;
            backdrop = null;
            paintScenery(g, panelWidth, panelHeight);
            paintChoice(g);
        }
        helmet.paint(g, HELMET.getX(), HELMET.getY(), HELMET.getWidth(), turn());
    }

    /** Something to draw on a kept picture with, set up as OutpostView sets up the screen's, in panel pixels. */
    private static Graphics2D pen(BufferedImage picture, AffineTransform device) {
        Graphics2D pen = picture.createGraphics();
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        pen.scale(device.getScaleX(), device.getScaleY());
        return pen;
    }

    /**
     * What changes only with the size of the panel: the sky, the stars, the ground, the
     * game's name and the line at the foot. {@code g} arrives in panel pixels and is left in
     * the 800 x 600 layout.
     */
    private static void paintScenery(Graphics2D g, int panelWidth, int panelHeight) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, panelHeight, new float[] {0f, SKY_MIDDLE_AT, 1f},
                new Color[] {SKY_TOP, SKY_MIDDLE, SKY_BOTTOM}));
        g.fillRect(0, 0, panelWidth, panelHeight);
        Draw.fit(g, panelWidth, panelHeight);
        paintStars(g);
        paintGround(g);

        g.setFont(HEADING_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, "Junior Astronaut: Mars Outpost", Draw.WIDTH / 2.0, 42);
        g.setFont(FOOT_FONT);
        g.setColor(Draw.CREAM);
        Draw.centred(g, "Real NASA weather and solar storms from the past. A saved copy, not live.",
                Draw.WIDTH / 2.0, 584);
    }

    /**
     * What changes with a pick or with the mouse: the picked mission's words under the
     * helmet, the button and the list. {@code g} is in the 800 x 600 layout.
     */
    private void paintChoice(Graphics2D g) {
        List<Scenario> scenarios = state.scenarios();
        Scenario mission = picked < scenarios.size() ? scenarios.get(picked) : null;
        g.setFont(NAME_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, mission == null ? SURPRISE : mission.title(), CENTRE, NAME_BASELINE);
        if (mission != null) {
            g.setFont(WHEN_FONT);
            g.setColor(Draw.MUTED);
            Draw.centred(g, MONTH.format(mission.start()), CENTRE, WHEN_BASELINE);
        }
        g.setFont(HARD_FONT);
        g.setColor(Glyphs.POWER);
        Draw.centred(g, mission == null ? SURPRISE_HARD : mission.hard(), CENTRE,
                mission == null ? (WHEN_BASELINE + HARD_BASELINE) / 2 : HARD_BASELINE);
        Draw.button(g, pickButton(), PICK, PICK_FONT, Glyphs.POWER, hot == PICK_BUTTON);

        for (int row = 0; row <= scenarios.size(); row++) {
            // The list runs from the gentlest mission to the hardest, so the first is the one to start with.
            paintRow(g, row(row, scenarios.size()), row < scenarios.size() ? scenarios.get(row).title() : SURPRISE,
                    row == picked, row == hot, row == 0);
        }
    }

    /**
     * A few faint stars in the dark of the sky, fainter toward the ground. They are spread
     * well past the 800 x 600 layout, so a panel of any shape has its share. The same every time.
     */
    private static void paintStars(Graphics2D g) {
        Random random = new Random(1969);
        for (int i = 0; i < 150; i++) {
            double x = -700 + random.nextDouble() * 2200;
            double y = -500 + random.nextDouble() * 860;
            double size = 0.9 + random.nextDouble() * 1.5;
            double high = Draw.clamp((360 - y) / 300);
            g.setColor(Draw.alpha(Color.WHITE, (int) Math.round((50 + random.nextInt(120)) * high)));
            g.fill(new Ellipse2D.Double(x - size / 2, y - size / 2, size, size));
        }
    }

    /** Two plain ridges of Mars ground along the bottom, wide enough for a panel of any shape. */
    private static void paintGround(Graphics2D g) {
        ridge(g, FAR_GROUND, -800, 536, 90, 528, 210, 514, 300, 524, 430, 518, 560, 530, 660, 516,
                740, 522, 1600, 534);
        ridge(g, NEAR_GROUND, -800, 554, 150, 549, 340, 556, 560, 547, 800, 552, 1600, 550);
    }

    /** Fills everything below the line through the given x, y points. */
    private static void ridge(Graphics2D g, Color colour, double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], 3 * Draw.HEIGHT);
        for (int i = 0; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.lineTo(xy[xy.length - 2], 3 * Draw.HEIGHT);
        path.closePath();
        g.setColor(colour);
        g.fill(path);
    }

    /**
     * One row of the list. The row that is picked has a yellow edge and a yellow pointer on
     * the side the helmet is; a row under the mouse lights up. The first row carries the
     * green "Start here" tab.
     */
    private static void paintRow(Graphics2D g, Rectangle2D area, String name, boolean picked, boolean hot,
                                 boolean first) {
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                picked ? ROW_PICKED : hot ? Draw.CARD_HOT : Draw.CARD_FILL,
                picked ? Glyphs.POWER : hot ? Color.WHITE : Draw.EDGE);
        if (picked) {
            // A second line inside the edge, so the picked row is easy to see, and a pointer toward the helmet.
            g.setColor(Glyphs.POWER);
            g.draw(new Rectangle2D.Double(area.getX() + 1.5, area.getY() + 1.5,
                    area.getWidth() - 3, area.getHeight() - 3));
            Path2D.Double pointer = new Path2D.Double();
            pointer.moveTo(area.getX() - 11, area.getCenterY());
            pointer.lineTo(area.getX() + 0.5, area.getCenterY() - 10);
            pointer.lineTo(area.getX() + 0.5, area.getCenterY() + 10);
            pointer.closePath();
            g.fill(pointer);
        }
        g.setFont(ROW_FONT);
        g.setColor(picked ? Glyphs.POWER : Color.WHITE);
        g.drawString(name, (float) area.getX() + 18, (float) area.getCenterY() + 6);
        if (first) {
            String mark = "Start here";
            g.setFont(MARK_FONT);
            double width = g.getFontMetrics().stringWidth(mark) + 22;
            double left = area.getMaxX() - width - 12;
            Draw.box(g, left, area.getY() - 10, width, 20, Glyphs.GOOD, Draw.INK);
            g.setColor(Draw.INK);
            Draw.centred(g, mark, left + width / 2, area.getY() + 4.5);
        }
    }
}
