package outpost.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;
import outpost.app.Rules;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Outpost;
import outpost.shared.Phase;
import outpost.shared.Place;
import outpost.shared.PlayerActions;
import outpost.shared.RadiationConditions;
import outpost.shared.Readings;
import outpost.shared.Soil;
import outpost.shared.UvIndex;
import outpost.shared.WeatherConditions;

/**
 * The game's one panel. Before a mission it shows the home screen (HomeScreen). In a mission
 * it shows the outpost from above: a photo of the ground with a picture of each of the four
 * places on it (Pictures), the astronaut (Astronaut), the light of the hour (Light), and
 * every control drawn over them. The player clicks a place, or the round button beside it,
 * to send the astronaut there, flips the heater and lamp switches, and presses SLEEP. Each
 * click is passed to PlayerActions. Everything is drawn from the GameState snapshot;
 * painting never changes the game.
 *
 * <p>Where each place is, where its button and its switch are and where the astronaut
 * stands there is all in one table, {@link Site}. Nothing here has a place's coordinates.
 *
 * <p>The screen is kept quiet by giving every fact one home. What the player is looking
 * after (the sol, health, energy) is in one strip along the top (StatusStrip), with the
 * day's two real readings between them: the temperature outside and the radiation. Each
 * place's round button carries the number that matters there now. Everything else about
 * the weather and the radiation is on a card, which opens under the strip when its reading
 * is clicked and stops the clock while it is open. The box at the bottom left names one
 * thing to do.
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
    private static final Color ALERT = StatusStrip.ALERT;
    private static final Color EARTH = new Color(232, 170, 120);
    /** The edge of each round button: its own colour, a little see-through. */
    private static final Color EARTH_EDGE = Draw.alpha(EARTH, 220);
    private static final Color SUN_EDGE = Draw.alpha(SUN_YELLOW, 220);
    private static final Color WARN_EDGE = Draw.alpha(Glyphs.WARN, 220);
    private static final Color LEAF_EDGE = Draw.alpha(LEAF_GREEN, 220);
    private static final Color SWITCH_OFF = new Color(92, 96, 116);
    private static final Color SWITCH_DEAD = new Color(132, 44, 52);
    private static final Color SWITCH_FRAME = new Color(24, 28, 44, 235);
    private static final Color BUTTON_FILL = new Color(24, 28, 44, 220);
    private static final Color QUIET_FILL = new Color(24, 28, 44, 110);
    /** Laid over a quiet button or a reading in the strip while the mouse is on it or its card is open. */
    private static final Color LIT = new Color(255, 255, 255, 46);
    private static final Color LABEL_EDGE = Draw.alpha(Color.WHITE, 120);
    private static final Color LABEL_EDGE_BAD = Draw.alpha(Glyphs.BAD, 120);
    private static final Color COLD_FILL = new Color(80, 24, 32, 225);
    private static final Color STORM_FILL = new Color(110, 20, 70, 235);
    private static final Color HELMET = new Color(246, 246, 250);
    private static final Color VISOR = new Color(30, 38, 66);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final Font GUIDE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    private static final Font SWITCH_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 9);
    private static final Font BUTTON_NAME_FONT = SWITCH_FONT;
    private static final Font BUTTON_VALUE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 10);
    private static final Font BUTTON_NOTE_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
    private static final BasicStroke BUTTON_EDGE = new BasicStroke(1.5f);
    private static final BasicStroke BUTTON_EDGE_HOT = new BasicStroke(2.5f);

    private static final Rectangle2D SLEEP_BUTTON = new Rectangle2D.Double(700, 550, 88, 28);
    private static final Rectangle2D HOME_BUTTON = new Rectangle2D.Double(546, 580, 84, 16);
    private static final Rectangle2D ABOUT_BUTTON = new Rectangle2D.Double(636, 580, 86, 16);
    private static final Rectangle2D RESTART_BUTTON = new Rectangle2D.Double(728, 580, 60, 16);
    /** "How to play", just above the guide. The lesson's own buttons take its place while the lesson runs. */
    private static final Rectangle2D HOW_BUTTON = new Rectangle2D.Double(12, 520, 76, 16);
    /** Where the time each repaint takes is shown, for whoever is tuning the drawing: see showPaintTime(). */
    private static final Rectangle2D PAINT_TIME = new Rectangle2D.Double(12, 64, 216, 18);

    /**
     * Things on screen that can be clicked or pointed at. The home screen keeps its own
     * (HomeScreen). WEATHER and RADIATION are the two readings in the strip; HEATER and
     * LAMPS are the two switches.
     */
    private enum Spot {
        NONE, HABITAT, GREENHOUSE, PANELS, DIGGER, WEATHER, RADIATION, HEATER, LAMPS, SLEEP, START,
        RESTART, ABOUT, HOME, PLAY_AGAIN, CARD, ENERGY, HOW, NEXT, SKIP
    }

    /** What is under the mouse. */
    private record Hit(Spot spot) {}

    private static final Hit NOTHING = new Hit(Spot.NONE);

    /**
     * What the guide tells the player, and the thing on screen it is about. For a place the
     * ring goes round its round button (round its picture, for the habitat, which has no
     * button), unless {@code whole} asks for everything the place has: picture, button and
     * switch together.
     */
    private record Hint(String text, Spot target, boolean whole) {
        Hint(String text, Spot target) {
            this(text, target, false);
        }
    }

    /** One line of a card: a name on the left and its value on the right. */
    private record Fact(String name, String value) {}

    /**
     * The box that opens when a reading in the strip, or "About the data", is clicked. A
     * card holds readings ({@code facts}) and sentences ({@code notes}, already split into
     * lines). A {@code pointed} card hangs under the strip and points up at its reading, at
     * {@code anchorX}; the others stand alone.
     */
    private record Card(Rectangle2D box, double anchorX, boolean pointed, String title, Color accent,
                        List<Fact> facts, List<String> notes) {}

    /** The ground and the buildings. Reading the picture files takes a moment, so it is done here, once. */
    private final transient Pictures pictures = new Pictures();
    private final transient Light light = new Light();
    private final transient PlayerActions actions;
    /** The home screen. While it is showing, every mouse and key event is passed to it. */
    private final transient HomeScreen home;
    private transient GameState state;
    /** Whose card is open: WEATHER, RADIATION, ABOUT or NONE. The clock stands still while one is. */
    private Spot selected = Spot.NONE;
    /** The step of the lesson that is showing, or NONE. The clock stands still while one is. */
    private Step lesson = Step.NONE;
    /** True once the lesson has started since the game opened. It is not given twice unless asked for. */
    private boolean taught;
    private transient Hit hover = NOTHING;

    // Where the astronaut's feet are drawn, in the layout. Follows the state: see follow().
    private double figureX;
    private double figureY;
    private Place figureGoal;
    private double figureWalkLeft;
    private boolean figureKnown;

    /** The guide's ring, kept so that painting it makes nothing new. */
    private final transient Rectangle2D.Double ringBox = new Rectangle2D.Double();
    private final transient Ellipse2D.Double ringRound = new Ellipse2D.Double();

    // For whoever is tuning the drawing: see showPaintTime().
    private boolean paintTimeShown;
    private final long[] paintNanos = new long[30];
    private final long[] paintStarted = new long[30];
    private int paintCount;

    /** @param actions receives what the player clicks */
    public OutpostView(PlayerActions actions) {
        this.actions = actions;
        setOpaque(false);
        setPreferredSize(new Dimension(Draw.WIDTH, Draw.HEIGHT));
        home = new HomeScreen(this, actions);
        setFocusable(true); // for the home screen's up and down keys
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                double[] at = layoutPoint(e);
                if (onHome()) {
                    home.pressed(at[0], at[1]);
                } else {
                    click(hitAt(at[0], at[1]));
                }
                pointAt(at); // the click may have changed what is under the mouse, or the whole screen
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (onHome()) {
                    double[] at = layoutPoint(e);
                    home.released(at[0], at[1]);
                    pointAt(at);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (onHome()) {
                    double[] at = layoutPoint(e);
                    home.dragged(at[0], at[1]);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                pointAt(layoutPoint(e));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (onHome()) {
                    home.exited();
                }
                point(NOTHING);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (onHome()) {
                    home.wheel(e.getPreciseWheelRotation());
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (onHome()) {
                    home.key(e.getKeyCode());
                }
            }
        });
    }

    /** Called by the controller wiring whenever the state changes. */
    public void show(GameState newState) {
        state = newState;
        if (newState.phase() != Phase.DAY) {
            selected = Spot.NONE; // cards and the lesson belong to the day; home and restart close them
            lesson = Step.NONE;
        }
        home.show(newState);
        follow(newState);
        repaint();
    }

    /** True while the home screen is showing: it then gets the mouse and the keys. */
    private boolean onHome() {
        return state != null && state.phase() == Phase.BRIEFING;
    }

    /** Where a mouse event is, in the 800 x 600 layout that every screen is drawn in. */
    private double[] layoutPoint(MouseEvent e) {
        double scale = Draw.scale(getWidth(), getHeight());
        return new double[] {
            (e.getX() - (getWidth() - Draw.WIDTH * scale) / 2) / scale,
            (e.getY() - (getHeight() - Draw.HEIGHT * scale) / 2) / scale};
    }

    /** Tells whichever screen is showing where the mouse now is, and sets the cursor to match. */
    private void pointAt(double[] at) {
        if (onHome()) {
            home.moved(at[0], at[1]);
            point(NOTHING);
            setCursor(Cursor.getPredefinedCursor(
                    home.clickable(at[0], at[1]) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        } else {
            point(hitAt(at[0], at[1]));
        }
    }

    /**
     * While times are shown in the corner, every repaint takes the corner with it, so that
     * the figure there keeps up when only part of the panel is repainted (the home screen
     * repaints only its helmet as it turns).
     */
    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        super.repaint(tm, x, y, width, height);
        if (paintTimeShown) {
            double scale = Draw.scale(getWidth(), getHeight());
            super.repaint(tm, (int) ((getWidth() - Draw.WIDTH * scale) / 2 + PAINT_TIME.getX() * scale) - 1,
                    (int) ((getHeight() - Draw.HEIGHT * scale) / 2 + PAINT_TIME.getY() * scale) - 1,
                    (int) (PAINT_TIME.getWidth() * scale) + 3, (int) (PAINT_TIME.getHeight() * scale) + 3);
        }
    }

    /**
     * Shows in a corner how long painting takes, averaged over the last 30 repaints. For
     * checking that the game stays quick, above all in the browser. Off unless asked for.
     */
    public void showPaintTime(boolean shown) {
        paintTimeShown = shown;
        repaint();
    }

    // ---- The astronaut --------------------------------------------------------------

    /**
     * Moves the drawn astronaut to match a new state. The rules say only where the astronaut
     * is going and how many hours of walking are left, so the view walks the figure in a
     * straight line from wherever it is to the place's standing spot (Site), to arrive as
     * the walking runs out.
     */
    private void follow(GameState s) {
        Outpost o = s.outpost();
        if (o == null) {
            figureKnown = false;
            return;
        }
        Site goal = Site.of(o.place());
        boolean sameWalk = figureKnown && o.place() == figureGoal && figureWalkLeft > 0
                && o.walkLeft() <= figureWalkLeft;
        if (!figureKnown || !o.walking() || s.phase() == Phase.READY || s.phase() == Phase.DAWN) {
            figureX = goal.standX();
            figureY = goal.standY();
        } else if (sameWalk) {
            double left = o.walkLeft() / figureWalkLeft;
            figureX = goal.standX() + (figureX - goal.standX()) * left;
            figureY = goal.standY() + (figureY - goal.standY()) * left;
        } // otherwise a new walk has begun: it starts from where the figure stands
        figureGoal = o.place();
        figureWalkLeft = o.walkLeft();
        figureKnown = true;
    }

    /**
     * True at night, when the astronaut is in bed. The figure is not drawn then, and its
     * label stays at the habitat's door with a small helmet in it. By day the astronaut is
     * always drawn, so that the player can see them: resting, they stand at the door.
     */
    private boolean asleep() {
        return state.phase() == Phase.NIGHT;
    }

    private void paintAstronaut(Graphics2D g) {
        if (asleep()) {
            return;
        }
        Outpost o = state.outpost();
        if (!o.walking()) {
            Astronaut.paint(g, figureX, figureY, 0, 0);
            return;
        }
        // Eight steps to a walk. The step comes from the state, so it stands still with the clock.
        double step = Math.sin(o.walkLeft() * 50);
        double ahead = Site.of(o.place()).standX() - figureX;
        Astronaut.paint(g, figureX, figureY, step, ahead > 1 ? 1 : ahead < -1 ? -1 : 0);
    }

    /**
     * A small label saying what the astronaut is doing: over their head by day, and in the
     * same place at the habitat's door, with a small helmet in it, while they sleep.
     */
    private void paintDoing(Graphics2D g) {
        Outpost o = state.outpost();
        Readings now = state.now();
        Phase phase = state.phase();
        if (phase != Phase.DAY && phase != Phase.NIGHT) {
            return;
        }
        String doing;
        Color colour = Color.WHITE;
        if (phase == Phase.NIGHT) {
            doing = "Sleeping";
        } else if (o.walking()) {
            doing = "Walking";
        } else {
            switch (o.place()) {
                case HABITAT:
                    doing = "Resting";
                    break;
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
        boolean asleep = asleep();
        double x = asleep ? Site.HABITAT.standX() : figureX;
        double head = (asleep ? Site.HABITAT.standY() : figureY) - Astronaut.HEIGHT;
        g.setFont(Draw.SMALL_FONT);
        double helmet = asleep ? 14 : 0;
        double width = g.getFontMetrics().stringWidth(doing) + 12 + helmet;
        double left = Math.round(x - width / 2);
        double top = Math.round(head - 5 - 16);
        Draw.box(g, left, top, width, 16, BUBBLE, colour == Color.WHITE ? LABEL_EDGE : LABEL_EDGE_BAD);
        if (asleep) {
            // The astronaut is out of sight: their helmet in the label says whose label it is.
            g.setColor(HELMET);
            g.fillOval((int) left + 4, (int) top + 3, 10, 10);
            g.setColor(VISOR);
            g.fillRoundRect((int) left + 6, (int) top + 6, 6, 4, 3, 3);
        }
        g.setColor(colour);
        g.drawString(doing, (float) (left + 6 + helmet), (float) top + 12);
    }

    // ---- Painting -------------------------------------------------------------------

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
            // 800 x 600 still fits its box when the whole screen is drawn smaller or larger.
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            Phase phase = state.phase();
            if (phase == Phase.BRIEFING) {
                home.paint(g, getWidth(), getHeight());
            } else {
                // Back to front: the ground and the buildings (one cached image), the
                // astronaut, the light of the hour over all of that, and then the controls,
                // which the light does not touch.
                pictures.paint(g, getWidth(), getHeight());
                Draw.fit(g, getWidth(), getHeight());
                paintAstronaut(g);
                // The light covers the whole panel, which is larger than the layout when
                // the window is not the layout's shape.
                double scale = Draw.scale(getWidth(), getHeight());
                light.paint(g, state,
                        (int) Math.floor(-(getWidth() - Draw.WIDTH * scale) / 2 / scale) - 1,
                        (int) Math.floor(-(getHeight() - Draw.HEIGHT * scale) / 2 / scale) - 1,
                        (int) Math.ceil(getWidth() / scale) + 2, (int) Math.ceil(getHeight() / scale) + 2);

                Hint hint = hint();
                // The buttons belong to a sol in play. Under a box in the middle of the
                // screen they would only show as cut-off pieces round its edge.
                if (phase == Phase.DAY || phase == Phase.NIGHT) {
                    paintSites(g, hint);
                }
                paintDoing(g);
                paintHud(g, hint);
                paintCard(g);
                paintEnergyTooltip(g);
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
            Draw.box(g, PAINT_TIME.getX(), PAINT_TIME.getY(), PAINT_TIME.getWidth(), PAINT_TIME.getHeight(),
                    BUBBLE, null);
            g.setColor(Color.WHITE);
            g.drawString(text, (float) PAINT_TIME.getX() + 6, (float) PAINT_TIME.getY() + 13);
        }
        paintNanos[paintCount % paintNanos.length] = System.nanoTime() - started;
        paintStarted[paintCount % paintStarted.length] = started;
        paintCount++;
    }

    /** A small button for things that are not part of playing a sol. */
    private void quietButton(Graphics2D g, Rectangle2D area, String text, Spot spot) {
        boolean hot = hover.spot() == spot;
        Draw.box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                hot ? LIT : QUIET_FILL, hot ? Color.WHITE : Draw.EDGE);
        g.setFont(Draw.SMALL_FONT);
        g.setColor(hot ? Color.WHITE : Draw.CREAM);
        Draw.centred(g, text, area.getCenterX(), area.getCenterY() + 3.5);
    }

    // ---- The places: their round buttons and their switches ---------------------------

    /**
     * The round button beside each place, with the number that matters there now, and the
     * two switches. Whatever {@code hint} points the player at is ringed in the guide's
     * yellow. Where they all are comes from {@link Site}.
     */
    private void paintSites(Graphics2D g, Hint hint) {
        Outpost o = state.outpost();
        Readings now = state.now();
        boolean dusty = o.panelsClean() < DUSTY_PANELS;
        roundButton(g, Site.DIGGER, "DIGGER", "Shield " + Math.round(o.shield() * 100) + "%", null,
                EARTH, EARTH_EDGE);
        roundButton(g, Site.PANELS, "SOLAR", Math.round(o.panelsClean() * 100) + "% clean", null,
                dusty ? Glyphs.WARN : SUN_YELLOW, dusty ? WARN_EDGE : SUN_EDGE);
        roundButton(g, Site.GREENHOUSE, "GROW", String.format(Locale.ENGLISH, "pH %.1f", Soil.ph(o.marsSoil())),
                String.format(Locale.ENGLISH, "Food %.1fd", o.foodDays()), LEAF_GREEN, LEAF_EDGE);
        toggle(g, Site.HABITAT, Spot.HEATER, "HEAT", o.heaterOn(), now.heaterWorking());
        toggle(g, Site.GREENHOUSE, Spot.LAMPS, "LAMPS", o.lampsOn(), now.lampsWorking());

        Spot target = hint.target();
        if (target == Spot.HEATER) {
            ring(g, Site.HABITAT.toggleFrame().getFrame());
        } else if (target == Spot.LAMPS) {
            ring(g, Site.GREENHOUSE.toggleFrame().getFrame());
        } else if (place(target) != null) {
            Site site = Site.of(place(target));
            if (hint.whole()) {
                Rectangle2D all = pictures.seen(site);
                if (site.button() != null) {
                    all = all.createUnion(site.button().getFrame());
                }
                if (site.toggleFrame() != null) {
                    all = all.createUnion(site.toggleFrame().getFrame());
                }
                ring(g, all);
            } else if (site.button() != null) {
                ring(g, site.button());
            } else {
                ring(g, pictures.seen(site));
            }
        }
    }

    /** A round button: the place's name, its reading, and a second, smaller reading or null. */
    private void roundButton(Graphics2D g, Site site, String name, String reading, String note, Color accent,
                             Color edge) {
        Ellipse2D area = site.button();
        boolean hot = place(hover.spot()) == site.place();
        g.setColor(BUTTON_FILL);
        g.fill(area);
        g.setStroke(hot ? BUTTON_EDGE_HOT : BUTTON_EDGE);
        g.setColor(hot ? Color.WHITE : edge);
        g.draw(area);
        g.setFont(BUTTON_NAME_FONT);
        g.setColor(accent);
        Draw.centred(g, name, area.getCenterX(), area.getCenterY() - 13);
        g.setFont(BUTTON_VALUE_FONT);
        g.setColor(Color.WHITE);
        Draw.centred(g, reading, area.getCenterX(), area.getCenterY() + 1);
        if (note != null) {
            g.setFont(BUTTON_NOTE_FONT);
            g.setColor(Draw.MUTED);
            Draw.centred(g, note, area.getCenterX(), area.getCenterY() + 14);
        }
    }

    /** A switch: green when on, grey when off, dark red when on with no energy to run on. */
    private void toggle(Graphics2D g, Site site, Spot spot, String name, boolean on, boolean working) {
        RoundRectangle2D area = site.toggle();
        g.setColor(SWITCH_FRAME);
        g.fill(site.toggleFrame());
        g.setStroke(Draw.HAIRLINE);
        g.setColor(hover.spot() == spot ? Color.WHITE : Draw.EDGE);
        g.draw(site.toggleFrame());
        g.setColor(!on ? SWITCH_OFF : !working ? SWITCH_DEAD : Glyphs.GOOD);
        g.fill(area);
        g.setFont(SWITCH_FONT);
        g.setColor(Draw.INK);
        Draw.centred(g, name + (on ? " ON" : " OFF"), area.getCenterX(), area.getCenterY() + 3);
    }

    /** The yellow ring round whatever the guide is talking about. */
    private void ring(Graphics2D g, Rectangle2D area) {
        ringBox.setRect(area.getX() - 1, area.getY() - 1, area.getWidth() + 2, area.getHeight() + 2);
        g.setStroke(Draw.THICK);
        g.setColor(Glyphs.POWER);
        g.draw(ringBox);
    }

    /** The same ring round a round button. */
    private void ring(Graphics2D g, Ellipse2D button) {
        ringRound.setFrame(button.getX() - 3, button.getY() - 3, button.getWidth() + 6, button.getHeight() + 6);
        g.setStroke(Draw.THICK);
        g.setColor(Glyphs.POWER);
        g.draw(ringRound);
    }

    // ---- Cards ----------------------------------------------------------------------

    private static final double CARD_WIDTH = 262;
    /** Below this share of Mars soil the garden soil counts as fresh again. */
    private static final double SOIL_DONE = 0.005;
    private static final double CARD_HEAD = 26;
    private static final double FACT_ROW = 16;
    private static final double NOTE_ROW = 14;

    /** The card for whatever is selected, or null when none is open. Used to paint and to click. */
    private Card card() {
        if (state == null || state.phase() != Phase.DAY || selected == Spot.NONE) {
            return null;
        }
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        Readings now = state.now();
        String title;
        Color accent;
        Rectangle2D reading = null;
        List<Fact> facts = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        switch (selected) {
            case WEATHER:
                title = "Weather";
                accent = SKY_BLUE;
                reading = StatusStrip.WEATHER;
                facts.add(new Fact("Sky", weather.sunlight() < 0.5 ? "Dusty" : "Clear"));
                facts.add(new Fact("Sunlight getting through",
                        String.format(Locale.ENGLISH, "%.0f%%", weather.sunlight() * 100)));
                facts.add(new Fact("Coldest and warmest today", StatusStrip.degrees(weather.minTempC())
                        + " and " + StatusStrip.degrees(weather.maxTempC())));
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
            case RADIATION:
                title = radiation.solarEvent() ? "Solar storm!" : "Radiation";
                accent = radiation.solarEvent() ? ALERT : Color.WHITE;
                reading = StatusStrip.RADIATION;
                facts.add(new Fact("Radiation today", String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv())));
                sentences.add(radiation.solarEvent() ? "A solar storm has raised it today."
                        : "No solar storm today.");
                double blockedDoseMsv = radiation.doseMsv() * now.shieldBlocks();
                sentences.add(String.format(Locale.ENGLISH,
                        "The shield stops %.0f%% (%.2f mSv) while your astronaut is in the habitat.",
                        now.shieldBlocks() * 100, blockedDoseMsv));
                sentences.add("Outside and in the greenhouse nothing stops it.");
                break;
            case ABOUT:
                title = "About the data";
                accent = Color.WHITE;
                sentences.add("Real NASA data, saved copy (not live).");
                sentences.add("Weather: Curiosity rover, NASA/JPL-Caltech/CAB.");
                sentences.add("Solar storms: NASA CCMC DONKI.");
                sentences.add("The buildings and the astronaut are not drawn to scale.");
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
        if (reading == null) {
            return new Card(new Rectangle2D.Double(Math.round((Draw.WIDTH - CARD_WIDTH) / 2),
                    Math.round((Draw.HEIGHT - height) / 2), CARD_WIDTH, height), 0, false, title, accent, facts, notes);
        }
        // Under the strip, below its reading, and to the right of the digger's button: there
        // an open card covers no button and no switch, and cuts none in half.
        double left = Math.max(reading.getCenterX() - CARD_WIDTH / 2, Site.DIGGER.button().getMaxX() + 6);
        left = Math.round(Math.max(6, Math.min(Draw.WIDTH - 6 - CARD_WIDTH, left)));
        boolean pointed = reading.getCenterX() > left + 10 && reading.getCenterX() < left + CARD_WIDTH - 10;
        return new Card(new Rectangle2D.Double(left, StatusStrip.AREA.getMaxY() + 7, CARD_WIDTH, height),
                reading.getCenterX(), pointed, title, accent, facts, notes);
    }

    private void paintCard(Graphics2D g) {
        Card card = card();
        if (card == null) {
            return;
        }
        Rectangle2D area = card.box();
        if (card.pointed()) {
            Draw.pointerUp(g, card.anchorX(), area.getY(), Draw.CARD_FILL);
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

    /** What is at (x, y) in the 800 x 600 layout. On the home screen nothing: HomeScreen answers for itself. */
    private Hit hitAt(double x, double y) {
        if (state == null) {
            return NOTHING;
        }
        switch (state.phase()) {
            case BRIEFING:
                return NOTHING;
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
                return StatusStrip.ENERGY.contains(x, y) ? new Hit(Spot.ENERGY)
                        : HOME_BUTTON.contains(x, y) ? new Hit(Spot.HOME)
                        : RESTART_BUTTON.contains(x, y) ? new Hit(Spot.RESTART) : NOTHING;
            default:
                return new Hit(lesson == Step.NONE ? spotByDay(x, y) : spotInLesson(x, y));
        }
    }

    /**
     * What is at (x, y) while a sol is being played. Everything here can be seen: a button,
     * a switch, a reading in the strip, or a part of a building's picture that is not
     * see-through. Open ground is nothing.
     */
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
        if (StatusStrip.WEATHER.contains(x, y)) {
            return Spot.WEATHER;
        }
        if (StatusStrip.RADIATION.contains(x, y)) {
            return Spot.RADIATION;
        }
        if (StatusStrip.ENERGY.contains(x, y)) {
            return Spot.ENERGY;
        }
        // The switches first: one of them lies over the corner of its building's picture.
        if (Site.HABITAT.toggleFrame().contains(x, y)) {
            return Spot.HEATER;
        }
        if (Site.GREENHOUSE.toggleFrame().contains(x, y)) {
            return Spot.LAMPS;
        }
        for (Site site : Site.values()) {
            if ((site.button() != null && site.button().contains(x, y)) || pictures.solid(site, x, y)) {
                return spot(site.place());
            }
        }
        return Spot.NONE;
    }

    private void click(Hit hit) {
        // A click on a reading opens its card and a second click on it closes the card again.
        // A click on an open card does nothing, and a click anywhere else closes the card
        // and then does what it would do anyway.
        boolean wasOpen = selected != Spot.NONE;
        switch (hit.spot()) {
            case WEATHER:
            case RADIATION:
            case ABOUT:
                selected = selected == hit.spot() ? Spot.NONE : hit.spot();
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

    /** The spot that sends the astronaut to {@code place}. */
    private static Spot spot(Place place) {
        switch (place) {
            case GREENHOUSE:
                return Spot.GREENHOUSE;
            case PANELS:
                return Spot.PANELS;
            case DIGGER:
                return Spot.DIGGER;
            default:
                return Spot.HABITAT;
        }
    }

    private void point(Hit hit) {
        boolean hand = hit.spot() != Spot.NONE && hit.spot() != Spot.CARD && hit.spot() != Spot.ENERGY;
        setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!hit.equals(hover)) {
            hover = hit;
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
        if (!o.heaterOn() && now.outsideC() < 0) {
            return new Hint("You're getting cold. Turn on the habitat heater.", Spot.HEATER);
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
            return o.marsSoil() > SOIL_DONE
                    ? new Hint("Adding fresh soil to bring the pH back down.", Spot.NONE)
                    : new Hint("Nothing left to pick and the soil is fresh. Pick another job.", Spot.NONE);
        }
        if (o.place() != Place.GREENHOUSE && Soil.zone(o.marsSoil()) != Soil.Zone.HEALTHY) {
            return new Hint("The soil pH is high. Click the greenhouse to add fresh soil.", Spot.GREENHOUSE);
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
                return "Click: brush the dust off the solar panels.";
            case DIGGER:
                return "Click: dig soil onto the shield. The digger uses energy.";
            case GREENHOUSE:
                return "Click: pick the food, and add fresh soil to bring the pH down.";
            case HABITAT:
                return "Click: rest inside. Health comes back slowly.";
            case HEATER:
                return o.heaterOn() ? "Click: switch the heater off." : "Click: switch the heater on.";
            case LAMPS:
                return o.lampsOn() ? "Click: switch the grow lamps off."
                        : "Click: switch the grow lamps on. They grow food and use energy.";
            case WEATHER:
                return selected == Spot.WEATHER ? "Click: close the weather card."
                        : "Click: read the weather. The clock stops.";
            case RADIATION:
                return selected == Spot.RADIATION ? "Click: close the radiation card."
                        : "Click: read the radiation. The clock stops.";
            case SLEEP:
                return "Click: go to bed now and skip to the night.";
            case ENERGY:
                return "Hover for energy gain, demand and net balance per second. The moon shows tonight's need.";
            default:
                return null;
        }
    }

    // ---- Heads-up display -----------------------------------------------------------

    private void paintHud(Graphics2D g, Hint hint) {
        Phase phase = state.phase();
        StatusStrip.paint(g, state);
        // A reading in the strip lights up under the mouse, and stays lit while its card is open.
        if (phase == Phase.DAY || phase == Phase.NIGHT) {
            g.setColor(LIT);
            if (phase == Phase.DAY && (hover.spot() == Spot.WEATHER || selected == Spot.WEATHER)) {
                g.fill(StatusStrip.WEATHER);
            }
            if (phase == Phase.DAY && (hover.spot() == Spot.RADIATION || selected == Spot.RADIATION)) {
                g.fill(StatusStrip.RADIATION);
            }
            if (hover.spot() == Spot.ENERGY) {
                g.fill(StatusStrip.ENERGY);
            }
        }

        // Temperature is explained in plain language instead of adding another meter.
        if (phase == Phase.DAY && state.now().outsideC() < 0 && !state.outpost().heaterOn()) {
            Rectangle2D heater = Site.HABITAT.toggle().getFrame();
            g.setFont(Draw.BODY_FONT);
            String warning = "You're getting cold \u2014 turn on the heater";
            int warningWidth = g.getFontMetrics().stringWidth(warning) + 20;
            double warningLeft = Math.max(6, Math.min(heater.getX() - 12, Draw.WIDTH - 6 - warningWidth));
            double warningTop = heater.getY() - 48;
            Draw.box(g, warningLeft, warningTop, warningWidth, 20, COLD_FILL, Glyphs.BAD);
            g.setColor(Color.WHITE);
            g.drawString(warning, (float) warningLeft + 10, (float) warningTop + 14);
        }

        // A solar storm is news, so it gets a line of its own under the strip. A card from
        // the strip opens in the same place, and while one is open the line makes way for it.
        RadiationConditions radiation = state.radiation();
        if (radiation.solarEvent() && radiation.detail() != null
                && selected != Spot.WEATHER && selected != Spot.RADIATION) {
            g.setFont(Draw.BODY_FONT);
            int width = g.getFontMetrics().stringWidth(radiation.detail()) + 24;
            double top = StatusStrip.AREA.getMaxY() + 6;
            Draw.box(g, (Draw.WIDTH - width) / 2.0, top, width, 22, STORM_FILL, ALERT);
            g.setColor(Color.WHITE);
            Draw.centred(g, radiation.detail(), Draw.WIDTH / 2.0, top + 15);
        }

        // The guide can point at two things that are not places: the battery and the SLEEP button.
        if (hint.target() == Spot.ENERGY) {
            Rectangle2D battery = StatusStrip.ENERGY;
            ringBox.setRect(battery.getX() + 2, battery.getY() + 2, battery.getWidth() - 4, battery.getHeight() - 4);
            ring(g, ringBox);
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
    }

    /**
     * The battery's live power balance, shown when its meter is hovered. Readings are per
     * Mars hour, so convert them using the clock speed for the current phase.
     */
    private void paintEnergyTooltip(Graphics2D g) {
        Phase phase = state.phase();
        if (hover.spot() != Spot.ENERGY || (phase != Phase.DAY && phase != Phase.NIGHT)) {
            return;
        }

        double marsHoursPerSecond = phase == Phase.NIGHT
                ? Rules.NIGHT_HOURS_PER_SECOND
                : (Rules.sunsetHour(state.weather()) - Rules.sunriseHour(state.weather())) / Rules.DAY_SECONDS;
        Readings now = state.now();
        double gain = now.energyIn() * marsHoursPerSecond;
        Rules.EnergyUse use = Rules.energyUse(state.outpost(), state.weather());
        double lifeSupport = use.lifeSupport() * marsHoursPerSecond;
        double heater = use.heater() * marsHoursPerSecond;
        double lamps = use.lamps() * marsHoursPerSecond;
        double digger = use.digger() * marsHoursPerSecond;
        double demand = use.total() * marsHoursPerSecond;
        double balance = gain - demand;

        String[] labels = {"Solar gain", "Life support", "Heater", "Grow lamps",
                "Digger", "Total demand", "Net balance"};
        double[] values = {gain, lifeSupport, heater, lamps, digger, demand, balance};
        int visibleRows = 0;
        for (int i = 0; i < labels.length; i++) {
            // Hide zero-valued flows, but retain the net balance as the summary line.
            if (i == labels.length - 1 || Math.abs(values[i]) > 1e-9) visibleRows++;
        }

        double left = StatusStrip.ENERGY.getX();
        double top = StatusStrip.AREA.getMaxY() + 6;
        double width = StatusStrip.ENERGY.getWidth();
        double rowSpacing = 12.5;
        double height = 25 + visibleRows * rowSpacing;
        Draw.box(g, left, top, width, height, BUBBLE, Glyphs.POWER);

        g.setFont(Draw.TITLE_FONT);
        g.setColor(Glyphs.POWER);
        g.drawString("Energy flow (per second)", (float) left + 10, (float) top + 16);

        String unit = " energy/s";
        g.setFont(Draw.BODY_FONT);
        int row = 0;
        for (int i = 0; i < labels.length; i++) {
            if (i != labels.length - 1 && Math.abs(values[i]) <= 1e-9) continue;

            double baseline = top + 32 + row++ * rowSpacing;
            g.setColor(Draw.MUTED);
            g.drawString(labels[i], (float) left + 10, (float) baseline);
            if (i == 0) {
                g.setColor(Color.WHITE);
            } else if (i == labels.length - 1) {
                g.setColor(balance > 1e-9 ? Glyphs.GOOD : balance < -1e-9 ? Glyphs.WARN : Color.WHITE);
            } else {
                g.setColor(Color.WHITE);
            }
            String value = i == 0 ? String.format(Locale.ENGLISH, "+%.2f%s", values[i], unit)
                    : i == labels.length - 1 ? String.format(Locale.ENGLISH, "%+.2f%s", values[i], unit)
                    : String.format(Locale.ENGLISH, "-%.2f%s", values[i], unit);
            Draw.rightAligned(g, value, left + width - 10, baseline);
        }
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
