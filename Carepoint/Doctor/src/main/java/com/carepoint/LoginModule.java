package com.carepoint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;

public final class LoginModule extends JFrame {
    static final Color NAVY = UITheme.NAVY;
    static final Color BLUE = UITheme.TEAL;
    static final Color TEAL = UITheme.TEAL;
    static final Color BG = UITheme.BG;
    static final Color MUTED = UITheme.TEXT_MUTED;
    static final Font BODY = UITheme.FONT_BODY;
    static final Font DISPLAY = UITheme.FONT_DISPLAY;
    private final JTextField email = new PlaceholderTextField("you@example.com", 24);
    private final JPasswordField password = new PlaceholderPasswordField("Enter your password", 24);
    private final JCheckBox remember = new JCheckBox("Remember me");
    private final JButton signIn = new JButton("Sign in");
    private final JLabel status = new JLabel(" ");
    private final Preferences preferences = Preferences.userNodeForPackage(LoginModule.class);

    public LoginModule() {
        super("CarePoint | Healthcare, made simpler");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 650));
        setContentPane(buildScreen());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        String savedEmail = preferences.get("rememberedEmail", "");
        email.setText(savedEmail);
        remember.setSelected(!savedEmail.isBlank());
        signIn.addActionListener(e -> authenticate());
        password.addActionListener(e -> authenticate());
    }

    private JPanel buildScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setMinimumSize(new Dimension(0, 0));
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(36, 44, 32, 44));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        JComponent mark = createBrandMark();
        JLabel logo = new JLabel("CarePoint");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        logo.setForeground(NAVY);
        logo.setBorder(new EmptyBorder(0, 10, 0, 0));
        brand.add(mark);
        brand.add(logo);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(brand);
        content.add(Box.createVerticalStrut(28));
        JLabel title = new JLabel("Welcome back");
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(NAVY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(label("Sign in to manage your care and appointments."));
        content.add(Box.createVerticalStrut(26));
        content.add(fieldLabel("Email address"));
        email.setToolTipText("Enter the email address on your CarePoint account");
        styleField(email);
        JPanel emailRow = fieldRow(email, FieldIcon.Type.EMAIL);
        content.add(emailRow);
        content.add(Box.createVerticalStrut(16));
        content.add(fieldLabel("Password"));
        JPanel passwordLine = new JPanel(new BorderLayout(8, 0));
        passwordLine.setBackground(Color.WHITE);
        styleField(password);
        passwordLine.add(password, BorderLayout.CENTER);
        password.setToolTipText("Enter your password");
        JButton showPassword = linkButton("Show password");
        showPassword.setFont(new Font("Segoe UI", Font.BOLD, 12));
        showPassword.setMargin(new Insets(0, 2, 0, 2));
        showPassword.addActionListener(e -> {
            boolean showing = password.getEchoChar() != (char) 0;
            password.setEchoChar(showing ? (char) 0 : '\u2022');
            showPassword.setText(showing ? "Hide password" : "Show password");
        });
        passwordLine.add(new FieldIcon(FieldIcon.Type.LOCK), BorderLayout.WEST);
        passwordLine.add(showPassword, BorderLayout.EAST);
        styleFieldRow(passwordLine, password);
        content.add(passwordLine);
        content.add(Box.createVerticalStrut(10));
        JPanel options = new JPanel(new BorderLayout());
        options.setBackground(Color.WHITE);
        remember.setFont(BODY);
        remember.setForeground(MUTED);
        remember.setBackground(Color.WHITE);
        remember.setFocusPainted(false);
        options.add(remember, BorderLayout.WEST);
        JButton forgot = linkButton("Forgot password?");
        forgot.setFont(new Font("Segoe UI", Font.BOLD, 12));
        forgot.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Password recovery by email is not configured. Please contact your CarePoint administrator.",
                "Password recovery", JOptionPane.INFORMATION_MESSAGE));
        options.add(forgot, BorderLayout.EAST);
        options.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        options.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(options);
        content.add(Box.createVerticalStrut(20));
        signIn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        signIn.setForeground(Color.WHITE);
        UITheme.stylePrimaryButton(signIn);
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        signIn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        content.add(signIn);
        status.setFont(BODY);
        status.setForeground(UITheme.DANGER);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(6));
        content.add(status);
        content.add(Box.createVerticalStrut(6));
        JButton register = new JButton("Create patient account");
        register.setFont(new Font("Segoe UI", Font.BOLD, 14));
        UITheme.styleSecondaryButton(register);
        register.setAlignmentX(Component.LEFT_ALIGNMENT);
        register.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        register.addActionListener(e -> showRegistration());
        content.add(register);
        content.add(Box.createVerticalStrut(16));
        JPanel security = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        security.setOpaque(false);
        JLabel lock = new JLabel("●");
        lock.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lock.setForeground(UITheme.TEAL);
        JLabel securityText = new JLabel("Your health information is private and secure.");
        securityText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        securityText.setForeground(MUTED);
        security.add(lock);
        security.add(securityText);
        security.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(security);
        GridBagConstraints formConstraints = new GridBagConstraints();
        formConstraints.gridx = 0;
        formConstraints.gridy = 0;
        formConstraints.weightx = 1;
        formConstraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(content, formConstraints);

        JPanel welcome = new WelcomePanel();
        welcome.setMinimumSize(new Dimension(0, 0));
        JSplitPane columns = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, form, welcome);
        columns.setResizeWeight(0.48);
        columns.setDividerSize(0);
        columns.setBorder(null);
        columns.setContinuousLayout(true);
        columns.setOneTouchExpandable(false);
        columns.setMinimumSize(new Dimension(0, 0));
        columns.addComponentListener(new ComponentAdapter() {
            private boolean dividerInitialized;
            @Override public void componentResized(ComponentEvent event) {
                if (!dividerInitialized && columns.getWidth() > 0) {
                    columns.setDividerLocation(0.48);
                    dividerInitialized = true;
                }
            }
        });
        root.add(columns, BorderLayout.CENTER);
        return root;
    }

    private static JComponent createBrandMark() {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(38, 38));
                setMinimumSize(new Dimension(38, 38));
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                UITheme.applyQualityHints(g);
                g.setColor(UITheme.TEAL_LIGHT);
                g.fillRoundRect(0, 0, 38, 38, 10, 10);
                g.setColor(new Color(185, 235, 230));
                g.drawRoundRect(0, 0, 37, 37, 10, 10);
                g.setColor(UITheme.TEAL);
                int cx = 19, cy = 19;
                g.fillRoundRect(cx - 3, cy - 10, 6, 20, 3, 3);
                g.fillRoundRect(cx - 10, cy - 3, 20, 6, 3, 3);
                g.dispose();
            }
        };
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(BODY);
        label.setForeground(MUTED);
        return label;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(NAVY);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static void styleField(JTextField field) {
        field.setFont(BODY);
        field.setForeground(NAVY);
        field.setCaretColor(BLUE);
        field.setBorder(BorderFactory.createEmptyBorder(12, 1, 12, 1));
        field.setOpaque(false);
        field.setPreferredSize(new Dimension(250, 46));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JPanel fieldRow(JTextField field, FieldIcon.Type iconType) {
        JPanel row = new JPanel(new BorderLayout(11, 0));
        row.setBackground(Color.WHITE);
        row.add(new FieldIcon(iconType), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        styleFieldRow(row, field);
        return row;
    }

    private static void styleFieldRow(JPanel row, JTextField field) {
        SoftBorder border = new SoftBorder();
        row.setBorder(BorderFactory.createCompoundBorder(border, new EmptyBorder(0, 13, 0, 13)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));
        row.setMinimumSize(new Dimension(160, 49));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                border.setFocused(true);
                row.repaint();
            }
            @Override public void focusLost(FocusEvent e) {
                border.setFocused(false);
                row.repaint();
            }
        });
    }

    private static JButton linkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(BLUE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static final class PlaceholderTextField extends JTextField {
        private final String hint;
        PlaceholderTextField(String hint, int columns) { super(columns); this.hint = hint; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getDocument().getLength() == 0) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());
                g.setColor(new Color(151, 162, 181));
                Insets insets = getInsets();
                g.drawString(hint, insets.left, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    }

    private static final class PlaceholderPasswordField extends JPasswordField {
        private final String hint;
        PlaceholderPasswordField(String hint, int columns) { super(columns); this.hint = hint; }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getDocument().getLength() == 0) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());
                g.setColor(new Color(151, 162, 181));
                Insets insets = getInsets();
                g.drawString(hint, insets.left, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                g.dispose();
            }
        }
    }

    private static final class FieldIcon extends JComponent implements Icon {
        enum Type { EMAIL, LOCK }
        private final Type type;
        FieldIcon(Type type) {
            this.type = type;
            setPreferredSize(new Dimension(19, 19));
            setMinimumSize(new Dimension(19, 19));
        }
        @Override public int getIconWidth() { return 19; }
        @Override public int getIconHeight() { return 19; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(112, 132, 166));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (type == Type.EMAIL) {
                g.drawRoundRect(x + 2, y + 4, 15, 11, 3, 3);
                g.drawLine(x + 3, y + 5, x + 9, y + 10);
                g.drawLine(x + 16, y + 5, x + 10, y + 10);
            } else {
                g.drawRoundRect(x + 3, y + 8, 13, 9, 2, 2);
                g.drawArc(x + 5, y + 2, 9, 12, 0, 180);
                g.fillOval(x + 8, y + 11, 3, 3);
                g.drawLine(x + 9, y + 13, x + 9, y + 15);
            }
            g.dispose();
        }
        @Override protected void paintComponent(Graphics graphics) {
            paintIcon(this, graphics, 0, 0);
        }
    }

    private static final class SoftBorder extends AbstractBorder {
        private boolean focused;
        void setFocused(boolean focused) { this.focused = focused; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            if (focused) {
                g.setColor(new Color(22, 166, 161, 45));
                g.setStroke(new BasicStroke(3.0f));
                g.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, width - 3f, height - 3f, 10, 10));
                g.setColor(UITheme.TEAL);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1f, height - 1f, 10, 10));
            } else {
                g.setColor(UITheme.BORDER);
                g.setStroke(new BasicStroke(1.0f));
                g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1f, height - 1f, 10, 10));
            }
            g.dispose();
        }
    }

    private void authenticate() {
        String address = email.getText().trim();
        char[] secret = password.getPassword();
        signIn.setEnabled(false);
        status.setForeground(MUTED);
        status.setText("Connecting securely…");
        new SwingWorker<DatabaseModule.User, Void>() {
            @Override protected DatabaseModule.User doInBackground() throws Exception {
                DatabaseModule.initialize();
                return DatabaseModule.authenticate(address, secret);
            }
            @Override protected void done() {
                signIn.setEnabled(true);
                java.util.Arrays.fill(secret, '\0');
                try {
                    DatabaseModule.User user = get();
                    if (remember.isSelected()) preferences.put("rememberedEmail", address);
                    else preferences.remove("rememberedEmail");
                    if (user.mustChangePassword() && !promptPasswordChange(user)) return;
                    dispose();
                    if ("ADMIN".equals(user.role())) new AdminModule(user).setVisible(true);
                    else new PatientModule(user).setVisible(true);
                } catch (Exception ex) {
                    logDatabaseFailure(ex);
                    status.setForeground(new Color(190, 55, 65));
                    status.setText(rootMessage(ex));
                }
            }
        }.execute();
    }

    private boolean promptPasswordChange(DatabaseModule.User user) {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 8));
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));
        JPasswordField oldPassword = new JPasswordField(22);
        JPasswordField newPassword = new JPasswordField(22);
        JPasswordField confirm = new JPasswordField(22);
        UITheme.styleInputField(oldPassword);
        UITheme.styleInputField(newPassword);
        UITheme.styleInputField(confirm);
        JLabel note = new JLabel("A password change is required before continuing.");
        note.setFont(new Font("Segoe UI", Font.BOLD, 12));
        note.setForeground(UITheme.TEAL);
        panel.add(note);
        panel.add(dialogLabel("Temporary password"));
        panel.add(oldPassword);
        panel.add(dialogLabel("New password (10-72 characters)"));
        panel.add(newPassword);
        panel.add(dialogLabel("Confirm new password"));
        panel.add(confirm);
        if (JOptionPane.showConfirmDialog(this, panel, "Secure your administrator account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
        char[] fresh = newPassword.getPassword();
        char[] repeated = confirm.getPassword();
        try {
            if (!java.util.Arrays.equals(fresh, repeated)) throw new IllegalArgumentException("The new passwords do not match.");
            DatabaseModule.changePassword(user, oldPassword.getPassword(), fresh);
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, rootMessage(e), "Password not changed", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            java.util.Arrays.fill(fresh, '\0');
            java.util.Arrays.fill(repeated, '\0');
        }
    }

    private void showRegistration() {
        JTextField name = new JTextField(22);
        JTextField address = new JTextField(22);
        JTextField phone = new JTextField(22);
        JPasswordField pass = new JPasswordField(22);
        JPasswordField confirm = new JPasswordField(22);
        UITheme.styleInputField(name);
        UITheme.styleInputField(address);
        UITheme.styleInputField(phone);
        UITheme.styleInputField(pass);
        UITheme.styleInputField(confirm);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 8));
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));
        panel.add(dialogLabel("Full name")); panel.add(name);
        panel.add(dialogLabel("Email address")); panel.add(address);
        panel.add(dialogLabel("Phone number")); panel.add(phone);
        panel.add(dialogLabel("Password (10-72 characters)")); panel.add(pass);
        panel.add(dialogLabel("Confirm password")); panel.add(confirm);
        if (JOptionPane.showConfirmDialog(this, panel, "Create your patient account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] first = pass.getPassword();
        char[] second = confirm.getPassword();
        try {
            if (!java.util.Arrays.equals(first, second)) throw new IllegalArgumentException("The passwords do not match.");
            DatabaseModule.registerPatient(name.getText(), address.getText(), phone.getText(), first);
            JOptionPane.showMessageDialog(this, "Your patient account is ready. You can sign in now.",
                    "Account created", JOptionPane.INFORMATION_MESSAGE);
            email.setText(address.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, rootMessage(e), "Unable to create account", JOptionPane.ERROR_MESSAGE);
        } finally {
            java.util.Arrays.fill(first, '\0');
            java.util.Arrays.fill(second, '\0');
        }
    }

    private static JLabel dialogLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(UITheme.NAVY);
        return label;
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        java.sql.SQLException sqlError = null;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sqlException) sqlError = sqlException;
            if (cause.getCause() == null) break;
            cause = cause.getCause();
        }
        if (sqlError != null) {
            String detail = safeDatabaseDetail(sqlError.getMessage());
            if (detail != null && detail.contains("Database configuration is missing")) return detail;
            if (detail != null && (detail.contains("Communications link failure") || detail.contains("Connection refused")))
                return "Cannot reach MySQL. Start the MySQL service and verify DB_URL, DB_USER, and DB_PASSWORD.";
            if (detail != null && (detail.contains("doesn't exist") || detail.contains("does not exist")
                    || detail.contains("Unknown database")))
                return "The CarePoint database or required tables are missing. Run database\\schema.sql first. Details: " + detail;
            if ("42S02".equals(sqlError.getSQLState()) || "42S22".equals(sqlError.getSQLState())
                    || (detail != null && detail.contains("appointment statuses are not up to date")))
                return "CarePoint schema check failed. Verify database/schema.sql and, for an existing installation, "
                        + "database/migrate-appointment-statuses.sql. " + sqlDiagnostic(sqlError);
            if (detail != null && detail.contains("Access denied"))
                return "MySQL rejected the configured account. Verify DB_USER and DB_PASSWORD and the carepoint database grants.";
            return "Database request failed. " + sqlDiagnostic(sqlError);
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? "CarePoint could not complete that request. Please try again." : message;
    }

    private static void logDatabaseFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.sql.SQLException sqlError) {
                System.err.println("CarePoint JDBC failure: " + sqlDiagnostic(sqlError));
                System.err.println("CarePoint database configuration: " + DatabaseConnection.configurationStatus());
                return;
            }
        }
    }

    private static String sqlDiagnostic(java.sql.SQLException error) {
        String detail = safeDatabaseDetail(error.getMessage());
        return error.getClass().getName()
                + " [SQLState=" + error.getSQLState()
                + ", vendorCode=" + error.getErrorCode() + "]"
                + (detail == null || detail.isBlank() ? "" : ": " + detail);
    }

    private static String safeDatabaseDetail(String detail) {
        return DatabaseConnection.redactDatabaseValues(detail);
    }

    static JButton button(String text) {
        JButton button = new JButton(text);
        stylePremiumButton(button);
        return button;
    }

    static void stylePremiumButton(JButton button) {
        UITheme.stylePrimaryButton(button);
    }

    static void styleTable(JTable table) {
        UITheme.styleTable(table);
    }

    private static final class WelcomePanel extends JPanel {
        WelcomePanel() {
            setBackground(NAVY);
            setLayout(new BorderLayout(0, 16));
            setBorder(new EmptyBorder(48, 54, 38, 54));
            JLabel eyebrow = new JLabel("YOUR HEALTH, IN GOOD HANDS");
            eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 12));
            eyebrow.setForeground(new Color(115, 226, 210));
            JLabel headline = new JLabel("<html>Healthcare,<br>made simpler.</html>");
            headline.setFont(new Font("Segoe UI", Font.BOLD, 40));
            headline.setForeground(Color.WHITE);
            JLabel sub = new JLabel("<html>Thoughtful care, trusted doctors, and simple booking —<br>all in one place.</html>");
            sub.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            sub.setForeground(new Color(210, 220, 240));
            JPanel copy = new JPanel();
            copy.setOpaque(false);
            copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
            copy.add(eyebrow); copy.add(Box.createVerticalStrut(16)); copy.add(headline);
            copy.add(Box.createVerticalStrut(10)); copy.add(sub);
            add(copy, BorderLayout.NORTH);
            add(new Illustration(), BorderLayout.CENTER);
            JPanel cards = new JPanel(new GridLayout(1, 3, 10, 0));
            cards.setOpaque(false);
            String[] features = {"Trusted doctors", "Easy booking", "Private & secure"};
            String[] captions = {"Care you can trust", "On your schedule", "Your data protected"};
            String[] icons = {"✚", "⏱", "🔒"};
            for (int i = 0; i < features.length; i++) {
                JPanel card = new JPanel();
                card.setOpaque(true);
                card.setBackground(new Color(26, 48, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(42, 70, 110)),
                        new EmptyBorder(12, 12, 12, 12)));
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                JLabel icon = new JLabel(icons[i]);
                icon.setForeground(new Color(115, 226, 210));
                icon.setFont(new Font("Segoe UI", Font.BOLD, 18));
                JLabel text = new JLabel(features[i]);
                text.setForeground(Color.WHITE);
                text.setFont(new Font("Segoe UI", Font.BOLD, 12));
                JLabel detail = new JLabel(captions[i]);
                detail.setForeground(new Color(172, 188, 217));
                detail.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                card.add(icon); card.add(Box.createVerticalStrut(4)); card.add(text);
                card.add(Box.createVerticalStrut(4)); card.add(detail); cards.add(card);
            }
            add(cards, BorderLayout.SOUTH);
        }
    }

    private static final class Illustration extends JPanel {
        Illustration() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            double scale = Math.min(w / 470.0, h / 310.0);
            if (scale <= 0) {
                g.dispose();
                return;
            }
            g.translate((w - 470 * scale) / 2.0 - 80, (h - 310 * scale) / 2.0);
            g.scale(scale, scale);

            g.setColor(new Color(36, 57, 101));
            g.fillOval(72, 14, 288, 270);
            g.setColor(new Color(39, 78, 105));
            g.fillOval(277, 38, 95, 95);
            g.setColor(new Color(115, 226, 210));
            g.fillOval(330, 67, 13, 13);
            g.fillOval(60, 72, 8, 8);

            // Doctor portrait and white coat.
            g.setColor(new Color(232, 177, 145));
            g.fillRoundRect(197, 99, 41, 56, 16, 16);
            g.setColor(new Color(47, 36, 45));
            g.fillOval(187, 47, 63, 76);
            g.setColor(new Color(232, 177, 145));
            g.fillOval(191, 63, 55, 62);
            g.setColor(new Color(47, 36, 45));
            g.fillArc(187, 48, 65, 54, 0, 180);
            g.fillRoundRect(184, 75, 14, 40, 8, 8);
            g.setColor(new Color(44, 58, 88));
            g.fillOval(205, 88, 4, 4);
            g.fillOval(229, 88, 4, 4);
            g.setColor(new Color(181, 105, 97));
            g.drawArc(211, 97, 15, 9, 205, 130);
            g.setColor(new Color(246, 249, 255));
            int[] coatX = {174, 188, 203, 230, 245, 263, 281, 282, 153, 154};
            int[] coatY = {147, 134, 128, 128, 134, 148, 184, 252, 252, 184};
            g.fillPolygon(coatX, coatY, coatX.length);
            g.setColor(new Color(218, 228, 243));
            g.drawLine(203, 137, 219, 164);
            g.drawLine(245, 137, 221, 164);
            g.setColor(TEAL);
            g.fillRoundRect(215, 138, 8, 38, 4, 4);
            g.setColor(new Color(65, 94, 145));
            g.drawLine(220, 174, 220, 193);
            g.drawArc(210, 187, 21, 21, 0, -180);
            g.fillOval(207, 195, 7, 7);

            // Floating appointment card with a doctor profile and visit confirmation.
            g.setColor(new Color(18, 31, 62, 55));
            g.fillRoundRect(265, 164, 174, 105, 18, 18);
            g.setColor(new Color(255, 255, 255));
            g.fillRoundRect(255, 154, 174, 105, 18, 18);
            g.setColor(new Color(232, 249, 246));
            g.fillOval(270, 172, 38, 38);
            g.setColor(new Color(47, 36, 45));
            g.fillOval(279, 176, 20, 23);
            g.setColor(new Color(232, 177, 145));
            g.fillOval(281, 182, 16, 17);
            g.setColor(NAVY);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.drawString("Your appointment", 316, 184);
            g.setColor(new Color(104, 120, 149));
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.drawString("Dr. Sarah Mitchell", 316, 201);
            g.setColor(new Color(235, 241, 250));
            g.fillRoundRect(270, 220, 143, 1, 1, 1);
            g.setColor(TEAL);
            g.fillOval(273, 233, 8, 8);
            g.setColor(new Color(75, 98, 133));
            g.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g.drawString("Visit confirmed", 287, 241);
            g.setColor(new Color(255, 255, 255, 25));
            g.fillOval(100, 226, 10, 10);
            g.fillOval(353, 29, 6, 6);
            g.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception e) { System.err.println("Using default Swing look and feel: " + e.getMessage()); }
            new LoginModule().setVisible(true);
        });
    }
}
