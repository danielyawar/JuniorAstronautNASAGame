package outpost.view;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Outpost;
import outpost.shared.Phase;
import outpost.shared.Place;
import outpost.shared.PlayerActions;
import outpost.shared.RadiationConditions;
import outpost.shared.Readings;
import outpost.shared.Scenario;
import outpost.shared.UvIndex;
import outpost.shared.WeatherConditions;

/**
 * The game's one panel. Before a mission it shows the home screen (HomeScreen). In a mission
 * it shows the angled bird's-eye view of the outpost (Scene) with every control drawn on it:
 * the player clicks a place to send the astronaut there, flips the heater and lamp switches
 * on the tags of their buildings, and presses SLEEP. Each click is passed to PlayerActions.
 * Everything is drawn from the GameState snapshot; painting never changes the game.
 *
 * <p>The screen is kept quiet by giving every fact one home. What the player is looking
 * after (the sol, health, food, energy) is in one strip along the top (StatusStrip). Each
 * building carries a one-line tag with the number that matters now. Everything else about
 * the weather and the radiation is on a card, which opens when its instrument is clicked
 * and stops the clock while it is open. The box at the bottom left names one thing to do.
 *
 * <p>The first time a mission is started, that box gives a short lesson before the clock
 * runs: see {@link Step}.
 *
 * <p>The view keeps a little display state of its own: which card is open, which step of
 * the lesson is showing, what the mouse is over, and where the astronaut is drawn on the
 * way between two places. It is updated when a new state or a mouse event arrives, never
 * while painting.
 */
public final class OutpostView extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color BUBBLE = new Color(24, 28, 44, 222);
    private static final Color SUN_YELLOW = new Color(255, 205, 80);
    private static final Color LEAF_GREEN = new Color(120, 214, 110);
    private static final Color SKY_BLUE = new Color(120, 190, 255);
    private static final Color WARM = new Color(255, 170, 90);
    private static final Color ALERT = new Color(255, 96, 160);
    private static final Color EARTH = new Color(232, 170, 120);
    private static final Color SWITCH_OFF = new Color(92, 96, 116);
    private static final Color SWITCH_DEAD = new Color(132, 44, 52);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final Font GUIDE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font SWITCH_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);
    private static final Font LANDMARK_FONT = new Font(Font.SANS_SERIF, Font.ITALIC, 11);
    private static final Font COMPASS_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    private static final Rectangle2D SLEEP_BUTTON = new Rectangle2D.Double(648, 536, 140, 38);
    private static final Rectangle2D HOME_BUTTON = new Rectangle2D.Double(546, 580, 84, 16);
    private static final Rectangle2D ABOUT_BUTTON = new Rectangle2D.Double(636, 580, 86, 16);
    private static final Rectangle2D RESTART_BUTTON = new Rectangle2D.Double(728, 580, 60, 16);
    /** "How to play", just above the guide. The lesson's own buttons take its place while the lesson runs. */
    private static final Rectangle2D HOW_BUTTON = new Rectangle2D.Double(12, 520, 76, 16);

    /** Things on screen that can be clicked or pointed at. */
    private enum Spot {
        NONE, HABITAT, GREENHOUSE, PANELS, DIGGER, WEATHER, DETECTOR, HEATER, LAMPS, SLEEP, START,
        RESTART, ABOUT, HOME, PLAY_AGAIN, CARD, ENERGY, SCENARIO, SURPRISE, HOW, NEXT, SKIP
    }

    /** What is under the mouse. {@code scenario} is set for a SCENARIO card on the home screen. */
    private record Hit(Spot spot, Scenario scenario) {
        Hit(Spot spot) {
            this(spot, null);
        }
    }

    private static final Hit NOTHING = new Hit(Spot.NONE);

    /** A short reading: an icon, or none if {@code icon} is null, and then text. */
    private record Stat(StatusStrip.Icon icon, Color iconColour, String text, Color textColour) {}

    /** A switch on a tag: its name, whether it is on, and whether what it switches has energy to run. */
    private record Switch(Spot spot, String name, boolean on, boolean working) {}

    /**
     * The one-line tag over a building: its name, a number or two, and for the habitat and
     * the greenhouse a switch. {@code switchArea} is where a click flips the switch and
     * {@code pill} is the switch itself; both are null on a tag without one.
     */
    private record Chip(Spot spot, Rectangle2D box, double anchorX, boolean below, Color accent, String title,
                        List<Stat> stats, Switch toggle, Rectangle2D switchArea, Rectangle2D pill) {}

    /**
     * What the guide tells the player, and the thing on screen it is about. On a tag with a
     * switch the ring goes round the building's name alone, unless {@code wholeTag} asks for
     * the name and the switch together.
     */
    private record Hint(String text, Spot target, boolean wholeTag) {
        Hint(String text, Spot target) {
            this(text, target, false);
        }
    }

    /** One line of a card: a name on the left and its value on the right. */
    private record Fact(String name, String value) {}

    /**
     * The box that opens when an instrument is clicked. A card holds readings ({@code facts})
     * and sentences ({@code notes}, already split into lines). {@code pointed} cards hang
     * over {@code anchorX}; the others stand alone.
     */
    private record Card(Rectangle2D box, double anchorX, boolean pointed, String title, Color accent,
                        List<Fact> facts, List<String> notes) {}

    private final transient Scene scene = new Scene();
    private final transient PlayerActions actions;
    private transient GameState state;
    /** Whose card is open: WEATHER, DETECTOR, ABOUT or NONE. The clock stands still while one is. */
    private Spot selected = Spot.NONE;
    /** The step of the lesson that is showing, or NONE. The clock stands still while one is. */
    private Step lesson = Step.NONE;
    /** True once the lesson has started since the game opened. It is not given twice unless asked for. */
    private boolean taught;
    private transient Hit hover = NOTHING;

    // Where the astronaut is drawn. Follows the state: see follow().
    private double figureU;
    private double figureV;
    private Place figureGoal;
    private double figureWalkLeft;
    private boolean figureKnown;

    // For whoever is tuning the drawing: see showPaintTime().
    private boolean paintTimeShown;
    private final long[] paintNanos = new long[30];
    private final long[] paintStarted = new long[30];
    private int paintCount;

    /** @param actions receives what the player clicks */
    public OutpostView(PlayerActions actions) {
        this.actions = actions;
        setPreferredSize(new Dimension(Draw.WIDTH, Draw.HEIGHT));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                click(hitAt(e));
                point(hitAt(e)); // the click may have changed what is under the mouse
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                point(hitAt(e));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                point(NOTHING);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** Called by the controller wiring whenever the state changes. */
    public void show(GameState newState) {
        state = newState;
        if (newState.phase() != Phase.DAY) {
            selected = Spot.NONE; // cards and the lesson belong to the day; home and restart close them
            lesson = Step.NONE;
        }
        follow(newState);
        repaint();
    }

    /**
     * Shows in a corner how long painting takes, averaged over the last 30 repaints. For
     * checking that the game stays quick, above all in the browser. Off unless asked for.
     */
    public void showPaintTime(boolean shown) {
        paintTimeShown = shown;
        repaint();
    }

    /**
     * Moves the drawn astronaut to match a new state. The rules say only where the astronaut
     * is going and how many hours of walking are left, so the view walks the figure in a
     * straight line from wherever it is, to arrive as the walking runs out.
     */
    private void follow(GameState s) {
        Outpost o = s.outpost();
        if (o == null) {
            figureKnown = false;
            return;
        }
        double[] goal = scene.spot(o.place());
        boolean sameWalk = figureKnown && o.place() == figureGoal && figureWalkLeft > 0
                && o.walkLeft() <= figureWalkLeft;
        if (!figureKnown || !o.walking() || s.phase() == Phase.READY || s.phase() == Phase.DAWN) {
            figureU = goal[0];
            figureV = goal[1];
        } else if (sameWalk) {
            double left = o.walkLeft() / figureWalkLeft;
            figureU = goal[0] + (figureU - goal[0]) * left;
            figureV = goal[1] + (figureV - goal[1]) * left;
        } // otherwise a new walk has begun: it starts from where the figure stands
        figureGoal = o.place();
        figureWalkLeft = o.walkLeft();
        figureKnown = true;
    }

    /** True while the astronaut is out of doors or in the greenhouse, where the figure is drawn. */
    private boolean figureShown() {
        Outpost o = state.outpost();
        return state.phase() != Phase.NIGHT && !o.at(Place.HABITAT);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (state == null) {
            return;
        }
        long started = System.nanoTime();
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Letters keep their true widths at any size of window, so text laid out at
            // 800 x 600 still fits its tag when the whole screen is drawn smaller or larger.
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            Phase phase = state.phase();
            if (phase == Phase.BRIEFING) {
                HomeScreen.paint(g, state, getWidth(), getHeight(), hover.scenario(), hover.spot() == Spot.SURPRISE);
            } else {
                scene.paintBuilt(g, getWidth(), getHeight(), state);
                Draw.fit(g, getWidth(), getHeight());
                Outpost o = state.outpost();
                double stride = o.walking() ? 0.05 * Math.abs(Math.sin(o.walkLeft() * 50)) : 0;
                scene.paintLive(g, state, figureShown() ? new double[] {figureU, figureV} : null, stride);

                Hint hint = hint();
                // The tags belong to a sol in play. Under a box in the middle of the screen
                // they would only show as cut-off pieces round its edge.
                if (phase == Phase.DAY || phase == Phase.NIGHT) {
                    for (Chip chip : chips()) {
                        paintChip(g, chip, hint);
                    }
                }
                paintDoing(g);
                paintHud(g, hint);
                paintCard(g);
                if (phase == Phase.READY) {
                    Notices.paintStart(g, state, this, hover.spot() == Spot.START);
                } else if (phase == Phase.DAWN) {
                    Notices.paintNight(g, state, hover.spot() == Spot.START);
                } else if (phase == Phase.WON || phase == Phase.LOST) {
                    Notices.paintEnding(g, state, hover.spot() == Spot.PLAY_AGAIN);
                }
                if (phase == Phase.READY || phase == Phase.DAWN) {
                    quietButton(g, HOME_BUTTON, "Change mission", Spot.HOME); // still works under the box
                }
            }
            if (paintTimeShown) {
                paintPaintTime(g, started);
            }
        } finally {
            g.dispose();
        }
    }

    /** Records how long this repaint took and draws the average of the last few. */
    private void paintPaintTime(Graphics2D g, long started) {
        if (state.phase() == Phase.BRIEFING) {
            Draw.fit(g, getWidth(), getHeight());
        }
        int counted = Math.min(paintCount, paintNanos.length);
        if (counted > 0) {
            long sum = 0;
            for (int i = 0; i < counted; i++) {
                sum += paintNanos[i];
            }
            // The repaints a second are counted over the same repaints, so they only mean
            // something while the clock is running and the game repaints at every tick.
            long oldest = paintStarted[counted < paintStarted.length ? 0 : paintCount % paintStarted.length];
            double seconds = (started - oldest) / 1e9;
            String text = String.format(Locale.ENGLISH, "paint %.1f ms, %.0f repaints a second",
                    sum / (counted * 1e6), seconds > 0 ? counted / seconds : 0);
            g.setFont(Draw.BODY_FONT);
            Draw.box(g, 12, 64, g.getFontMetrics().stringWidth(text) + 12, 18, BUBBLE, null);
            g.setColor(Color.WHITE);
            g.drawString(text, 18, 77);
        }
        paintNanos[paintCount % paintNanos.length] = System.nanoTime() - started;
        paintStarted[paintCount % paintStarted.length] = started;
        paintCount++;
    }

    // ---- Small drawing helpers ------------------------------------------------------

    private static Stat text(String text) {
        return new Stat(null, null, text, Color.WHITE);
    }

    private static Stat text(String text, Color colour) {
        return new Stat(null, null, text, colour);
    }

    /** A small button for things that are not part of playing a sol. */
    private void quietButton(Graphics2D g, Rectangle2D area, String text, Spot spot) {
        boolean hot = hover.spot() == spot;
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                hot ? new Color(255, 255, 255, 46) : new Color(24, 28, 44, 110), hot ? Color.WHITE : Draw.EDGE);
        g.setFont(Draw.SMALL_FONT);
        g.setColor(hot ? Color.WHITE : Draw.CREAM);
        Draw.centred(g, text, area.getCenterX(), area.getCenterY() + 3.5);
    }

    /** A whole number of degrees, never "-0". */
    private static String degrees(double celsius) {
        return Math.round(celsius) + "°C";
    }

    // ---- Building tags --------------------------------------------------------------

    private static final double CHIP_HEIGHT = 22;
    private static final double CHIP_PAD = 8;
    private static final double CHIP_GAP = 9;
    private static final double CHIP_ICON = 11;
    private static final double PILL_WIDTH = 38;
    private static final double PILL_HEIGHT = 14;
    /**
     * The detector stands just left of the habitat, so its tag and its card hang off to the
     * left: they reach no further right of it than this, and so stay clear of the habitat's.
     */
    private static final double DETECTOR_REACH = 18;

    /**
     * The tags to show now, one per building. Used to paint and to click. Each holds only
     * what matters for the next choice; the rest is on a card. A tag that an open card would
     * partly cover is left out, as is the tag of the card's own instrument.
     */
    private List<Chip> chips() {
        Outpost o = state.outpost();
        Readings now = state.now();
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        boolean dusty = weather.sunlight() < 0.5;
        boolean storm = radiation.solarEvent();

        List<Chip> all = new ArrayList<>();
        List<Stat> sky = new ArrayList<>();
        sky.add(text(degrees(now.outsideC())));
        if (!state.sol().earthDate().equals(weather.measuredOn())) {
            sky.add(text("no reading today", Draw.MUTED)); // the rover has nothing for this date
        }
        all.add(chip(Spot.WEATHER, scene.weatherAnchor, Double.POSITIVE_INFINITY, dusty ? Glyphs.WARN : SKY_BLUE,
                dusty ? "Dusty sky" : "Weather", null, sky));
        all.add(chip(Spot.DETECTOR, scene.detectorAnchor, DETECTOR_REACH, storm ? ALERT : Color.WHITE,
                storm ? "Solar storm!" : "Radiation", null,
                List.of(text(String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv())))));
        all.add(chip(Spot.HABITAT, scene.habitatAnchor, Double.POSITIVE_INFINITY, WARM, "Habitat",
                new Switch(Spot.HEATER, "Heater", o.heaterOn(), now.heaterWorking()), List.of()));
        all.add(chip(Spot.GREENHOUSE, scene.greenhouseAnchor, Double.POSITIVE_INFINITY, LEAF_GREEN, "Greenhouse",
                new Switch(Spot.LAMPS, "Lamps", o.lampsOn(), now.lampsWorking()),
                List.of(new Stat(Glyphs::leaf, Glyphs.GREENHOUSE,
                        String.format(Locale.ENGLISH, "%.1f ready", o.cropsReady()), Color.WHITE))));
        all.add(chip(Spot.PANELS, scene.solarAnchor, Double.POSITIVE_INFINITY, SUN_YELLOW, "Solar panels", null,
                List.of(text(Math.round(o.panelsClean() * 100) + "% clean",
                        o.panelsClean() < DUSTY_PANELS ? Glyphs.WARN : Color.WHITE))));
        all.add(chip(Spot.DIGGER, scene.diggerAnchor, Double.POSITIVE_INFINITY, EARTH, "Digger", null,
                List.of(new Stat(Glyphs::shield, Glyphs.SHIELD,
                        "Shield " + Math.round(o.shield() * 100) + "%", Color.WHITE))));

        Card card = card();
        if (card == null) {
            return all;
        }
        List<Chip> clear = new ArrayList<>();
        for (Chip chip : all) {
            if (chip.spot() != selected && !card.box().intersects(chip.box())) {
                clear.add(chip);
            }
        }
        return clear;
    }

    /** A tag centred over {@code anchor}, or as near to that as {@code reach} to its right allows. */
    private Chip chip(Spot spot, double[] anchor, double reach, Color accent, String title, Switch toggle,
                      List<Stat> stats) {
        FontMetrics body = getFontMetrics(Draw.BODY_FONT);
        double width = 2 * CHIP_PAD + getFontMetrics(Draw.TITLE_FONT).stringWidth(title);
        for (Stat stat : stats) {
            width += CHIP_GAP + (stat.icon() == null ? 0 : CHIP_ICON + 3) + body.stringWidth(stat.text());
        }
        double switchWidth = toggle == null ? 0 : body.stringWidth(toggle.name()) + 5 + PILL_WIDTH;
        width = Math.ceil(width + (toggle == null ? 0 : CHIP_GAP + switchWidth));
        double left = Math.round(Math.min(anchor[0] - width / 2, anchor[0] + reach - width));
        // Every tag sits above its building except the digger's, which hangs below it.
        boolean below = spot == Spot.DIGGER;
        double top = Math.round(below ? anchor[1] + 7 : anchor[1] - 7 - CHIP_HEIGHT);
        Rectangle2D switchArea = null;
        Rectangle2D pill = null;
        if (toggle != null) {
            double right = left + width - CHIP_PAD;
            pill = new Rectangle2D.Double(right - PILL_WIDTH, top + (CHIP_HEIGHT - PILL_HEIGHT) / 2,
                    PILL_WIDTH, PILL_HEIGHT);
            switchArea = new Rectangle2D.Double(right - switchWidth - 4, top, switchWidth + 4 + CHIP_PAD, CHIP_HEIGHT);
        }
        return new Chip(spot, new Rectangle2D.Double(left, top, width, CHIP_HEIGHT), anchor[0], below, accent,
                title, stats, toggle, switchArea, pill);
    }

    /** Whatever {@code hint} is pointing the player at is ringed in the guide's yellow. */
    private void paintChip(Graphics2D g, Chip chip, Hint hint) {
        Spot hinted = hint.target();
        Rectangle2D area = chip.box();
        // The edge is in the tag's own colour, and white under the mouse. Yellow is kept for the guide.
        boolean hot = hover.spot() == chip.spot();
        if (chip.below()) {
            Draw.pointerUp(g, chip.anchorX(), area.getY(), BUBBLE);
        } else {
            Draw.pointer(g, chip.anchorX(), area.getMaxY(), BUBBLE);
        }
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), BUBBLE,
                hot ? Color.WHITE : Draw.alpha(chip.accent(), 150));
        if (hot) {
            g.draw(new Rectangle2D.Double(area.getX() + 1.5, area.getY() + 1.5, area.getWidth() - 3, area.getHeight() - 3));
        }
        if (chip.spot() == hinted) {
            // On a tag with a switch, ring only the building's name unless the switch is meant too.
            ring(g, chip.switchArea() == null || hint.wholeTag() ? area : new Rectangle2D.Double(area.getX(),
                    area.getY(), chip.switchArea().getX() - area.getX() - 2, area.getHeight()));
        }

        double x = area.getX() + CHIP_PAD;
        float baseline = (float) area.getY() + 15;
        g.setFont(Draw.TITLE_FONT);
        g.setColor(chip.accent());
        g.drawString(chip.title(), (float) x, baseline);
        x += g.getFontMetrics().stringWidth(chip.title());
        g.setFont(Draw.BODY_FONT);
        for (Stat stat : chip.stats()) {
            x += CHIP_GAP;
            if (stat.icon() != null) {
                stat.icon().draw(g, x, area.getY() + 5.5, CHIP_ICON, stat.iconColour());
                x += CHIP_ICON + 3;
            }
            g.setColor(stat.textColour());
            g.drawString(stat.text(), (float) x, baseline);
            x += g.getFontMetrics().stringWidth(stat.text());
        }
        if (chip.toggle() != null) {
            paintSwitch(g, chip, baseline);
            if (chip.toggle().spot() == hinted) {
                ring(g, chip.switchArea());
            }
        }
    }

    /** The yellow ring round whatever the guide is talking about. */
    private static void ring(Graphics2D g, Rectangle2D area) {
        g.setStroke(Draw.THICK);
        g.setColor(Glyphs.POWER);
        g.draw(new Rectangle2D.Double(area.getX() - 1, area.getY() - 1, area.getWidth() + 2, area.getHeight() + 2));
    }

    /**
     * A switch: its name and a pill with a knob. Green with the knob to the right is on;
     * grey with the knob to the left is off; dark red is on but with no energy to run on.
     */
    private void paintSwitch(Graphics2D g, Chip chip, float baseline) {
        Switch toggle = chip.toggle();
        Rectangle2D pill = chip.pill();
        boolean hot = hover.spot() == toggle.spot();
        boolean dead = toggle.on() && !toggle.working();
        g.setFont(Draw.BODY_FONT);
        g.setColor(dead ? Glyphs.BAD : Color.WHITE);
        g.drawString(toggle.name(), (float) chip.switchArea().getX() + 4, baseline);

        RoundRectangle2D shape = new RoundRectangle2D.Double(pill.getX(), pill.getY(), pill.getWidth(),
                pill.getHeight(), pill.getHeight(), pill.getHeight());
        g.setColor(!toggle.on() ? SWITCH_OFF : dead ? SWITCH_DEAD : Glyphs.GOOD);
        g.fill(shape);
        g.setStroke(Draw.HAIRLINE);
        g.setColor(hot ? Color.WHITE : dead ? Glyphs.BAD : new Color(12, 14, 24));
        g.draw(shape);
        double knob = pill.getHeight() - 4;
        double knobX = toggle.on() ? pill.getMaxX() - 2 - knob : pill.getX() + 2;
        g.setColor(toggle.on() ? Color.WHITE : new Color(206, 210, 222));
        g.fill(new Ellipse2D.Double(knobX, pill.getY() + 2, knob, knob));
        g.setFont(SWITCH_FONT);
        if (toggle.on()) {
            g.setColor(dead ? Draw.CREAM : Draw.INK);
            g.drawString("ON", (float) pill.getX() + 7, (float) pill.getY() + 10.5f);
        } else {
            g.setColor(Draw.CREAM);
            g.drawString("OFF", (float) pill.getX() + 16, (float) pill.getY() + 10.5f);
        }
    }

    // ---- The astronaut's label ------------------------------------------------------

    /** A small label over the astronaut, or at the airlock when they are indoors, saying what they are doing. */
    private void paintDoing(Graphics2D g) {
        Outpost o = state.outpost();
        Readings now = state.now();
        Phase phase = state.phase();
        if (phase != Phase.DAY && phase != Phase.NIGHT) {
            return;
        }
        String doing;
        Color colour = Color.WHITE;
        double[] at;
        if (phase == Phase.NIGHT) {
            doing = "Sleeping";
            at = scene.doorTop();
        } else if (o.walking()) {
            doing = "Walking";
            at = scene.headAt(figureU, figureV);
        } else if (o.place() == Place.HABITAT) {
            doing = "Resting";
            at = scene.doorTop();
        } else {
            at = scene.headAt(figureU, figureV);
            switch (o.place()) {
                case PANELS:
                    doing = o.panelsClean() >= 0.995 ? "All clean!" : "Brushing";
                    break;
                case DIGGER:
                    if (o.shield() >= 1) {
                        doing = "Shield finished";
                    } else if (now.diggerWorking()) {
                        doing = "Digging";
                    } else {
                        doing = "Digger has no energy";
                        colour = Glyphs.BAD;
                    }
                    break;
                default:
                    doing = o.cropsReady() > 0.02 ? "Picking food" : "Nothing to pick yet";
                    break;
            }
        }
        g.setFont(Draw.SMALL_FONT);
        double width = g.getFontMetrics().stringWidth(doing) + 12;
        double left = Math.round(at[0] - width / 2);
        double top = Math.round(at[1] - 5 - 16);
        Draw.box(g, left, top, width, 16, BUBBLE, Draw.alpha(colour, 120));
        g.setColor(colour);
        g.drawString(doing, (float) left + 6, (float) top + 12);
    }

    // ---- Cards ----------------------------------------------------------------------

    private static final double CARD_WIDTH = 262;
    private static final double CARD_HEAD = 26;
    private static final double FACT_ROW = 16;
    private static final double NOTE_ROW = 14;

    /** The card for whatever is selected, or null when none is open. Used to paint and to click. */
    private Card card() {
        if (state == null || state.phase() != Phase.DAY) {
            return null;
        }
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        Readings now = state.now();
        String title;
        Color accent;
        double[] anchor = null;
        double reach = Double.POSITIVE_INFINITY;
        List<Fact> facts = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        switch (selected) {
            case WEATHER:
                title = "Weather station";
                accent = SKY_BLUE;
                anchor = scene.weatherAnchor;
                facts.add(new Fact("Sky", weather.sunlight() < 0.5 ? "Dusty" : "Clear"));
                facts.add(new Fact("Sunlight getting through",
                        String.format(Locale.ENGLISH, "%.0f%%", weather.sunlight() * 100)));
                facts.add(new Fact("Coldest and warmest today",
                        degrees(weather.minTempC()) + " and " + degrees(weather.maxTempC())));
                if (!Double.isNaN(weather.pressurePa())) {
                    facts.add(new Fact("Air pressure", String.format(Locale.ENGLISH, "%.0f Pa", weather.pressurePa())));
                }
                if (weather.sunrise() != null && weather.sunset() != null) {
                    facts.add(new Fact("Sunrise and sunset", weather.sunrise() + " and " + weather.sunset()));
                }
                if (weather.uv() != UvIndex.UNKNOWN) {
                    facts.add(new Fact("UV light", weather.uv().label()));
                }
                if (now.sunStrength() > 1.07) {
                    sentences.add("Mars is close to the Sun now, so sunlight is stronger.");
                } else if (now.sunStrength() < 0.93) {
                    sentences.add("Mars is far from the Sun now, so sunlight is weaker.");
                }
                if (weather.measuredOn() == null) {
                    sentences.add("Weather is not a rover reading.");
                } else if (weather.measuredOn().equals(state.sol().earthDate())) {
                    sentences.add("Measured by Curiosity on its sol " + weather.missionSol() + ".");
                } else {
                    sentences.add("No rover reading today. Showing " + DATE.format(weather.measuredOn()) + ".");
                }
                break;
            case DETECTOR:
                title = radiation.solarEvent() ? "Solar storm!" : "Radiation detector";
                accent = radiation.solarEvent() ? ALERT : Color.WHITE;
                anchor = scene.detectorAnchor;
                reach = DETECTOR_REACH;
                facts.add(new Fact("Radiation today", String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv())));
                sentences.add(radiation.solarEvent() ? "A solar storm has raised it today."
                        : "No solar storm today.");
                sentences.add(String.format(Locale.ENGLISH,
                        "The soil bank stops %.0f%% while your astronaut is in the habitat.",
                        now.shieldBlocks() * 100));
                sentences.add("Outside and in the greenhouse nothing stops it.");
                break;
            case ABOUT:
                title = "About the data";
                accent = Color.WHITE;
                sentences.add("Real NASA data, saved copy (not live).");
                sentences.add("Weather: Curiosity rover, NASA/JPL-Caltech/CAB.");
                sentences.add("Solar storms: NASA CCMC DONKI.");
                sentences.add("Ground: real heights at Curiosity's landing site in Gale Crater, NASA MOLA.");
                sentences.add(String.format(Locale.ENGLISH,
                        "One tile is about %.1f km. Hills drawn %dx taller. Buildings not to scale.",
                        scene.tileKm(), Scene.HEIGHT_EXAGGERATION));
                break;
            default:
                return null;
        }

        List<String> notes = new ArrayList<>();
        for (String sentence : sentences) {
            notes.addAll(Draw.wrap(sentence, getFontMetrics(Draw.BODY_FONT), CARD_WIDTH - 24));
        }
        double height = CARD_HEAD + FACT_ROW * facts.size() + NOTE_ROW * notes.size()
                + (facts.isEmpty() || notes.isEmpty() ? 0 : 4) + 8;
        double left;
        double top;
        if (anchor == null) {
            left = Math.round((Draw.WIDTH - CARD_WIDTH) / 2);
            top = Math.round((Draw.HEIGHT - height) / 2);
        } else {
            left = Math.min(anchor[0] - CARD_WIDTH / 2, anchor[0] + reach - CARD_WIDTH);
            left = Math.round(Math.max(6, Math.min(Draw.WIDTH - 6 - CARD_WIDTH, left)));
            top = Math.round(Math.max(StatusStrip.AREA.getMaxY() + 6, anchor[1] - 7 - height));
        }
        // No pointer if the card had to be pushed down over its own instrument.
        boolean pointed = anchor != null && top + height <= anchor[1] - 6;
        return new Card(new Rectangle2D.Double(left, top, CARD_WIDTH, height), anchor == null ? 0 : anchor[0],
                pointed, title, accent, facts, notes);
    }

    private void paintCard(Graphics2D g) {
        Card card = card();
        if (card == null) {
            return;
        }
        Rectangle2D area = card.box();
        if (card.pointed()) {
            Draw.pointer(g, card.anchorX(), area.getMaxY(), Draw.CARD_FILL);
        }
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), Draw.CARD_FILL, card.accent());
        g.setFont(Draw.TITLE_FONT);
        g.setColor(card.accent());
        g.drawString(card.title(), (float) area.getX() + 10, (float) area.getY() + 17);
        g.setFont(Draw.BODY_FONT);
        g.setColor(Draw.MUTED);
        Draw.rightAligned(g, "clock stopped", area.getMaxX() - 10, area.getY() + 17);

        double y = area.getY() + CARD_HEAD;
        for (Fact fact : card.facts()) {
            g.setFont(Draw.BODY_FONT);
            g.setColor(Draw.MUTED);
            g.drawString(fact.name(), (float) area.getX() + 10, (float) y + 11);
            g.setFont(Draw.TITLE_FONT);
            g.setColor(Color.WHITE);
            Draw.rightAligned(g, fact.value(), area.getMaxX() - 10, y + 11);
            y += FACT_ROW;
        }
        if (!card.facts().isEmpty() && !card.notes().isEmpty()) {
            y += 4;
        }
        g.setFont(Draw.BODY_FONT);
        g.setColor(Draw.MUTED);
        for (String line : card.notes()) {
            g.drawString(line, (float) area.getX() + 10, (float) y + 10);
            y += NOTE_ROW;
        }
    }

    // ---- Mouse ----------------------------------------------------------------------

    /** What is under the mouse, in the 800 x 600 layout the scene is drawn in. */
    private Hit hitAt(MouseEvent e) {
        if (state == null) {
            return NOTHING;
        }
        double scale = Draw.scale(getWidth(), getHeight());
        double x = (e.getX() - (getWidth() - Draw.WIDTH * scale) / 2) / scale;
        double y = (e.getY() - (getHeight() - Draw.HEIGHT * scale) / 2) / scale;
        switch (state.phase()) {
            case BRIEFING:
                Scenario scenario = HomeScreen.scenarioAt(state, x, y);
                return scenario != null ? new Hit(Spot.SCENARIO, scenario)
                        : HomeScreen.surpriseAt(state, x, y) ? new Hit(Spot.SURPRISE) : NOTHING;
            case WON:
            case LOST:
                return Notices.PLAY_AGAIN_BUTTON.contains(x, y) ? new Hit(Spot.PLAY_AGAIN) : NOTHING;
            case READY:
                return Notices.START_BUTTON.contains(x, y) ? new Hit(Spot.START)
                        : HOME_BUTTON.contains(x, y) ? new Hit(Spot.HOME) : NOTHING;
            case DAWN:
                return Notices.nextSolButton(state).contains(x, y) ? new Hit(Spot.START)
                        : HOME_BUTTON.contains(x, y) ? new Hit(Spot.HOME) : NOTHING;
            case NIGHT:
                return HOME_BUTTON.contains(x, y) ? new Hit(Spot.HOME)
                        : RESTART_BUTTON.contains(x, y) ? new Hit(Spot.RESTART) : NOTHING;
            default:
                return new Hit(lesson == Step.NONE ? spotByDay(x, y) : spotInLesson(x, y));
        }
    }

    /** What is at (x, y) while a sol is being played. */
    private Spot spotByDay(double x, double y) {
        Card card = card();
        if (card != null && card.box().contains(x, y)) {
            return Spot.CARD;
        }
        if (SLEEP_BUTTON.contains(x, y)) {
            return Spot.SLEEP;
        }
        if (RESTART_BUTTON.contains(x, y)) {
            return Spot.RESTART;
        }
        if (HOME_BUTTON.contains(x, y)) {
            return Spot.HOME;
        }
        if (ABOUT_BUTTON.contains(x, y)) {
            return Spot.ABOUT;
        }
        if (HOW_BUTTON.contains(x, y)) {
            return Spot.HOW;
        }
        if (StatusStrip.ENERGY.contains(x, y)) {
            return Spot.ENERGY;
        }
        for (Chip chip : chips()) {
            if (chip.switchArea() != null && chip.switchArea().contains(x, y)) {
                return chip.toggle().spot();
            }
            if (chip.box().contains(x, y)) {
                return chip.spot();
            }
        }
        // The buildings themselves. The small ones come first so a big neighbour cannot hide them.
        if (scene.weatherArea.contains(x, y)) {
            return Spot.WEATHER;
        }
        if (scene.detectorArea.contains(x, y)) {
            return Spot.DETECTOR;
        }
        if (scene.diggerArea.contains(x, y)) {
            return Spot.DIGGER;
        }
        if (scene.habitatArea.contains(x, y)) {
            return Spot.HABITAT;
        }
        if (scene.greenhouseArea.contains(x, y)) {
            return Spot.GREENHOUSE;
        }
        if (scene.solarArea.contains(x, y)) {
            return Spot.PANELS;
        }
        return Spot.NONE;
    }

    private void click(Hit hit) {
        // A click on a card's instrument opens the card, a click on an open card does nothing,
        // and a click anywhere else closes the card and then does what it would do anyway.
        boolean wasOpen = selected != Spot.NONE;
        switch (hit.spot()) {
            case WEATHER:
            case DETECTOR:
            case ABOUT:
                selected = hit.spot();
                break;
            case CARD:
                break;
            default:
                selected = Spot.NONE;
                break;
        }
        boolean open = selected != Spot.NONE;
        if (open != wasOpen) {
            actions.pause(open); // time stands still while the player reads
        }
        switch (hit.spot()) {
            case HABITAT:
            case GREENHOUSE:
            case PANELS:
            case DIGGER:
                actions.send(place(hit.spot()));
                break;
            case HEATER:
                boolean switchingOn = !state.outpost().heaterOn();
                actions.switchHeater(switchingOn);
                if (lesson == Step.HEATER && switchingOn) {
                    lesson = Step.BATTERY; // the player has tried the switch and left it on
                }
                break;
            case LAMPS:
                actions.switchLamps(!state.outpost().lampsOn());
                break;
            case SLEEP:
                actions.sleep();
                break;
            case START:
                boolean firstMission = state.phase() == Phase.READY && !taught;
                actions.startSol();
                if (firstMission) {
                    teach();
                }
                break;
            case HOW:
                teach();
                break;
            case NEXT:
                lesson = lesson.next();
                break;
            case SKIP:
                if (lesson == Step.HEATER && !state.outpost().heaterOn()) {
                    actions.switchHeater(true); // the lesson asked for it off; it never leaves it so
                }
                endLesson();
                break;
            case RESTART:
                actions.restart();
                break;
            case HOME:
            case PLAY_AGAIN:
                actions.home();
                break;
            case SCENARIO:
                actions.start(hit.scenario());
                break;
            case SURPRISE:
                actions.startSurprise();
                break;
            default:
                break;
        }
        if (lesson == Step.SEND && place(hit.spot()) != null) {
            endLesson(); // the last step: the clock starts and the astronaut is seen to set off
        }
        repaint();
    }

    /** The place a click on {@code spot} sends the astronaut to, or null if it sends nobody. */
    private static Place place(Spot spot) {
        switch (spot) {
            case HABITAT:
                return Place.HABITAT;
            case GREENHOUSE:
                return Place.GREENHOUSE;
            case PANELS:
                return Place.PANELS;
            case DIGGER:
                return Place.DIGGER;
            default:
                return null;
        }
    }

    private void point(Hit hit) {
        if (!hit.equals(hover)) {
            hover = hit;
            boolean hand = hit.spot() != Spot.NONE && hit.spot() != Spot.CARD && hit.spot() != Spot.ENERGY;
            setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }
    }

    // ---- The lesson -----------------------------------------------------------------

    /**
     * The steps of the lesson, in the order they are taught; NONE when no lesson is running.
     * The lesson is given the first time a mission is started after the game opens, and
     * again whenever "How to play" is pressed. Each step is one sentence in the guide, with
     * the guide's yellow ring round the one thing the step is about.
     *
     * <p>The clock stands still for the whole lesson, in the same way as for an open card:
     * the view asks for a pause when the lesson starts and lifts it when the lesson ends.
     * So no time passes and no health is lost, however long the player takes. While the
     * lesson runs, a click does something only on the lesson's own buttons, on the one
     * thing the step asks the player to click, and on Change mission and Restart.
     *
     * <p>Sending the astronaut is the last step, because walking takes game time and would
     * show nothing while the clock is stopped. That click ends the lesson, so the clock
     * starts and the astronaut is seen to set off at once.
     */
    private enum Step {
        NONE, HEATER, BATTERY, FOOD, SLEEP, SEND;

        /** The steps a lesson has. */
        static final int COUNT = values().length - 1;

        /** True for a step with nothing to try: it has a NEXT button. The others wait for the player to do them. */
        boolean told() {
            return this == BATTERY || this == FOOD || this == SLEEP;
        }

        Step next() {
            return this == SEND ? NONE : values()[ordinal() + 1];
        }
    }

    private static final double LESSON_TOP = 510;

    /** Starts the lesson at its first step and stops the clock. */
    private void teach() {
        taught = true;
        lesson = Step.HEATER;
        actions.pause(true);
    }

    /** Ends the lesson and lets the clock run. */
    private void endLesson() {
        lesson = Step.NONE;
        actions.pause(false);
    }

    /** The lesson's sentence for the step that is showing, and the thing to ring. */
    private Hint lessonHint() {
        switch (lesson) {
            case HEATER:
                return new Hint(state.outpost().heaterOn()
                        ? "Try the heater switch: click it off, then on again."
                        : "Brr! Click it back on to keep your astronaut warm.", Spot.HEATER);
            case BATTERY:
                return new Hint("The Sun fills the battery, and the moon mark shows what the night will need.",
                        Spot.ENERGY);
            case FOOD:
                return new Hint("The lamps grow food, and a click on the greenhouse picks it.",
                        Spot.GREENHOUSE, true);
            case SLEEP:
                return new Hint("When the jobs are done, press SLEEP to skip to the night.", Spot.SLEEP);
            default:
                return new Hint("Click a place to send your astronaut to work there: try the digger!",
                        Spot.DIGGER);
        }
    }

    private String lessonLabel() {
        return "Step " + lesson.ordinal() + " of " + Step.COUNT;
    }

    /** Skip, in the row above the guide. It is in the same place at every step. */
    private Rectangle2D skipButton() {
        double left = 22 + getFontMetrics(Draw.TITLE_FONT).stringWidth(lessonLabel()) + 10;
        return new Rectangle2D.Double(Math.round(left), LESSON_TOP + 5, 40, 16);
    }

    /** NEXT, beside Skip. Shown on the steps that have nothing to try. */
    private Rectangle2D nextButton() {
        return new Rectangle2D.Double(skipButton().getMaxX() + 8, LESSON_TOP + 2, 64, 22);
    }

    /** The row above the guide while the lesson runs: which step this is, Skip, and NEXT where there is nothing to try. */
    private void paintLesson(Graphics2D g) {
        Rectangle2D skip = skipButton();
        Rectangle2D next = nextButton();
        double right = (lesson.told() ? next.getMaxX() : skip.getMaxX()) + 6;
        Draw.box(g, 12, LESSON_TOP, right - 12, 26, BUBBLE, Glyphs.POWER);
        g.setFont(Draw.TITLE_FONT);
        g.setColor(Color.WHITE);
        g.drawString(lessonLabel(), 22f, (float) LESSON_TOP + 17);
        quietButton(g, skip, "Skip", Spot.SKIP);
        if (lesson.told()) {
            Draw.button(g, next, "NEXT", Draw.BUTTON_FONT, Glyphs.POWER, hover.spot() == Spot.NEXT);
        }
    }

    /** What is at (x, y) while the lesson runs: only what the step lets the player click. */
    private Spot spotInLesson(double x, double y) {
        if (skipButton().contains(x, y)) {
            return Spot.SKIP;
        }
        if (lesson.told() && nextButton().contains(x, y)) {
            return Spot.NEXT;
        }
        Spot spot = spotByDay(x, y);
        switch (spot) {
            case HOME:
            case RESTART:
                return spot;
            case HEATER:
                return lesson == Step.HEATER ? spot : Spot.NONE;
            case HABITAT:
            case GREENHOUSE:
            case PANELS:
            case DIGGER:
                return lesson == Step.SEND ? spot : Spot.NONE;
            default:
                return Spot.NONE;
        }
    }

    // ---- The guide ------------------------------------------------------------------

    /** Below this share of clean, the panels are worth a trip to brush. */
    private static final double DUSTY_PANELS = 0.7;

    /** The one thing the player should do next. Shown in the guide, bottom left. */
    private Hint hint() {
        Outpost o = state.outpost();
        Readings now = state.now();
        if (state.phase() == Phase.NIGHT) {
            return new Hint(now.dayShare() < 1 ? "Your astronaut is in bed. The rest of the day goes by fast."
                    : "Night. Your astronaut is asleep.", Spot.NONE);
        }
        if (state.phase() != Phase.DAY) {
            return new Hint("", Spot.NONE);
        }
        if (lesson != Step.NONE) {
            return lessonHint();
        }
        boolean filling = now.energyIn() > now.energyOut();
        if (!o.heaterOn()) {
            return new Hint("It is cold! Switch on the heater.", Spot.HEATER);
        }
        if (!now.heaterWorking()) {
            return o.lampsOn()
                    ? new Hint("No energy for the heater! Switch off the lamps.", Spot.LAMPS)
                    : new Hint("No energy! The heater has stopped until the Sun fills the battery.", Spot.NONE);
        }
        // By day the lamps can run on sunlight, so this waits until the battery has stopped
        // filling in the afternoon, or until the day is nearly over.
        boolean nightNear = now.dayShare() > 0.8 || (now.dayShare() > 0.5 && !filling);
        if (nightNear && o.lampsOn() && o.energy() < now.nightNeed()) {
            return new Hint("Night is coming. Switch off the lamps to save energy.", Spot.LAMPS);
        }
        boolean brushing = o.place() == Place.PANELS && o.panelsClean() < 0.995;
        boolean picking = o.place() == Place.GREENHOUSE && o.cropsReady() > 0.02;
        boolean panelsDusty = o.panelsClean() < DUSTY_PANELS;
        boolean foodReady = o.cropsReady() >= 1;
        if (state.radiation().solarEvent() && o.place() != Place.HABITAT && !brushing && !picking
                && !panelsDusty && !foodReady) {
            return new Hint("Solar storm! Send your astronaut into the habitat.", Spot.HABITAT);
        }
        if (panelsDusty && o.place() != Place.PANELS) {
            return new Hint("The panels are dusty. Click them to brush.", Spot.PANELS);
        }
        if (foodReady && o.place() != Place.GREENHOUSE) {
            return new Hint("Food is ready! Click the greenhouse to pick it.", Spot.GREENHOUSE);
        }
        if (!o.lampsOn() && filling && o.foodDays() + o.cropsReady() < 3) {
            return new Hint("Food is low. Switch on the lamps to grow more.", Spot.LAMPS);
        }
        if (o.at(Place.PANELS) && !brushing) {
            return new Hint("The panels are clean. Pick another job.", Spot.NONE);
        }
        if (o.at(Place.GREENHOUSE) && !picking) {
            return new Hint("Nothing left to pick. Pick another job.", Spot.NONE);
        }
        if (o.at(Place.DIGGER) && o.shield() >= 1) {
            return new Hint("The shield is finished! Pick another job.", Spot.NONE);
        }
        if (brushing || picking || o.place() == Place.DIGGER) {
            return new Hint("Fill the battery past the moon mark before night.", Spot.NONE);
        }
        if (state.radiation().solarEvent()) {
            return new Hint("Solar storm today. Stay in the habitat when you can.", Spot.NONE);
        }
        if (o.shield() < 1) {
            return new Hint("Dig to build the shield, or rest in the habitat.", Spot.DIGGER);
        }
        return new Hint("All is well. Rest, or press SLEEP.", Spot.NONE);
    }

    /** What a click on the thing under the mouse will do, or null if the mouse is over nothing to explain. */
    private String clickDoes() {
        Outpost o = state.outpost();
        switch (hover.spot()) {
            case PANELS:
                return "Click: brush the dust off the panels.";
            case DIGGER:
                return "Click: dig soil onto the shield. The digger uses energy.";
            case GREENHOUSE:
                return "Click: pick the food that has grown.";
            case HABITAT:
                return "Click: rest inside. Health comes back slowly.";
            case HEATER:
                return o.heaterOn() ? "Click: switch the heater off." : "Click: switch the heater on.";
            case LAMPS:
                return o.lampsOn() ? "Click: switch the grow lamps off."
                        : "Click: switch the grow lamps on. They grow food and use energy.";
            case WEATHER:
                return "Click: read the weather. The clock stops.";
            case DETECTOR:
                return "Click: read the radiation. The clock stops.";
            case SLEEP:
                return "Click: go to bed now and skip to the night.";
            case ENERGY:
                return "The moon mark shows how much energy tonight will take.";
            default:
                return null;
        }
    }

    // ---- Heads-up display -----------------------------------------------------------

    private void paintHud(Graphics2D g, Hint hint) {
        Phase phase = state.phase();
        StatusStrip.paint(g, state);

        // Landmark and compass, drawn straight onto the picture. North is toward the lower left.
        g.setFont(LANDMARK_FONT);
        g.setColor(new Color(60, 30, 20, 210));
        g.drawString("Lower slopes of Mount Sharp", 452, 104);
        double cx = 768;
        double cy = 508;
        Path2D.Double needle = new Path2D.Double();
        needle.moveTo(cx - 5, cy + 9);
        needle.lineTo(cx + 15, cy + 5);
        needle.lineTo(cx + 10, cy - 5);
        needle.closePath();
        g.setColor(Draw.CREAM);
        g.fill(needle);
        g.setFont(COMPASS_FONT);
        g.drawString("N", (float) cx - 17, (float) cy + 4);

        // A solar storm is news, so it gets a line of its own under the strip.
        RadiationConditions radiation = state.radiation();
        if (radiation.solarEvent() && radiation.detail() != null) {
            g.setFont(Draw.BODY_FONT);
            int width = g.getFontMetrics().stringWidth(radiation.detail()) + 24;
            double top = StatusStrip.AREA.getMaxY() + 6;
            Draw.box(g, (Draw.WIDTH - width) / 2.0, top, width, 22, new Color(110, 20, 70, 235), ALERT);
            g.setColor(Color.WHITE);
            Draw.centred(g, radiation.detail(), Draw.WIDTH / 2.0, top + 15);
        }

        // The guide can point at two things that are not tags: the battery and the SLEEP button.
        if (hint.target() == Spot.ENERGY) {
            Rectangle2D battery = StatusStrip.ENERGY;
            ring(g, new Rectangle2D.Double(battery.getX() + 2, battery.getY() + 2,
                    battery.getWidth() - 4, battery.getHeight() - 4));
        }
        boolean teaching = lesson != Step.NONE;
        if (phase == Phase.DAY || phase == Phase.NIGHT) {
            // During the lesson the guide keeps to the lesson, whatever the mouse is over.
            String does = phase == Phase.DAY && !teaching ? clickDoes() : null;
            paintGuide(g, does != null ? does : hint.text(), does != null);
            quietButton(g, HOME_BUTTON, "Change mission", Spot.HOME);
            quietButton(g, RESTART_BUTTON, "Restart", Spot.RESTART);
        }
        if (phase == Phase.DAY) {
            Draw.button(g, SLEEP_BUTTON, "SLEEP", Draw.BUTTON_FONT, Draw.MOON, hover.spot() == Spot.SLEEP);
            if (hint.target() == Spot.SLEEP) {
                ring(g, SLEEP_BUTTON);
            }
            if (teaching) {
                paintLesson(g);
            } else {
                quietButton(g, ABOUT_BUTTON, "About the data", Spot.ABOUT);
                quietButton(g, HOW_BUTTON, "How to play", Spot.HOW);
            }
        }

        g.setFont(Draw.SMALL_FONT);
        g.setColor(Draw.CREAM);
        g.drawString("Real NASA data, saved copy (not live).  Weather: Curiosity rover.  "
                + "Solar storms: DONKI.  Ground: MOLA.", 12, Draw.HEIGHT - 8);
    }

    /**
     * Bottom left: one line. Usually the next thing to do, in yellow; while the mouse is over
     * something, what a click on it will do, in white.
     */
    private void paintGuide(Graphics2D g, String text, boolean aboutTheMouse) {
        if (text.isEmpty()) {
            return;
        }
        g.setFont(GUIDE_FONT);
        double width = Math.ceil(g.getFontMetrics().stringWidth(text)) + 24;
        Draw.box(g, 12, 542, width, 32, BUBBLE, aboutTheMouse ? Draw.EDGE : Glyphs.POWER);
        g.setColor(aboutTheMouse ? Color.WHITE : Glyphs.POWER);
        g.drawString(text, 24, 563);
    }
}
