package outpost.foodpower;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import outpost.shared.GameState;
import outpost.shared.PowerAllocation;

/**
 * The power controls: a minus and a plus button for each of shielding, greenhouse and
 * heating, and a readout of the power left over. Food is not simulated yet.
 */
public final class FoodPowerPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Allocation a new game starts with. */
    public static final PowerAllocation START_ALLOCATION = new PowerAllocation(30, 40, 30);

    /** Percentage points one press of a button moves. */
    private static final int STEP = 10;

    private final transient Consumer<PowerAllocation> onAllocate;
    private final JLabel[] values = new JLabel[3];
    private final JButton[] minus = new JButton[3];
    private final JButton[] plus = new JButton[3];
    private final JLabel spare = new JLabel();
    private transient GameState state;

    /** @param onAllocate receives the allocation each time the player changes it */
    public FoodPowerPanel(Consumer<PowerAllocation> onAllocate) {
        this.onAllocate = onAllocate;
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        String[] names = {"Shield", "Greenhouse", "Heat"};
        for (int i = 0; i < 3; i++) {
            int index = i;
            add(new JLabel(names[i]));
            minus[i] = button("-", () -> change(index, -STEP));
            add(minus[i]);
            values[i] = new JLabel();
            values[i].setFont(values[i].getFont().deriveFont(Font.BOLD));
            add(values[i]);
            plus[i] = button("+", () -> change(index, STEP));
            add(plus[i]);
            add(new JLabel("  "));
        }
        add(spare);
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(1, 7, 1, 7));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void change(int index, int delta) {
        if (state == null) {
            return;
        }
        PowerAllocation a = state.allocation();
        int[] shares = {a.shielding(), a.greenhouse(), a.heating()};
        int unused = 100 - shares[0] - shares[1] - shares[2];
        // Clamp so the share stays at 0 or more and the total stays at 100 or less.
        int applied = Math.max(-shares[index], Math.min(unused, delta));
        if (applied != 0) {
            shares[index] += applied;
            onAllocate.accept(new PowerAllocation(shares[0], shares[1], shares[2]));
        }
    }

    /** Called by the controller wiring whenever the state changes. */
    public void show(GameState newState) {
        state = newState;
        PowerAllocation a = newState.allocation();
        int unused = 100 - a.shielding() - a.greenhouse() - a.heating();
        ToIntFunction<Integer> share = i -> i == 0 ? a.shielding() : i == 1 ? a.greenhouse() : a.heating();
        for (int i = 0; i < 3; i++) {
            int value = share.applyAsInt(i);
            values[i].setText(value + "%");
            minus[i].setEnabled(value > 0);
            plus[i].setEnabled(unused > 0);
        }
        spare.setText("Spare power " + unused + "%");
    }
}
