import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Builds SpaceGuy.jar from src/. Needs only a JDK (17 or newer) on the PATH.
 * Run from the repository root:
 *
 *     java Build.java
 *
 * Compiles for Java 17 whatever JDK runs it, and writes jar entries in a fixed order with
 * a fixed timestamp so the same sources always give the same jar.
 */
public class Build {

    static final Path SRC = Path.of("src");
    static final Path CLASSES = Path.of("build", "classes");
    static final Path JAR = Path.of("SpaceGuy.jar");
    static final String MAIN_CLASS = "outpost.app.Main";
    static final long FIXED_TIME = 1_577_836_800_000L; // 2020-01-01 UTC

    public static void main(String[] args) throws IOException {
        if (Files.exists(CLASSES)) {
            List<Path> old = sorted(CLASSES);
            Collections.reverse(old);
            for (Path p : old) {
                Files.delete(p);
            }
        }
        Files.createDirectories(CLASSES);

        List<String> javacArgs = new ArrayList<>(
                List.of("--release", "17", "-encoding", "UTF-8", "-Xlint:all", "-Werror", "-d", CLASSES.toString()));
        for (Path p : sorted(SRC)) {
            if (p.toString().endsWith(".java")) {
                javacArgs.add(p.toString());
            }
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("No compiler found. Run with a JDK, not a JRE.");
        }
        if (javac.run(null, null, null, javacArgs.toArray(new String[0])) != 0) {
            System.exit(1);
        }

        // Data files (the bundled NASA archives) sit beside the classes that read them.
        for (Path p : sorted(SRC)) {
            if (Files.isRegularFile(p) && !p.toString().endsWith(".java")) {
                Path target = CLASSES.resolve(SRC.relativize(p));
                Files.createDirectories(target.getParent());
                Files.copy(p, target);
            }
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        try (OutputStream out = Files.newOutputStream(JAR);
             JarOutputStream jar = new JarOutputStream(out)) {
            putEntry(jar, "META-INF/MANIFEST.MF");
            manifest.write(jar);
            for (Path p : sorted(CLASSES)) {
                if (Files.isRegularFile(p)) {
                    putEntry(jar, CLASSES.relativize(p).toString().replace('\\', '/'));
                    Files.copy(p, jar);
                }
            }
        }
        System.out.println("Built " + JAR + " (" + Files.size(JAR) + " bytes)");
    }

    static void putEntry(JarOutputStream jar, String name) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(FIXED_TIME);
        jar.putNextEntry(entry);
    }

    static List<Path> sorted(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.sorted().collect(Collectors.toCollection(ArrayList::new));
        }
    }
}
