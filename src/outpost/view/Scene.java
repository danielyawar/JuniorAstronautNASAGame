package outpost.view;

import static outpost.view.Mesh.mix;
import static outpost.view.Mesh.p;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import outpost.shared.GameState;
import outpost.shared.Outpost;
import outpost.shared.Place;

/**
 * The picture of the outpost: a low-poly model of the real ground around Curiosity's landing
 * site, the buildings on it, the astronaut, and the light of the day and the night. It also
 * knows where each thing is on screen, so that OutpostView can hang tags on the buildings
 * and tell what was clicked.
 *
 * <p>The view repaints about twenty times a second, so nothing that stays the same is drawn
 * twice. The sky, ground and rocks are painted once into an image. The buildings are painted
 * onto a copy of it, and painted again only when one of them looks different (see
 * {@link Look}). An ordinary frame draws that one image, the astronaut, one see-through
 * rectangle for the time of day and the dust, and the lights. The images are a cache of
 * drawing work; no game state lives here.
 *
 * <p>What is real: the ground heights (NASA MOLA) and the compass direction. What is not:
 * heights are drawn {@value #HEIGHT_EXAGGERATION} times taller than true scale so the
 * slopes read at this size, and the buildings are drawn far larger than life (a tile is
 * about 0.9 km across).
 */
final class Scene {

    static final int HEIGHT_EXAGGERATION = 2;
    /** Days of food waiting on the plants at which the crop rows are drawn full grown. */
    static final double CROPS_FULL = 2;
    private static final Projection CAMERA = new Projection(400, 176, 18, 9, 22);

    private static final Color ROCK_LOW = new Color(183, 100, 60);
    private static final Color ROCK_HIGH = new Color(226, 170, 122);
    private static final Color SKY_TOP = new Color(236, 196, 148);
    private static final Color SKY_BOTTOM = new Color(128, 82, 62);
    private static final Color HULL = new Color(236, 232, 224);
    private static final Color METAL = new Color(150, 154, 164);
    private static final Color DARK = new Color(52, 54, 64);
    private static final Color CUPS = new Color(120, 190, 255);
    private static final Color ALERT = new Color(255, 96, 160);
    private static final Color SOIL = new Color(150, 84, 54);
    private static final Color SOIL_LIGHT = new Color(160, 92, 58);
    private static final Color DIGGER_YELLOW = new Color(238, 178, 56);
    private static final Color WINDOW_WARM = new Color(255, 200, 96);
    private static final Color WINDOW_COLD = new Color(64, 84, 118);
    private static final Color SUIT = new Color(246, 246, 250);
    private static final Color SUIT_PACK = new Color(255, 140, 40);
    private static final Color VISOR = new Color(36, 44, 70);
    /** The colour of the night, and how thickly it lies over the scene when the Sun is down. */
    private static final Color NIGHT = new Color(10, 14, 44);
    private static final double NIGHT_ALPHA = 0.64;
    /** The colour of dust in the air. */
    private static final Color DUST = new Color(196, 148, 104);

    // Where things stand, in tiles. The camera looks south-east from the north-west, so
    // u grows toward the west and v toward the north. Bradbury Landing is the grid centre.
    private static final double HAB_U = 10;
    private static final double HAB_V = 10;
    private static final double HAB_RADIUS = 1.7;
    /** Height of the finished soil bank, in tiles. Low enough to leave the dome's windows in view. */
    private static final double BANK_HEIGHT = 1.0;
    private static final double GREENHOUSE_U = 12.7;
    private static final double GREENHOUSE_V = 8.0;
    private static final double SOLAR_U = 7.0;
    private static final double SOLAR_V = 13.3;
    private static final double WEATHER_U = 9.6;
    private static final double WEATHER_V = 5.9;
    private static final double DETECTOR_U = 6.2;
    private static final double DETECTOR_V = 10.3;
    private static final double DIGGER_U = 15.3;
    private static final double DIGGER_V = 13.3;
    /** Where the astronaut stands to do each job, as {u, v}. The first is the airlock door. */
    private static final double[] AT_DOOR = {12.15, 11.33};
    private static final double[] AT_GREENHOUSE = {13.8, 9.45};
    private static final double[] AT_PANELS = {10.95, 14.3};
    private static final double[] AT_DIGGER = {14.3, 14.6};
    /** The astronaut is drawn this many times larger than the boxes below describe. */
    private static final double FIGURE = 1.3;
    /**
     * The buildings are drawn this many times larger than the ground grid, growing outward
     * from the habitat, so they can be seen. The positions above are in these larger units.
     */
    private static final double SITE_SCALE = 1.5;

    /** Thin lines round the faces of the models, and the streaks of a solar storm. Made once. */
    private static final BasicStroke FACE_EDGE = new BasicStroke(0.8f);
    private static final BasicStroke STREAK = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    private record Rock(double u, double v, double size, int sides, double turn, double tone) {}

    /**
     * Everything that decides how the buildings look, in steps coarse enough that the
     * buildings are repainted only now and then.
     *
     * @param shield soil bank height, 0 to 20
     * @param crops  crop growth, 0 to 8
     * @param clean  panel cleanness, 0 to 10
     * @param warm   true when the heater is working, so the windows are lit
     * @param storm  true on a solar storm sol, so the detector's light is pink
     */
    private record Look(int shield, int crops, int clean, boolean warm, boolean storm) {
        static Look of(GameState state) {
            Outpost o = state.outpost();
            return new Look((int) Math.round(Draw.clamp(o.shield()) * 20),
                    (int) Math.round(Draw.clamp(o.cropsReady() / CROPS_FULL) * 8),
                    (int) Math.round(Draw.clamp(o.panelsClean()) * 10),
                    state.now().heaterWorking(), state.radiation().solarEvent());
        }
    }

    private final Terrain terrain;
    private final int tiles;
    /** Ground height in tiles at each grid corner, indexed [u][v], already exaggerated. */
    private final double[][] ground;
    /** Camera for the buildings: the ground camera enlarged by SITE_SCALE about the habitat. */
    private final Projection site;
    private final List<Rock> rocks = new ArrayList<>();

    /**
     * Screen positions (in the 800 x 600 layout) the building tags and cards point at. Each
     * is just above its building, except the digger's, which is just below it.
     */
    final double[] habitatAnchor;
    final double[] greenhouseAnchor;
    final double[] solarAnchor;
    final double[] weatherAnchor;
    final double[] detectorAnchor;
    final double[] diggerAnchor;
    /** The screen rectangle around each building, so the building itself can be clicked. */
    final Rectangle2D habitatArea;
    final Rectangle2D greenhouseArea;
    final Rectangle2D solarArea;
    final Rectangle2D weatherArea;
    final Rectangle2D detectorArea;
    final Rectangle2D diggerArea;

    /** The dome windows and the airlock door, relit after dark. Made once; they never move. */
    private final List<Path2D.Double> windowGlow;
    /** The greenhouse's glass, lit from inside while the grow lamps work. */
    private final List<Path2D.Double> lampGlow;

    // The cache of drawing work. See the class comment.
    private BufferedImage groundImage;
    private BufferedImage builtImage;
    private Look builtLook;
    private int cachedWidth;
    private int cachedHeight;
    private double cachedScaleX;
    private double cachedScaleY;
    /** The streaks of a solar storm, worked out once for the sol in {@code streaksSol}. */
    private double[][] streaks;
    private int streaksSol = -1;

    Scene() {
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
            {DETECTOR_U, DETECTOR_V, 1.2}, {DIGGER_U - 0.6, DIGGER_V + 0.5, 2.0}};
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
        // A little right of the middle, so that the greenhouse's long tag stays off the habitat.
        greenhouseAnchor = new double[] {
            site.x(GREENHOUSE_U + 1.9, GREENHOUSE_V - 0.25), site.y(GREENHOUSE_U + 1.9, GREENHOUSE_V - 0.25, 1.2)};
        solarAnchor = new double[] {
            site.x(SOLAR_U + 1.8, SOLAR_V + 0.9), site.y(SOLAR_U + 1.8, SOLAR_V + 0.9, 1.05)};
        weatherAnchor = new double[] {site.x(WEATHER_U, WEATHER_V), site.y(WEATHER_U, WEATHER_V, 1.75)};
        detectorAnchor = new double[] {site.x(DETECTOR_U, DETECTOR_V), site.y(DETECTOR_U, DETECTOR_V, 1.0)};
        // The digger's tag hangs under it, leaving the ground between it and the airlock clear
        // for the astronaut to walk across. This is the point just below the digger.
        diggerAnchor = new double[] {
            site.x(DIGGER_U - 0.3, DIGGER_V), site.y(DIGGER_U + 0.45, DIGGER_V + 0.4, 0) + 3};
        habitatArea = screenBox(HAB_U - HAB_RADIUS, HAB_V - HAB_RADIUS,
                HAB_U + HAB_RADIUS, HAB_V + HAB_RADIUS, 1.7);
        greenhouseArea = screenBox(GREENHOUSE_U, GREENHOUSE_V - 0.85,
                GREENHOUSE_U + 3, GREENHOUSE_V + 0.85, 1.0);
        solarArea = screenBox(SOLAR_U, SOLAR_V, SOLAR_U + 3.55, SOLAR_V + 1.95, 0.8);
        // The small things have click areas a little larger than they are.
        weatherArea = screenBox(WEATHER_U - 0.55, WEATHER_V - 0.55, WEATHER_U + 0.55, WEATHER_V + 0.55, 1.45);
        detectorArea = screenBox(DETECTOR_U - 0.45, DETECTOR_V - 0.45, DETECTOR_U + 0.45, DETECTOR_V + 0.45, 0.7);
        diggerArea = screenBox(DIGGER_U - 1.75, DIGGER_V - 0.5, DIGGER_U + 0.6, DIGGER_V + 0.75, 0.85);

        Mesh windows = new Mesh(site, 1);
        habitatWindows(windows, Color.WHITE);
        windowGlow = windows.outlines();
        Mesh glass = new Mesh(site, 1);
        greenhouseGlass(glass, Color.WHITE, Color.WHITE);
        lampGlow = glass.outlines();
    }

    /** Kilometres between neighbouring ground heights: the width of one tile. */
    double tileKm() {
        return terrain.spacingM / 1000;
    }

    /** Where the astronaut stands to do the job at {@code place}, as {u, v}. */
    double[] spot(Place place) {
        switch (place) {
            case GREENHOUSE:
                return AT_GREENHOUSE;
            case PANELS:
                return AT_PANELS;
            case DIGGER:
                return AT_DIGGER;
            default:
                return AT_DOOR;
        }
    }

    /** Where on screen, in the 800 x 600 layout, the top of the astronaut's helmet is when they stand at (u, v). */
    double[] headAt(double u, double v) {
        return new double[] {site.x(u, v), site.y(u, v, 0.53 * FIGURE)};
    }

    /** Where on screen the top of the airlock door is. */
    double[] doorTop() {
        return new double[] {site.x(AT_DOOR[0], AT_DOOR[1]) - 4, site.y(AT_DOOR[0] - 0.3, AT_DOOR[1], 0.62)};
    }

    /** The screen rectangle around a box standing on the site, from ground level up to {@code height}. */
    private Rectangle2D screenBox(double u0, double v0, double u1, double v1, double height) {
        double left = site.x(u0, v1);
        double right = site.x(u1, v0);
        double top = site.y(u0, v0, height);
        double bottom = site.y(u1, v1, 0);
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    // ---- Painting -------------------------------------------------------------------

    /**
     * Draws the sky, ground and buildings, from the cache where it can. {@code g} must still
     * be in panel coordinates: this is the one thing drawn before the layout is scaled.
     */
    void paintBuilt(Graphics2D g, int width, int height, GameState state) {
        AffineTransform device = g.getTransform();
        // On a screen that Java scales (a laptop set to 150%, say), keep the image as sharp
        // as the text by making it in the screen's own pixels.
        boolean plain = device.getShearX() == 0 && device.getShearY() == 0
                && device.getScaleX() > 0 && device.getScaleY() > 0;
        double scaleX = plain ? device.getScaleX() : 1;
        double scaleY = plain ? device.getScaleY() : 1;
        refresh(width, height, scaleX, scaleY, Look.of(state));
        if (plain) {
            g.setTransform(AffineTransform.getTranslateInstance(device.getTranslateX(), device.getTranslateY()));
            g.drawImage(builtImage, 0, 0, null);
            g.setTransform(device);
        } else {
            g.drawImage(builtImage, 0, 0, null);
        }
    }

    /** Brings the cached images up to date for this panel size and this look of the buildings. */
    private void refresh(int width, int height, double scaleX, double scaleY, Look look) {
        int pixelsWide = Math.max(1, (int) Math.ceil(width * scaleX));
        int pixelsHigh = Math.max(1, (int) Math.ceil(height * scaleY));
        if (groundImage == null || width != cachedWidth || height != cachedHeight
                || scaleX != cachedScaleX || scaleY != cachedScaleY) {
            groundImage = new BufferedImage(pixelsWide, pixelsHigh, BufferedImage.TYPE_INT_RGB);
            builtImage = new BufferedImage(pixelsWide, pixelsHigh, BufferedImage.TYPE_INT_RGB);
            builtLook = null;
            cachedWidth = width;
            cachedHeight = height;
            cachedScaleX = scaleX;
            cachedScaleY = scaleY;
            Graphics2D c = groundImage.createGraphics();
            try {
                smooth(c);
                c.scale(scaleX, scaleY);
                c.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, height, SKY_BOTTOM));
                c.fillRect(0, 0, width, height);
                Draw.fit(c, width, height);
                c.setStroke(FACE_EDGE);
                paintTerrain(c);
                paintGroundMarks(c);
                Mesh scenery = new Mesh(CAMERA, 1);
                for (Rock rock : rocks) {
                    rock(scenery, rock);
                }
                scenery.paint(c);
            } finally {
                c.dispose();
            }
        }
        if (!look.equals(builtLook)) {
            Graphics2D c = builtImage.createGraphics();
            try {
                c.drawImage(groundImage, 0, 0, null);
                smooth(c);
                c.scale(scaleX, scaleY);
                Draw.fit(c, width, height);
                c.setStroke(FACE_EDGE);
                paintBuildings(c, look);
            } finally {
                c.dispose();
            }
            builtLook = look;
        }
    }

    private static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /**
     * Draws what changes from frame to frame, over the built scene: the astronaut, the light
     * of the hour and the dust in the air, the lit windows and lamps, and a solar storm.
     * {@code g} is in the 800 x 600 layout. {@code figure} is where the astronaut stands as
     * {u, v}, or null when they are indoors; {@code stride} lifts their step while walking.
     */
    void paintLive(Graphics2D g, GameState state, double[] figure, double stride) {
        if (figure != null) {
            g.setStroke(FACE_EDGE);
            Mesh mesh = new Mesh(site, 1);
            figure(mesh, figure[0], figure[1], stride);
            mesh.paint(g);
        }
        double dark = Math.pow(1 - Draw.clamp(state.now().sun()), 3);
        Color light = light(dark, state.weather().sunlight());
        if (light != null) {
            g.setColor(light);
            g.fillRect(-Draw.WIDTH, -Draw.HEIGHT, 3 * Draw.WIDTH, 3 * Draw.HEIGHT);
        }
        if (state.now().heaterWorking() && dark > 0.04) {
            g.setColor(new Color(255, 206, 110, (int) Math.round(235 * dark)));
            for (Path2D.Double window : windowGlow) {
                g.fill(window);
            }
        }
        if (state.now().lampsWorking()) {
            g.setColor(new Color(255, 110, 210, (int) Math.round(70 + 90 * dark)));
            for (Path2D.Double pane : lampGlow) {
                g.fill(pane);
            }
        }
        if (state.radiation().solarEvent()) {
            paintStorm(g, state.sol().number(), state.outpost().clock());
        }
    }

    /**
     * The one see-through colour that stands for both the time of day and the dust in the
     * air: night laid over dust. Null in full daylight under a clear sky.
     *
     * @param dark     0.0 at midday to 1.0 at night
     * @param sunlight share of sunlight the sky lets through
     */
    private static Color light(double dark, double sunlight) {
        double night = NIGHT_ALPHA * dark;
        double dust = 150 / 255.0 * Math.pow(1 - Draw.clamp(sunlight), 1.3);
        double alpha = night + dust * (1 - night);
        if (alpha < 0.004) {
            return null;
        }
        double fromDust = dust * (1 - night) / alpha;
        return new Color(
                (int) Math.round(NIGHT.getRed() * (1 - fromDust) + DUST.getRed() * fromDust),
                (int) Math.round(NIGHT.getGreen() * (1 - fromDust) + DUST.getGreen() * fromDust),
                (int) Math.round(NIGHT.getBlue() * (1 - fromDust) + DUST.getBlue() * fromDust),
                (int) Math.round(255 * alpha));
    }

    /** Streaks for the particles of a solar storm. The pattern depends only on the sol; the clock makes it fall. */
    private void paintStorm(Graphics2D g, int sol, double clock) {
        if (streaks == null || streaksSol != sol) {
            Random random = new Random(sol * 7919L);
            streaks = new double[44][];
            for (int i = 0; i < streaks.length; i++) {
                streaks[i] = new double[] {
                    random.nextDouble(), random.nextDouble(), 14 + random.nextDouble() * 26,
                    120 + random.nextInt(80), 70 + random.nextInt(90)};
            }
            streaksSol = sol;
        }
        double fallen = clock * 0.35;
        double span = Draw.HEIGHT - 120;
        g.setStroke(STREAK);
        for (double[] streak : streaks) {
            double along = (streak[1] + fallen) % 1;
            double x = ((streak[0] - 0.5 * fallen * span / (Draw.WIDTH + 200)) % 1 + 1) % 1 * (Draw.WIDTH + 200) - 100;
            double y = 66 + along * span;
            double length = streak[2];
            g.setColor(new Color(255, (int) streak[3], 220, (int) streak[4]));
            g.draw(new Line2D.Double(x, y, x - length * 0.5, y + length));
        }
    }

    // ---- Ground ---------------------------------------------------------------------

    private void paintTerrain(Graphics2D g) {
        Mesh mesh = new Mesh(CAMERA, 1);
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
    private void paintGroundMarks(Graphics2D g) {
        g.setColor(new Color(70, 52, 50));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        cable(g, SOLAR_U + 1.8, SOLAR_V, HAB_U - 0.4, HAB_V + 1.9);
        cable(g, HAB_U + 1.9, HAB_V - 0.6, GREENHOUSE_U, GREENHOUSE_V + 0.3);
        cable(g, HAB_U - 0.2, HAB_V - 1.9, WEATHER_U + 0.1, WEATHER_V + 0.3);
        cable(g, HAB_U - 1.9, HAB_V + 0.1, DETECTOR_U + 0.5, DETECTOR_V + 0.2);
        g.setStroke(FACE_EDGE);

        Mesh pads = new Mesh(site, 1);
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
        // The pit the digger takes its soil from.
        double[][] pit = new double[8][];
        for (int k = 0; k < 8; k++) {
            double angle = k * Math.PI / 4;
            pit[k] = p(DIGGER_U - 1.25 + Math.cos(angle) * 0.62, DIGGER_V + 0.15 + Math.sin(angle) * 0.5, 0);
        }
        pads.sheet(new Color(120, 66, 46), pit);
        pads.paint(g);
    }

    private void flatPad(Mesh mesh, Color colour, double u, double v, double du, double dv) {
        mesh.sheet(colour, p(u, v, 0), p(u + du, v, 0), p(u + du, v + dv, 0), p(u, v + dv, 0));
    }

    private void cable(Graphics2D g, double u0, double v0, double u1, double v1) {
        g.draw(new Line2D.Double(site.x(u0, v0), site.y(u0, v0, 0), site.x(u1, v1), site.y(u1, v1, 0)));
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

    // ---- Buildings ------------------------------------------------------------------

    private void paintBuildings(Graphics2D g, Look look) {
        record Item(double depth, Consumer<Mesh> build) {}
        List<Item> items = new ArrayList<>();
        items.add(new Item(WEATHER_U + WEATHER_V, this::weatherStation));
        items.add(new Item(DETECTOR_U + DETECTOR_V, mesh -> detector(mesh, look.storm())));
        items.add(new Item(HAB_U + HAB_V, mesh -> habitat(mesh, look.shield() / 20.0, look.warm())));
        items.add(new Item(GREENHOUSE_U + GREENHOUSE_V + 1.5, mesh -> greenhouse(mesh, look.crops() / 8.0)));
        items.add(new Item(SOLAR_U + SOLAR_V + 2.5, mesh -> solarArray(mesh, look.clean() / 10.0)));
        items.add(new Item(DIGGER_U + DIGGER_V, this::digger));
        items.sort(Comparator.comparingDouble(Item::depth));
        for (Item item : items) {
            Mesh mesh = new Mesh(site, 1);
            item.build().accept(mesh);
            mesh.paint(g);
        }
    }

    /**
     * The astronaut's home: a faceted dome on a ring wall. A bank of Mars soil is piled
     * around it as a radiation shield, as tall as {@code shield} (0.0 to 1.0) of its full
     * height, and the windows are lit while the heater works.
     */
    private void habitat(Mesh mesh, double shield, boolean warm) {
        double bankHeight = BANK_HEIGHT * shield;
        if (shield > 0) {
            for (int k = 6; k <= 13; k++) {
                bank(mesh, bankHeight, k);
            }
        }

        int segments = 12;
        double wall = 0.34;
        double[] inside = p(HAB_U, HAB_V, wall);
        Color window = warm ? WINDOW_WARM : WINDOW_COLD;
        for (int k = 0; k < segments; k++) {
            double a0 = k * 2 * Math.PI / segments;
            double a1 = (k + 1) * 2 * Math.PI / segments;
            mesh.face(inside, k % 2 == 0 ? window : METAL,
                    ringPoint(a0, HAB_RADIUS, 0), ringPoint(a1, HAB_RADIUS, 0),
                    ringPoint(a1, HAB_RADIUS, wall), ringPoint(a0, HAB_RADIUS, wall));
        }
        for (int ring = 0; ring < DOME_TILT.length; ring++) {
            for (int k = 0; k < segments; k++) {
                Color panel = ring == 1 && k % 2 == 0 ? window
                        : (k + ring) % 2 == 0 ? HULL : Mesh.scale(HULL, 0.95);
                mesh.face(inside, panel, domePanel(ring, k));
            }
        }

        // Airlock, facing the camera through the gap in the soil bank.
        mesh.box(HAB_U + 0.85, HAB_V + 0.85, 0, 0.95, 0.95, 0.62, METAL);
        mesh.face(p(HAB_U + 1.3, HAB_V + 1.3, 0.3), warm ? WINDOW_WARM : DARK, door());
        if (shield > 0) {
            for (int k : new int[] {14, 5, 15, 4, 0, 3}) {
                bank(mesh, bankHeight, k);
            }
        }
    }

    private static final double[] DOME_TILT = {0, 0.52, 1.02};
    private static final double DOME_SQUASH = 0.82;
    private static final double DOME_WALL = 0.34;

    /** The corners of one panel of the dome: ring 0 is the lowest, and panel k of 12 runs round it. */
    private static double[][] domePanel(int ring, int k) {
        double a0 = k * 2 * Math.PI / 12;
        double a1 = (k + 1) * 2 * Math.PI / 12;
        double r0 = HAB_RADIUS * Math.cos(DOME_TILT[ring]);
        double z0 = DOME_WALL + HAB_RADIUS * DOME_SQUASH * Math.sin(DOME_TILT[ring]);
        if (ring == DOME_TILT.length - 1) {
            return new double[][] {ringPoint(a0, r0, z0), ringPoint(a1, r0, z0),
                p(HAB_U, HAB_V, DOME_WALL + HAB_RADIUS * DOME_SQUASH)};
        }
        double r1 = HAB_RADIUS * Math.cos(DOME_TILT[ring + 1]);
        double z1 = DOME_WALL + HAB_RADIUS * DOME_SQUASH * Math.sin(DOME_TILT[ring + 1]);
        return new double[][] {ringPoint(a0, r0, z0), ringPoint(a1, r0, z0),
            ringPoint(a1, r1, z1), ringPoint(a0, r1, z1)};
    }

    /** The airlock door: a flat panel on the side of the airlock that faces the camera. */
    private static double[][] door() {
        double u = HAB_U + 1.803;
        return new double[][] {p(u, HAB_V + 1.08, 0), p(u, HAB_V + 1.58, 0),
            p(u, HAB_V + 1.58, 0.48), p(u, HAB_V + 1.08, 0.48)};
    }

    /** The parts of the habitat that light up: the windows in the dome's middle ring, and the door. */
    private static void habitatWindows(Mesh mesh, Color colour) {
        double[] inside = p(HAB_U, HAB_V, DOME_WALL);
        for (int k = 0; k < 12; k += 2) {
            mesh.face(inside, colour, domePanel(1, k));
        }
        mesh.face(p(HAB_U + 1.3, HAB_V + 1.3, 0.3), colour, door());
    }

    private static double[] ringPoint(double angle, double radius, double h) {
        return p(HAB_U + Math.cos(angle) * radius, HAB_V + Math.sin(angle) * radius, h);
    }

    /** One of sixteen wedges of the soil bank around the habitat. Wedges 1 and 2 are the doorway. */
    private void bank(Mesh mesh, double height, int k) {
        double a0 = k * Math.PI / 8;
        double a1 = (k + 1) * Math.PI / 8;
        double inner = HAB_RADIUS + 0.06;
        double crest = HAB_RADIUS + 0.3;
        double foot = crest + height * 0.55;
        double[] inside = p(HAB_U + Math.cos((a0 + a1) / 2) * crest,
                HAB_V + Math.sin((a0 + a1) / 2) * crest, height / 2);
        Color soil = k % 2 == 0 ? SOIL : SOIL_LIGHT;
        mesh.face(inside, soil, ringPoint(a0, inner, height), ringPoint(a1, inner, height),
                ringPoint(a1, crest, height), ringPoint(a0, crest, height));
        mesh.face(inside, soil, ringPoint(a0, crest, height), ringPoint(a1, crest, height),
                ringPoint(a1, foot, 0), ringPoint(a0, foot, 0));
        // End walls, seen only at the doorway.
        mesh.face(inside, soil, ringPoint(a0, inner, 0), ringPoint(a0, foot, 0),
                ringPoint(a0, crest, height), ringPoint(a0, inner, height));
        mesh.face(inside, soil, ringPoint(a1, inner, 0), ringPoint(a1, foot, 0),
                ringPoint(a1, crest, height), ringPoint(a1, inner, height));
    }

    /** A glass tunnel of crops. The rows grow from seedlings to full green as food ripens on them. */
    private void greenhouse(Mesh mesh, double grown) {
        double u0 = GREENHOUSE_U;
        double u1 = GREENHOUSE_U + 3.0;
        double radius = 0.85;
        double floor = 0.14;
        mesh.box(u0 - 0.08, GREENHOUSE_V - radius - 0.08, 0, 3.16, 2 * radius + 0.16, 0.14, METAL);
        mesh.sheet(new Color(104, 74, 56), p(u0, GREENHOUSE_V - radius, floor), p(u1, GREENHOUSE_V - radius, floor),
                p(u1, GREENHOUSE_V + radius, floor), p(u0, GREENHOUSE_V + radius, floor));
        Color crop = mix(new Color(132, 112, 70), new Color(58, 176, 72), grown);
        double cropHeight = 0.05 + 0.3 * grown;
        for (int row = 0; row < 3; row++) {
            double v = GREENHOUSE_V - 0.62 + row * 0.47;
            mesh.box(u0 + 0.15, v, floor, 2.7, 0.3, cropHeight, crop);
        }
        greenhouseGlass(mesh, new Color(196, 236, 226, 96), new Color(226, 240, 236, 150));
    }

    /** The panes of the greenhouse, in {@code glass} and {@code frame} by turns. */
    private static void greenhouseGlass(Mesh mesh, Color glass, Color frame) {
        double u0 = GREENHOUSE_U;
        double u1 = GREENHOUSE_U + 3.0;
        double radius = 0.85;
        double floor = 0.14;
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

    /** Six panels tilted toward the north, where the Sun is at this latitude. Blue when clean, tan under dust. */
    private void solarArray(Mesh mesh, double clean) {
        Color cell = mix(new Color(196, 150, 110), new Color(46, 128, 240), clean);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                double u = SOLAR_U + col * 1.25;
                double v = SOLAR_V + row * 1.1;
                mesh.box(u + 0.47, v + 0.3, 0, 0.08, 0.08, 0.5, METAL);
                double high = 0.78;
                double lowEdge = 0.36;
                double[] below = p(u + 0.5, v + 0.4, 0);
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
        mesh.box(WEATHER_U - 0.22, WEATHER_V - 0.22, 0, 0.44, 0.44, 0.16, METAL);
        mesh.box(WEATHER_U - 0.04, WEATHER_V - 0.04, 0.16, 0.08, 0.08, 1.15, HULL);
        mesh.box(WEATHER_U - 0.45, WEATHER_V - 0.03, 1.0, 0.9, 0.06, 0.06, METAL);
        mesh.box(WEATHER_U - 0.52, WEATHER_V - 0.09, 0.94, 0.18, 0.18, 0.2, CUPS);
        mesh.box(WEATHER_U + 0.34, WEATHER_V - 0.09, 0.94, 0.18, 0.18, 0.2, CUPS);
        mesh.box(WEATHER_U - 0.1, WEATHER_V - 0.1, 1.31, 0.2, 0.2, 0.12, DARK);
    }

    private void detector(Mesh mesh, boolean storm) {
        mesh.box(DETECTOR_U - 0.3, DETECTOR_V - 0.3, 0, 0.6, 0.6, 0.36, HULL);
        mesh.box(DETECTOR_U - 0.16, DETECTOR_V - 0.16, 0.36, 0.32, 0.32, 0.1, DARK);
        mesh.box(DETECTOR_U - 0.09, DETECTOR_V - 0.09, 0.46, 0.18, 0.18, 0.2,
                storm ? ALERT : new Color(110, 220, 130));
    }

    /** The digger: a small yellow machine on tracks, its scoop over a heap of soil beside the pit. */
    private void digger(Mesh mesh) {
        double u = DIGGER_U;
        double v = DIGGER_V;
        // The heap of dug soil, furthest from the camera, first.
        double heapU = u - 1.3;
        double heapV = v - 0.1;
        double[] inside = p(heapU, heapV, 0);
        double[] peak = p(heapU, heapV, 0.36);
        for (int k = 0; k < 7; k++) {
            double a0 = k * 2 * Math.PI / 7;
            double a1 = (k + 1) * 2 * Math.PI / 7;
            mesh.face(inside, k % 2 == 0 ? SOIL : SOIL_LIGHT,
                    p(heapU + Math.cos(a0) * 0.45, heapV + Math.sin(a0) * 0.45, 0),
                    p(heapU + Math.cos(a1) * 0.45, heapV + Math.sin(a1) * 0.45, 0), peak);
        }
        // Arm and scoop, reaching from the body toward the heap.
        Color arm = Mesh.scale(DIGGER_YELLOW, 0.86);
        mesh.box(u - 1.06, v - 0.16, 0.3, 0.2, 0.32, 0.16, DARK);
        mesh.sheet(arm, p(u - 0.95, v - 0.05, 0.42), p(u - 0.95, v + 0.07, 0.42),
                p(u - 0.7, v + 0.07, 0.78), p(u - 0.7, v - 0.05, 0.78));
        mesh.sheet(arm, p(u - 0.7, v - 0.05, 0.78), p(u - 0.7, v + 0.07, 0.78),
                p(u - 0.36, v + 0.07, 0.4), p(u - 0.36, v - 0.05, 0.4));
        // Tracks, body and cab.
        mesh.box(u - 0.45, v - 0.34, 0, 0.9, 0.16, 0.15, DARK);
        mesh.box(u - 0.38, v - 0.24, 0.13, 0.62, 0.5, 0.24, DIGGER_YELLOW);
        mesh.box(u - 0.02, v - 0.18, 0.37, 0.24, 0.38, 0.22, new Color(88, 112, 150));
        mesh.box(u - 0.45, v + 0.2, 0, 0.9, 0.16, 0.15, DARK);
    }

    /**
     * The astronaut, standing at (u, v): a white suit, an orange backpack and a dark visor
     * that faces the camera. {@code lift} raises them a little, for a step.
     */
    private static void figure(Mesh mesh, double u, double v, double lift) {
        double s = FIGURE;
        double[][] shadow = new double[8][];
        for (int k = 0; k < 8; k++) {
            double angle = k * Math.PI / 4;
            shadow[k] = p(u + Math.cos(angle) * 0.16 * s, v + Math.sin(angle) * 0.16 * s, 0.002);
        }
        mesh.sheet(new Color(0, 0, 0, 70), shadow);
        mesh.box(u - 0.13 * s, v - 0.13 * s, lift + 0.17 * s, 0.13 * s, 0.13 * s, 0.21 * s, SUIT_PACK);
        mesh.box(u - 0.08 * s, v - 0.045 * s, lift, 0.065 * s, 0.09 * s, 0.17 * s, METAL);
        mesh.box(u + 0.015 * s, v - 0.045 * s, lift, 0.065 * s, 0.09 * s, 0.17 * s, METAL);
        mesh.box(u - 0.09 * s, v - 0.09 * s, lift + 0.16 * s, 0.18 * s, 0.18 * s, 0.21 * s, SUIT);
        double head = lift + 0.37 * s;
        mesh.box(u - 0.08 * s, v - 0.08 * s, head, 0.16 * s, 0.16 * s, 0.16 * s, SUIT);
        // The visor wraps round the corner of the helmet nearest the camera.
        double out = 0.083 * s;
        mesh.sheet(VISOR, p(u + out, v - 0.04 * s, head + 0.04 * s), p(u + out, v + 0.08 * s, head + 0.04 * s),
                p(u + out, v + 0.08 * s, head + 0.12 * s), p(u + out, v - 0.04 * s, head + 0.12 * s));
        mesh.sheet(VISOR, p(u - 0.04 * s, v + out, head + 0.04 * s), p(u + 0.08 * s, v + out, head + 0.04 * s),
                p(u + 0.08 * s, v + out, head + 0.12 * s), p(u - 0.04 * s, v + out, head + 0.12 * s));
    }
}
