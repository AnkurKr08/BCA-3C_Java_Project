package com.carepoint;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * Shared CarePoint design tokens, antialiased rendering utilities,
 * and UI component styling for a modern, premium healthcare SaaS experience.
 */
public final class UITheme {
    private UITheme() {}

    // Core Color Palette
    public static final Color NAVY = new Color(20, 43, 74);       // #142B4A Primary Navy
    public static final Color TEAL = new Color(22, 166, 161);     // #16A6A1 Medical Teal
    public static final Color TEAL_HOVER = new Color(18, 148, 143);
    public static final Color TEAL_LIGHT = new Color(232, 248, 247);
    public static final Color BLUE = new Color(47, 105, 221);     // Accent Blue
    public static final Color BG = new Color(244, 247, 251);       // #F4F7FB Canvas Background
    public static final Color CARD_BG = Color.WHITE;               // #FFFFFF Surface/Card
    public static final Color TEXT_MAIN = new Color(29, 41, 57);   // #1D2939 Main Text
    public static final Color TEXT_MUTED = new Color(102, 112, 133); // #667085 Secondary Text
    public static final Color BORDER = new Color(228, 234, 241);   // #E4EAF1 Subtle Borders
    public static final Color BORDER_FOCUS = new Color(22, 166, 161);

    // Status Colors
    public static final Color SUCCESS = new Color(33, 150, 83);    // #219653
    public static final Color SUCCESS_BG = new Color(232, 248, 240);
    public static final Color SUCCESS_BORDER = new Color(198, 238, 216);

    public static final Color WARNING = new Color(217, 119, 6);    // Amber
    public static final Color WARNING_BG = new Color(254, 246, 233);
    public static final Color WARNING_BORDER = new Color(250, 219, 179);

    public static final Color INFO = new Color(37, 99, 235);       // Blue
    public static final Color INFO_BG = new Color(235, 245, 255);
    public static final Color INFO_BORDER = new Color(204, 229, 255);

    public static final Color DANGER = new Color(214, 69, 69);     // #D64545
    public static final Color DANGER_BG = new Color(253, 240, 240);
    public static final Color DANGER_BORDER = new Color(249, 208, 208);

    public static final Color MUTED_BG = new Color(242, 244, 247);
    public static final Color MUTED_BORDER = new Color(234, 236, 240);

    // Typography
    public static final Font FONT_DISPLAY = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FONT_HEADING = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_TINY = new Font("Segoe UI", Font.BOLD, 10);

    /**
     * Enables antialiasing and high-quality rendering hints.
     */
    public static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * Creates a rounded card border with optional subtle shadow tint.
     */
    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 12, 1),
                new EmptyBorder(16, 18, 16, 18)
        );
    }

    /**
     * Styles primary button with medical teal gradient, smooth pill rounding,
     * crisp typography, and interactive hover/press states.
     */
    public static void stylePrimaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                applyQualityHints(g);
                JButton btn = (JButton) component;
                int w = btn.getWidth();
                int h = btn.getHeight();
                boolean enabled = btn.isEnabled();
                boolean pressed = btn.getModel().isPressed() && btn.getModel().isArmed();
                boolean hovered = btn.getModel().isRollover();

                Color top = !enabled ? new Color(145, 175, 195)
                        : pressed ? new Color(15, 128, 124)
                        : hovered ? new Color(20, 180, 175) : TEAL;
                Color bottom = !enabled ? new Color(125, 155, 175)
                        : pressed ? new Color(12, 108, 104)
                        : hovered ? new Color(16, 156, 151) : TEAL_HOVER;

                g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);

                if (btn.hasFocus() && enabled) {
                    g.setColor(new Color(255, 255, 255, 180));
                    g.drawRoundRect(2, 2, w - 5, h - 5, 8, 8);
                }
                g.dispose();
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Styles secondary button with clean white background, border, and text.
     */
    public static void styleSecondaryButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(TEXT_MAIN);
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                applyQualityHints(g);
                JButton btn = (JButton) component;
                int w = btn.getWidth();
                int h = btn.getHeight();
                boolean enabled = btn.isEnabled();
                boolean pressed = btn.getModel().isPressed() && btn.getModel().isArmed();
                boolean hovered = btn.getModel().isRollover();

                Color bg = !enabled ? new Color(248, 249, 251)
                        : pressed ? new Color(230, 236, 245)
                        : hovered ? new Color(242, 246, 252) : Color.WHITE;

                g.setColor(bg);
                g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
                g.setColor(BORDER);
                g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

                if (btn.hasFocus() && enabled) {
                    g.setColor(TEAL);
                    g.drawRoundRect(1, 1, w - 3, h - 3, 9, 9);
                }
                g.dispose();
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(9, 16, 9, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Styles danger action button (e.g., Cancel / Deactivate / Logout).
     */
    public static void styleDangerButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(DANGER);
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                applyQualityHints(g);
                JButton btn = (JButton) component;
                int w = btn.getWidth();
                int h = btn.getHeight();
                boolean pressed = btn.getModel().isPressed() && btn.getModel().isArmed();
                boolean hovered = btn.getModel().isRollover();

                Color bg = pressed ? new Color(250, 220, 220)
                        : hovered ? DANGER_BG : Color.WHITE;

                g.setColor(bg);
                g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g.setColor(DANGER_BORDER);
                g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                g.dispose();
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(7, 14, 7, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * Applies premium styling to tables including clean headers, 40px row height,
     * cell padding, and alternating row background.
     */
    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setForeground(TEXT_MAIN);
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(238, 242, 247));
        table.setSelectionBackground(new Color(232, 244, 252));
        table.setSelectionForeground(NAVY);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Styled Header
        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_SMALL_BOLD);
        header.setBackground(new Color(247, 249, 252));
        header.setForeground(new Color(71, 84, 103));
        header.setPreferredSize(new Dimension(0, 38));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        // Default cell renderer with left/right padding
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val,
                                                           boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, val, isSelected, hasFocus, row, col);
                setBorder(new EmptyBorder(0, 14, 0, 14));
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(250, 252, 254));
                    c.setForeground(TEXT_MAIN);
                }
                return c;
            }
        });
    }

    /**
     * Installs an antialiased pill status badge renderer on a specific table column.
     */
    public static void installStatusBadgeRenderer(JTable table, int columnIndex) {
        table.getColumnModel().getColumn(columnIndex).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int col) {
                String status = value == null ? "" : String.valueOf(value).trim();
                PillBadge badge = new PillBadge(status);
                if (isSelected) {
                    badge.setRowSelected(true);
                }
                return badge;
            }
        });
    }

    /**
     * Styles an input text field with consistent font, padding, and subtle rounded border.
     */
    public static void styleInputField(JTextField field) {
        field.setFont(FONT_BODY);
        field.setForeground(TEXT_MAIN);
        field.setCaretColor(TEAL);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(BORDER, 8, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    /**
     * Styles a combo box with consistent font and height.
     */
    public static void styleComboBox(JComboBox<?> box) {
        box.setFont(FONT_BODY);
        box.setForeground(TEXT_MAIN);
        box.setBackground(Color.WHITE);
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 36));
    }

    /**
     * Styles a spinner with consistent font and height.
     */
    public static void styleSpinner(JSpinner spinner) {
        spinner.setFont(FONT_BODY);
        spinner.setForeground(TEXT_MAIN);
        spinner.setBackground(Color.WHITE);
        spinner.setPreferredSize(new Dimension(spinner.getPreferredSize().width, 36));
    }

    /**
     * Antialiased rounded border implementation.
     */
    public static final class RoundedLineBorder extends AbstractBorder {
        private final Color color;
        private final int radius;
        private final int thickness;

        public RoundedLineBorder(Color color, int radius, int thickness) {
            this.color = color;
            this.radius = radius;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            applyQualityHints(g);
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            g.draw(new RoundRectangle2D.Float(
                    x + thickness / 2.0f,
                    y + thickness / 2.0f,
                    width - thickness,
                    height - thickness,
                    radius, radius
            ));
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
            return insets;
        }
    }

    /**
     * Antialiased pill badge for status rendering in tables.
     */
    public static final class PillBadge extends JComponent {
        private final String text;
        private boolean rowSelected;

        public PillBadge(String text) {
            this.text = text;
            setFont(FONT_SMALL_BOLD);
            setOpaque(false);
        }

        public void setRowSelected(boolean selected) {
            this.rowSelected = selected;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            applyQualityHints(g);

            int w = getWidth();
            int h = getHeight();

            Color bg;
            Color fg;
            Color border;
            Color dotColor;

            String upper = text.toUpperCase();
            if ("CONFIRMED".equals(upper) || "ACTIVE".equals(upper)) {
                bg = SUCCESS_BG;
                fg = SUCCESS;
                border = SUCCESS_BORDER;
                dotColor = SUCCESS;
            } else if ("PENDING".equals(upper)) {
                bg = WARNING_BG;
                fg = WARNING;
                border = WARNING_BORDER;
                dotColor = WARNING;
            } else if ("COMPLETED".equals(upper)) {
                bg = INFO_BG;
                fg = INFO;
                border = INFO_BORDER;
                dotColor = INFO;
            } else if ("CANCELLED".equals(upper) || "INACTIVE".equals(upper)) {
                bg = DANGER_BG;
                fg = DANGER;
                border = DANGER_BORDER;
                dotColor = DANGER;
            } else {
                bg = MUTED_BG;
                fg = TEXT_MUTED;
                border = MUTED_BORDER;
                dotColor = TEXT_MUTED;
            }

            if (rowSelected) {
                g.setColor(new Color(232, 244, 252));
                g.fillRect(0, 0, w, h);
            }

            FontMetrics fm = g.getFontMetrics(getFont());
            int textWidth = fm.stringWidth(text);
            int badgeWidth = Math.min(w - 12, textWidth + 28);
            int badgeHeight = 24;
            int x = (w - badgeWidth) / 2;
            int y = (h - badgeHeight) / 2;

            g.setColor(bg);
            g.fillRoundRect(x, y, badgeWidth, badgeHeight, 12, 12);
            g.setColor(border);
            g.setStroke(new BasicStroke(1.0f));
            g.drawRoundRect(x, y, badgeWidth - 1, badgeHeight - 1, 12, 12);

            // Small status dot
            int dotSize = 6;
            int dotX = x + 8;
            int dotY = y + (badgeHeight - dotSize) / 2;
            g.setColor(dotColor);
            g.fillOval(dotX, dotY, dotSize, dotSize);

            // Text
            g.setColor(fg);
            g.setFont(getFont());
            int textX = dotX + dotSize + 6;
            int textY = y + ((badgeHeight - fm.getHeight()) / 2) + fm.getAscent();
            g.drawString(text, textX, textY);

            g.dispose();
        }
    }
}
