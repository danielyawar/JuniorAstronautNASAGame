package outpost.app;

import java.awt.BorderLayout;
import java.time.LocalDate;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import outpost.foodpower.FoodPowerPanel;
import outpost.radiation.ArchiveRadiationSource;
import outpost.shared.Sol;
import outpost.view.OutpostView;
import outpost.weather.ArchiveWeatherSource;

/** Entry point. Wires the feature modules to the shared controller. */
public final class Main {

    /**
     * Fixed start, a few sols before the 2018 global dust storm reached Gale Crater. The
     * plan calls for a random date from the archive.
     */
    private static final Sol START = new Sol(1, LocalDate.of(2018, 6, 1));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        // Module wiring: to swap a module, change only its lines here.
        GameController controller = new GameController(
                new ArchiveWeatherSource(), new ArchiveRadiationSource(),
                START, FoodPowerPanel.START_ALLOCATION);

        OutpostView view = new OutpostView();
        FoodPowerPanel powerControls = new FoodPowerPanel(controller::setAllocation);
        controller.addListener(view::show);
        controller.addListener(powerControls::show);

        // Shared controls.
        JButton nextSol = new JButton("Next Sol");
        nextSol.addActionListener(e -> controller.nextSol());
        JButton restart = new JButton("Restart");
        restart.addActionListener(e -> controller.restart());
        JPanel buttons = new JPanel();
        buttons.add(nextSol);
        buttons.add(restart);
        JPanel controls = new JPanel(new BorderLayout());
        controls.add(powerControls, BorderLayout.CENTER);
        controls.add(buttons, BorderLayout.EAST);

        JFrame frame = new JFrame("Junior Astronaut: Mars Outpost");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private Main() {}
}
