package outpost.view;

import static outpost.view.Mesh.mix;
import static outpost.view.Mesh.p;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import javax.swing.JPanel;
import outpost.shared.GameState;
import outpost.shared.PowerAllocation;
import outpost.shared.RadiationConditions;
import outpost.shared.UvIndex;
import outpost.shared.WeatherConditions;

/**
 * The angled bird's-eye view of the outpost: a low-poly model of the real ground around
 * Curiosity's landing site, with the outpost buildings on it. Everything that changes is
 * drawn from the GameState snapshot; painting never changes anything.
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

    private record Label(double x, double y, Color accent, String title, String[] lines) {}

    private final transient Terrain terrain;
    private final int tiles;
    /** Ground height in tiles at each grid corner, indexed [u][v], already exaggerated. */
    private final double[][] ground;
    /** Camera for the buildings: the ground camera enlarged by SITE_SCALE about the habitat. */
    private final transient Projection site;
    private final transient List<Rock> rocks = new ArrayList<>();
    private transient GameState state;

    public OutpostView() {
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
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
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
        items.add(new Item(HAB_U + HAB_V, mesh -> habitat(mesh, power.shielding(), power.heating())));
        items.add(new Item(GREENHOUSE_U + GREENHOUSE_V + 1.5, mesh -> greenhouse(mesh, power.greenhouse())));
        items.add(new Item(SOLAR_U + SOLAR_V + 2.5, mesh -> solarArray(mesh, weather.sunlight())));
        items.add(new Item(ROVER_U + ROVER_V, this::rover));
        items.sort(Comparator.comparingDouble(Item::depth));
        for (Item item : items) {
            Mesh mesh = new Mesh(site, brightness);
            item.build().accept(mesh);
            mesh.paint(g);
        }

        labels.add(label(WEATHER_U, WEATHER_V, 1.75, SKY_BLUE, "Weather station",
                String.format(Locale.ENGLISH, "Low %.0f°C   High %.0f°C",
                        weather.minTempC(), weather.maxTempC()),
                Double.isNaN(weather.pressurePa()) ? null
                        : String.format(Locale.ENGLISH, "Air pressure %.0f Pa", weather.pressurePa())));
        labels.add(label(DETECTOR_U, DETECTOR_V, 1.0, radiation.solarEvent() ? ALERT : Color.WHITE,
                radiation.solarEvent() ? "Solar storm!" : "Radiation",
                String.format(Locale.ENGLISH, "%.2f mSv today", radiation.doseMsv())));
        labels.add(label(HAB_U, HAB_V, 2.05, WARM, "Habitat",
                "Shield " + power.shielding() + "%   Heat " + power.heating() + "%"));
        labels.add(label(GREENHOUSE_U + 1.5, GREENHOUSE_V, 1.25, LEAF_GREEN, "Greenhouse",
                "Power " + power.greenhouse() + "%"));
        labels.add(label(SOLAR_U + 1.8, SOLAR_V + 0.9, 1.05, SUN_YELLOW, "Solar panels",
                String.format(Locale.ENGLISH, "Sunlight %.0f%%", weather.sunlight() * 100)));
    }

    private Label label(double u, double v, double above, Color accent, String title, String... lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line != null) {
                kept.add(line);
            }
        }
        return new Label(site.x(u, v), site.y(u, v, above), accent, title,
                kept.toArray(new String[0]));
    }

    /**
     * The crew's home: a faceted dome on a ring wall. A bank of Mars soil is piled around it
     * as a radiation shield, taller the more power goes to shielding, and the windows glow
     * warmer the more power goes to heating.
     */
    private void habitat(Mesh mesh, int shielding, int heating) {
        double h0 = 0;
        double bankHeight = 1.15 * shielding / 100.0;
        if (shielding > 0) {
            for (int k = 6; k <= 13; k++) {
                bank(mesh, h0, bankHeight, k);
            }
        }

        int segments = 12;
        double wall = 0.34;
        double[] inside = p(HAB_U, HAB_V, h0 + wall);
        Color window = mix(new Color(64, 84, 118), new Color(255, 200, 96), heating / 60.0);
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

    /** A glass tunnel of crops. The rows turn from bare soil to green as it gets more power. */
    private void greenhouse(Mesh mesh, int powerShare) {
        double u0 = GREENHOUSE_U;
        double u1 = GREENHOUSE_U + 3.0;
        double radius = 0.85;
        double h0 = 0;
        double floor = h0 + 0.14;
        mesh.box(u0 - 0.08, GREENHOUSE_V - radius - 0.08, h0, 3.16, 2 * radius + 0.16, 0.14, METAL);
        mesh.sheet(new Color(104, 74, 56), p(u0, GREENHOUSE_V - radius, floor), p(u1, GREENHOUSE_V - radius, floor),
                p(u1, GREENHOUSE_V + radius, floor), p(u0, GREENHOUSE_V + radius, floor));
        Color crop = mix(new Color(136, 104, 70), new Color(58, 176, 72), powerShare / 60.0);
        double cropHeight = 0.05 + 0.3 * Math.min(1, powerShare / 60.0);
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

    // ---- Text -----------------------------------------------------------------------

    private void paintLabel(Graphics2D g, Label label) {
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        Font lineFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        FontMetrics titleMetrics = g.getFontMetrics(titleFont);
        FontMetrics lineMetrics = g.getFontMetrics(lineFont);
        int width = titleMetrics.stringWidth(label.title());
        for (String line : label.lines()) {
            width = Math.max(width, lineMetrics.stringWidth(line));
        }
        width += 18;
        int height = 20 + 14 * label.lines().length;
        double left = label.x() - width / 2.0;
        double top = label.y() - height - 7;

        Path2D.Double pointer = new Path2D.Double();
        pointer.moveTo(label.x() - 6, top + height - 1);
        pointer.lineTo(label.x() + 6, top + height - 1);
        pointer.lineTo(label.x(), top + height + 7);
        pointer.closePath();
        g.setColor(BUBBLE);
        g.fill(pointer);
        g.fill(new RoundRectangle2D.Double(left, top, width, height, 12, 12));
        g.setFont(titleFont);
        g.setColor(label.accent());
        g.drawString(label.title(), (float) (label.x() - titleMetrics.stringWidth(label.title()) / 2.0),
                (float) (top + 15));
        g.setFont(lineFont);
        g.setColor(Color.WHITE);
        for (int i = 0; i < label.lines().length; i++) {
            String line = label.lines()[i];
            g.drawString(line, (float) (label.x() - lineMetrics.stringWidth(line) / 2.0),
                    (float) (top + 29 + 14 * i));
        }
    }

    private void paintHud(Graphics2D g) {
        WeatherConditions weather = state.weather();
        RadiationConditions radiation = state.radiation();

        // Top left: which day it is, and where the weather reading comes from.
        List<String> day = new ArrayList<>();
        day.add("Earth date " + DATE.format(state.sol().earthDate()));
        if (weather.measuredOn() == null) {
            day.add("Weather is not a rover reading");
        } else if (weather.measuredOn().equals(state.sol().earthDate())) {
            day.add("Weather measured by Curiosity on its sol " + weather.missionSol());
        } else {
            day.add("No rover reading today. Showing " + DATE.format(weather.measuredOn()));
        }
        panel(g, 12, 12, false, "Sol " + state.sol().number(), Color.WHITE, day);

        // Top right: daylight.
        List<String> light = new ArrayList<>();
        if (weather.sunrise() != null && weather.sunset() != null) {
            light.add("Sunrise " + weather.sunrise() + "   Sunset " + weather.sunset());
        }
        if (weather.uv() != UvIndex.UNKNOWN) {
            light.add("UV light: " + weather.uv().label());
        }
        String sky = weather.sunlight() < 0.5 ? "Dusty sky" : "Clear sky";
        panel(g, WIDTH - 12, 12, true, sky, SUN_YELLOW, light);

        if (radiation.solarEvent() && radiation.detail() != null) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 12);
            FontMetrics metrics = g.getFontMetrics(font);
            int width = metrics.stringWidth(radiation.detail()) + 24;
            g.setColor(new Color(110, 20, 70, 230));
            g.fill(new RoundRectangle2D.Double((WIDTH - width) / 2.0, 80, width, 24, 12, 12));
            g.setFont(font);
            g.setColor(Color.WHITE);
            g.drawString(radiation.detail(), (WIDTH - width) / 2f + 12, 96);
        }

        // Landmark and compass. North is toward the lower left in this view.
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        g.setColor(new Color(60, 30, 20, 210));
        g.drawString("Lower slopes of Mount Sharp", 420, 64);
        double cx = 742;
        double cy = 482;
        g.setColor(BUBBLE);
        g.fill(new Ellipse2D.Double(cx - 24, cy - 24, 48, 48));
        Path2D.Double needle = new Path2D.Double();
        needle.moveTo(cx - 5, cy + 9);
        needle.lineTo(cx + 15, cy + 5);
        needle.lineTo(cx + 10, cy - 5);
        needle.closePath();
        g.setColor(Color.WHITE);
        g.fill(needle);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        g.drawString("N", (float) cx - 17, (float) cy + 4);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        g.setColor(new Color(255, 244, 232, 235));
        g.drawString("Real NASA data, saved copy (not live).  Weather: Curiosity rover, NASA/JPL-Caltech/CAB.  "
                + "Solar storms: NASA CCMC DONKI.", 12, HEIGHT - 22);
        g.drawString(String.format(Locale.ENGLISH,
                "Ground: real heights at Curiosity's landing site in Gale Crater, NASA MOLA.  "
                        + "One tile is about %.1f km.  Hills drawn %dx taller.  Buildings not to scale.",
                terrain.spacingM / 1000, HEIGHT_EXAGGERATION), 12, HEIGHT - 9);
    }

    /** A dark rounded box of text anchored at its top-left corner, or top-right if {@code alignRight}. */
    private void panel(Graphics2D g, double x, double y, boolean alignRight, String title,
                       Color titleColour, List<String> lines) {
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 17);
        Font lineFont = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
        FontMetrics titleMetrics = g.getFontMetrics(titleFont);
        FontMetrics lineMetrics = g.getFontMetrics(lineFont);
        int width = titleMetrics.stringWidth(title);
        for (String line : lines) {
            width = Math.max(width, lineMetrics.stringWidth(line));
        }
        width += 22;
        int height = 30 + 14 * lines.size();
        double left = alignRight ? x - width : x;
        g.setColor(BUBBLE);
        g.fill(new RoundRectangle2D.Double(left, y, width, height, 14, 14));
        g.setFont(titleFont);
        g.setColor(titleColour);
        g.drawString(title, (float) left + 11, (float) y + 21);
        g.setFont(lineFont);
        g.setColor(Color.WHITE);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), (float) left + 11, (float) y + 37 + 14 * i);
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
