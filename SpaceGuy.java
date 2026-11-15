import java.awt.*;
import java.awt.geom.*;
import java.util.Random;
import javax.swing.*;

/**
 * Quick demo: a window that draws a Moon outpost scene with Graphics2D.
 * No image files needed. Click the button to toggle a solar flare.
 *
 * Run with:  java OutpostDemo.java   (JDK 11+)
 */
public class SpaceGuy extends JPanel {

    private boolean flare = false;
    private final int[] starX = new int[80];
    private final int[] starY = new int[80];

    public SpaceGuy() {
        setPreferredSize(new Dimension(640, 360));
        Random rng = new Random(42);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rng.nextInt(640);
            starY[i] = rng.nextInt(220);
        }
    }

    void setFlare(boolean on) {
        flare = on;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        // Space
        g.setColor(new Color(8, 8, 24));
        g.fillRect(0, 0, w, h);

        // Stars
        g.setColor(Color.WHITE);
        for (int i = 0; i < starX.length; i++) {
            g.fillOval(starX[i] * w / 640, starY[i] * h / 360, 2, 2);
        }

        // Earth in the sky
        g.setPaint(new GradientPaint(w - 120, 40, new Color(60, 120, 220),
                                     w - 70, 90, new Color(40, 160, 90)));
        g.fill(new Ellipse2D.Double(w - 120, 40, 50, 50));

        // Solar flare glow + rays from the upper left
        if (flare) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 320f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 170, 0, 200), new Color(255, 80, 0, 0)}));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 200, 60, 160));
            g.setStroke(new BasicStroke(3f));
            for (int i = 0; i < 7; i++) {
                g.drawLine(10, 10, 140 + i * 70, h - 100);
            }
        }

        // Moon ground
        int groundY = h - 110;
        g.setColor(new Color(150, 150, 155));
        g.fillRect(0, groundY, w, 110);
        g.setColor(new Color(120, 120, 125));
        g.fill(new Ellipse2D.Double(80, groundY + 40, 90, 22));
        g.fill(new Ellipse2D.Double(430, groundY + 60, 120, 26));

        // Habitat dome
        int cx = w / 2;
        g.setColor(new Color(220, 225, 235));
        g.fill(new Arc2D.Double(cx - 70, groundY - 60, 140, 120, 0, 180, Arc2D.PIE));
        g.setColor(new Color(90, 150, 220));
        g.fill(new Rectangle2D.Double(cx - 12, groundY - 22, 24, 22));

        // Shield outline (shows when flare is active)
        if (flare) {
            g.setColor(new Color(80, 200, 255, 150));
            g.setStroke(new BasicStroke(4f));
            g.draw(new Arc2D.Double(cx - 85, groundY - 78, 170, 156, 0, 180, Arc2D.OPEN));
        }

        // Rover
        g.setColor(new Color(200, 140, 40));
        g.fillRect(cx + 130, groundY + 10, 50, 18);
        g.setColor(Color.DARK_GRAY);
        g.fillOval(cx + 134, groundY + 24, 14, 14);
        g.fillOval(cx + 162, groundY + 24, 14, 14);

        // Caption
        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g.drawString(flare ? "Solar flare! Radiation is rising." : "A quiet day on the Moon.", 12, h - 12);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Moon Outpost - Image Window Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            SpaceGuy scene = new SpaceGuy();
            JToggleButton flareBtn = new JToggleButton("Trigger solar flare");
            JToggleButton mybtn = new JToggleButton("Hello World");
            flareBtn.addActionListener(e -> scene.setFlare(flareBtn.isSelected()));

            JLabel credit = new JLabel("Illustration drawn in code (not a NASA image).",
                                       SwingConstants.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(4, 4));
            JPanel bottom2 = new JPanel(new BorderLayout(4, 4));
            bottom.add(bottom2, BorderLayout.CENTER);
            bottom.add(credit, BorderLayout.NORTH);

            bottom2.add(mybtn, BorderLayout.WEST);
            bottom2.add(flareBtn, BorderLayout.EAST);

            frame.add(scene, BorderLayout.CENTER);
            frame.add(bottom, BorderLayout.NORTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}