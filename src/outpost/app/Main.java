package outpost.app;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import outpost.radiation.ArchiveRadiationSource;
import outpost.view.OutpostView;
import outpost.weather.ArchiveWeatherSource;

/**
 * Entry point. Wires the feature modules to the shared controller and owns the clock.
 *
 * <p>Two words on the command line are for whoever works on the game:
 * {@code balance} plays whole missions with scripted players and prints the results
 * instead of opening a window (see Balance); {@code paint-times} shows in a corner how
 * long each repaint takes.
 */
public final class Main {

    /** Milliseconds between ticks of the clock: 20 a second when the computer keeps up. */
    private static final int TICK_MILLIS = 50;
    /**
     * Most real time one tick may count for. If the computer is slow, or the browser tab was
     * in the background, the game slows down instead of jumping ahead.
     */
    private static final double LONGEST_TICK_SECONDS = 0.25;

    public static void main(String[] args) {
        List<String> options = List.of(args);
        // Module wiring: to swap a module, change only its lines here. The controller is
        // told where the weather archive ends so that a surprise mission fits inside it.
        ArchiveWeatherSource weather = new ArchiveWeatherSource();
        ArchiveRadiationSource radiation = new ArchiveRadiationSource();
        if (options.contains("balance")) {
            Balance.run(weather, radiation, weather.lastDate(), options);
            return;
        }
        SwingUtilities.invokeLater(() -> start(
                new GameController(weather, radiation, weather.lastDate()), options.contains("paint-times")));
    }

    private static void start(GameController controller, boolean paintTimes) {
        // The view is the whole window: every control is drawn in it.
        OutpostView view = new OutpostView(controller);
        view.showPaintTime(paintTimes);
        controller.addListener(view::show);

        JFrame frame = new JFrame("Junior Astronaut: Mars Outpost");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(view, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // The clock. A Swing timer fires on the event thread, and drops ticks instead of
        // queueing them when painting is slow, so the game stays responsive. The controller
        // ignores ticks while a card is open or no sol is running, so nothing is repainted then.
        long[] last = {System.nanoTime()};
        Timer clock = new Timer(TICK_MILLIS, event -> {
            long now = System.nanoTime();
            double seconds = Math.min((now - last[0]) / 1e9, LONGEST_TICK_SECONDS);
            last[0] = now;
            controller.advance(seconds);
        });
        clock.start();
    }

    private Main() {}
}
