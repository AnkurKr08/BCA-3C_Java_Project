package com.carepoint;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

public final class PatientModule extends JFrame {
    private final DatabaseModule.User patient;
    private final CardLayout pages = new CardLayout();
    private final JPanel pagePanel = new JPanel(pages);
    private final DefaultTableModel doctorModel = model("ID", "Doctor", "Specialty", "Fee", "Availability");
    private final DefaultTableModel upcomingModel = model("ID", "Doctor", "Specialty", "Date", "Time", "Reason", "Status", "Fee");
    private final DefaultTableModel historyModel = model("ID", "Doctor", "Specialty", "Date", "Time", "Reason", "Status", "Fee");
    private final JTable doctorTable = new JTable(doctorModel);
    private final JTable upcomingTable = new JTable(upcomingModel);
    private final JTable historyTable = new JTable(historyModel);
    private final JComboBox<String> specialtyFilter = new JComboBox<>();
    private final JTextField doctorSearch = new JTextField(18);
    private final JTextField appointmentSearch = new JTextField(18);
    private final JLabel appointmentSummary = new JLabel(" ");

    private final java.util.List<JButton> navButtons = new java.util.ArrayList<>();
    private String currentCard = "dashboard";

    public PatientModule(DatabaseModule.User patient) {
        super("CarePoint | Patient dashboard");
        this.patient = patient;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 700));
        setContentPane(buildUI());
        setSize(1240, 820);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        refresh();
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.BG);
        root.add(header(), BorderLayout.NORTH);
        pagePanel.setBackground(UITheme.BG);
        pagePanel.add(buildDashboard(), "dashboard");
        pagePanel.add(buildDoctors(), "doctors");
        pagePanel.add(buildAppointments(), "appointments");
        root.add(pagePanel, BorderLayout.CENTER);
        return root;
    }

    private JPanel header() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                new EmptyBorder(12, 28, 12, 28)));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.add(createBrandMark());
        JLabel name = new JLabel("CarePoint");
        name.setForeground(UITheme.NAVY);
        name.setFont(new Font("Segoe UI", Font.BOLD, 21));
        brand.add(name);
        bar.add(brand, BorderLayout.WEST);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        nav.setOpaque(false);
        nav.add(navButton("Overview", "dashboard"));
        nav.add(navButton("Find a doctor", "doctors"));
        nav.add(navButton("My appointments", "appointments"));
        bar.add(nav, BorderLayout.CENTER);
        JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        account.setOpaque(false);
        JLabel identity = new JLabel(patient.fullName());
        identity.setFont(UITheme.FONT_BODY_BOLD);
        identity.setForeground(UITheme.NAVY);
        account.add(identity);
        JButton logout = new JButton("Log out");
        UITheme.styleDangerButton(logout);
        logout.addActionListener(e -> logout());
        account.add(logout);
        bar.add(account, BorderLayout.EAST);
        return bar;
    }

    private static JComponent createBrandMark() {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(32, 32));
                setMinimumSize(new Dimension(32, 32));
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                UITheme.applyQualityHints(g);
                g.setColor(UITheme.TEAL_LIGHT);
                g.fillRoundRect(0, 0, 32, 32, 8, 8);
                g.setColor(new Color(185, 235, 230));
                g.drawRoundRect(0, 0, 31, 31, 8, 8);
                g.setColor(UITheme.TEAL);
                int cx = 16, cy = 16;
                g.fillRoundRect(cx - 2, cy - 8, 5, 16, 2, 2);
                g.fillRoundRect(cx - 8, cy - 2, 16, 5, 2, 2);
                g.dispose();
            }
        };
    }

    private JButton navButton(String text, String card) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(new EmptyBorder(8, 14, 8, 14));
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent c) {
                Graphics2D g = (Graphics2D) graphics.create();
                UITheme.applyQualityHints(g);
                JButton btn = (JButton) c;
                boolean active = card.equals(currentCard);
                boolean hovered = btn.getModel().isRollover();
                if (active) {
                    g.setColor(UITheme.TEAL_LIGHT);
                    g.fillRoundRect(0, 0, btn.getWidth(), btn.getHeight(), 8, 8);
                    g.setColor(UITheme.TEAL);
                    g.fillRect(8, btn.getHeight() - 3, btn.getWidth() - 16, 3);
                } else if (hovered) {
                    g.setColor(new Color(245, 247, 250));
                    g.fillRoundRect(0, 0, btn.getWidth(), btn.getHeight(), 8, 8);
                }
                g.dispose();
                super.paint(graphics, c);
            }
        });
        button.addActionListener(e -> {
            currentCard = card;
            pages.show(pagePanel, card);
            for (JButton b : navButtons) b.repaint();
        });
        navButtons.add(button);
        return button;
    }

    private JPanel buildDashboard() {
        JPanel page = basePage();
        JLabel title = new JLabel("Good to see you, " + patient.fullName().split(" ")[0]);
        title.setFont(UITheme.FONT_DISPLAY);
        title.setForeground(UITheme.NAVY);
        page.add(title);
        page.add(Box.createVerticalStrut(6));
        JLabel caption = new JLabel("Your care, appointments, and trusted doctors — all in one place.");
        caption.setFont(UITheme.FONT_BODY);
        caption.setForeground(UITheme.TEXT_MUTED);
        page.add(caption);
        page.add(Box.createVerticalStrut(20));
        JPanel summary = new JPanel(new GridLayout(1, 3, 16, 0));
        summary.setOpaque(false);
        summary.add(summaryCard("Your next step", "Schedule a visit", "Find a doctor and choose a time that works."));
        summary.add(summaryCard("Appointment hours", "9:00 AM – 5:00 PM", "30-minute visits, Monday through Saturday."));
        summary.add(summaryCard("Appointments", appointmentSummary, "Your upcoming visits are ready when you are."));
        page.add(summary);
        page.add(Box.createVerticalStrut(22));
        JPanel action = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        action.setOpaque(false);
        JButton book = new JButton("Book an appointment");
        UITheme.stylePrimaryButton(book);
        book.addActionListener(e -> openBooking(null));
        action.add(book);
        page.add(action);
        return page;
    }

    private JPanel summaryCard(String label, String value, String detail) {
        return summaryCard(label, new JLabel(value), detail);
    }

    private JPanel summaryCard(String label, JLabel value, String detail) {
        JPanel card = whiteCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(label.toUpperCase());
        heading.setFont(UITheme.FONT_TINY);
        heading.setForeground(UITheme.TEAL);
        value.setFont(new Font("Segoe UI", Font.BOLD, 22));
        value.setForeground(UITheme.NAVY);
        JLabel text = new JLabel("<html>" + detail + "</html>");
        text.setFont(UITheme.FONT_BODY);
        text.setForeground(UITheme.TEXT_MUTED);
        card.add(heading); card.add(Box.createVerticalStrut(10)); card.add(value);
        card.add(Box.createVerticalStrut(6)); card.add(text);
        return card;
    }

    private JPanel buildDoctors() {
        JPanel page = basePage();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("Find your doctor");
        title.setFont(UITheme.FONT_DISPLAY);
        title.setForeground(UITheme.NAVY);
        top.add(title, BorderLayout.WEST);
        JButton book = new JButton("Book selected doctor");
        UITheme.stylePrimaryButton(book);
        book.addActionListener(e -> bookSelectedDoctor());
        top.add(book, BorderLayout.EAST);
        page.add(top);
        page.add(Box.createVerticalStrut(16));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filters.setOpaque(false);
        doctorSearch.putClientProperty("JTextField.placeholderText", "Search doctors...");
        UITheme.styleInputField(doctorSearch);
        specialtyFilter.addItem("All specialties");
        UITheme.styleComboBox(specialtyFilter);
        JLabel searchLbl = new JLabel("Search");
        searchLbl.setFont(UITheme.FONT_BODY_BOLD);
        searchLbl.setForeground(UITheme.NAVY);
        filters.add(searchLbl);
        filters.add(doctorSearch);
        JLabel specLbl = new JLabel("Specialty");
        specLbl.setFont(UITheme.FONT_BODY_BOLD);
        specLbl.setForeground(UITheme.NAVY);
        filters.add(specLbl);
        filters.add(specialtyFilter);
        page.add(filters);
        page.add(Box.createVerticalStrut(14));
        style(doctorTable);
        doctorTable.setAutoCreateRowSorter(true);
        doctorTable.getColumnModel().getColumn(0).setMinWidth(0);
        doctorTable.getColumnModel().getColumn(0).setMaxWidth(0);
        JScrollPane scroll = new JScrollPane(doctorTable);
        scroll.setBorder(new UITheme.RoundedLineBorder(UITheme.BORDER, 8, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        page.add(scroll);
        doctorSearch.getDocument().addDocumentListener((SimpleDocumentListener) e -> filterDoctors());
        specialtyFilter.addActionListener(e -> filterDoctors());
        return page;
    }

    private JPanel buildAppointments() {
        JPanel page = basePage();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("My appointments");
        title.setFont(UITheme.FONT_DISPLAY);
        title.setForeground(UITheme.NAVY);
        top.add(title, BorderLayout.WEST);
        JButton refresh = new JButton("Refresh");
        UITheme.styleSecondaryButton(refresh);
        refresh.addActionListener(e -> refresh());
        top.add(refresh, BorderLayout.EAST);
        page.add(top);
        page.add(Box.createVerticalStrut(14));
        appointmentSearch.setToolTipText("Search the selected appointment list");
        appointmentSearch.putClientProperty("JTextField.placeholderText", "Search appointments...");
        UITheme.styleInputField(appointmentSearch);
        page.add(appointmentSearch);
        page.add(Box.createVerticalStrut(12));
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UITheme.FONT_BODY_BOLD);
        tabs.addTab("Upcoming", appointmentPanel(upcomingTable));
        tabs.addTab("History", appointmentPanel(historyTable));
        page.add(tabs);
        page.add(Box.createVerticalStrut(12));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        JButton cancel = new JButton("Cancel selected upcoming appointment");
        UITheme.styleDangerButton(cancel);
        cancel.addActionListener(e -> cancelSelected());
        actions.add(cancel);
        page.add(actions);
        appointmentSearch.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
            filterTable(upcomingTable, appointmentSearch.getText());
            filterTable(historyTable, appointmentSearch.getText());
        });
        return page;
    }

    private JScrollPane appointmentPanel(JTable table) {
        style(table);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        UITheme.installStatusBadgeRenderer(table, 6);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new UITheme.RoundedLineBorder(UITheme.BORDER, 8, 1));
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    private JPanel basePage() {
        JPanel page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBackground(UITheme.BG);
        page.setBorder(new EmptyBorder(26, 32, 26, 32));
        return page;
    }

    private JPanel whiteCard() {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(UITheme.createCardBorder());
        return card;
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }

    private static void style(JTable table) { UITheme.styleTable(table); }

    private void refresh() {
        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() throws Exception {
                return new Object[]{DatabaseModule.doctors(false), DatabaseModule.patientAppointments(patient, false),
                        DatabaseModule.patientAppointments(patient, true), DatabaseModule.specialties()};
            }
            @SuppressWarnings("unchecked")
            @Override protected void done() {
                try {
                    Object[] result = get();
                    List<DatabaseModule.Doctor> doctors = (List<DatabaseModule.Doctor>) result[0];
                    List<DatabaseModule.Appointment> upcoming = (List<DatabaseModule.Appointment>) result[1];
                    List<DatabaseModule.Appointment> history = (List<DatabaseModule.Appointment>) result[2];
                    List<String> specialties = (List<String>) result[3];
                    doctorModel.setRowCount(0);
                    for (DatabaseModule.Doctor d : doctors)
                        doctorModel.addRow(new Object[]{d.id(), d.fullName(), d.specialty(), d.fee(), d.availability()});
                    upcomingModel.setRowCount(0);
                    for (DatabaseModule.Appointment a : upcoming) addAppointment(upcomingModel, a);
                    historyModel.setRowCount(0);
                    for (DatabaseModule.Appointment a : history) addAppointment(historyModel, a);
                    String selected = (String) specialtyFilter.getSelectedItem();
                    specialtyFilter.removeAllItems();
                    specialtyFilter.addItem("All specialties");
                    for (String specialty : specialties) specialtyFilter.addItem(specialty);
                    if (selected != null) specialtyFilter.setSelectedItem(selected);
                    appointmentSummary.setText(String.valueOf(upcoming.size()));
                    filterDoctors();
                } catch (Exception e) { showError(e); }
            }
        }.execute();
    }

    private static void addAppointment(DefaultTableModel model, DatabaseModule.Appointment a) {
        model.addRow(new Object[]{a.id(), a.doctorName(), a.specialty(), a.date(), a.time(), a.reason(), a.status(), a.fee()});
    }

    private void filterDoctors() {
        if (doctorTable.getRowSorter() instanceof TableRowSorter<?> sorter) {
            String specialty = (String) specialtyFilter.getSelectedItem();
            java.util.List<RowFilter<Object, Object>> filters = new java.util.ArrayList<>();
            String query = doctorSearch.getText().trim();
            if (!query.isEmpty()) filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query)));
            if (specialty != null && !"All specialties".equals(specialty))
                filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(specialty), 2));
            sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        }
    }

    private static void filterTable(JTable table, String query) {
        if (table.getRowSorter() instanceof TableRowSorter<?> sorter) {
            if (query == null || query.isBlank()) sorter.setRowFilter(null);
            else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query)));
        }
    }

    private void bookSelectedDoctor() {
        int row = doctorTable.getSelectedRow();
        if (row < 0) { openBooking(null); return; }
        long id = ((Number) doctorModel.getValueAt(doctorTable.convertRowIndexToModel(row), 0)).longValue();
        openBooking(id);
    }

    private void openBooking(Long doctorId) {
        new AppointmentModule(this, patient, doctorId).setVisible(true);
        refresh();
    }

    private void cancelSelected() {
        int row = upcomingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an upcoming appointment first.", "No appointment selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        long id = ((Number) upcomingModel.getValueAt(upcomingTable.convertRowIndexToModel(row), 0)).longValue();
        if (JOptionPane.showConfirmDialog(this, "Cancel this appointment? Its time will become available again.",
                "Confirm cancellation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { DatabaseModule.cancel(patient, id); return null; }
            @Override protected void done() {
                try { get(); JOptionPane.showMessageDialog(PatientModule.this, "Appointment canceled.", "Updated", JOptionPane.INFORMATION_MESSAGE); refresh(); }
                catch (Exception e) { showError(e); }
            }
        }.execute();
    }

    private void logout() {
        dispose();
        new LoginModule().setVisible(true);
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(this, LoginModule.rootMessage(error), "CarePoint", JOptionPane.ERROR_MESSAGE);
    }

    @FunctionalInterface
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update(javax.swing.event.DocumentEvent event);
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { update(e); }
    }
}
