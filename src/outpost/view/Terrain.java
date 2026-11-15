package outpost.view;

import java.util.List;
import outpost.shared.CsvResource;

/**
 * Real ground heights around Curiosity's landing site in Gale Crater, from NASA's MOLA
 * laser altimeter map, read from the archive bundled in the jar (gale_terrain.csv, made
 * by tools/fetch_nasa_data.py). Rows run north to south and columns west to east.
 */
final class Terrain {

    /** Heights along one side of the square grid. */
    final int size;
    /** Distance between neighbouring heights, in metres. */
    final double spacingM;
    private final double[][] metres;

    private Terrain(double[][] metres, double spacingM) {
        this.metres = metres;
        this.size = metres.length;
        this.spacingM = spacingM;
    }

    static Terrain load() {
        CsvResource.Table table = CsvResource.read(Terrain.class, "gale_terrain.csv");
        List<String[]> rows = table.rows();
        double[][] metres = new double[rows.size()][];
        for (int r = 0; r < metres.length; r++) {
            String[] cells = rows.get(r);
            if (cells.length != metres.length) {
                throw new IllegalStateException("gale_terrain.csv is not a square grid");
            }
            metres[r] = new double[cells.length];
            for (int c = 0; c < cells.length; c++) {
                metres[r][c] = Double.parseDouble(cells[c]);
            }
        }
        double spacing = Double.NaN;
        for (String comment : table.comments()) {
            if (comment.startsWith("spacing_m=")) {
                spacing = Double.parseDouble(comment.substring("spacing_m=".length()));
            }
        }
        if (metres.length < 2 || Double.isNaN(spacing)) {
            throw new IllegalStateException("gale_terrain.csv is missing heights or spacing_m");
        }
        return new Terrain(metres, spacing);
    }

    /** Height in metres above the Mars reference surface. Row 0 is north, column 0 is west. */
    double metresAt(int row, int col) {
        return metres[row][col];
    }

    double lowest() {
        double low = Double.POSITIVE_INFINITY;
        for (double[] row : metres) {
            for (double m : row) {
                low = Math.min(low, m);
            }
        }
        return low;
    }

    double highest() {
        double high = Double.NEGATIVE_INFINITY;
        for (double[] row : metres) {
            for (double m : row) {
                high = Math.max(high, m);
            }
        }
        return high;
    }
}
