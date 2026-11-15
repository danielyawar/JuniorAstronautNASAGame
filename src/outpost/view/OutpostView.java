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

    private record Label(double x, double y, Color accent, String title, String[] lines, Spot spot) {}

    /** Things on screen that can be clicked. */
    private enum Spot { NONE, HABITAT, GREENHOUSE, SOLAR, COLLECT, END_SOL, RESTART, PLAY_AGAIN, CARD, MINUS, PLUS }

    /** What is under the mouse. {@code use} is set for the MINUS and PLUS buttons of a card. */
    private record Hit(Spot spot, PowerUse use) {}

    private static final Hit NOTHING = new Hit(Spot.NONE, null);

    /** One line of a building's card: a use of energy with its minus and plus buttons. */
    private record Stepper(PowerUse use, double top, Rectangle2D minus, Rectangle2D plus) {}

    /** The box of controls that opens over a building when it is clicked. */
    private record Card(Rectangle2D box, double anchorX, double anchorY, String title, Color accent,
                        List<Stepper> rows) {}

    private final transient Terrain terrain;
    private final int tiles;
    /** Ground height in tiles at each grid corner, indexed [u][v], already exaggerated. */
    private final double[][] ground;
    /** Camera for the buildings: the ground camera enlarged by SITE_SCALE about the habitat. */
    private final transient Projection site;
    private final transient List<Rock> rocks = new ArrayList<>();
    private final transient PlayerActions actions;
    /** Screen positions (in the 800 x 600 layout) the building labels point at. */
    private final double[] habitatAnchor;
    private final double[] greenhouseAnchor;
    private final double[] solarAnchor;
    private final transient List<Rectangle2D> habitatArea;
    private final transient List<Rectangle2D> greenhouseArea;
    private final transient List<Rectangle2D> solarArea;
    private transient GameState state;
    /** The building whose card is open: HABITAT, GREENHOUSE or NONE. */
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
        habitatArea = List.of(labelArea(habitatAnchor), screenBox(HAB_U - HAB_RADIUS, HAB_V - HAB_RADIUS,
                HAB_U + HAB_RADIUS, HAB_V + HAB_RADIUS, 1.7));
        greenhouseArea = List.of(labelArea(greenhouseAnchor), screenBox(GREENHOUSE_U, GREENHOUSE_V - 0.85,
                GREENHOUSE_U + 3, GREENHOUSE_V + 0.85, 1.0));
        solarArea = List.of(labelArea(solarAnchor), screenBox(SOLAR_U, SOLAR_V,
                SOLAR_U + 3.55, SOLAR_V + 1.95, 0.8));

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

    /** Roughly where a building's label is drawn, so the label can be clicked as well. */
    private static Rectangle2D labelArea(double[] anchor) {
        return new Rectangle2D.Double(anchor[0] - 62, anchor[1] - 56, 124, 56);
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
            List<Label> labels = new ArrayList<>();
            paintTerrain(g, brightness);
            paintGroundMarks(g, brightness);
            paintObjects(g, brightness, labels);
            paintDust(g, sun);
            if (state.radiation().solarEvent()) {
                paintStorm(g);
            }
            for (Label label : labels) {
                paintLabel(g, label);
            }
            paintCollectButton(g);
            paintCard(g);
            paintHud(g);
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

    private void paintObjects(Graphics2D g, double brightness, List<Label> labels) {
        PowerAllocation power = state.allocation();
        PowerAllocation limit = state.limit();
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();
        SolReport forecast = state.forecast();

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

        labels.add(label(site.x(WEATHER_U, WEATHER_V), site.y(WEATHER_U, WEATHER_V, 1.75),
                Spot.NONE, SKY_BLUE, "Weather station",
                String.format(Locale.ENGLISH, "Low %.0f°C   High %.0f°C",
                        weather.minTempC(), weather.maxTempC()),
                Double.isNaN(weather.pressurePa()) ? null
                        : String.format(Locale.ENGLISH, "Air pressure %.0f Pa", weather.pressurePa())));
        labels.add(label(site.x(DETECTOR_U, DETECTOR_V), site.y(DETECTOR_U, DETECTOR_V, 1.0),
                Spot.NONE, radiation.solarEvent() ? ALERT : Color.WHITE,
                radiation.solarEvent() ? "Solar storm!" : "Radiation",
                String.format(Locale.ENGLISH, "%.2f mSv today", radiation.doseMsv())));
        if (selected != Spot.HABITAT) {
            labels.add(label(habitatAnchor[0], habitatAnchor[1], Spot.HABITAT, WARM, "Habitat",
                    "Heat " + power.heating() + " of " + limit.heating() + "   Shield " + power.shielding()));
        }
        if (selected != Spot.GREENHOUSE) {
            labels.add(label(greenhouseAnchor[0], greenhouseAnchor[1], Spot.GREENHOUSE, LEAF_GREEN, "Greenhouse",
                    String.format(Locale.ENGLISH, "Energy %d   Grows %.1f food",
                            power.greenhouse(), forecast.foodGrown())));
        }
        labels.add(label(solarAnchor[0], solarAnchor[1], state.collected() ? Spot.NONE : Spot.SOLAR,
                SUN_YELLOW, "Solar panels",
                String.format(Locale.ENGLISH, "Sunlight %.0f%%", weather.sunlight() * 100),
                state.collected() ? "Collected " + state.solarOutput() + " energy today"
                        : "Made " + state.solarOutput() + " energy today"));
    }

    /** Energy spent on a use as a share of the most it can take, 0.0 to 1.0. */
    private static double share(int spent, int most) {
        return most <= 0 ? 0 : Math.min(1.0, spent / (double) most);
    }

    private static Label label(double x, double y, Spot spot, Color accent, String title, String... lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line != null) {
                kept.add(line);
            }
        }
        return new Label(x, y, accent, title, kept.toArray(new String[0]), spot);
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
    private static final Color EDGE = new Color(255, 255, 255, 80);
    private static final Color MUTED = new Color(196, 200, 214);
    private static final Color EMPTY_CELL = new Color(62, 66, 88);

    private static final Rectangle2D END_SOL_BUTTON = new Rectangle2D.Double(656, 524, 132, 34);
    private static final Rectangle2D RESTART_BUTTON = new Rectangle2D.Double(574, 530, 74, 28);
    private static final Rectangle2D PLAY_AGAIN_BUTTON = new Rectangle2D.Double(335, 322, 130, 34);

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

    private void pointer(Graphics2D g, double x, double bottom) {
        Path2D.Double pointer = new Path2D.Double();
        pointer.moveTo(x - 6, bottom - 1);
        pointer.lineTo(x + 6, bottom - 1);
        pointer.lineTo(x, bottom + 7);
        pointer.closePath();
        g.setColor(BUBBLE);
        g.fill(pointer);
    }

    private void paintLabel(Graphics2D g, Label label) {
        FontMetrics titleMetrics = g.getFontMetrics(TITLE_FONT);
        FontMetrics lineMetrics = g.getFontMetrics(BODY_FONT);
        int width = titleMetrics.stringWidth(label.title());
        for (String line : label.lines()) {
            width = Math.max(width, lineMetrics.stringWidth(line));
        }
        width += 18;
        int height = 20 + 14 * label.lines().length;
        double top = Math.round(label.y() - height - 7);

        // A label that can be clicked has an edge in its own colour, brighter under the mouse.
        boolean clickable = label.spot() != Spot.NONE;
        Color edge = !clickable ? EDGE
                : hover.spot() == label.spot() ? label.accent() : alpha(label.accent(), 150);
        pointer(g, label.x(), top + height);
        box(g, label.x() - width / 2.0, top, width, height, BUBBLE, edge);
        g.setFont(TITLE_FONT);
        g.setColor(label.accent());
        centred(g, label.title(), label.x(), top + 15);
        g.setFont(BODY_FONT);
        g.setColor(Color.WHITE);
        for (int i = 0; i < label.lines().length; i++) {
            centred(g, label.lines()[i], label.x(), top + 29 + 14 * i);
        }
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

    /** Where the COLLECT button sits: just above the solar panels' label. */
    private Rectangle2D collectButton() {
        double labelTop = Math.round(solarAnchor[1] - 48 - 7);
        return new Rectangle2D.Double(Math.round(solarAnchor[0]) - 66, labelTop - 32, 132, 28);
    }

    private void paintCollectButton(Graphics2D g) {
        if (!state.collected() && state.phase() == Phase.PLAYING) {
            button(g, collectButton(), "COLLECT +" + state.solarOutput(), Glyphs.POWER, Spot.COLLECT, true, true);
        }
    }

    // ---- Building cards -------------------------------------------------------------

    private static final double CARD_WIDTH = 252;
    private static final double CARD_HEAD = 26;
    private static final double CARD_ROW = 46;

    /** The card for the selected building, or null when none is open. Used to paint and to click. */
    private Card card() {
        if (state == null || state.phase() != Phase.PLAYING || selected == Spot.NONE) {
            return null;
        }
        boolean habitat = selected == Spot.HABITAT;
        double[] anchor = habitat ? habitatAnchor : greenhouseAnchor;
        PowerUse[] uses = habitat
                ? new PowerUse[] {PowerUse.HEAT, PowerUse.SHIELD} : new PowerUse[] {PowerUse.GREENHOUSE};
        double height = CARD_HEAD + CARD_ROW * uses.length + 4;
        double left = Math.round(Math.max(6, Math.min(WIDTH - 6 - CARD_WIDTH, anchor[0] - CARD_WIDTH / 2)));
        double top = Math.round(Math.max(6, anchor[1] - 7 - height));
        List<Stepper> rows = new ArrayList<>();
        for (int i = 0; i < uses.length; i++) {
            double rowTop = top + CARD_HEAD + CARD_ROW * i;
            double right = left + CARD_WIDTH - 10;
            rows.add(new Stepper(uses[i], rowTop,
                    new Rectangle2D.Double(right - 94, rowTop + 3, 24, 22),
                    new Rectangle2D.Double(right - 24, rowTop + 3, 24, 22)));
        }
        return new Card(new Rectangle2D.Double(left, top, CARD_WIDTH, height), anchor[0], anchor[1],
                habitat ? "Habitat" : "Greenhouse", habitat ? WARM : LEAF_GREEN, rows);
    }

    private void paintCard(Graphics2D g) {
        Card card = card();
        if (card == null) {
            return;
        }
        Rectangle2D area = card.box();
        PowerAllocation power = state.allocation();
        PowerAllocation limit = state.limit();
        SolReport forecast = state.forecast();
        pointer(g, card.anchorX(), area.getMaxY());
        box(g, area.getX(), area.getY(), area.getWidth(), area.getHeight(), new Color(24, 28, 44), card.accent());
        g.setFont(TITLE_FONT);
        g.setColor(card.accent());
        g.drawString(card.title(), (float) area.getX() + 10, (float) area.getY() + 17);
        g.setFont(BODY_FONT);
        g.setColor(MUTED);
        String battery = state.energy() + " energy left";
        g.drawString(battery, (float) (area.getMaxX() - 10 - g.getFontMetrics().stringWidth(battery)),
                (float) area.getY() + 17);

        for (Stepper row : card.rows()) {
            int spent = power.get(row.use());
            int most = limit.get(row.use());
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
            for (Stepper row : card.rows()) {
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
        if (!state.collected() && collectButton().contains(x, y)) {
            return new Hit(Spot.COLLECT, null);
        }
        if (within(habitatArea, x, y)) {
            return new Hit(Spot.HABITAT, null);
        }
        if (within(greenhouseArea, x, y)) {
            return new Hit(Spot.GREENHOUSE, null);
        }
        if (!state.collected() && within(solarArea, x, y)) {
            return new Hit(Spot.SOLAR, null);
        }
        return NOTHING;
    }

    private static boolean within(List<Rectangle2D> areas, double x, double y) {
        for (Rectangle2D area : areas) {
            if (area.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private void click(Hit hit) {
        switch (hit.spot()) {
            case COLLECT:
            case SOLAR:
                actions.collect();
                break;
            case HABITAT:
            case GREENHOUSE:
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

    private void paintHud(Graphics2D g) {
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();

        // Top left: which day it is, where the weather reading comes from, and how the crew is.
        List<String> day = new ArrayList<>();
        day.add("Earth date " + DATE.format(state.sol().earthDate()));
        if (weather.measuredOn() == null) {
            day.add("Weather is not a rover reading");
        } else if (weather.measuredOn().equals(state.sol().earthDate())) {
            day.add("Weather measured by Curiosity on its sol " + weather.missionSol());
        } else {
            day.add("No rover reading today. Showing " + DATE.format(weather.measuredOn()));
        }
        double dayBottom = panel(g, 12, 12, false,
                "Sol " + state.sol().number() + " of " + state.missionSols(), Color.WHITE, day);
        paintVitals(g, 12, dayBottom + 6);

        // Top right: the battery, then daylight.
        double energyBottom = paintEnergy(g, WIDTH - 12, 12);
        List<String> light = new ArrayList<>();
        if (weather.sunrise() != null && weather.sunset() != null) {
            light.add("Sunrise " + weather.sunrise() + "   Sunset " + weather.sunset());
        }
        if (weather.uv() != UvIndex.UNKNOWN) {
            light.add("UV light: " + weather.uv().label());
        }
        String sky = weather.sunlight() < 0.5 ? "Dusty sky" : "Clear sky";
        panel(g, WIDTH - 12, energyBottom + 6, true, sky, SUN_YELLOW, light);

        if (radiation.solarEvent() && radiation.detail() != null) {
            g.setFont(TITLE_FONT);
            int width = g.getFontMetrics().stringWidth(radiation.detail()) + 24;
            box(g, (WIDTH - width) / 2.0, 140, width, 24, new Color(110, 20, 70, 235), ALERT);
            g.setColor(Color.WHITE);
            centred(g, radiation.detail(), WIDTH / 2.0, 156);
        }

        // Landmark and compass. North is toward the lower left in this view.
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        g.setColor(new Color(60, 30, 20, 210));
        g.drawString("Lower slopes of Mount Sharp", 380, 30);
        double cx = 764;
        double cy = 488;
        box(g, cx - 24, cy - 24, 48, 48, BUBBLE, EDGE);
        Path2D.Double needle = new Path2D.Double();
        needle.moveTo(cx - 5, cy + 9);
        needle.lineTo(cx + 15, cy + 5);
        needle.lineTo(cx + 10, cy - 5);
        needle.closePath();
        g.setColor(Color.WHITE);
        g.fill(needle);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("N", (float) cx - 17, (float) cy + 4);

        if (state.phase() == Phase.PLAYING) {
            paintAdvice(g);
            button(g, END_SOL_BUTTON, "END SOL  >", Glyphs.GOOD, Spot.END_SOL, true, false);
            button(g, RESTART_BUTTON, "Restart", new Color(190, 194, 206), Spot.RESTART, true, false);
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.setColor(new Color(255, 244, 232, 235));
        g.drawString("Real NASA data, saved copy (not live).  Weather: Curiosity rover, NASA/JPL-Caltech/CAB.  "
                + "Solar storms: NASA CCMC DONKI.", 12, HEIGHT - 22);
        g.drawString(String.format(Locale.ENGLISH,
                "Ground: real heights at Curiosity's landing site in Gale Crater, NASA MOLA.  "
                        + "One tile is about %.1f km.  Hills drawn %dx taller.  Buildings not to scale.",
                terrain.spacingM / 1000, HEIGHT_EXAGGERATION), 12, HEIGHT - 9);

        if (state.phase() != Phase.PLAYING) {
            paintEnding(g);
        }
    }

    /**
     * A dark square-cornered box of text anchored at its top-left corner, or top-right if
     * {@code alignRight}. Returns the y of its bottom edge.
     */
    private double panel(Graphics2D g, double x, double y, boolean alignRight, String title,
                         Color titleColour, List<String> lines) {
        FontMetrics titleMetrics = g.getFontMetrics(BIG_FONT);
        FontMetrics lineMetrics = g.getFontMetrics(BODY_FONT);
        int width = titleMetrics.stringWidth(title);
        for (String line : lines) {
            width = Math.max(width, lineMetrics.stringWidth(line));
        }
        width += 22;
        int height = 30 + 14 * lines.size();
        double left = Math.round(alignRight ? x - width : x);
        box(g, left, y, width, height, BUBBLE, EDGE);
        g.setFont(BIG_FONT);
        g.setColor(titleColour);
        g.drawString(title, (float) left + 11, (float) y + 21);
        g.setFont(BODY_FONT);
        g.setColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), (float) left + 11, (float) y + 37 + 14 * i);
        }
        return y + height;
    }

    /** The battery: how much energy is stored, one cell per unit. Returns the y of its bottom edge. */
    private double paintEnergy(Graphics2D g, double right, double y) {
        int capacity = state.energyCapacity();
        int cell = 9;
        int gap = 2;
        double width = 20 + capacity * cell + (capacity - 1) * gap;
        double left = right - width;
        box(g, left, y, width, 52, BUBBLE, Glyphs.POWER);
        Glyphs.bolt(g, left + 9, y + 7, 15, Glyphs.POWER);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(Glyphs.POWER);
        g.drawString("ENERGY", (float) left + 28, (float) y + 20);
        String amount = state.energy() + " / " + capacity;
        g.setColor(Color.WHITE);
        g.drawString(amount, (float) (right - 10 - g.getFontMetrics().stringWidth(amount)), (float) y + 20);

        // Cells that would fill if today's solar energy were collected are shown as outlines.
        int waiting = state.collected() || state.phase() != Phase.PLAYING ? state.energy()
                : Math.min(capacity, state.energy() + state.solarOutput());
        for (int i = 0; i < capacity; i++) {
            double cellX = left + 10 + i * (cell + gap);
            Color fill = i < state.energy() ? Glyphs.POWER : EMPTY_CELL;
            box(g, cellX, y + 29, cell, 14, fill, i >= state.energy() && i < waiting ? Glyphs.POWER : null);
        }
        return y + 52;
    }

    /** Crew health and food, each with what tonight will do to it. */
    private void paintVitals(Graphics2D g, double x, double y) {
        SolReport forecast = state.forecast();
        boolean playing = state.phase() == Phase.PLAYING;
        box(g, x, y, 270, 48, BUBBLE, EDGE);
        Glyphs.heart(g, x + 10, y + 7, 13, Glyphs.HEALTH);
        vital(g, x, y + 5, "Crew health", state.crewHealth() / 100, Glyphs.HEALTH,
                String.format(Locale.ENGLISH, "%.0f", state.crewHealth()),
                playing ? forecast.healthAfter() - state.crewHealth() : 0);
        Glyphs.leaf(g, x + 10, y + 27, 13, Glyphs.GREENHOUSE);
        vital(g, x, y + 25, "Food (days)", state.foodDays() / 10, Glyphs.GREENHOUSE,
                String.format(Locale.ENGLISH, "%.1f", state.foodDays()),
                playing ? forecast.foodAfter() - state.foodDays() : 0);
    }

    private void vital(Graphics2D g, double x, double y, String name, double share, Color colour,
                       String value, double change) {
        g.setFont(BODY_FONT);
        g.setColor(Color.WHITE);
        g.drawString(name, (float) x + 28, (float) y + 13);
        box(g, x + 98, y + 5, 60, 8, EMPTY_CELL, null);
        box(g, x + 98, y + 5, 60 * clamp(share), 8, colour, null);
        g.setFont(TITLE_FONT);
        g.drawString(value, (float) x + 165, (float) y + 13);
        if (Math.abs(change) >= 0.05) {
            g.setFont(BODY_FONT);
            g.setColor(change < 0 ? Glyphs.BAD : Glyphs.GOOD);
            g.drawString(String.format(Locale.ENGLISH, "%+.1f tonight", change), (float) x + 195, (float) y + 13);
        }
    }

    /** Bottom left: what to do next, and what ending the sol now would do and why. */
    private void paintAdvice(Graphics2D g) {
        SolReport forecast = state.forecast();
        String next;
        if (!state.collected()) {
            next = "Click COLLECT on the solar panels.";
        } else if (forecast.heatShort() > 0 && state.energy() > 0) {
            next = "The habitat will be cold tonight. Click it.";
        } else if (state.energy() > 0 && forecast.foodGrown() == 0) {
            next = "Click the greenhouse to grow food.";
        } else {
            next = "Spend more, or save it and press END SOL.";
        }

        List<String> lines = new ArrayList<>();
        List<Color> colours = new ArrayList<>();
        lines.add("If the sol ends now:");
        colours.add(MUTED);
        lines.add(String.format(Locale.ENGLISH, "Radiation  %+.1f health", forecast.healthRadiation())
                + (forecast.shieldBlocked() > 0
                        ? String.format(Locale.ENGLISH, "  (shield stops %.0f%%)", forecast.shieldBlocked() * 100)
                        : "  (no shield)"));
        colours.add(state.radiation().solarEvent() ? ALERT : Glyphs.WARN);
        if (forecast.heatShort() > 0) {
            lines.add(String.format(Locale.ENGLISH, "Cold  %+.1f health  (heat is %d short)",
                    forecast.healthCold(), forecast.heatShort()));
            colours.add(Glyphs.BAD);
        }
        if (forecast.hungry()) {
            lines.add(String.format(Locale.ENGLISH, "Hunger  %+.1f health  (not enough food)",
                    forecast.healthHunger()));
            colours.add(Glyphs.BAD);
        }
        if (forecast.healthRest() > 0) {
            lines.add(String.format(Locale.ENGLISH, "Warm and fed  %+.1f health", forecast.healthRest()));
            colours.add(Glyphs.GOOD);
        }
        lines.add(String.format(Locale.ENGLISH, "Food  grows %.1f, crew eats %.1f",
                forecast.foodGrown(), forecast.foodEaten()));
        colours.add(forecast.foodGrown() >= forecast.foodEaten() ? Glyphs.GOOD : Color.WHITE);

        int width = g.getFontMetrics(TITLE_FONT).stringWidth(next);
        for (String line : lines) {
            width = Math.max(width, g.getFontMetrics(BODY_FONT).stringWidth(line));
        }
        width += 22;
        int height = 28 + 14 * lines.size();
        double top = 562 - height;
        box(g, 12, top, width, height, BUBBLE, Glyphs.POWER);
        g.setFont(TITLE_FONT);
        g.setColor(Glyphs.POWER);
        g.drawString(next, 23, (float) top + 17);
        g.setFont(BODY_FONT);
        for (int i = 0; i < lines.size(); i++) {
            g.setColor(colours.get(i));
            g.drawString(lines.get(i), 23, (float) top + 34 + 14 * i);
        }
    }

    /** The end of the mission: what happened, and a button to play again. */
    private void paintEnding(Graphics2D g) {
        boolean won = state.phase() == Phase.WON;
        SolReport last = state.forecast();
        g.setColor(new Color(10, 10, 20, 130));
        g.fillRect(-WIDTH, -HEIGHT, 3 * WIDTH, 3 * HEIGHT);
        box(g, 170, 214, 460, 158, new Color(24, 28, 44), won ? Glyphs.GOOD : Glyphs.BAD);
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
