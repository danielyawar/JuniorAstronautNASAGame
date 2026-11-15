package outpost.view;

/**
 * The angled bird's-eye camera. Ground positions are (u, v) in tiles and h is height in
 * tiles. On screen, u runs down and to the right, v runs down and to the left, and h runs
 * straight up, so a point with larger u + v is nearer the camera.
 *
 * @param originX    screen x of ground position (0, 0)
 * @param originY    screen y of ground position (0, 0) at height 0
 * @param halfWidth  pixels a tile spans sideways from its centre to a corner
 * @param halfHeight pixels a tile spans up or down from its centre to a corner
 * @param rise       pixels one tile of height lifts a point
 */
record Projection(double originX, double originY, double halfWidth, double halfHeight, double rise) {

    double x(double u, double v) {
        return originX + (u - v) * halfWidth;
    }

    double y(double u, double v, double h) {
        return originY + (u + v) * halfHeight - h * rise;
    }

    /** True when a surface with outward direction {@code n} = {u, v, h} can be seen. */
    boolean facesCamera(double[] n) {
        // Moving by (1, 1, 2 * halfHeight / rise) does not move a point on screen, so that
        // is the direction from the scene toward the camera.
        return n[0] + n[1] + n[2] * (2 * halfHeight / rise) > 1e-9;
    }
}
