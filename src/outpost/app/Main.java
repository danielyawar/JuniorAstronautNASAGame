package outpost.app;

import java.awt.BorderLayout;
import java.time.LocalDate;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import outpost.radiation.ArchiveRadiationSource;
import outpost.shared.Sol;
import outpost.view.OutpostView;
import outpost.weather.ArchiveWeatherSource;

/** Entry point. Wires the feature modules to the shared controller. */
public final class Main {

    /**
     * Fixed start, two weeks before the 2018 global dust storm reached Gale Crater. The
     * plan calls for a random date from the archive.
     */
    private static final Sol START = new Sol(1, LocalDate.of(2018, 6, 1));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        // Module wiring: to swap a module, change only its lines here.
        GameController controller = new GameController(
                new ArchiveWeatherSource(), new ArchiveRadiationSource(), START);

        // The view is the whole window: every control is drawn in it.
        OutpostView view = new OutpostView(controller);
        controller.addListener(view::show);

        JFrame frame = new JFrame("Junior Astronaut: Mars Outpost");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private Main() {}
}
