package outpost.view;

import static outpost.view.Mesh.mix;
import static outpost.view.Mesh.p;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.JPanel;
import outpost.shared.GameState;
import outpost.shared.Glyphs;
import outpost.shared.Phase;
import outpost.shared.PlayerActions;
import outpost.shared.PowerAllocation;
import outpost.shared.PowerUse;
import outpost.shared.RadiationConditions;
import outpost.shared.SolReport;
import outpost.shared.UvIndex;
import outpost.shared.WeatherConditions;

/**
 * The angled bird's-eye view of the outpost: a low-poly model of the real ground around
 * Curiosity's landing site, with the outpost buildings on it. Everything that changes is
 * drawn from the GameState snapshot; painting never changes anything. The view is also
 * the controls: the player clicks the solar panels to collect energy, clicks the habitat
 * or the greenhouse to spend it, and clicks END SOL. Each click is passed to PlayerActions.
 *
 * <p>The screen is kept quiet by giving every fact one home. What the player is looking
 * after (the sol, crew health, food, energy) is in one strip along the top. Each building
 * carries a one-line tag with the number that matters now. Everything else about a
 * building is on its card, which opens when the building is clicked.
 *
 * <p>What is real: the ground heights (NASA MOLA) and the compass direction. What is not:
 * heights are drawn {@value #HEIGHT_EXAGGERATION} times taller than true scale so the
 * slopes read at this size, and the buildings are drawn far larger than life (a tile is
 * about 0.9 km across).
 */
public final class OutpostView extends JPanel {

    private static final long serialVersionUID = 1L;

    /** The scene is laid out for this size and scaled to fit the panel. */
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final Projection CAMERA = new Projection(400, 176, 18, 9, 22);
    static final int HEIGHT_EXAGGERATION = 2;

    private static final Color ROCK_LOW = new Color(183, 100, 60);
    private static final Color ROCK_HIGH = new Color(226, 170, 122);
    private static final Color HULL = new Color(236, 232, 224);
    private static final Color METAL = new Color(150, 154, 164);
    private static final Color DARK = new Color(52, 54, 64);
    private static final Color BUBBLE = new Color(24, 28, 44, 222);
    private static final Color SUN_YELLOW = new Color(255, 205, 80);
    private static final Color LEAF_GREEN = new Color(120, 214, 110);
    private static final Color SKY_BLUE = new Color(120, 190, 255);
    private static final Color WARM = new Color(255, 170, 90);
    private static final Color ALERT = new Color(255, 96, 160);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    // Where things stand, in tiles. The camera looks south-east from the north-west, so
    // u grows toward the west and v toward the north. Bradbury Landing is the grid centre.
    private static final double HAB_U = 10;
    private static final double HAB_V = 10;
    private static final double HAB_RADIUS = 1.7;
    private static final double GREENHOUSE_U = 12.7;
    private static final double GREENHOUSE_V = 8.0;
    private static final double SOLAR_U = 7.0;
    private static final double SOLAR_V = 13.3;
    private static final double WEATHER_U = 9.6;
    private static final double WEATHER_V = 5.9;
    private static final double DETECTOR_U = 6.2;
    private static final double DETECTOR_V = 10.3;
    private static final double ROVER_U = 13.4;
    private static final double ROVER_V = 13.2;
    /**
     * The buildings are drawn this many times larger than the ground grid, growing outward
     * from the habitat, so they can be seen. The positions above are in these larger units.
     */
    private static final double SITE_SCALE = 1.5;

    private record Rock(double u, double v, double size, int sides, double turn, double tone) {}

    /** One of the Glyphs drawing methods. */
    @FunctionalInterface
    private interface Icon {
        void draw(Graphics2D g, double x, double y, double size, Color colour);
    }

    /** A short reading: an icon, or none if {@code icon} is null, and then text. */
    private record Stat(Icon icon, Color iconColour, String text, Color textColour) {}

    /** The one-line tag over a building: its name and the one or two numbers that matter now. */
    private record Chip(Spot spot, Rectangle2D box, double anchorX, Color accent, String title,
                        List<Stat> stats) {}

    /** Things on screen that can be clicked. */
    private enum Spot {
        NONE, HABITAT, GREENHOUSE, WEATHER, DETECTOR, COLLECT, END_SOL, RESTART, ABOUT, PLAY_AGAIN,
        CARD, MINUS, PLUS
    }

    /** What is under the mouse. {@code use} is set for the MINUS and PLUS buttons of a card. */
    private record Hit(Spot spot, PowerUse use) {}

    private static final Hit NOTHING = new Hit(Spot.NONE, null);

    /** What the guide tells the player to do next, and the thing on screen it is about. */
    private record Hint(String text, Spot target) {}

    /** One line of a building's card: a use of energy with its minus and plus buttons. */
    private record Stepper(PowerUse use, double top, Rectangle2D minus, Rectangle2D plus) {}

    /** One line of a card that only informs: a name on the left and its value on the right. */
    private record Fact(String name, String value) {}

    /**
     * The box that opens when a building is clicked. A card holds controls
     * ({@code steppers}), readings ({@code facts}) and sentences ({@code notes}, already
     * split into lines), in that order from the top. {@code pointed} cards hang over
     * {@code anchorX}; the others stand alone.
     */
    private record Card(Rectangle2D box, double anchorX, boolean pointed, String title, Color accent,
                        String corner, List<Stepper> steppers, List<Fact> facts, List<String> notes) {}

    private final transient Terrain terrain;
    private final int tiles;
    /** Ground height in tiles at each grid corner, indexed [u][v], already exaggerated. */
    private final double[][] ground;
    /** Camera for the buildings: the ground camera enlarged by SITE_SCALE about the habitat. */
    private final transient Projection site;
    private final transient List<Rock> rocks = new ArrayList<>();
    private final transient PlayerActions actions;
    /** Screen positions (in the 800 x 600 layout) the building tags and cards point at. */
    private final double[] habitatAnchor;
    private final double[] greenhouseAnchor;
    private final double[] solarAnchor;
    private final double[] weatherAnchor;
    private final double[] detectorAnchor;
    /** The screen rectangle around each building, so the building itself can be clicked. */
    private final transient Rectangle2D habitatArea;
    private final transient Rectangle2D greenhouseArea;
    private final transient Rectangle2D solarArea;
    private final transient Rectangle2D weatherArea;
    private final transient Rectangle2D detectorArea;
    private transient GameState state;
    /** Whose card is open: HABITAT, GREENHOUSE, WEATHER, DETECTOR, ABOUT or NONE. */
    private Spot selected = Spot.NONE;
    private transient Hit hover = NOTHING;

    /** @param actions receives what the player clicks */
    public OutpostView(PlayerActions actions) {
        this.actions = actions;
        terrain = Terrain.load();
        tiles = terrain.size - 1;
        ground = new double[tiles + 1][tiles + 1];
        double base = terrain.metresAt(tiles / 2, tiles / 2);
        for (int u = 0; u <= tiles; u++) {
            for (int v = 0; v <= tiles; v++) {
                double metres = terrain.metresAt(tiles - v, tiles - u);
                ground[u][v] = (metres - base) / terrain.spacingM * HEIGHT_EXAGGERATION;
            }
        }
        double habX = CAMERA.x(HAB_U, HAB_V);
        double habY = CAMERA.y(HAB_U, HAB_V, groundAt(HAB_U, HAB_V));
        site = new Projection(
                habX - (HAB_U - HAB_V) * CAMERA.halfWidth() * SITE_SCALE,
                habY - (HAB_U + HAB_V) * CAMERA.halfHeight() * SITE_SCALE,
                CAMERA.halfWidth() * SITE_SCALE, CAMERA.halfHeight() * SITE_SCALE,
                CAMERA.rise() * SITE_SCALE);
        // Scenery rocks. Decoration only: fixed positions, not data.
        Random random = new Random(11);
        double[][] keepClear = {
            {HAB_U, HAB_V, 3.2}, {GREENHOUSE_U + 1.5, GREENHOUSE_V, 2.6},
            {SOLAR_U + 1.8, SOLAR_V + 1, 2.8}, {WEATHER_U, WEATHER_V, 1.2},
            {DETECTOR_U, DETECTOR_V, 1.2}, {ROVER_U, ROVER_V, 1.2}};
        while (rocks.size() < 34) {
            double u = 0.6 + random.nextDouble() * (tiles - 1.2);
            double v = 0.6 + random.nextDouble() * (tiles - 1.2);
            boolean clear = true;
            for (double[] zone : keepClear) {
                // Zones are in building units; rocks are in ground tiles.
                double zoneU = HAB_U + (zone[0] - HAB_U) * SITE_SCALE;
                double zoneV = HAB_V + (zone[1] - HAB_V) * SITE_SCALE;
                clear &= Math.hypot(u - zoneU, v - zoneV) > zone[2] * SITE_SCALE;
            }
            if (clear) {
                rocks.add(new Rock(u, v, 0.12 + random.nextDouble() * 0.2, 4 + random.nextInt(3),
                        random.nextDouble() * 6, 0.75 + random.nextDouble() * 0.3));
            }
        }
        rocks.sort(Comparator.comparingDouble(r -> r.u() + r.v()));
        habitatAnchor = new double[] {site.x(HAB_U, HAB_V), site.y(HAB_U, HAB_V, 2.05)};
        greenhouseAnchor = new double[] {
            site.x(GREENHOUSE_U + 1.5, GREENHOUSE_V), site.y(GREENHOUSE_U + 1.5, GREENHOUSE_V, 1.25)};
        solarAnchor = new double[] {
            site.x(SOLAR_U + 1.8, SOLAR_V + 0.9), site.y(SOLAR_U + 1.8, SOLAR_V + 0.9, 1.05)};
        weatherAnchor = new double[] {site.x(WEATHER_U, WEATHER_V), site.y(WEATHER_U, WEATHER_V, 1.75)};
        detectorAnchor = new double[] {site.x(DETECTOR_U, DETECTOR_V), site.y(DETECTOR_U, DETECTOR_V, 1.0)};
        habitatArea = screenBox(HAB_U - HAB_RADIUS, HAB_V - HAB_RADIUS,
                HAB_U + HAB_RADIUS, HAB_V + HAB_RADIUS, 1.7);
        greenhouseArea = screenBox(GREENHOUSE_U, GREENHOUSE_V - 0.85,
                GREENHOUSE_U + 3, GREENHOUSE_V + 0.85, 1.0);
        solarArea = screenBox(SOLAR_U, SOLAR_V, SOLAR_U + 3.55, SOLAR_V + 1.95, 0.8);
        // The two instruments are small, so their click areas are a little larger than they are.
        weatherArea = screenBox(WEATHER_U - 0.55, WEATHER_V - 0.55, WEATHER_U + 0.55, WEATHER_V + 0.55, 1.45);
        detectorArea = screenBox(DETECTOR_U - 0.45, DETECTOR_V - 0.45, DETECTOR_U + 0.45, DETECTOR_V + 0.45, 0.7);

        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                click(hitAt(e));
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

    /** The screen rectangle around a box standing on the site, from ground level up to {@code height}. */
    private Rectangle2D screenBox(double u0, double v0, double u1, double v1, double height) {
        double left = site.x(u0, v1);
        double right = site.x(u1, v0);
        double top = site.y(u0, v0, height);
        double bottom = site.y(u1, v1, 0);
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    /** Called by the controller wiring whenever the state changes. */
    public void show(GameState newState) {
        state = newState;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (state == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            WeatherConditions weather = state.weather();
            double sun = clamp(weather.sunlight());

            Color skyTop = mix(new Color(142, 98, 72), new Color(236, 196, 148), sun);
            Color skyBottom = mix(new Color(70, 46, 40), new Color(128, 82, 62), sun);
            g.setPaint(new GradientPaint(0, 0, skyTop, 0, getHeight(), skyBottom));
            g.fillRect(0, 0, getWidth(), getHeight());

            double scale = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
            g.translate((getWidth() - WIDTH * scale) / 2, (getHeight() - HEIGHT * scale) / 2);
            g.scale(scale, scale);
            g.setStroke(new BasicStroke(0.8f));

            double brightness = 0.72 + 0.28 * sun;
            paintTerrain(g, brightness);
            paintGroundMarks(g, brightness);
            paintObjects(g, brightness);
            paintDust(g, sun);
            if (state.radiation().solarEvent()) {
                paintStorm(g);
            }
            Hint hint = hint();
            for (Chip chip : chips()) {
                paintChip(g, chip, hint.target());
            }
            paintCollectButton(g);
            paintHud(g, hint);
            paintCard(g);
            if (state.phase() != Phase.PLAYING) {
                paintEnding(g);
            }
        } finally {
            g.dispose();
        }
    }

    // ---- Ground ---------------------------------------------------------------------

    private void paintTerrain(Graphics2D g, double brightness) {
        Mesh mesh = new Mesh(CAMERA, brightness);
        double low = terrain.lowest();
        double range = Math.max(1, terrain.highest() - low);
        double base = terrain.metresAt(tiles / 2, tiles / 2);
        // Back to front: tiles with a smaller u + v are further from the camera.
        for (int sum = 0; sum <= 2 * (tiles - 1); sum++) {
            for (int u = Math.max(0, sum - (tiles - 1)); u <= Math.min(tiles - 1, sum); u++) {
                int v = sum - u;
                double[] a = p(u, v, ground[u][v]);
                double[] b = p(u + 1, v, ground[u + 1][v]);
                double[] c = p(u + 1, v + 1, ground[u + 1][v + 1]);
                double[] d = p(u, v + 1, ground[u][v + 1]);
                double[][][] halves = (u + v) % 2 == 0
                        ? new double[][][] {{a, b, d}, {b, c, d}}
                        : new double[][][] {{a, b, c}, {a, c, d}};
                for (int half = 0; half < 2; half++) {
                    double[][] t = halves[half];
                    double height = (t[0][2] + t[1][2] + t[2][2]) / 3;
                    double metres = base + height * terrain.spacingM / HEIGHT_EXAGGERATION;
                    Color tone = mix(ROCK_LOW, ROCK_HIGH, Math.pow((metres - low) / range, 0.7));
                    int speckle = Math.floorMod(u * 31 + v * 17 + half * 7 + u * v, 7) - 3;
                    mesh.sheet(Mesh.scale(tone, 1 + speckle * 0.014), t);
                }
            }
        }
        // The cut sides of the block of ground, so it reads as a model on a table.
        double floor = (low - base) / terrain.spacingM * HEIGHT_EXAGGERATION - 0.9;
        Color cut = new Color(132, 72, 48);
        for (int i = 0; i < tiles; i++) {
            mesh.sheet(cut, p(tiles, i, ground[tiles][i]), p(tiles, i + 1, ground[tiles][i + 1]),
                    p(tiles, i + 1, floor), p(tiles, i, floor));
            mesh.sheet(cut, p(i, tiles, ground[i][tiles]), p(i + 1, tiles, ground[i + 1][tiles]),
                    p(i + 1, tiles, floor), p(i, tiles, floor));
        }
        mesh.paint(g);
    }

    /** Ground height in tiles at any position, blended between the four nearest corners. */
    private double groundAt(double u, double v) {
        int i = (int) Math.max(0, Math.min(tiles - 1, Math.floor(u)));
        int j = (int) Math.max(0, Math.min(tiles - 1, Math.floor(v)));
        double fu = u - i;
        double fv = v - j;
        return ground[i][j] * (1 - fu) * (1 - fv) + ground[i + 1][j] * fu * (1 - fv)
                + ground[i][j + 1] * (1 - fu) * fv + ground[i + 1][j + 1] * fu * fv;
    }

    /** Foundations and power cables, drawn flat on the ground under everything else. */
    private void paintGroundMarks(Graphics2D g, double brightness) {
        g.setColor(Mesh.scale(new Color(70, 52, 50), brightness));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        cable(g, SOLAR_U + 1.8, SOLAR_V, HAB_U - 0.4, HAB_V + 1.9);
        cable(g, HAB_U + 1.9, HAB_V - 0.6, GREENHOUSE_U, GREENHOUSE_V + 0.3);
        cable(g, HAB_U - 0.2, HAB_V - 1.9, WEATHER_U + 0.1, WEATHER_V + 0.3);
        cable(g, HAB_U - 1.9, HAB_V + 0.1, DETECTOR_U + 0.5, DETECTOR_V + 0.2);
        g.setStroke(new BasicStroke(0.8f));

        Mesh pads = new Mesh(site, brightness);
        Color pad = new Color(170, 122, 96);
        double[][] ring = new double[12][];
        for (int k = 0; k < 12; k++) {
            double angle = k * Math.PI / 6;
            double u = HAB_U + Math.cos(angle) * (HAB_RADIUS + 0.75);
            double v = HAB_V + Math.sin(angle) * (HAB_RADIUS + 0.75);
            ring[k] = p(u, v, 0);
        }
        pads.sheet(pad, ring);
        flatPad(pads, pad, GREENHOUSE_U - 0.3, GREENHOUSE_V - 1.15, 3.6, 2.3);
        flatPad(pads, pad, SOLAR_U - 0.3, SOLAR_V - 0.3, 4.3, 2.7);
        pads.paint(g);
    }

    private void flatPad(Mesh mesh, Color colour, double u, double v, double du, double dv) {
        mesh.sheet(colour, p(u, v, 0), p(u + du, v, 0), p(u + du, v + dv, 0), p(u, v + dv, 0));
    }

    private void cable(Graphics2D g, double u0, double v0, double u1, double v1) {
        g.draw(new Line2D.Double(site.x(u0, v0), site.y(u0, v0, 0), site.x(u1, v1), site.y(u1, v1, 0)));
    }

    // ---- Buildings ------------------------------------------------------------------

    private void paintObjects(Graphics2D g, double brightness) {
        PowerAllocation power = state.allocation();
        PowerAllocation limit = state.limit();
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();

        // Rocks first: none stands in front of a building, so none can be painted over wrongly.
        Mesh scenery = new Mesh(CAMERA, brightness);
        for (Rock rock : rocks) {
            rock(scenery, rock);
        }
        scenery.paint(g);

        record Item(double depth, Consumer<Mesh> build) {}
        List<Item> items = new ArrayList<>();
        items.add(new Item(WEATHER_U + WEATHER_V, this::weatherStation));
        items.add(new Item(DETECTOR_U + DETECTOR_V, mesh -> detector(mesh, radiation.solarEvent())));
        items.add(new Item(HAB_U + HAB_V, mesh -> habitat(mesh,
                share(power.shielding(), limit.shielding()), share(power.heating(), limit.heating()))));
        items.add(new Item(GREENHOUSE_U + GREENHOUSE_V + 1.5, mesh -> greenhouse(mesh,
                share(power.greenhouse(), limit.greenhouse()))));
        items.add(new Item(SOLAR_U + SOLAR_V + 2.5, mesh -> solarArray(mesh, weather.sunlight())));
        items.add(new Item(ROVER_U + ROVER_V, this::rover));
        items.sort(Comparator.comparingDouble(Item::depth));
        for (Item item : items) {
            Mesh mesh = new Mesh(site, brightness);
            item.build().accept(mesh);
            mesh.paint(g);
        }
    }

    /** Energy spent on a use as a share of the most it can take, 0.0 to 1.0. */
    private static double share(int spent, int most) {
        return most <= 0 ? 0 : Math.min(1.0, spent / (double) most);
    }

    /**
     * The crew's home: a faceted dome on a ring wall. A bank of Mars soil is piled around it
     * as a radiation shield, taller the more energy goes to shielding, and the windows glow
     * warmer the nearer the heating is to what the night needs. Both shares are 0.0 to 1.0.
     */
    private void habitat(Mesh mesh, double shielding, double heating) {
        double h0 = 0;
        double bankHeight = 1.15 * shielding;
        if (shielding > 0) {
            for (int k = 6; k <= 13; k++) {
                bank(mesh, h0, bankHeight, k);
            }
        }

        int segments = 12;
        double wall = 0.34;
        double[] inside = p(HAB_U, HAB_V, h0 + wall);
        Color window = mix(new Color(64, 84, 118), new Color(255, 200, 96), heating);
        for (int k = 0; k < segments; k++) {
            double a0 = k * 2 * Math.PI / segments;
            double a1 = (k + 1) * 2 * Math.PI / segments;
            mesh.face(inside, k % 2 == 0 ? window : METAL,
                    ringPoint(a0, HAB_RADIUS, h0), ringPoint(a1, HAB_RADIUS, h0),
                    ringPoint(a1, HAB_RADIUS, h0 + wall), ringPoint(a0, HAB_RADIUS, h0 + wall));
        }
        double[] tilt = {0, 0.52, 1.02};
        double squash = 0.82;
        for (int ring = 0; ring < tilt.length; ring++) {
            double r0 = HAB_RADIUS * Math.cos(tilt[ring]);
            double z0 = h0 + wall + HAB_RADIUS * squash * Math.sin(tilt[ring]);
            boolean top = ring == tilt.length - 1;
            double r1 = top ? 0 : HAB_RADIUS * Math.cos(tilt[ring + 1]);
            double z1 = h0 + wall + HAB_RADIUS * squash * (top ? 1 : Math.sin(tilt[ring + 1]));
            for (int k = 0; k < segments; k++) {
                double a0 = k * 2 * Math.PI / segments;
                double a1 = (k + 1) * 2 * Math.PI / segments;
                Color panel = (k + ring) % 2 == 0 ? HULL : Mesh.scale(HULL, 0.95);
                if (top) {
                    mesh.face(inside, panel, ringPoint(a0, r0, z0), ringPoint(a1, r0, z0),
                            p(HAB_U, HAB_V, z1));
                } else {
                    mesh.face(inside, panel, ringPoint(a0, r0, z0), ringPoint(a1, r0, z0),
                            ringPoint(a1, r1, z1), ringPoint(a0, r1, z1));
                }
            }
        }

        // Airlock, facing the camera through the gap in the soil bank.
        mesh.box(HAB_U + 0.85, HAB_V + 0.85, h0, 0.95, 0.95, 0.62, METAL);
        mesh.box(HAB_U + 1.8, HAB_V + 1.08, h0, 0.03, 0.5, 0.48, DARK);
        if (shielding > 0) {
            for (int k : new int[] {14, 5, 15, 4, 0, 3}) {
                bank(mesh, h0, bankHeight, k);
            }
        }
    }

    private static double[] ringPoint(double angle, double radius, double h) {
        return p(HAB_U + Math.cos(angle) * radius, HAB_V + Math.sin(angle) * radius, h);
    }

    /** One of sixteen wedges of the soil bank around the habitat. Wedges 1 and 2 are the doorway. */
    private void bank(Mesh mesh, double h0, double height, int k) {
        double a0 = k * Math.PI / 8;
        double a1 = (k + 1) * Math.PI / 8;
        double inner = HAB_RADIUS + 0.06;
        double crest = HAB_RADIUS + 0.3;
        double foot = crest + height * 0.55;
        double[] inside = p(HAB_U + Math.cos((a0 + a1) / 2) * crest,
                HAB_V + Math.sin((a0 + a1) / 2) * crest, h0 + height / 2);
        Color soil = k % 2 == 0 ? new Color(150, 84, 54) : new Color(160, 92, 58);
        mesh.face(inside, soil, ringPoint(a0, inner, h0 + height), ringPoint(a1, inner, h0 + height),
                ringPoint(a1, crest, h0 + height), ringPoint(a0, crest, h0 + height));
        mesh.face(inside, soil, ringPoint(a0, crest, h0 + height), ringPoint(a1, crest, h0 + height),
                ringPoint(a1, foot, h0), ringPoint(a0, foot, h0));
        // End walls, seen only at the doorway.
        mesh.face(inside, soil, ringPoint(a0, inner, h0), ringPoint(a0, foot, h0),
                ringPoint(a0, crest, h0 + height), ringPoint(a0, inner, h0 + height));
        mesh.face(inside, soil, ringPoint(a1, inner, h0), ringPoint(a1, foot, h0),
                ringPoint(a1, crest, h0 + height), ringPoint(a1, inner, h0 + height));
    }

    /** A glass tunnel of crops. The rows turn from bare soil to green as it gets more energy. */
    private void greenhouse(Mesh mesh, double powerShare) {
        double u0 = GREENHOUSE_U;
        double u1 = GREENHOUSE_U + 3.0;
        double radius = 0.85;
        double h0 = 0;
        double floor = h0 + 0.14;
        mesh.box(u0 - 0.08, GREENHOUSE_V - radius - 0.08, h0, 3.16, 2 * radius + 0.16, 0.14, METAL);
        mesh.sheet(new Color(104, 74, 56), p(u0, GREENHOUSE_V - radius, floor), p(u1, GREENHOUSE_V - radius, floor),
                p(u1, GREENHOUSE_V + radius, floor), p(u0, GREENHOUSE_V + radius, floor));
        Color crop = mix(new Color(136, 104, 70), new Color(58, 176, 72), powerShare);
        double cropHeight = 0.05 + 0.3 * powerShare;
        for (int row = 0; row < 3; row++) {
            double v = GREENHOUSE_V - 0.62 + row * 0.47;
            mesh.box(u0 + 0.15, v, floor, 2.7, 0.3, cropHeight, crop);
        }

        Color glass = new Color(196, 236, 226, 96);
        Color frame = new Color(226, 240, 236, 150);
        double[] inside = p((u0 + u1) / 2, GREENHOUSE_V, floor);
        int arcs = 6;
        // Far side first, so nearer panes are painted over it.
        for (int k = arcs - 1; k >= 0; k--) {
            double a0 = k * Math.PI / arcs;
            double a1 = (k + 1) * Math.PI / arcs;
            mesh.face(inside, k % 2 == 0 ? glass : frame,
                    arcPoint(u0, a0, radius, floor), arcPoint(u1, a0, radius, floor),
                    arcPoint(u1, a1, radius, floor), arcPoint(u0, a1, radius, floor));
        }
        double[][] end = new double[arcs + 1][];
        for (int k = 0; k <= arcs; k++) {
            end[k] = arcPoint(u1, k * Math.PI / arcs, radius, floor);
        }
        mesh.face(inside, frame, end);
    }

    private static double[] arcPoint(double u, double angle, double radius, double floor) {
        return p(u, GREENHOUSE_V + Math.cos(angle) * radius, floor + Math.sin(angle) * radius);
    }

    /** Six panels tilted toward the north, where the Sun is at this latitude. Bluer in more sunlight. */
    private void solarArray(Mesh mesh, double sunlight) {
        Color cell = mix(new Color(44, 50, 78), new Color(46, 128, 240), sunlight);
        Color dust = new Color(190, 140, 100);
        cell = mix(cell, dust, (1 - sunlight) * 0.45);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                double u = SOLAR_U + col * 1.25;
                double v = SOLAR_V + row * 1.1;
                double h0 = 0;
                mesh.box(u + 0.47, v + 0.3, h0, 0.08, 0.08, 0.5, METAL);
                double high = h0 + 0.78;
                double lowEdge = h0 + 0.36;
                double[] below = p(u + 0.5, v + 0.4, h0);
                mesh.face(below, new Color(214, 216, 224), p(u, v, high), p(u + 1.05, v, high),
                        p(u + 1.05, v + 0.85, lowEdge), p(u, v + 0.85, lowEdge));
                for (int part = 0; part < 2; part++) {
                    double c0 = u + 0.06 + part * 0.5;
                    mesh.face(below, cell,
                            p(c0, v + 0.06, high - 0.03 + 0.001), p(c0 + 0.43, v + 0.06, high - 0.03 + 0.001),
                            p(c0 + 0.43, v + 0.79, lowEdge + 0.03 + 0.001), p(c0, v + 0.79, lowEdge + 0.03 + 0.001));
                }
            }
        }
    }

    private void weatherStation(Mesh mesh) {
        double h0 = 0;
        mesh.box(WEATHER_U - 0.22, WEATHER_V - 0.22, h0, 0.44, 0.44, 0.16, METAL);
        mesh.box(WEATHER_U - 0.04, WEATHER_V - 0.04, h0 + 0.16, 0.08, 0.08, 1.15, HULL);
        mesh.box(WEATHER_U - 0.45, WEATHER_V - 0.03, h0 + 1.0, 0.9, 0.06, 0.06, METAL);
        mesh.box(WEATHER_U - 0.52, WEATHER_V - 0.09, h0 + 0.94, 0.18, 0.18, 0.2, SKY_BLUE);
        mesh.box(WEATHER_U + 0.34, WEATHER_V - 0.09, h0 + 0.94, 0.18, 0.18, 0.2, SKY_BLUE);
        mesh.box(WEATHER_U - 0.1, WEATHER_V - 0.1, h0 + 1.31, 0.2, 0.2, 0.12, DARK);
    }

    private void detector(Mesh mesh, boolean storm) {
        double h0 = 0;
        mesh.box(DETECTOR_U - 0.3, DETECTOR_V - 0.3, h0, 0.6, 0.6, 0.36, HULL);
        mesh.box(DETECTOR_U - 0.16, DETECTOR_V - 0.16, h0 + 0.36, 0.32, 0.32, 0.1, DARK);
        mesh.box(DETECTOR_U - 0.09, DETECTOR_V - 0.09, h0 + 0.46, 0.18, 0.18, 0.2,
                storm ? ALERT : new Color(110, 220, 130));
    }

    private void rover(Mesh mesh) {
        double h0 = 0;
        for (int axle = 0; axle < 3; axle++) {
            mesh.box(ROVER_U - 0.42 + axle * 0.34, ROVER_V - 0.3, h0, 0.16, 0.1, 0.16, DARK);
        }
        mesh.box(ROVER_U - 0.4, ROVER_V - 0.2, h0 + 0.14, 0.8, 0.4, 0.2, HULL);
        mesh.box(ROVER_U + 0.2, ROVER_V - 0.05, h0 + 0.34, 0.07, 0.07, 0.36, METAL);
        mesh.box(ROVER_U + 0.13, ROVER_V - 0.1, h0 + 0.7, 0.2, 0.18, 0.12, HULL);
        for (int axle = 0; axle < 3; axle++) {
            mesh.box(ROVER_U - 0.42 + axle * 0.34, ROVER_V + 0.2, h0, 0.16, 0.1, 0.16, DARK);
        }
    }

    private void rock(Mesh mesh, Rock rock) {
        double h0 = groundAt(rock.u(), rock.v());
        double[] inside = p(rock.u(), rock.v(), h0);
        double[] peak = p(rock.u() + rock.size() * 0.15, rock.v() - rock.size() * 0.1, h0 + rock.size() * 0.9);
        Color stone = Mesh.scale(new Color(126, 74, 56), rock.tone());
        for (int k = 0; k < rock.sides(); k++) {
            double a0 = rock.turn() + k * 2 * Math.PI / rock.sides();
            double a1 = rock.turn() + (k + 1) * 2 * Math.PI / rock.sides();
            mesh.face(inside, stone,
                    p(rock.u() + Math.cos(a0) * rock.size(), rock.v() + Math.sin(a0) * rock.size(), h0),
                    p(rock.u() + Math.cos(a1) * rock.size(), rock.v() + Math.sin(a1) * rock.size(), h0),
                    peak);
        }
    }

    // ---- Sky effects ----------------------------------------------------------------

    /** Dust in the air: the less sunlight gets through, the thicker the haze over the scene. */
    private void paintDust(Graphics2D g, double sunlight) {
        int alpha = (int) Math.round(150 * Math.pow(1 - sunlight, 1.3));
        if (alpha > 0) {
            g.setColor(new Color(196, 148, 104, alpha));
            g.fillRect(-WIDTH, -HEIGHT, 3 * WIDTH, 3 * HEIGHT);
        }
    }

    /** Streaks for the particles of a solar storm. The pattern depends only on the sol. */
    private void paintStorm(Graphics2D g) {
        Random random = new Random(state.sol().number() * 7919L);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 70; i++) {
            double x = random.nextDouble() * (WIDTH + 200) - 100;
            double y = 50 + random.nextDouble() * (HEIGHT - 120);
            double length = 14 + random.nextDouble() * 26;
            g.setColor(new Color(255, 120 + random.nextInt(80), 220, 70 + random.nextInt(90)));
            g.draw(new Line2D.Double(x, y, x - length * 0.5, y + length));
        }
        g.setStroke(new BasicStroke(0.8f));
    }

    // ---- Text and controls ----------------------------------------------------------

    private static final Font TITLE_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font BODY_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    private static final Font SMALL_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final Color EDGE = new Color(255, 255, 255, 80);
    private static final Color MUTED = new Color(196, 200, 214);
    private static final Color CREAM = new Color(255, 244, 232, 235);
    private static final Color EMPTY_CELL = new Color(62, 66, 88);
    private static final Color CARD_FILL = new Color(24, 28, 44);

    /** The strip along the top that holds the sol, crew health, food and energy. */
    private static final Rectangle2D STATUS = new Rectangle2D.Double(12, 10, 776, 44);
    private static final Rectangle2D END_SOL_BUTTON = new Rectangle2D.Double(648, 536, 140, 38);
    private static final Rectangle2D ABOUT_BUTTON = new Rectangle2D.Double(636, 580, 86, 16);
    private static final Rectangle2D RESTART_BUTTON = new Rectangle2D.Double(728, 580, 60, 16);
    private static final Rectangle2D PLAY_AGAIN_BUTTON = new Rectangle2D.Double(335, 322, 130, 34);
    private static final Rectangle2D ENDING_BOX = new Rectangle2D.Double(170, 214, 460, 158);

    /** A square-cornered box with a one-pixel edge, snapped to whole pixels so it stays crisp. */
    private static void box(Graphics2D g, double x, double y, double width, double height, Color fill, Color edge) {
        double left = Math.round(x);
        double top = Math.round(y);
        double w = Math.round(width);
        double h = Math.round(height);
        g.setColor(fill);
        g.fill(new Rectangle2D.Double(left, top, w, h));
        if (edge != null) {
            g.setStroke(new BasicStroke(1f));
            g.setColor(edge);
            g.draw(new Rectangle2D.Double(left + 0.5, top + 0.5, w - 1, h - 1));
        }
    }

    private static Color alpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    private static void centred(Graphics2D g, String text, double centreX, double baseline) {
        g.drawString(text, (float) (centreX - g.getFontMetrics().stringWidth(text) / 2.0), (float) baseline);
    }

    private static void rightAligned(Graphics2D g, String text, double right, double baseline) {
        g.drawString(text, (float) (right - g.getFontMetrics().stringWidth(text)), (float) baseline);
    }

    /** The small triangle under a tag, button or card that points at its building. */
    private static void pointer(Graphics2D g, double x, double bottom, Color colour) {
        Path2D.Double pointer = new Path2D.Double();
        pointer.moveTo(x - 6, bottom - 1);
        pointer.lineTo(x + 6, bottom - 1);
        pointer.lineTo(x, bottom + 7);
        pointer.closePath();
        g.setColor(colour);
        g.fill(pointer);
    }

    private static Stat text(String text) {
        return new Stat(null, null, text, Color.WHITE);
    }

    private static Stat text(String text, Color colour) {
        return new Stat(null, null, text, colour);
    }

    /** Splits a sentence into lines no wider than {@code width}. */
    private static List<String> wrap(String sentence, FontMetrics metrics, double width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : sentence.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(longer) > width) {
                lines.add(line);
                line = word;
            } else {
                line = longer;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /** True while today's solar energy is still waiting to be collected. */
    private boolean collectable() {
        return state.phase() == Phase.PLAYING && !state.collected();
    }

    /** A flat button. {@code bolt} adds the energy icon before the text. */
    private void button(Graphics2D g, Rectangle2D area, String text, Color fill, Spot spot,
                        boolean enabled, boolean bolt) {
        boolean hot = enabled && hover.spot() == spot;
        Color face = !enabled ? new Color(86, 88, 100) : hot ? Mesh.scale(fill, 1.12) : fill;
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), face,
                hot ? Color.WHITE : new Color(20, 22, 34));
        Color ink = enabled ? new Color(24, 26, 38) : new Color(150, 152, 164);
        g.setFont(TITLE_FONT);
        double centre = area.getCenterX() + (bolt ? 8 : 0);
        if (bolt) {
            double textWidth = g.getFontMetrics().stringWidth(text);
            Glyphs.bolt(g, centre - textWidth / 2 - 17, area.getCenterY() - 7, 14, ink);
        }
        g.setColor(ink);
        centred(g, text, centre, area.getCenterY() + 4.5);
    }

    /** A small button for things that are not part of playing a sol. */
    private void quietButton(Graphics2D g, Rectangle2D area, String text, Spot spot) {
        boolean hot = hover.spot() == spot;
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(),
                hot ? new Color(255, 255, 255, 46) : new Color(24, 28, 44, 110), hot ? Color.WHITE : EDGE);
        g.setFont(SMALL_FONT);
        g.setColor(hot ? Color.WHITE : CREAM);
        centred(g, text, area.getCenterX(), area.getCenterY() + 3.5);
    }

    /** Where the COLLECT button sits: over the solar panels, where their tag goes afterwards. */
    private Rectangle2D collectButton() {
        return new Rectangle2D.Double(Math.round(solarAnchor[0]) - 66, Math.round(solarAnchor[1] - 7 - 28), 132, 28);
    }

    private void paintCollectButton(Graphics2D g) {
        if (collectable()) {
            Rectangle2D area = collectButton();
            pointer(g, solarAnchor[0], area.getMaxY(),
                    hover.spot() == Spot.COLLECT ? Mesh.scale(Glyphs.POWER, 1.12) : Glyphs.POWER);
            button(g, area, "COLLECT +" + state.solarOutput(), Glyphs.POWER, Spot.COLLECT, true, true);
        }
    }

    // ---- Building tags --------------------------------------------------------------

    private static final double CHIP_HEIGHT = 20;
    private static final double CHIP_PAD = 8;
    private static final double CHIP_GAP = 9;
    private static final double CHIP_ICON = 11;
    /**
     * The detector stands just left of the habitat, so its tag and its card hang off to the
     * left: they reach no further right of it than this, and so stay clear of the habitat's.
     */
    private static final double DETECTOR_REACH = 38;

    /**
     * The tags to show now, one per building. Used to paint and to click. Each holds only
     * the number that matters for today's choices; the rest is on the building's card. A
     * building whose card is open has no tag, and neither has one that the open card, or the
     * box at the end of the mission, would partly cover.
     */
    private List<Chip> chips() {
        PowerAllocation power = state.allocation();
        PowerAllocation limit = state.limit();
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        boolean dusty = weather.sunlight() < 0.5;
        boolean storm = radiation.solarEvent();
        boolean cold = state.forecast().heatShort() > 0;

        List<Chip> all = new ArrayList<>();
        Stat tonight = text(String.format(Locale.ENGLISH, "%.0f°C tonight", weather.minTempC()));
        Stat dose = text(String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv()));
        if (state.sol().earthDate().equals(weather.measuredOn())) {
            all.add(chip(Spot.WEATHER, weatherAnchor, dusty ? Glyphs.WARN : SKY_BLUE,
                    dusty ? "Dusty sky" : "Weather", tonight));
        } else {
            // The rover has nothing for this date, so say the reading is not today's.
            all.add(chip(Spot.WEATHER, weatherAnchor, dusty ? Glyphs.WARN : SKY_BLUE,
                    dusty ? "Dusty sky" : "Weather", tonight, text("no reading today", MUTED)));
        }
        all.add(chip(Spot.DETECTOR, detectorAnchor, DETECTOR_REACH, storm ? ALERT : Color.WHITE,
                storm ? "Solar storm!" : "Radiation", dose));
        all.add(chip(Spot.HABITAT, habitatAnchor, WARM, "Habitat",
                new Stat(Glyphs::flame, Glyphs.HEAT, power.heating() + "/" + limit.heating(),
                        cold ? Glyphs.BAD : Color.WHITE),
                new Stat(Glyphs::shield, Glyphs.SHIELD, power.shielding() + "/" + limit.shielding(),
                        Color.WHITE)));
        all.add(chip(Spot.GREENHOUSE, greenhouseAnchor, LEAF_GREEN, "Greenhouse",
                new Stat(Glyphs::leaf, Glyphs.GREENHOUSE, power.greenhouse() + "/" + limit.greenhouse(),
                        Color.WHITE)));
        if (!collectable()) {
            // Until then the COLLECT button stands here instead.
            all.add(chip(Spot.NONE, solarAnchor, SUN_YELLOW, "Solar panels",
                    new Stat(Glyphs::bolt, Glyphs.POWER,
                            "+" + state.solarOutput() + (state.collected() ? " collected" : ""), Color.WHITE)));
        }

        Card card = card();
        Rectangle2D cover = state.phase() != Phase.PLAYING ? ENDING_BOX : card == null ? null : card.box();
        if (cover == null) {
            return all;
        }
        List<Chip> clear = new ArrayList<>();
        for (Chip chip : all) {
            if ((card == null || chip.spot() != selected) && !cover.intersects(chip.box())) {
                clear.add(chip);
            }
        }
        return clear;
    }

    private Chip chip(Spot spot, double[] anchor, Color accent, String title, Stat... stats) {
        return chip(spot, anchor, Double.POSITIVE_INFINITY, accent, title, stats);
    }

    /** A tag centred over {@code anchor}, or as near to that as {@code reach} to its right allows. */
    private Chip chip(Spot spot, double[] anchor, double reach, Color accent, String title, Stat... stats) {
        FontMetrics body = getFontMetrics(BODY_FONT);
        double width = 2 * CHIP_PAD + getFontMetrics(TITLE_FONT).stringWidth(title);
        for (Stat stat : stats) {
            width += CHIP_GAP + (stat.icon() == null ? 0 : CHIP_ICON + 3) + body.stringWidth(stat.text());
        }
        width = Math.ceil(width);
        double left = Math.round(Math.min(anchor[0] - width / 2, anchor[0] + reach - width));
        return new Chip(spot, new Rectangle2D.Double(left, Math.round(anchor[1] - 7 - CHIP_HEIGHT), width, CHIP_HEIGHT),
                anchor[0], accent, title, List.of(stats));
    }

    /** {@code hinted} is what the guide is pointing the player at; its tag is ringed to match. */
    private void paintChip(Graphics2D g, Chip chip, Spot hinted) {
        Rectangle2D area = chip.box();
        // A tag that can be clicked has an edge in its own colour, brighter under the mouse.
        boolean clickable = chip.spot() != Spot.NONE;
        Color edge = !clickable ? EDGE
                : hover.spot() == chip.spot() ? chip.accent() : alpha(chip.accent(), 150);
        pointer(g, chip.anchorX(), area.getMaxY(), BUBBLE);
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), BUBBLE, edge);
        if (clickable && chip.spot() == hinted) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(Glyphs.POWER);
            g.draw(new Rectangle2D.Double(area.getX() - 1, area.getY() - 1, area.getWidth() + 2, area.getHeight() + 2));
        }

        double x = area.getX() + CHIP_PAD;
        float baseline = (float) area.getY() + 14;
        g.setFont(TITLE_FONT);
        g.setColor(chip.accent());
        g.drawString(chip.title(), (float) x, baseline);
        x += g.getFontMetrics().stringWidth(chip.title());
        g.setFont(BODY_FONT);
        for (Stat stat : chip.stats()) {
            x += CHIP_GAP;
            if (stat.icon() != null) {
                stat.icon().draw(g, x, area.getY() + 4.5, CHIP_ICON, stat.iconColour());
                x += CHIP_ICON + 3;
            }
            g.setColor(stat.textColour());
            g.drawString(stat.text(), (float) x, baseline);
            x += g.getFontMetrics().stringWidth(stat.text());
        }
    }

    // ---- Cards ----------------------------------------------------------------------

    private static final double CARD_WIDTH = 252;
    private static final double CARD_HEAD = 26;
    private static final double CARD_ROW = 46;
    private static final double FACT_ROW = 16;
    private static final double NOTE_ROW = 14;

    /** The card for whatever is selected, or null when none is open. Used to paint and to click. */
    private Card card() {
        if (state == null || state.phase() != Phase.PLAYING) {
            return null;
        }
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        String title;
        Color accent;
        String corner = null;
        double[] anchor = null;
        double reach = Double.POSITIVE_INFINITY;
        PowerUse[] uses = {};
        List<Fact> facts = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        switch (selected) {
            case HABITAT:
                title = "Habitat";
                accent = WARM;
                anchor = habitatAnchor;
                corner = state.energy() + " energy left";
                uses = new PowerUse[] {PowerUse.HEAT, PowerUse.SHIELD};
                break;
            case GREENHOUSE:
                title = "Greenhouse";
                accent = LEAF_GREEN;
                anchor = greenhouseAnchor;
                corner = state.energy() + " energy left";
                uses = new PowerUse[] {PowerUse.GREENHOUSE};
                break;
            case WEATHER:
                title = "Weather station";
                accent = SKY_BLUE;
                anchor = weatherAnchor;
                facts.add(new Fact("Sky", weather.sunlight() < 0.5 ? "Dusty" : "Clear"));
                facts.add(new Fact("Sunlight", String.format(Locale.ENGLISH, "%.0f%%", weather.sunlight() * 100)));
                facts.add(new Fact("Low and high", String.format(Locale.ENGLISH, "%.0f°C and %.0f°C",
                        weather.minTempC(), weather.maxTempC())));
                if (!Double.isNaN(weather.pressurePa())) {
                    facts.add(new Fact("Air pressure", String.format(Locale.ENGLISH, "%.0f Pa", weather.pressurePa())));
                }
                if (weather.sunrise() != null && weather.sunset() != null) {
                    facts.add(new Fact("Sunrise and sunset", weather.sunrise() + " and " + weather.sunset()));
                }
                if (weather.uv() != UvIndex.UNKNOWN) {
                    facts.add(new Fact("UV light", weather.uv().label()));
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
                anchor = detectorAnchor;
                reach = DETECTOR_REACH;
                facts.add(new Fact("Radiation today", String.format(Locale.ENGLISH, "%.2f mSv", radiation.doseMsv())));
                sentences.add(radiation.solarEvent() ? "A solar storm has raised it today."
                        : "No solar storm today.");
                sentences.add("A shield on the habitat stops some of it.");
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
                        terrain.spacingM / 1000, HEIGHT_EXAGGERATION));
                break;
            default:
                return null;
        }

        List<String> notes = new ArrayList<>();
        for (String sentence : sentences) {
            notes.addAll(wrap(sentence, getFontMetrics(BODY_FONT), CARD_WIDTH - 24));
        }
        double height = CARD_HEAD + CARD_ROW * uses.length + FACT_ROW * facts.size() + NOTE_ROW * notes.size()
                + (facts.isEmpty() || notes.isEmpty() ? 0 : 4) + (uses.length > 0 ? 4 : 8);
        double left;
        double top;
        if (anchor == null) {
            left = Math.round((WIDTH - CARD_WIDTH) / 2);
            top = Math.round((HEIGHT - height) / 2);
        } else {
            left = Math.min(anchor[0] - CARD_WIDTH / 2, anchor[0] + reach - CARD_WIDTH);
            left = Math.round(Math.max(6, Math.min(WIDTH - 6 - CARD_WIDTH, left)));
            top = Math.round(Math.max(STATUS.getMaxY() + 6, anchor[1] - 7 - height));
        }
        List<Stepper> steppers = new ArrayList<>();
        for (int i = 0; i < uses.length; i++) {
            double rowTop = top + CARD_HEAD + CARD_ROW * i;
            double right = left + CARD_WIDTH - 10;
            steppers.add(new Stepper(uses[i], rowTop,
                    new Rectangle2D.Double(right - 94, rowTop + 3, 24, 22),
                    new Rectangle2D.Double(right - 24, rowTop + 3, 24, 22)));
        }
        // No pointer if the card had to be pushed down over its own building.
        boolean pointed = anchor != null && top + height <= anchor[1] - 6;
        return new Card(new Rectangle2D.Double(left, top, CARD_WIDTH, height), anchor == null ? 0 : anchor[0],
                pointed, title, accent, corner, steppers, facts, notes);
    }

    private void paintCard(Graphics2D g) {
        Card card = card();
        if (card == null) {
            return;
        }
        Rectangle2D area = card.box();
        if (card.pointed()) {
            pointer(g, card.anchorX(), area.getMaxY(), CARD_FILL);
        }
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), CARD_FILL, card.accent());
        g.setFont(TITLE_FONT);
        g.setColor(card.accent());
        g.drawString(card.title(), (float) area.getX() + 10, (float) area.getY() + 17);
        if (card.corner() != null) {
            g.setFont(BODY_FONT);
            g.setColor(MUTED);
            rightAligned(g, card.corner(), area.getMaxX() - 10, area.getY() + 17);
        }

        for (Stepper row : card.steppers()) {
            paintStepper(g, area, row);
        }
        double y = area.getY() + CARD_HEAD + CARD_ROW * card.steppers().size();
        for (Fact fact : card.facts()) {
            g.setFont(BODY_FONT);
            g.setColor(MUTED);
            g.drawString(fact.name(), (float) area.getX() + 10, (float) y + 11);
            g.setFont(TITLE_FONT);
            g.setColor(Color.WHITE);
            rightAligned(g, fact.value(), area.getMaxX() - 10, y + 11);
            y += FACT_ROW;
        }
        if (!card.facts().isEmpty() && !card.notes().isEmpty()) {
            y += 4;
        }
        g.setFont(BODY_FONT);
        g.setColor(MUTED);
        for (String line : card.notes()) {
            g.drawString(line, (float) area.getX() + 10, (float) y + 10);
            y += NOTE_ROW;
        }
    }

    /** One use of energy on a card: its name, minus and plus buttons, and what the spending does. */
    private void paintStepper(Graphics2D g, Rectangle2D area, Stepper row) {
        SolReport forecast = state.forecast();
        int spent = state.allocation().get(row.use());
        int most = state.limit().get(row.use());
        String name;
        String effect;
        double iconX = area.getX() + 10;
        double iconY = row.top() + 7;
        switch (row.use()) {
            case HEAT:
                Glyphs.flame(g, iconX, iconY, 14, Glyphs.HEAT);
                name = "Heat";
                effect = forecast.heatShort() > 0
                        ? String.format(Locale.ENGLISH, "Tonight falls to %.0f°C. Needs %d more.",
                                state.weather().minTempC(), forecast.heatShort())
                        : String.format(Locale.ENGLISH, "Warm enough for tonight's %.0f°C.",
                                state.weather().minTempC());
                break;
            case SHIELD:
                Glyphs.shield(g, iconX, iconY, 14, Glyphs.SHIELD);
                name = "Shield";
                effect = String.format(Locale.ENGLISH, "Stops %.0f%% of today's radiation.",
                        forecast.shieldBlocked() * 100);
                break;
            default:
                Glyphs.leaf(g, iconX, iconY, 14, Glyphs.GREENHOUSE);
                name = "Grow food";
                effect = String.format(Locale.ENGLISH, "Grows %.1f days of food. Crew eats %.0f a day.",
                        forecast.foodGrown(), forecast.foodEaten());
                break;
        }
        g.setFont(TITLE_FONT);
        g.setColor(Color.WHITE);
        g.drawString(name, (float) iconX + 20, (float) row.top() + 19);
        stepperButton(g, row.minus(), "-", Spot.MINUS, row.use(),
                spent > 0 && state.energy() < state.energyCapacity());
        stepperButton(g, row.plus(), "+", Spot.PLUS, row.use(), state.energy() > 0 && spent < most);
        g.setFont(TITLE_FONT);
        g.setColor(Color.WHITE);
        centred(g, spent + " of " + most, (row.minus().getMaxX() + row.plus().getX()) / 2, row.top() + 19);
        g.setFont(BODY_FONT);
        g.setColor(MUTED);
        g.drawString(effect, (float) iconX, (float) row.top() + 39);
    }

    private void stepperButton(Graphics2D g, Rectangle2D area, String sign, Spot spot, PowerUse use,
                               boolean enabled) {
        boolean hot = enabled && hover.spot() == spot && hover.use() == use;
        Color face = !enabled ? new Color(70, 72, 86) : hot ? Color.WHITE : Glyphs.POWER;
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), face, new Color(20, 22, 34));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(enabled ? new Color(24, 26, 38) : new Color(128, 130, 144));
        centred(g, sign, area.getCenterX(), area.getCenterY() + 5);
    }

    // ---- Mouse ----------------------------------------------------------------------

    /** What is under the mouse, in the 800 x 600 layout the scene is drawn in. */
    private Hit hitAt(MouseEvent e) {
        if (state == null) {
            return NOTHING;
        }
        double scale = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
        double x = (e.getX() - (getWidth() - WIDTH * scale) / 2) / scale;
        double y = (e.getY() - (getHeight() - HEIGHT * scale) / 2) / scale;
        if (state.phase() != Phase.PLAYING) {
            return PLAY_AGAIN_BUTTON.contains(x, y) ? new Hit(Spot.PLAY_AGAIN, null) : NOTHING;
        }
        Card card = card();
        if (card != null && card.box().contains(x, y)) {
            for (Stepper row : card.steppers()) {
                if (row.minus().contains(x, y)) {
                    return new Hit(Spot.MINUS, row.use());
                }
                if (row.plus().contains(x, y)) {
                    return new Hit(Spot.PLUS, row.use());
                }
            }
            return new Hit(Spot.CARD, null);
        }
        if (END_SOL_BUTTON.contains(x, y)) {
            return new Hit(Spot.END_SOL, null);
        }
        if (RESTART_BUTTON.contains(x, y)) {
            return new Hit(Spot.RESTART, null);
        }
        if (ABOUT_BUTTON.contains(x, y)) {
            return new Hit(Spot.ABOUT, null);
        }
        if (collectable() && collectButton().contains(x, y)) {
            return new Hit(Spot.COLLECT, null);
        }
        for (Chip chip : chips()) {
            if (chip.spot() != Spot.NONE && chip.box().contains(x, y)) {
                return new Hit(chip.spot(), null);
            }
        }
        // The buildings themselves. The two small instruments come first so a big neighbour cannot hide them.
        if (weatherArea.contains(x, y)) {
            return new Hit(Spot.WEATHER, null);
        }
        if (detectorArea.contains(x, y)) {
            return new Hit(Spot.DETECTOR, null);
        }
        if (habitatArea.contains(x, y)) {
            return new Hit(Spot.HABITAT, null);
        }
        if (greenhouseArea.contains(x, y)) {
            return new Hit(Spot.GREENHOUSE, null);
        }
        if (collectable() && solarArea.contains(x, y)) {
            return new Hit(Spot.COLLECT, null);
        }
        return NOTHING;
    }

    private void click(Hit hit) {
        switch (hit.spot()) {
            case COLLECT:
                actions.collect();
                break;
            case HABITAT:
            case GREENHOUSE:
            case WEATHER:
            case DETECTOR:
            case ABOUT:
                selected = hit.spot();
                break;
            case PLUS:
                actions.spend(hit.use(), 1);
                break;
            case MINUS:
                actions.spend(hit.use(), -1);
                break;
            case END_SOL:
                selected = Spot.NONE;
                actions.nextSol();
                break;
            case RESTART:
            case PLAY_AGAIN:
                selected = Spot.NONE;
                actions.restart();
                break;
            case CARD:
                break;
            default:
                selected = Spot.NONE;
                break;
        }
        repaint();
    }

    private void point(Hit hit) {
        if (!hit.equals(hover)) {
            hover = hit;
            boolean hand = hit.spot() != Spot.NONE && hit.spot() != Spot.CARD;
            setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }
    }

    // ---- Heads-up display -----------------------------------------------------------

    /** What the player should do next. Shown in the guide, bottom left. */
    private Hint hint() {
        SolReport forecast = state.forecast();
        if (state.phase() != Phase.PLAYING) {
            return new Hint("", Spot.NONE);
        }
        if (!state.collected()) {
            return new Hint("Click COLLECT on the solar panels.", Spot.COLLECT);
        }
        if (forecast.heatShort() > 0 && state.energy() > 0) {
            return new Hint("The habitat will be cold tonight. Click it.", Spot.HABITAT);
        }
        if (state.energy() > 0 && forecast.foodGrown() == 0) {
            return new Hint("Click the greenhouse to grow food.", Spot.GREENHOUSE);
        }
        return new Hint("Spend more, or save it and press END SOL.", Spot.END_SOL);
    }

    private void paintHud(Graphics2D g, Hint hint) {
        paintStatus(g);

        // Landmark and compass, drawn straight onto the picture. North is toward the lower left.
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        g.setColor(new Color(60, 30, 20, 210));
        g.drawString("Lower slopes of Mount Sharp", 446, 76);
        double cx = 768;
        double cy = 508;
        Path2D.Double needle = new Path2D.Double();
        needle.moveTo(cx - 5, cy + 9);
        needle.lineTo(cx + 15, cy + 5);
        needle.lineTo(cx + 10, cy - 5);
        needle.closePath();
        g.setColor(CREAM);
        g.fill(needle);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("N", (float) cx - 17, (float) cy + 4);

        // A solar storm is news, so it gets a line of its own under the strip.
        RadiationConditions radiation = state.radiation();
        if (radiation.solarEvent() && radiation.detail() != null) {
            g.setFont(BODY_FONT);
            int width = g.getFontMetrics().stringWidth(radiation.detail()) + 24;
            double top = STATUS.getMaxY() + 6;
            box(g, (WIDTH - width) / 2.0, top, width, 22, new Color(110, 20, 70, 235), ALERT);
            g.setColor(Color.WHITE);
            centred(g, radiation.detail(), WIDTH / 2.0, top + 15);
        }

        if (state.phase() == Phase.PLAYING) {
            paintGuide(g, hint);
            button(g, END_SOL_BUTTON, "END SOL  >", Glyphs.GOOD, Spot.END_SOL, true, false);
            quietButton(g, ABOUT_BUTTON, "About the data", Spot.ABOUT);
            quietButton(g, RESTART_BUTTON, "Restart", Spot.RESTART);
        }

        g.setFont(SMALL_FONT);
        g.setColor(CREAM);
        g.drawString("Real NASA data, saved copy (not live).  Weather: Curiosity rover.  "
                + "Solar storms: DONKI.  Ground: MOLA.", 12, HEIGHT - 8);
    }

    /**
     * The strip along the top: which sol it is, then the three things the player looks
     * after. Each of the three has the same shape, a name and a number over a bar.
     */
    private void paintStatus(Graphics2D g) {
        SolReport forecast = state.forecast();
        boolean playing = state.phase() == Phase.PLAYING;
        double left = STATUS.getX();
        double top = STATUS.getY();
        box(g, left, top, STATUS.getWidth(), STATUS.getHeight(), BUBBLE, EDGE);

        g.setFont(BIG_FONT);
        g.setColor(Color.WHITE);
        g.drawString("Sol " + state.sol().number() + " of " + state.missionSols(), (float) left + 11, (float) top + 21);
        g.setFont(BODY_FONT);
        g.setColor(MUTED);
        g.drawString("Earth date " + DATE.format(state.sol().earthDate()), (float) left + 11, (float) top + 36);

        // Once the mission is over there is no tonight, so the bars show only what is left.
        double healthAfter = playing ? forecast.healthAfter() : state.crewHealth();
        double foodAfter = playing ? forecast.foodAfter() : state.foodDays();
        meter(g, left + 176, 192, Glyphs::heart, Glyphs.HEALTH, "Crew health",
                String.format(Locale.ENGLISH, "%.0f", state.crewHealth()),
                state.crewHealth() / 100, healthAfter / 100, healthAfter - state.crewHealth());
        meter(g, left + 368, 192, Glyphs::leaf, Glyphs.GREENHOUSE, "Food",
                String.format(Locale.ENGLISH, "%.1f days", state.foodDays()),
                state.foodDays() / 10, foodAfter / 10, foodAfter - state.foodDays());
        paintEnergy(g, left + 560, STATUS.getMaxX() - (left + 560));
    }

    /** Starts a section of the strip at {@code left}: a faint line, an icon and a name. */
    private void section(Graphics2D g, double left, Icon icon, Color colour, String name) {
        double top = STATUS.getY();
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new Rectangle2D.Double(left, top + 7, 1, STATUS.getHeight() - 14));
        icon.draw(g, left + 12, top + 7.5, 12, colour);
        g.setFont(BODY_FONT);
        g.setColor(Color.WHITE);
        g.drawString(name, (float) left + 30, (float) top + 18);
    }

    /**
     * A bar in the strip: how full something is now ({@code share}, 0.0 to 1.0) and how
     * full it is about to be ({@code shareAfter}). The end that is about to go is drawn
     * faded; what is about to be added is an outline.
     */
    private static void bar(Graphics2D g, double left, double width, Color colour, double share,
                            double shareAfter) {
        double top = STATUS.getY() + 27;
        double now = width * clamp(share);
        double after = width * clamp(shareAfter);
        g.setColor(EMPTY_CELL);
        g.fill(new Rectangle2D.Double(left, top, width, 8));
        g.setColor(colour);
        g.fill(new Rectangle2D.Double(left, top, Math.min(now, after), 8));
        if (after < now) {
            g.setColor(alpha(colour, 90));
            g.fill(new Rectangle2D.Double(left + after, top, now - after, 8));
        } else if (after > now) {
            g.setStroke(new BasicStroke(1f));
            g.draw(new Rectangle2D.Double(left + now + 0.5, top + 0.5, after - now - 1, 7));
        }
    }

    /** Crew health or food: how much there is now, and what tonight will do to it. */
    private void meter(Graphics2D g, double left, double width, Icon icon, Color colour, String name,
                       String value, double share, double shareAfter, double change) {
        double top = STATUS.getY();
        section(g, left, icon, colour, name);
        g.setFont(TITLE_FONT);
        rightAligned(g, value, left + width - 12, top + 18);

        double barWidth = 90;
        bar(g, left + 12, barWidth, colour, share, shareAfter);
        if (Math.abs(change) >= 0.05) {
            g.setFont(BODY_FONT);
            g.setColor(change < 0 ? Glyphs.BAD : Glyphs.GOOD);
            g.drawString(String.format(Locale.ENGLISH, "%+.1f tonight", change),
                    (float) (left + 12 + barWidth + 7), (float) top + 35);
        }
    }

    /** The battery: how much energy is stored, and how much more is waiting to be collected. */
    private void paintEnergy(Graphics2D g, double left, double width) {
        double top = STATUS.getY();
        int capacity = state.energyCapacity();
        section(g, left, Glyphs::bolt, Glyphs.POWER, "Energy");
        g.setFont(TITLE_FONT);
        rightAligned(g, state.energy() + " / " + capacity, left + width - 12, top + 18);

        // The part that would fill if today's solar energy were collected is shown as an outline.
        int waiting = collectable() ? Math.min(capacity, state.energy() + state.solarOutput()) : state.energy();
        bar(g, left + 12, width - 24, Glyphs.POWER, state.energy() / (double) capacity,
                waiting / (double) capacity);
    }

    /**
     * Bottom left: what to do next, and under it what tonight will do to the crew's health
     * and why. The total is beside the health bar; what happens to the food is beside the
     * food bar and on the greenhouse's card.
     */
    private void paintGuide(Graphics2D g, Hint hint) {
        SolReport forecast = state.forecast();
        List<Stat> causes = new ArrayList<>();
        causes.add(text(String.format(Locale.ENGLISH, "Radiation %+.1f", forecast.healthRadiation()),
                state.radiation().solarEvent() ? ALERT : Glyphs.WARN));
        if (forecast.heatShort() > 0) {
            causes.add(text(String.format(Locale.ENGLISH, "Cold %+.1f", forecast.healthCold()), Glyphs.BAD));
        }
        if (forecast.hungry()) {
            causes.add(text(String.format(Locale.ENGLISH, "Hunger %+.1f", forecast.healthHunger()), Glyphs.BAD));
        }
        if (forecast.healthRest() > 0) {
            causes.add(text(String.format(Locale.ENGLISH, "Warm and fed %+.1f", forecast.healthRest()),
                    Glyphs.GOOD));
        }

        FontMetrics body = g.getFontMetrics(BODY_FONT);
        String lead = "Tonight:";
        double row = 17 + body.stringWidth(lead);
        for (Stat cause : causes) {
            row += 10 + body.stringWidth(cause.text());
        }
        double width = Math.ceil(Math.max(g.getFontMetrics(TITLE_FONT).stringWidth(hint.text()), row)) + 22;
        double top = 528;
        box(g, 12, top, width, 46, BUBBLE, Glyphs.POWER);
        g.setFont(TITLE_FONT);
        g.setColor(Glyphs.POWER);
        g.drawString(hint.text(), 23, (float) top + 18);

        double x = 23;
        Glyphs.heart(g, x, top + 26, 12, Glyphs.HEALTH);
        x += 17;
        g.setFont(BODY_FONT);
        g.setColor(MUTED);
        g.drawString(lead, (float) x, (float) top + 36);
        x += body.stringWidth(lead);
        for (Stat cause : causes) {
            x += 10;
            g.setColor(cause.textColour());
            g.drawString(cause.text(), (float) x, (float) top + 36);
            x += body.stringWidth(cause.text());
        }
    }

    /** The end of the mission: what happened, and a button to play again. */
    private void paintEnding(Graphics2D g) {
        boolean won = state.phase() == Phase.WON;
        SolReport last = state.forecast();
        g.setColor(new Color(10, 10, 20, 130));
        g.fillRect(-WIDTH, -HEIGHT, 3 * WIDTH, 3 * HEIGHT);
        box(g, ENDING_BOX.getX(), ENDING_BOX.getY(), ENDING_BOX.getWidth(), ENDING_BOX.getHeight(), CARD_FILL,
                won ? Glyphs.GOOD : Glyphs.BAD);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.setColor(won ? Glyphs.GOOD : Glyphs.BAD);
        centred(g, won ? "Mission complete!" : "Mission over", WIDTH / 2.0, 248);
        g.setFont(TITLE_FONT);
        g.setColor(Color.WHITE);
        if (won) {
            centred(g, "The crew stayed safe for all " + state.missionSols() + " sols.", WIDTH / 2.0, 274);
            centred(g, String.format(Locale.ENGLISH, "Crew health %.0f.  Food left: %.1f days.",
                    state.crewHealth(), state.foodDays()), WIDTH / 2.0, 294);
        } else {
            centred(g, "The crew's health ran out on sol " + state.sol().number() + " and they had to go home.",
                    WIDTH / 2.0, 274);
            centred(g, String.format(Locale.ENGLISH, "Last night:  radiation %+.1f   cold %+.1f   hunger %+.1f",
                    last.healthRadiation(), last.healthCold(), last.healthHunger()), WIDTH / 2.0, 294);
        }
        button(g, PLAY_AGAIN_BUTTON, "PLAY AGAIN", Glyphs.POWER, Spot.PLAY_AGAIN, true, false);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
