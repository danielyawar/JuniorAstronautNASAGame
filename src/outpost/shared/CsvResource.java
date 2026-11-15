package outpost.shared;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the small CSV archives bundled in the jar. The files have no quoted fields: lines
 * starting with '#' are comments, the first other line is the column header, and every
 * line after that is one row.
 */
public final class CsvResource {

    /** The comment lines (without the leading '#') and the data rows of one file. */
    public record Table(List<String> comments, List<String[]> rows) {}

    /**
     * @param owner a class in the same package as the file
     * @param name  the file name, for example "curiosity_weather.csv"
     */
    public static Table read(Class<?> owner, String name) {
        InputStream in = owner.getResourceAsStream(name);
        if (in == null) {
            throw new IllegalStateException("Missing data file " + name + " next to "
                    + owner.getName() + ". Rebuild with: java Build.java");
        }
        List<String> comments = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            boolean headerSeen = false;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#")) {
                    comments.add(line.substring(1).trim());
                } else if (!line.isBlank()) {
                    if (headerSeen) {
                        rows.add(line.split(",", -1));
                    }
                    headerSeen = true;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Table(comments, rows);
    }

    private CsvResource() {}
}
