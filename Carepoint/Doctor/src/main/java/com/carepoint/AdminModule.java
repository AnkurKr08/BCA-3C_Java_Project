package com.carepoint;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public final class AdminModule extends JFrame {
    private static final Color NAVY = UITheme.NAVY;
    private static final Color BLUE = UITheme.TEAL;
    private static final Color TEAL = UITheme.TEAL;
    private static final Color BORDER = UITheme.BORDER;
    private static final Color CANVAS = UITheme.BG;
    private static final Color INK = UITheme.TEXT_MAIN;
    private static final Color MUTED = UITheme.TEXT_MUTED;
    private static final Color[] CHART_COLORS = {
            UITheme.TEAL, new Color(47, 105, 221), new Color(130, 86, 220),
            UITheme.WARNING, UITheme.DANGER, new Color(55, 133, 178)
    };
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MMM d, h:mm a");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");

    private final DatabaseModule.User admin;
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JPanel navigation = new JPanel();
    private final JTextField globalSearch = new JTextField(20);
    private final JLabel pageTitle = new JLabel("Dashboard");
    private final JLabel connectionStatus = new JLabel(" ");
    private final JButton refreshButton = new JButton("Refresh");
    private final JComboBox<String> appointmentStatusFilter = new JComboBox<>(
            new String[]{"All statuses", "PENDING", "CONFIRMED", "COMPLETED", "CANCELLED"});
    private final JTextField appointmentDateFilter = new JTextField(10);
    private final JComboBox<String> patientAccountFilter = new JComboBox<>(
            new String[]{"All accounts", "Active", "Inactive"});
    private final JComboBox<String> doctorSpecialtyFilter = new JComboBox<>(new String[]{"All specialties"});
    private final JComboBox<String> doctorActiveFilter = new JComboBox<>(
            new String[]{"All doctors", "Active", "Inactive"});
    private JButton notificationButton;

    private final DefaultTableModel patientModel = model("ID", "Patient", "Email", "Phone", "Registered", "Account");
    private final DefaultTableModel doctorModel = model("ID", "Doctor", "Department", "Fee", "Availability", "Status");
    private final DefaultTableModel appointmentModel = model("ID", "Patient", "Doctor", "Department", "Date", "Time", "Reason", "Status");
    private final DefaultTableModel medicalModel = model("ID", "Patient", "Doctor", "Subject", "Created", "Updated", "Details");
    private final JTable patientTable = new PlaceholderTable(patientModel, "No patients registered yet");
    private final JTable doctorTable = new PlaceholderTable(doctorModel, "No doctors in the directory yet");
    private final JTable appointmentTable = new PlaceholderTable(appointmentModel, "No appointments scheduled yet");
    private final JTable medicalTable = new PlaceholderTable(medicalModel, "No medical records available");
    private JTable activeTable;
    private JButton activeNavigation;

    private DatabaseModule.Stats stats;
    private List<DatabaseModule.Appointment> appointments = List.of();
    private List<DatabaseModule.User> patients = List.of();
    private List<DatabaseModule.Doctor> doctors = List.of();
    private List<DatabaseModule.MonthSummary> monthSummaries = List.of();
    private List<DatabaseModule.SpecialtyCount> specialtyCounts = List.of();
    private List<DatabaseModule.Activity> activity = List.of();
    private List<DatabaseModule.MedicalRecord> records = List.of();
    private StatCard patientStat;
    private StatCard doctorStat;
    private StatCard totalAppointmentStat;
    private StatCard pendingStat;
    private StatCard todayStat;
    private StatCard confirmedStat;
    private StatCard completedStat;
    private StatCard cancelledStat;
    private StatCard upcomingStat;
    private OverviewChart appointmentChart;
    private OverviewChart specialtyChart;
    private OverviewChart reportAppointmentChart;
    private OverviewChart reportSpecialtyChart;
    private OverviewChart reportDoctorChart;
    private JPanel upcomingList;
    private JPanel activityList;
    private JPanel reportSummary;
    private JTextField reportFrom;
    private JTextField reportTo;
    private JLabel reportFilterStatus;

    public AdminModule(DatabaseModule.User admin) {
        super("CarePoint | Healthcare");
        if (admin == null || !"ADMIN".equals(admin.role()))
            throw new SecurityException("Administrator access is required.");
        this.admin = admin;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setContentPane(buildWindow());
        setSize(1440, 900);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        refresh();
    }

    private JPanel buildWindow() {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(CANVAS);
        shell.add(buildSidebar(), BorderLayout.WEST);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(CANVAS);
        workspace.add(buildHeader(), BorderLayout.NORTH);
        pages.setBackground(CANVAS);
        pages.add(buildDashboardPage(), "Dashboard");
        pages.add(scrollPage(buildPatientPage()), "Patients");
        pages.add(scrollPage(buildDoctorPage()), "Doctors");
        pages.add(scrollPage(buildAppointmentPage()), "Appointments");
        pages.add(scrollPage(buildMedicalPage()), "Medical Records");
        pages.add(scrollPage(buildReportsPage()), "Reports");
        pages.add(scrollPage(buildSettingsPage()), "Settings");
        workspace.add(pages, BorderLayout.CENTER);
        shell.add(workspace, BorderLayout.CENTER);
        return shell;
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
                g.setColor(UITheme.TEAL);
                g.fillRoundRect(0, 0, 38, 38, 10, 10);
                g.setColor(Color.WHITE);
                int cx = 19, cy = 19;
                g.fillRoundRect(cx - 3, cy - 10, 6, 20, 3, 3);
                g.fillRoundRect(cx - 10, cy - 3, 20, 6, 3, 3);
                g.dispose();
            }
        };
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(NAVY);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setMinimumSize(new Dimension(220, 0));
        sidebar.setBorder(new EmptyBorder(22, 15, 18, 15));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent mark = createBrandMark();
        JLabel name = new JLabel("CarePoint");
        name.setForeground(Color.WHITE);
        name.setFont(new Font("Segoe UI", Font.BOLD, 22));
        brand.add(mark);
        brand.add(name);
        top.add(brand);
        JLabel sub = new JLabel("HEALTHCARE");
        sub.setForeground(UITheme.TEAL);
        sub.setFont(new Font("Segoe UI", Font.BOLD, 10));
        sub.setBorder(new EmptyBorder(13, 8, 22, 0));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(sub);

        navigation.setOpaque(false);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(navigation);
        sidebar.add(top, BorderLayout.NORTH);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton logout = navButton("Logout", null);
        logout.setForeground(Color.WHITE);
        logout.setBackground(new Color(175, 52, 68));
        logout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(193, 75, 86)),
                new EmptyBorder(10, 11, 10, 8)));
        logout.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { logout.setBackground(new Color(195, 60, 75)); }
            @Override public void mouseExited(MouseEvent e) { logout.setBackground(new Color(175, 52, 68)); }
        });
        footer.add(logout);
        JLabel version = new JLabel("<html>CAREPOINT<br>Healthcare system · v1.0</html>");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        version.setForeground(new Color(148, 168, 204));
        version.setBorder(new EmptyBorder(18, 9, 4, 0));
        footer.add(version);
        sidebar.add(footer, BorderLayout.SOUTH);

        String[] items = {"Dashboard", "Patients", "Doctors", "Appointments", "Medical Records", "Reports", "Settings"};
        for (int i = 0; i < items.length; i++) {
            JButton button = navButton(items[i], items[i]);
            navigation.add(button);
            navigation.add(Box.createVerticalStrut(5));
            if (i == 0) selectNavigation(button);
        }
        return sidebar;
    }

    private JButton navButton(String title, String page) {
        JButton button = new JButton(title);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent c) {
                Graphics2D g = (Graphics2D) graphics.create();
                UITheme.applyQualityHints(g);
                JButton btn = (JButton) c;
                int w = btn.getWidth();
                int h = btn.getHeight();
                boolean isActive = (btn == activeNavigation);
                boolean isHovered = btn.getModel().isRollover();
                if (isActive) {
                    g.setColor(new Color(28, 54, 90));
                    g.fillRoundRect(0, 0, w, h, 8, 8);
                    g.setColor(UITheme.TEAL);
                    g.fillRoundRect(0, 5, 4, h - 10, 2, 2);
                } else if (isHovered) {
                    g.setColor(new Color(25, 48, 80));
                    g.fillRoundRect(0, 0, w, h, 8, 8);
                }
                g.dispose();
                super.paint(graphics, c);
            }
        });
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(new Color(226, 234, 248));
        button.setIcon(new NavigationIcon(button));
        button.setIconTextGap(14);
        button.setBorder(new EmptyBorder(10, 14, 10, 10));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> {
            if (page == null) {
                logout();
                return;
            }
            navigate(page);
        });
        return button;
    }

    private void navigate(String page) {
        pageLayout.show(pages, page);
        pageTitle.setText(page);
        switch (page) {
            case "Patients" -> setActiveTable(patientTable);
            case "Doctors" -> setActiveTable(doctorTable);
            case "Appointments" -> setActiveTable(appointmentTable);
            case "Medical Records" -> setActiveTable(medicalTable);
            default -> setActiveTable(null);
        }
        for (Component component : navigation.getComponents()) {
            if (component instanceof JButton button && button.getText().trim().endsWith(page)) {
                selectNavigation(button);
                break;
            }
        }
        if (page.equals("Reports")) refreshReports();
    }

    private void selectNavigation(JButton button) {
        if (activeNavigation != null) {
            activeNavigation.repaint();
        }
        activeNavigation = button;
        activeNavigation.repaint();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                new EmptyBorder(14, 27, 14, 27)));
        header.setPreferredSize(new Dimension(0, 76));

        JPanel leading = new JPanel();
        leading.setOpaque(false);
        leading.setLayout(new BoxLayout(leading, BoxLayout.Y_AXIS));
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 21));
        pageTitle.setForeground(INK);
        JLabel subtitle = new JLabel("Healthcare operations at a glance");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitle.setForeground(MUTED);
        connectionStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        connectionStatus.setForeground(MUTED);
        leading.add(pageTitle);
        leading.add(Box.createVerticalStrut(3));
        leading.add(subtitle);
        leading.add(connectionStatus);
        header.add(leading, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 3));
        tools.setOpaque(false);
        JLabel topBrand = new JLabel("CarePoint");
        topBrand.setFont(new Font("Segoe UI", Font.BOLD, 15));
        topBrand.setForeground(BLUE);
        tools.add(topBrand);
        globalSearch.setToolTipText("Search the current list; press Enter to search patients, doctors, and appointments");
        globalSearch.putClientProperty("JTextField.placeholderText", "Search records...");
        UITheme.styleInputField(globalSearch);
        globalSearch.setPreferredSize(new Dimension(180, 36));
        globalSearch.getDocument().addDocumentListener((SimpleDocumentListener) e -> applySearch());
        globalSearch.addActionListener(e -> searchAcrossRecords());
        tools.add(globalSearch);
        refreshButton.setText("Refresh");
        refreshButton.setIcon(new RefreshIcon());
        refreshButton.setIconTextGap(8);
        styleRefreshButton(refreshButton);
        refreshButton.addActionListener(e -> refresh());
        tools.add(refreshButton);
        notificationButton = new JButton("Today · 0");
        LoginModule.stylePremiumButton(notificationButton);
        notificationButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        notificationButton.setToolTipText("Today's appointments");
        notificationButton.addActionListener(e -> showTodayAppointments());
        tools.add(notificationButton);
        JButton profile = new JButton("<html><b>" + html(admin.fullName())
                + "</b><br><font color='#e4edff'>Administrator</font></html>");
        profile.setHorizontalAlignment(SwingConstants.LEFT);
        LoginModule.stylePremiumButton(profile);
        profile.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        profile.setBorder(new EmptyBorder(5, 10, 5, 10));
        profile.addActionListener(e -> showSettingsPassword());
        tools.add(profile);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    private JPanel buildDashboardPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(CANVAS);
        page.setBorder(new EmptyBorder(21, 25, 25, 25));
        JPanel content = new JPanel();
        content.setBackground(CANVAS);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildWelcomeBanner());
        content.add(Box.createVerticalStrut(17));
        JPanel cards = new JPanel(new GridLayout(3, 3, 13, 12));
        cards.setOpaque(false);
        patientStat = new StatCard("TOTAL PATIENTS", "—", "Registered patient accounts", "●",
                new Color(33, 90, 170), new Color(45, 115, 210));
        doctorStat = new StatCard("TOTAL DOCTORS", "—", "Active care professionals", "✚",
                new Color(18, 148, 143), new Color(22, 166, 161));
        totalAppointmentStat = new StatCard("TOTAL APPOINTMENTS", "—", "All recorded visits", "■",
                new Color(20, 43, 74), new Color(38, 70, 115));
        pendingStat = new StatCard("PENDING", "—", "Awaiting confirmation", "⏱",
                new Color(217, 119, 6), new Color(245, 158, 11));
        confirmedStat = new StatCard("CONFIRMED", "—", "Confirmed visits", "✓",
                new Color(22, 138, 75), new Color(33, 165, 92));
        completedStat = new StatCard("COMPLETED", "—", "Visits completed", "✔",
                new Color(29, 111, 158), new Color(42, 148, 204));
        cancelledStat = new StatCard("CANCELLED", "—", "Cancelled visits", "✕",
                new Color(190, 55, 65), new Color(220, 75, 88));
        todayStat = new StatCard("TODAY'S APPOINTMENTS", "—", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")), "⏱",
                new Color(205, 115, 30), new Color(235, 145, 55));
        upcomingStat = new StatCard("UPCOMING", "—", "Pending or confirmed future visits", "➔",
                new Color(24, 120, 155), new Color(35, 150, 185));
        cards.add(patientStat);
        cards.add(doctorStat);
        cards.add(totalAppointmentStat);
        cards.add(pendingStat);
        cards.add(confirmedStat);
        cards.add(completedStat);
        cards.add(cancelledStat);
        cards.add(todayStat);
        cards.add(upcomingStat);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));
        content.add(cards);
        content.add(Box.createVerticalStrut(17));
        content.add(buildDashboardLower());
        content.add(Box.createVerticalStrut(17));
        content.add(buildQuickActions());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(CANVAS);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildWelcomeBanner() {
        JPanel banner = new JPanel(new BorderLayout(20, 0)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                UITheme.applyQualityHints(g);
                g.setPaint(new GradientPaint(0, 0, UITheme.NAVY, getWidth(), getHeight(), new Color(24, 72, 125)));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g.setColor(new Color(255, 255, 255, 22));
                g.fillOval(getWidth() - 220, -70, 240, 220);
                g.fillOval(getWidth() - 145, 70, 115, 115);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(20, 25, 20, 25));
        banner.setPreferredSize(new Dimension(0, 143));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("YOUR HEALTHCARE OVERVIEW");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 10));
        eyebrow.setForeground(new Color(115, 226, 210));
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String adminFirst = admin.fullName() != null && !admin.fullName().isBlank()
                ? admin.fullName().split(" ")[0] : "Admin";
        JLabel title = new JLabel(greeting + ", " + adminFirst + "!");
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setForeground(Color.WHITE);
        JLabel desc = new JLabel("Welcome back to CarePoint. Here's what's happening today.");
        desc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        desc.setForeground(new Color(226, 237, 255));
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(8));
        copy.add(title);
        copy.add(Box.createVerticalStrut(5));
        copy.add(desc);
        banner.add(copy, BorderLayout.WEST);

        JPanel illustration = new HealthcareIllustration();
        illustration.setOpaque(false);
        illustration.setPreferredSize(new Dimension(230, 100));
        banner.add(illustration, BorderLayout.EAST);
        return banner;
    }

    private JPanel buildDashboardLower() {
        JPanel row = new JPanel(new BorderLayout(15, 0));
        row.setOpaque(false);
        JPanel charts = new JPanel(new GridLayout(1, 2, 13, 0));
        charts.setOpaque(false);
        appointmentChart = new OverviewChart("Appointments by month and status", OverviewChart.Kind.BARS);
        specialtyChart = new OverviewChart("Patients by specialty", OverviewChart.Kind.DONUT);
        specialtyChart.setToolTipText("Distinct active patients with appointments, grouped by the booked doctor's specialty. "
                + "Patients may appear under more than one specialty.");
        charts.add(appointmentChart);
        charts.add(specialtyChart);
        row.add(charts, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setPreferredSize(new Dimension(245, 0));
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        upcomingList = new JPanel();
        activityList = new JPanel();
        right.add(listCard("Upcoming Appointments", "View all", this::showAppointments, upcomingList));
        right.add(Box.createVerticalStrut(13));
        right.add(listCard("Recent Activity", "Refresh", this::refresh, activityList));
        row.add(right, BorderLayout.EAST);
        row.setPreferredSize(new Dimension(0, 405));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 415));
        return row;
    }

    private JPanel listCard(String title, String action, Runnable onAction, JPanel list) {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 15, 14, 15));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(INK);
        JButton link = new JButton(action);
        LoginModule.stylePremiumButton(link);
        link.setFont(new Font("Segoe UI", Font.BOLD, 10));
        link.setBorder(new EmptyBorder(4, 8, 4, 8));
        link.addActionListener(e -> onAction.run());
        heading.add(label, BorderLayout.WEST);
        heading.add(link, BorderLayout.EAST);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        card.add(heading, BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        int cardHeight = "Upcoming Appointments".equals(title) ? 230 : 165;
        card.setPreferredSize(new Dimension(245, cardHeight));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardHeight + 5));
        return card;
    }

    private JPanel buildQuickActions() {
        JPanel section = new JPanel(new BorderLayout(0, 11));
        section.setOpaque(false);
        JLabel title = new JLabel("Quick Actions");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(INK);
        section.add(title, BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(2, 4, 12, 10));
        cards.setOpaque(false);
        cards.add(quickAction("＋", "Add Patient", "Register a patient account", new Color(237, 246, 255), () -> addPatient()));
        cards.add(quickAction("✚", "Add Doctor", "Add a care professional", new Color(232, 250, 246), () -> editDoctor(null)));
        cards.add(quickAction("▣", "Book Appointment", "Schedule a patient visit", new Color(245, 239, 255), () -> bookAppointment()));
        cards.add(quickAction("♙", "View Patients", "Open the patient directory", new Color(237, 246, 255), () -> navigate("Patients")));
        cards.add(quickAction("▦", "View Appointments", "Open the appointment schedule", new Color(240, 244, 255), () -> navigate("Appointments")));
        cards.add(quickAction("▤", "Add Medical Record", "Create a restricted clinical note", new Color(232, 250, 246), () -> editMedicalRecord(null)));
        cards.add(quickAction("▥", "View Reports", "Review clinic summaries", new Color(255, 245, 233), () -> navigate("Reports")));
        cards.add(quickAction("↻", "Refresh Dashboard", "Reload current database data", new Color(255, 245, 233), this::refresh));
        cards.setPreferredSize(new Dimension(0, 180));
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 370));
        cards.addComponentListener(new ComponentAdapter() {
            private boolean compact;
            @Override public void componentResized(ComponentEvent event) {
                boolean useCompactLayout = cards.getWidth() < 850;
                if (useCompactLayout == compact) return;
                compact = useCompactLayout;
                cards.setLayout(new GridLayout(compact ? 4 : 2, compact ? 2 : 4, 12, 10));
                cards.setPreferredSize(new Dimension(0, compact ? 360 : 180));
                cards.revalidate();
                if (cards.getParent() != null) cards.getParent().revalidate();
            }
        });
        section.add(cards, BorderLayout.CENTER);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 405));
        return section;
    }

    private JButton quickAction(String icon, String title, String detail, Color tint, Runnable action) {
        JButton card = new JButton();
        card.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        card.setLayout(new BorderLayout(12, 0));
        card.setBackground(Color.WHITE);
        card.setOpaque(true);
        card.setContentAreaFilled(true);
        Border normalBorder = BorderFactory.createCompoundBorder(
                new UITheme.RoundedLineBorder(BORDER, 1, 10),
                new EmptyBorder(12, 14, 12, 14));
        Border hoverBorder = BorderFactory.createCompoundBorder(
                new UITheme.RoundedLineBorder(TEAL, 1, 10),
                new EmptyBorder(12, 14, 12, 14));
        card.setBorder(normalBorder);
        card.setFocusPainted(false);
        card.addActionListener(e -> action.run());
        JLabel symbol = new JLabel(icon) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                UITheme.applyQualityHints(g2);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        symbol.setOpaque(false);
        symbol.setBackground(tint);
        symbol.setForeground(BLUE);
        symbol.setFont(new Font("Segoe UI", Font.BOLD, 18));
        symbol.setHorizontalAlignment(SwingConstants.CENTER);
        symbol.setPreferredSize(new Dimension(42, 42));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(new Font("Segoe UI", Font.BOLD, 12));
        name.setForeground(INK);
        JLabel caption = new JLabel(detail);
        caption.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        caption.setForeground(MUTED);
        text.add(name);
        text.add(Box.createVerticalStrut(4));
        text.add(caption);
        card.add(symbol, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installClickListener(symbol, action);
        installClickListener(text, action);
        installClickListener(name, action);
        installClickListener(caption, action);
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                card.setBorder(hoverBorder);
                card.setBackground(new Color(248, 251, 255));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBorder(normalBorder);
                card.setBackground(Color.WHITE);
            }
        });
        return card;
    }

    private static void installClickListener(JComponent component, Runnable action) {
        component.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
        });
    }

    private JPanel buildPatientPage() {
        configureTable(patientTable);
        patientTable.setAutoCreateRowSorter(true);
        hideId(patientTable);
        styleStatusColumn(patientTable, 5);
        JPanel actions = actionBar(
                actionButton("＋ Add patient", e -> addPatient()),
                actionButton("Edit patient", e -> editSelectedPatient()),
                actionButton("View details", e -> viewSelectedPatient()),
                actionButton("Activate / deactivate", e -> toggleSelectedPatient()));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        JLabel accountLabel = new JLabel("Account");
        accountLabel.setFont(UITheme.FONT_SMALL_BOLD);
        accountLabel.setForeground(MUTED);
        UITheme.styleComboBox(patientAccountFilter);
        filters.add(accountLabel);
        filters.add(patientAccountFilter);
        patientAccountFilter.addActionListener(e -> applySearch());
        return tablePage("Patient directory", "Registered patients and contact information", patientTable, actions, filters);
    }

    private JPanel buildDoctorPage() {
        configureTable(doctorTable);
        doctorTable.setAutoCreateRowSorter(true);
        hideId(doctorTable);
        styleStatusColumn(doctorTable, 5);
        JPanel actions = actionBar(
                actionButton("＋ Add doctor", e -> editDoctor(null)),
                actionButton("Edit doctor", e -> editSelectedDoctor()),
                actionButton("View details", e -> viewSelectedDoctor()),
                actionButton("Activate / deactivate", e -> toggleSelectedDoctor()));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);
        JLabel specialtyLabel = new JLabel("Specialty");
        specialtyLabel.setFont(UITheme.FONT_SMALL_BOLD);
        specialtyLabel.setForeground(MUTED);
        JLabel docAccountLabel = new JLabel("Account");
        docAccountLabel.setFont(UITheme.FONT_SMALL_BOLD);
        docAccountLabel.setForeground(MUTED);
        UITheme.styleComboBox(doctorSpecialtyFilter);
        UITheme.styleComboBox(doctorActiveFilter);
        filters.add(specialtyLabel);
        filters.add(doctorSpecialtyFilter);
        filters.add(docAccountLabel);
        filters.add(doctorActiveFilter);
        doctorSpecialtyFilter.addActionListener(e -> applySearch());
        doctorActiveFilter.addActionListener(e -> applySearch());
        return tablePage("Doctor directory", "Manage specialists, fees and availability", doctorTable, actions, filters);
    }

    private JPanel buildAppointmentPage() {
        configureTable(appointmentTable);
        appointmentTable.setAutoCreateRowSorter(true);
        hideId(appointmentTable);
        styleStatusColumn(appointmentTable, 7);
        JPanel actions = actionBar(
                actionButton("＋ Book appointment", e -> bookAppointment()),
                actionButton("Edit appointment", e -> editSelectedAppointment()),
                actionButton("Confirm", e -> setSelectedAppointmentStatus("CONFIRMED")),
                actionButton("Complete", e -> setSelectedAppointmentStatus("COMPLETED")),
                actionButton("Cancel", e -> setSelectedAppointmentStatus("CANCELLED")),
                actionButton("View details", e -> viewSelectedAppointment()));
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterBar.setOpaque(false);
        UITheme.styleInputField(appointmentDateFilter);
        appointmentDateFilter.setPreferredSize(new Dimension(110, 32));
        appointmentDateFilter.setToolTipText("Optional date filter, yyyy-MM-dd");
        UITheme.styleComboBox(appointmentStatusFilter);
        appointmentStatusFilter.setToolTipText("Filter appointments by status");
        JLabel dateLabel = new JLabel("Date");
        dateLabel.setFont(UITheme.FONT_SMALL_BOLD);
        dateLabel.setForeground(MUTED);
        JLabel statusLabel = new JLabel("Status");
        statusLabel.setFont(UITheme.FONT_SMALL_BOLD);
        statusLabel.setForeground(MUTED);
        filterBar.add(dateLabel);
        filterBar.add(appointmentDateFilter);
        filterBar.add(statusLabel);
        filterBar.add(appointmentStatusFilter);
        appointmentDateFilter.getDocument().addDocumentListener((SimpleDocumentListener) e -> applySearch());
        appointmentStatusFilter.addActionListener(e -> applySearch());
        return tablePage("Appointment schedule", "View and manage patient visits", appointmentTable, actions, filterBar);
    }

    private JPanel buildMedicalPage() {
        configureTable(medicalTable);
        medicalTable.setAutoCreateRowSorter(true);
        hideId(medicalTable);
        hideColumn(medicalTable, 6);
        JPanel viewActions = actionBar(
                actionButton("＋ New record", e -> editMedicalRecord(null)),
                actionButton("View / edit selected", e -> editSelectedMedicalRecord()));
        return tablePage("Medical records", "Restricted clinical notes; access is limited to administrators", medicalTable, viewActions);
    }

    private JPanel buildReportsPage() {
        JPanel page = pageContainer();
        JPanel heading = sectionHeading("Reports & insights", "Live summaries calculated from CarePoint records");
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(heading);
        reportSummary = new JPanel(new GridLayout(0, 3, 12, 12));
        reportSummary.setOpaque(false);
        reportSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportFrom = new JTextField(10);
        reportTo = new JTextField(10);
        reportFrom.setToolTipText("Optional start date, yyyy-MM-dd");
        reportTo.setToolTipText("Optional end date, yyyy-MM-dd");
        styleDateField(reportFrom);
        styleDateField(reportTo);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel fromLabel = new JLabel("From");
        JLabel toLabel = new JLabel("To");
        fromLabel.setForeground(INK);
        toLabel.setForeground(INK);
        fromLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        actions.add(fromLabel);
        actions.add(reportFrom);
        actions.add(toLabel);
        actions.add(reportTo);
        actions.add(actionButton("Apply dates", e -> refreshReports()));
        actions.add(actionButton("Export CSV", e -> exportReportCsv()));
        actions.add(actionButton("Refresh data", e -> refresh()));
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        page.add(Box.createVerticalStrut(2));
        page.add(actions);
        reportFilterStatus = new JLabel("Leave dates blank to include all appointment history.");
        reportFilterStatus.setForeground(MUTED);
        reportFilterStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        reportFilterStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportFilterStatus.setBorder(new EmptyBorder(2, 2, 5, 0));
        page.add(reportFilterStatus);
        page.add(Box.createVerticalStrut(5));
        page.add(reportSummary);
        JPanel charts = new JPanel(new GridLayout(0, 3, 12, 12));
        charts.setOpaque(false);
        charts.setAlignmentX(Component.LEFT_ALIGNMENT);
        reportAppointmentChart = new OverviewChart("Appointments by month · status", OverviewChart.Kind.BARS);
        reportSpecialtyChart = new OverviewChart("Patients by department", OverviewChart.Kind.DONUT);
        reportDoctorChart = new OverviewChart("Active doctors by specialty", OverviewChart.Kind.DONUT);
        charts.add(reportAppointmentChart);
        charts.add(reportSpecialtyChart);
        charts.add(reportDoctorChart);
        charts.setAlignmentX(Component.LEFT_ALIGNMENT);
        page.add(charts);
        page.add(Box.createVerticalStrut(5));
        page.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                layoutReportSections(page.getWidth());
            }
        });
        layoutReportSections(1100);
        reportFrom.getDocument().addDocumentListener((SimpleDocumentListener) e -> refreshReports());
        reportTo.getDocument().addDocumentListener((SimpleDocumentListener) e -> refreshReports());
        return page;
    }

    private void layoutReportSections(int width) {
        if (reportSummary == null || width <= 0) return;
        int columns = width >= 1080 ? 3 : width >= 640 ? 2 : 1;
        int rows = (8 + columns - 1) / columns;
        reportSummary.setLayout(new GridLayout(0, columns, 12, 12));
        reportSummary.setPreferredSize(new Dimension(0, rows * 104 + Math.max(0, rows - 1) * 12));
        reportSummary.setMaximumSize(new Dimension(Integer.MAX_VALUE, rows * 104 + Math.max(0, rows - 1) * 12));
        reportSummary.revalidate();

        int chartColumns = width >= 1080 ? 3 : width >= 700 ? 2 : 1;
        int chartRows = (3 + chartColumns - 1) / chartColumns;
        Container parent = reportSummary.getParent();
        if (parent != null && parent.getComponentCount() > 0) {
            for (Component component : parent.getComponents()) {
                if (component instanceof JPanel panel && panel != reportSummary
                        && panel.getComponentCount() == 3
                        && panel.getComponent(0) == reportAppointmentChart) {
                    panel.setLayout(new GridLayout(0, chartColumns, 12, 12));
                    int height = chartRows * 320 + Math.max(0, chartRows - 1) * 12;
                    panel.setPreferredSize(new Dimension(0, height));
                    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                    panel.revalidate();
                    break;
                }
            }
        }
        reportSummary.getParent().revalidate();
    }

    private static void styleDateField(JTextField field) {
        UITheme.styleInputField(field);
        field.setPreferredSize(new Dimension(115, 34));
        field.setToolTipText("Enter a date in yyyy-MM-dd format");
    }

    private JPanel buildSettingsPage() {
        JPanel page = pageContainer();
        page.add(sectionHeading("Settings", "Application preferences and administrator account"));
        JPanel account = cardPanel();
        account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));
        account.setBorder(new EmptyBorder(22, 24, 22, 24));
        account.add(settingLine("Signed in as", admin.fullName() + " · Administrator"));
        account.add(Box.createVerticalStrut(14));
        account.add(settingLine("Database", "CarePoint MySQL database (configured with DB_URL)"));
        account.add(Box.createVerticalStrut(14));
        account.add(settingLine("Clinic hours", DatabaseModule.clinicHours() + " · 30-minute appointments"));
        account.add(Box.createVerticalStrut(14));
        account.add(settingLine("Data access", "Administrator-only dashboard and medical-record access"));
        account.add(Box.createVerticalStrut(20));
        JButton changePassword = actionButton("Change administrator password", e -> showSettingsPassword());
        account.add(changePassword);
        account.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        page.add(account);
        JLabel notice = new JLabel("<html>Clinic hours are configured with CLINIC_OPEN and CLINIC_CLOSE in the launch environment. " +
                "Database credentials remain in environment variables and are never stored in CarePoint source files.</html>");
        notice.setForeground(MUTED);
        notice.setBorder(new EmptyBorder(17, 2, 0, 2));
        page.add(notice);
        return page;
    }

    private JPanel tablePage(String title, String subtitle, JTable table, JPanel actions) {
        return tablePage(title, subtitle, table, actions, null);
    }

    private JPanel tablePage(String title, String subtitle, JTable table, JPanel actions, JPanel filterBar) {
        JPanel page = pageContainer();
        page.add(sectionHeading(title, subtitle));
        JPanel surface = cardPanel();
        surface.setLayout(new BorderLayout(0, 12));
        surface.setBorder(new EmptyBorder(12, 12, 12, 12));
        if (filterBar != null) surface.add(filterBar, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(0, 430));
        surface.add(scroll, BorderLayout.CENTER);
        surface.add(actions, BorderLayout.SOUTH);
        surface.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
        page.add(surface);
        return page;
    }

    private JScrollPane scrollPage(JPanel page) {
        JScrollPane scroll = new JScrollPane(page);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(CANVAS);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JPanel pageContainer() {
        JPanel page = new JPanel();
        page.setBackground(CANVAS);
        page.setBorder(new EmptyBorder(23, 26, 26, 26));
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        return page;
    }

    private JPanel sectionHeading(String title, String subtitle) {
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(new Font("Segoe UI", Font.BOLD, 23));
        name.setForeground(INK);
        JLabel caption = new JLabel(subtitle);
        caption.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        caption.setForeground(MUTED);
        heading.add(name);
        heading.add(Box.createVerticalStrut(5));
        heading.add(caption);
        heading.setBorder(new EmptyBorder(0, 0, 17, 0));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    private JPanel actionBar(JButton... buttons) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        bar.setOpaque(false);
        for (JButton button : buttons) bar.add(button);
        return bar;
    }

    private static JButton actionButton(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        if (text.startsWith("+") || text.startsWith("＋") || text.startsWith("Add") || text.startsWith("New") || text.startsWith("Create")
                || "Confirm".equals(text) || "Complete".equals(text)) {
            UITheme.stylePrimaryButton(button);
        } else if (text.startsWith("Deactivate") || text.startsWith("Cancel")) {
            UITheme.styleDangerButton(button);
        } else {
            UITheme.styleSecondaryButton(button);
        }
        button.addActionListener(action);
        return button;
    }

    private static void styleRefreshButton(JButton button) {
        UITheme.styleSecondaryButton(button);
        button.setPreferredSize(new Dimension(130, 36));
        button.setToolTipText("Reload dashboard data, charts, appointments, and activity from MySQL");
    }

    private static JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(UITheme.createCardBorder());
        return panel;
    }

    private static void configureTable(JTable table) {
        UITheme.styleTable(table);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
    }

    private static void styleStatusColumn(JTable table, int column) {
        UITheme.installStatusBadgeRenderer(table, column);
    }

    private static void hideId(JTable table) {
        hideColumn(table, 0);
    }

    private static void hideColumn(JTable table, int column) {
        table.getColumnModel().getColumn(column).setMinWidth(0);
        table.getColumnModel().getColumn(column).setMaxWidth(0);
        table.getColumnModel().getColumn(column).setPreferredWidth(0);
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        refreshButton.setText("Refreshing...");
        connectionStatus.setText("Refreshing live database data…");
        new SwingWorker<DashboardData, Void>() {
            @Override protected DashboardData doInBackground() throws Exception {
                return new DashboardData(DatabaseModule.stats(admin), DatabaseModule.adminAppointments(admin),
                        DatabaseModule.patients(admin), DatabaseModule.doctors(true),
                        DatabaseModule.appointmentOverview(admin), DatabaseModule.patientCountsBySpecialty(admin),
                        DatabaseModule.recentActivity(admin, 8), DatabaseModule.medicalRecords(admin));
            }

            @Override protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("Refresh");
                DashboardData data;
                try {
                    data = get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showError(error);
                    connectionStatus.setText("Could not refresh database data");
                    connectionStatus.setForeground(new Color(194, 67, 78));
                    return;
                } catch (ExecutionException error) {
                    showError(error);
                    connectionStatus.setText("Could not refresh database data");
                    connectionStatus.setForeground(new Color(194, 67, 78));
                    return;
                }
                stats = data.stats();
                appointments = data.appointments();
                patients = data.patients();
                doctors = data.doctors();
                monthSummaries = data.months();
                specialtyCounts = data.specialties();
                activity = data.activity();
                records = data.records();
                updateTables();
                updateDashboard();
                connectionStatus.setText("Live data · refreshed " + LocalTime.now().format(TIME));
                connectionStatus.setForeground(new Color(37, 145, 115));
            }
        }.execute();
    }

    private record DashboardData(DatabaseModule.Stats stats, List<DatabaseModule.Appointment> appointments,
                                 List<DatabaseModule.User> patients, List<DatabaseModule.Doctor> doctors,
                                 List<DatabaseModule.MonthSummary> months,
                                 List<DatabaseModule.SpecialtyCount> specialties,
                                 List<DatabaseModule.Activity> activity,
                                 List<DatabaseModule.MedicalRecord> records) {}

    private void updateTables() {
        patientModel.setRowCount(0);
        for (DatabaseModule.User patient : patients)
            patientModel.addRow(new Object[]{patient.id(), patient.fullName(), patient.email(), patient.phone(),
                    patient.createdAt().toLocalDate(), patient.active() ? "Active" : "Inactive"});
        doctorModel.setRowCount(0);
        for (DatabaseModule.Doctor doctor : doctors)
            doctorModel.addRow(new Object[]{doctor.id(), doctor.fullName(), doctor.specialty(), doctor.fee(),
                    doctor.availability(), doctor.active() ? "Active" : "Inactive"});
        String selectedSpecialty = (String) doctorSpecialtyFilter.getSelectedItem();
        doctorSpecialtyFilter.removeAllItems();
        doctorSpecialtyFilter.addItem("All specialties");
        doctors.stream().map(DatabaseModule.Doctor::specialty).distinct().sorted().forEach(doctorSpecialtyFilter::addItem);
        if (selectedSpecialty != null) doctorSpecialtyFilter.setSelectedItem(selectedSpecialty);
        appointmentModel.setRowCount(0);
        for (DatabaseModule.Appointment appointment : appointments)
            appointmentModel.addRow(new Object[]{appointment.id(), appointment.patientName(), appointment.doctorName(),
                    appointment.specialty(), appointment.date(), appointment.time().format(TIME),
                    appointment.reason(), appointment.status()});
        medicalModel.setRowCount(0);
        for (DatabaseModule.MedicalRecord record : records)
            medicalModel.addRow(new Object[]{record.id(), record.patientName(), record.doctorName(),
                    record.subject(), record.createdAt().format(DAY_TIME), record.updatedAt().format(DAY_TIME),
                    record.details()});
        applySearch();
    }

    private void updateDashboard() {
        if (stats == null) return;
        patientStat.setValue(String.valueOf(stats.patients()));
        doctorStat.setValue(String.valueOf(stats.doctors()));
        totalAppointmentStat.setValue(String.valueOf(stats.totalAppointments()));
        pendingStat.setValue(String.valueOf(stats.pending()));
        confirmedStat.setValue(String.valueOf(stats.confirmed()));
        completedStat.setValue(String.valueOf(stats.completed()));
        cancelledStat.setValue(String.valueOf(stats.cancelled()));
        todayStat.setValue(String.valueOf(stats.today()));
        upcomingStat.setValue(String.valueOf(stats.upcoming()));
        if (notificationButton != null) {
            notificationButton.setText("Today · " + stats.today());
            notificationButton.setToolTipText(stats.today() + " appointment(s) scheduled today");
        }
        appointmentChart.setMonths(monthSummaries);
        specialtyChart.setSpecialties(specialtyCounts);
        fillUpcoming();
        fillActivity();
        refreshReports();
    }

    private void fillUpcoming() {
        if (upcomingList == null) return;
        upcomingList.removeAll();
        List<DatabaseModule.Appointment> future = appointments.stream()
                .filter(a -> "PENDING".equals(a.status()) || "CONFIRMED".equals(a.status()))
                .filter(a -> !LocalDateTime.of(a.date(), a.time()).isBefore(LocalDateTime.now()))
                .sorted((a, b) -> LocalDateTime.of(a.date(), a.time()).compareTo(LocalDateTime.of(b.date(), b.time())))
                .limit(3).toList();
        if (future.isEmpty()) {
            addEmpty(upcomingList, "No upcoming appointments");
            JButton book = actionButton("＋  Book appointment", e -> bookAppointment());
            book.setFont(new Font("Segoe UI", Font.BOLD, 10));
            book.setBorder(new EmptyBorder(6, 9, 6, 9));
            book.setAlignmentX(Component.LEFT_ALIGNMENT);
            upcomingList.add(Box.createVerticalStrut(2));
            upcomingList.add(book);
        } else {
            for (DatabaseModule.Appointment appointment : future) {
                addUpcomingItem(appointment);
            }
        }
        upcomingList.revalidate();
        upcomingList.repaint();
    }

    private void addUpcomingItem(DatabaseModule.Appointment appointment) {
        JPanel item = new JPanel(new BorderLayout(7, 0));
        item.setOpaque(false);
        item.setBorder(new EmptyBorder(5, 0, 6, 0));
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        String when = appointment.date().format(DateTimeFormatter.ofPattern("MMM d, yyyy")) + " · "
                + appointment.time().format(TIME);
        JLabel date = new JLabel(when);
        date.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        date.setForeground(MUTED);
        JLabel patient = new JLabel(appointment.patientName());
        patient.setFont(new Font("Segoe UI", Font.BOLD, 11));
        patient.setForeground(INK);
        patient.setToolTipText(appointment.patientName());
        JLabel doctor = new JLabel(appointment.doctorName() + " · " + appointment.specialty());
        doctor.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        doctor.setForeground(MUTED);
        doctor.setToolTipText(appointment.doctorName() + " · " + appointment.specialty());
        details.add(date);
        details.add(patient);
        details.add(doctor);
        Color badgeColor = "PENDING".equals(appointment.status())
                ? new Color(255, 243, 224) : new Color(224, 247, 239);
        Color textColor = "PENDING".equals(appointment.status())
                ? new Color(174, 105, 24) : new Color(33, 129, 99);
        JLabel badge = new JLabel(appointment.status(), SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(badgeColor);
        badge.setForeground(textColor);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 8));
        badge.setBorder(new EmptyBorder(4, 6, 4, 6));
        item.add(details, BorderLayout.CENTER);
        item.add(badge, BorderLayout.EAST);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        upcomingList.add(item);
    }

    private void fillActivity() {
        if (activityList == null) return;
        activityList.removeAll();
        if (activity.isEmpty()) {
            addEmpty(activityList, "No activity available yet");
        } else {
            for (DatabaseModule.Activity item : activity.stream().limit(3).toList())
                addListItem(activityList, item.createdAt().format(DAY_TIME), item.type(), item.description(),
                        new Color(39, 179, 152));
        }
        activityList.revalidate();
        activityList.repaint();
    }

    private void refreshReports() {
        if (reportSummary == null || stats == null) return;
        LocalDate from;
        LocalDate to;
        List<DatabaseModule.Appointment> filtered;
        try {
            from = parseOptionalDate(reportFrom);
            to = parseOptionalDate(reportTo);
            if (from != null && to != null && from.isAfter(to))
                throw new IllegalArgumentException("The report start date must be on or before its end date.");
            filtered = appointments.stream()
                    .filter(appointment -> from == null || !appointment.date().isBefore(from))
                    .filter(appointment -> to == null || !appointment.date().isAfter(to))
                    .toList();
        } catch (IllegalArgumentException e) {
            reportFilterStatus.setText(e.getMessage());
            reportFilterStatus.setForeground(new Color(194, 67, 78));
            return;
        }
        long pending = filtered.stream().filter(a -> "PENDING".equals(a.status())).count();
        long confirmed = filtered.stream().filter(a -> "CONFIRMED".equals(a.status())).count();
        long completed = filtered.stream().filter(a -> "COMPLETED".equals(a.status())).count();
        long cancelled = filtered.stream().filter(a -> "CANCELLED".equals(a.status())).count();
        long total = filtered.size();
        long registered = patients.stream()
                .filter(patient -> from == null || !patient.createdAt().toLocalDate().isBefore(from))
                .filter(patient -> to == null || !patient.createdAt().toLocalDate().isAfter(to))
                .count();
        reportSummary.removeAll();
        reportSummary.add(reportTile("Registered patients", String.valueOf(registered), "In selected date range"));
        reportSummary.add(reportTile("Active doctors", String.valueOf(stats.doctors()), "Available in directory"));
        reportSummary.add(reportTile("Total appointments", String.valueOf(total), "In selected date range"));
        reportSummary.add(reportTile("Pending", String.valueOf(pending), percentage(pending, total) + " of appointments"));
        reportSummary.add(reportTile("Confirmed", String.valueOf(confirmed), percentage(confirmed, total) + " of appointments"));
        reportSummary.add(reportTile("Completed", String.valueOf(completed), percentage(completed, total) + " of appointments"));
        reportSummary.add(reportTile("Cancelled", String.valueOf(cancelled), percentage(cancelled, total) + " of appointments"));
        long today = filtered.stream().filter(a -> a.date().equals(LocalDate.now())).count();
        long upcoming = filtered.stream().filter(a -> List.of("PENDING", "CONFIRMED").contains(a.status()))
                .filter(a -> !LocalDateTime.of(a.date(), a.time()).isBefore(LocalDateTime.now())).count();
        reportSummary.add(reportTile("Today / upcoming", today + " / " + upcoming, "Scheduled / future visits"));
        if (reportAppointmentChart != null) reportAppointmentChart.setMonths(monthSummaries(filtered, from, to));
        if (reportSpecialtyChart != null) reportSpecialtyChart.setSpecialties(specialties(filtered));
        if (reportDoctorChart != null) reportDoctorChart.setSpecialties(doctorSpecialties());
        reportFilterStatus.setText("Showing " + total + " appointment(s) and " + registered + " registration(s).");
        reportFilterStatus.setForeground(MUTED);
        reportSummary.revalidate();
        reportSummary.repaint();
    }

    private static LocalDate parseOptionalDate(JTextField field) {
        if (field == null || field.getText().isBlank()) return null;
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Enter report dates in yyyy-MM-dd format.");
        }
    }

    private static String percentage(long count, long total) {
        return total == 0 ? "0.0%" : String.format(java.util.Locale.ROOT, "%.1f%%", count * 100.0 / total);
    }

    private List<DatabaseModule.MonthSummary> monthSummaries(List<DatabaseModule.Appointment> rows,
                                                              LocalDate from, LocalDate to) {
        Map<String, long[]> counts = new HashMap<>();
        for (DatabaseModule.Appointment appointment : rows) {
            String month = YearMonth.from(appointment.date()).toString();
            long[] values = counts.computeIfAbsent(month, ignored -> new long[4]);
            int status = switch (appointment.status()) {
                case "PENDING" -> 0;
                case "CONFIRMED" -> 1;
                case "COMPLETED" -> 2;
                case "CANCELLED" -> 3;
                default -> -1;
            };
            if (status >= 0) values[status]++;
        }
        List<DatabaseModule.MonthSummary> result = new ArrayList<>();
        YearMonth last = to == null ? YearMonth.now() : YearMonth.from(to);
        YearMonth first = from == null ? last.minusMonths(11) : YearMonth.from(from);
        if (first.isAfter(last)) first = last;
        if (first.plusMonths(11).isBefore(last)) first = last.minusMonths(11);
        for (YearMonth current = first; !current.isAfter(last); current = current.plusMonths(1)) {
            String month = current.toString();
            long[] values = counts.getOrDefault(month, new long[4]);
            result.add(new DatabaseModule.MonthSummary(month, values[0], values[1], values[2], values[3]));
        }
        return result;
    }

    private List<DatabaseModule.SpecialtyCount> specialties(List<DatabaseModule.Appointment> rows) {
        Map<String, Set<Long>> patientsBySpecialty = new HashMap<>();
        for (DatabaseModule.Appointment appointment : rows)
            patientsBySpecialty.computeIfAbsent(appointment.specialty(), ignored -> new HashSet<>())
                    .add(appointment.patientId());
        return patientsBySpecialty.entrySet().stream()
                .map(entry -> new DatabaseModule.SpecialtyCount(entry.getKey(), entry.getValue().size()))
                .sorted((left, right) -> Long.compare(right.patients(), left.patients()))
                .toList();
    }

    private List<DatabaseModule.SpecialtyCount> doctorSpecialties() {
        Map<String, Long> counts = new HashMap<>();
        for (DatabaseModule.Doctor doctor : doctors)
            if (doctor.active()) counts.merge(doctor.specialty(), 1L, Long::sum);
        return counts.entrySet().stream()
                .map(entry -> new DatabaseModule.SpecialtyCount(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> Long.compare(right.patients(), left.patients()))
                .toList();
    }

    private void exportReportCsv() {
        List<DatabaseModule.Appointment> filtered;
        try {
            LocalDate from = parseOptionalDate(reportFrom);
            LocalDate to = parseOptionalDate(reportTo);
            if (from != null && to != null && from.isAfter(to))
                throw new IllegalArgumentException("The report start date must be on or before its end date.");
            filtered = appointments.stream()
                    .filter(appointment -> from == null || !appointment.date().isBefore(from))
                    .filter(appointment -> to == null || !appointment.date().isAfter(to))
                    .toList();
        } catch (IllegalArgumentException e) {
            showError(e);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("carepoint-appointments.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("Appointment ID,Patient,Doctor,Department,Date,Time,Reason,Status");
            writer.newLine();
            for (DatabaseModule.Appointment appointment : filtered) {
                writer.write(appointment.id() + "," + csv(appointment.patientName()) + ","
                        + csv(appointment.doctorName()) + "," + csv(appointment.specialty()) + ","
                        + appointment.date() + "," + appointment.time() + ","
                        + csv(appointment.reason()) + "," + appointment.status());
                writer.newLine();
            }
            showSuccess("Exported " + filtered.size() + " appointment(s) to " + path + ".");
        } catch (IOException e) {
            showError(e);
        }
    }

    private static String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    private JPanel reportTile(String name, String value, String detail) {
        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(240, 104));
        card.setMinimumSize(new Dimension(0, 100));
        JLabel title = new JLabel(name);
        title.setFont(new Font("Segoe UI", Font.BOLD, 11));
        title.setForeground(MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel count = new JLabel(value);
        count.setFont(new Font("Segoe UI", Font.BOLD, 25));
        count.setForeground(BLUE);
        count.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel caption = new JLabel(detail);
        caption.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        caption.setForeground(MUTED);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(count);
        card.add(caption);
        return card;
    }

    private void addListItem(JPanel parent, String eyebrow, String title, String detail, Color color) {
        JPanel item = new JPanel(new BorderLayout(8, 0));
        item.setOpaque(false);
        item.setBorder(new EmptyBorder(4, 0, 5, 0));
        JLabel dot = new JLabel("●");
        dot.setForeground(color);
        dot.setFont(new Font("Segoe UI", Font.BOLD, 10));
        item.add(dot, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel first = new JLabel(eyebrow + "  ·  " + title);
        first.setFont(new Font("Segoe UI", Font.BOLD, 10));
        first.setForeground(INK);
        first.setToolTipText(title);
        JLabel second = new JLabel(detail);
        second.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        second.setForeground(MUTED);
        text.add(first);
        text.add(Box.createVerticalStrut(2));
        text.add(second);
        item.add(text, BorderLayout.CENTER);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        parent.add(item);
    }

    private void addEmpty(JPanel parent, String message) {
        JLabel empty = new JLabel(message);
        empty.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        empty.setForeground(MUTED);
        empty.setBorder(new EmptyBorder(15, 2, 8, 2));
        parent.add(empty);
    }

    private void applySearch() {
        if (activeTable == null) return;
        if (activeTable.getRowSorter() instanceof TableRowSorter<?> sorter) {
            String query = globalSearch.getText().trim();
            List<RowFilter<Object, Object>> filters = new ArrayList<>();
            if (!query.isEmpty())
                filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(query)));
            if (activeTable == appointmentTable) {
                String status = (String) appointmentStatusFilter.getSelectedItem();
                if (status != null && !"All statuses".equals(status))
                    filters.add(RowFilter.regexFilter("^" + status + "$", 7));
                String date = appointmentDateFilter.getText().trim();
                if (!date.isEmpty())
                    filters.add(RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(date) + "$", 4));
            } else if (activeTable == patientTable) {
                String status = (String) patientAccountFilter.getSelectedItem();
                if (status != null && !"All accounts".equals(status))
                    filters.add(RowFilter.regexFilter("^" + status + "$", 5));
            } else if (activeTable == doctorTable) {
                String specialty = (String) doctorSpecialtyFilter.getSelectedItem();
                if (specialty != null && !"All specialties".equals(specialty))
                    filters.add(RowFilter.regexFilter("(?i)^" + java.util.regex.Pattern.quote(specialty) + "$", 2));
                String status = (String) doctorActiveFilter.getSelectedItem();
                if (status != null && !"All doctors".equals(status))
                    filters.add(RowFilter.regexFilter("^" + status + "$", 5));
            }
            sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        }
    }

    private void setActiveTable(JTable table) {
        activeTable = table;
        applySearch();
    }

    private void searchAcrossRecords() {
        String query = globalSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) return;
        List<SearchResult> matches = new java.util.ArrayList<>();
        for (DatabaseModule.User patient : patients) {
            if (contains(patient.fullName(), query) || contains(patient.email(), query) || contains(patient.phone(), query))
                matches.add(new SearchResult("Patient · " + patient.fullName() + " · " + patient.email(), "Patients"));
        }
        for (DatabaseModule.Doctor doctor : doctors) {
            if (contains(doctor.fullName(), query) || contains(doctor.specialty(), query))
                matches.add(new SearchResult("Doctor · " + doctor.fullName() + " · " + doctor.specialty(), "Doctors"));
        }
        for (DatabaseModule.Appointment appointment : appointments) {
            if (contains(appointment.patientName(), query) || contains(appointment.doctorName(), query)
                    || contains(appointment.specialty(), query) || contains(appointment.status(), query))
                matches.add(new SearchResult("Appointment · " + appointment.patientName() + " · "
                        + appointment.doctorName() + " · " + appointment.date() + " " + appointment.time(), "Appointments"));
        }
        for (DatabaseModule.MedicalRecord record : records) {
            if (contains(record.patientName(), query) || contains(record.doctorName(), query)
                    || contains(record.subject(), query) || contains(record.details(), query))
                matches.add(new SearchResult("Medical record · " + record.patientName() + " · "
                        + record.subject(), "Medical Records"));
        }
        if (matches.isEmpty()) {
            showInfo("No patients, doctors, appointments, or medical records matched that search.");
            return;
        }
        DefaultListModel<SearchResult> model = new DefaultListModel<>();
        matches.stream().limit(30).forEach(model::addElement);
        JList<SearchResult> results = new JList<>(model);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setSelectedIndex(0);
        results.setVisibleRowCount(Math.min(10, model.size()));
        int choice = JOptionPane.showConfirmDialog(this, new JScrollPane(results),
                "Search results (" + matches.size() + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION && results.getSelectedValue() != null)
            navigate(results.getSelectedValue().page());
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private record SearchResult(String label, String page) {
        @Override public String toString() { return label; }
    }

    private void addPatient() {
        JTextField name = new JTextField(23);
        JTextField email = new JTextField(23);
        JTextField phone = new JTextField(23);
        JPasswordField password = new JPasswordField(23);
        JPasswordField confirm = new JPasswordField(23);
        JPanel form = formPanel("Full name", name, "Email address", email, "Phone", phone,
                "Password (10–72 characters)", password, "Confirm password", confirm);
        if (JOptionPane.showConfirmDialog(this, form, "Add patient",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] secret = password.getPassword();
        char[] repeated = confirm.getPassword();
        try {
            if (!java.util.Arrays.equals(secret, repeated))
                throw new IllegalArgumentException("The passwords do not match.");
            char[] submitted = secret.clone();
            String fullName = name.getText();
            String address = email.getText();
            String telephone = phone.getText();
            runTask(() -> {
                try {
                    DatabaseModule.createPatient(admin, fullName, address, telephone, submitted);
                } finally {
                    java.util.Arrays.fill(submitted, '\0');
                }
            }, "Patient account created.");
        } catch (Exception e) {
            showError(e);
        } finally {
            java.util.Arrays.fill(secret, '\0');
            java.util.Arrays.fill(repeated, '\0');
        }
    }

    private void editSelectedPatient() {
        int row = selectedModelRow(patientTable);
        if (row < 0) return;
        long id = ((Number) patientModel.getValueAt(row, 0)).longValue();
        JTextField name = new JTextField(String.valueOf(patientModel.getValueAt(row, 1)), 23);
        JTextField email = new JTextField(String.valueOf(patientModel.getValueAt(row, 2)), 23);
        JTextField phone = new JTextField(String.valueOf(patientModel.getValueAt(row, 3)), 23);
        JPanel form = formPanel("Full name", name, "Email address", email, "Phone", phone);
        if (JOptionPane.showConfirmDialog(this, form, "Edit patient",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String fullName = name.getText();
        String address = email.getText();
        String telephone = phone.getText();
        runTask(() -> DatabaseModule.updatePatient(admin, id, fullName, address, telephone),
                "Patient details updated.");
    }

    private void toggleSelectedPatient() {
        int row = selectedModelRow(patientTable);
        if (row < 0) return;
        long id = ((Number) patientModel.getValueAt(row, 0)).longValue();
        String name = String.valueOf(patientModel.getValueAt(row, 1));
        boolean activate = !"Active".equals(patientModel.getValueAt(row, 5));
        if (!activate && JOptionPane.showConfirmDialog(this,
                "Deactivate " + name + "? Sign-in will be disabled and appointment/medical history retained.",
                "Confirm patient deactivation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        runTask(() -> DatabaseModule.setPatientActive(admin, id, activate),
                "Patient account " + (activate ? "activated." : "deactivated."));
    }

    private void viewSelectedPatient() {
        int row = selectedModelRow(patientTable);
        if (row < 0) return;
        long id = ((Number) patientModel.getValueAt(row, 0)).longValue();
        patients.stream().filter(patient -> patient.id() == id).findFirst().ifPresent(patient ->
                JOptionPane.showMessageDialog(this,
                        "Patient: " + patient.fullName() + "\nEmail: " + patient.email() + "\nPhone: "
                                + patient.phone() + "\nRegistered: " + patient.createdAt().format(DAY_TIME)
                                + "\nAccount: " + (patient.active() ? "Active" : "Inactive"),
                        "Patient details", JOptionPane.INFORMATION_MESSAGE));
    }

    private void editDoctor(DatabaseModule.Doctor existing) {
        JTextField name = new JTextField(existing == null ? "" : existing.fullName(), 23);
        JTextField specialty = new JTextField(existing == null ? "" : existing.specialty(), 23);
        JTextField fee = new JTextField(existing == null ? "" : existing.fee().toPlainString(), 23);
        JTextField availability = new JTextField(existing == null ? "Mon-Fri, 9:00 AM-5:00 PM" : existing.availability(), 23);
        JCheckBox active = new JCheckBox("Active and available for booking", existing == null || existing.active());
        JPanel form = formPanel("Doctor name", name, "Department / specialty", specialty,
                "Consultation fee", fee, "Availability", availability, " ", active);
        if (JOptionPane.showConfirmDialog(this, form, existing == null ? "Add doctor" : "Edit doctor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            BigDecimal amount = new BigDecimal(fee.getText().trim());
            DatabaseModule.Doctor doctor = new DatabaseModule.Doctor(existing == null ? 0 : existing.id(),
                    name.getText(), specialty.getText(), amount, availability.getText(), active.isSelected());
            runTask(() -> DatabaseModule.saveDoctor(admin, doctor),
                    existing == null ? "Doctor added." : "Doctor details updated.");
        } catch (NumberFormatException e) {
            showError(new IllegalArgumentException("Enter a valid consultation fee.", e));
        }
    }

    private void editSelectedDoctor() {
        int row = selectedModelRow(doctorTable);
        if (row < 0) return;
        DatabaseModule.Doctor doctor = new DatabaseModule.Doctor(
                ((Number) doctorModel.getValueAt(row, 0)).longValue(),
                String.valueOf(doctorModel.getValueAt(row, 1)),
                String.valueOf(doctorModel.getValueAt(row, 2)),
                new BigDecimal(String.valueOf(doctorModel.getValueAt(row, 3))),
                String.valueOf(doctorModel.getValueAt(row, 4)),
                "Active".equals(doctorModel.getValueAt(row, 5)));
        editDoctor(doctor);
    }

    private void toggleSelectedDoctor() {
        int row = selectedModelRow(doctorTable);
        if (row < 0) return;
        long id = ((Number) doctorModel.getValueAt(row, 0)).longValue();
        String name = String.valueOf(doctorModel.getValueAt(row, 1));
        boolean activate = !"Active".equals(doctorModel.getValueAt(row, 5));
        if (!activate && JOptionPane.showConfirmDialog(this,
                "Deactivate " + name + "? Existing appointment history will be preserved.",
                "Confirm doctor deactivation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        runTask(() -> DatabaseModule.setDoctorActive(admin, id, activate),
                "Doctor " + (activate ? "activated." : "deactivated."));
    }

    private void viewSelectedDoctor() {
        int row = selectedModelRow(doctorTable);
        if (row < 0) return;
        long id = ((Number) doctorModel.getValueAt(row, 0)).longValue();
        doctors.stream().filter(doctor -> doctor.id() == id).findFirst().ifPresent(doctor ->
                JOptionPane.showMessageDialog(this,
                        "Doctor: " + doctor.fullName() + "\nSpecialty: " + doctor.specialty()
                                + "\nConsultation fee: " + doctor.fee() + "\nAvailability: "
                                + doctor.availability() + "\nStatus: " + (doctor.active() ? "Active" : "Inactive"),
                        "Doctor details", JOptionPane.INFORMATION_MESSAGE));
    }

    private void bookAppointment() {
        List<DatabaseModule.User> activePatients;
        List<DatabaseModule.Doctor> activeDoctors = doctors.stream().filter(DatabaseModule.Doctor::active).toList();
        try {
            activePatients = DatabaseModule.patients(admin).stream().filter(DatabaseModule.User::active).toList();
        } catch (Exception e) {
            showError(e);
            return;
        }
        if (activePatients.isEmpty() || activeDoctors.isEmpty()) {
            showInfo("Add an active patient and doctor before booking an appointment.");
            return;
        }
        JComboBox<DatabaseModule.User> patient = new JComboBox<>(activePatients.toArray(DatabaseModule.User[]::new));
        patient.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof DatabaseModule.User user) setText(user.fullName() + " — " + user.email());
                return this;
            }
        });
        JComboBox<DatabaseModule.Doctor> doctor = new JComboBox<>(activeDoctors.toArray(DatabaseModule.Doctor[]::new));
        JTextField date = new JTextField(LocalDate.now().plusDays(1).toString(), 15);
        JTextField time = new JTextField("09:00", 15);
        JTextField reason = new JTextField(23);
        JPanel form = formPanel("Patient", patient, "Doctor", doctor, "Date (yyyy-MM-dd)", date,
                "Time (HH:mm)", time, "Reason", reason);
        if (JOptionPane.showConfirmDialog(this, form, "Book appointment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            LocalDate visitDate = LocalDate.parse(date.getText().trim());
            LocalTime visitTime = LocalTime.parse(time.getText().trim());
            DatabaseModule.User selectedPatient = (DatabaseModule.User) patient.getSelectedItem();
            DatabaseModule.Doctor selectedDoctor = (DatabaseModule.Doctor) doctor.getSelectedItem();
            if (selectedPatient == null || selectedDoctor == null) throw new IllegalArgumentException("Choose a patient and doctor.");
            String visitReason = reason.getText();
            runTask(() -> DatabaseModule.bookForPatient(admin, selectedPatient.id(), selectedDoctor,
                    visitDate, visitTime, visitReason), "Appointment booked.");
        } catch (java.time.format.DateTimeParseException e) {
            showError(new IllegalArgumentException("Enter the date as yyyy-MM-dd and time as HH:mm.", e));
        }
    }

    private void editSelectedAppointment() {
        int row = selectedModelRow(appointmentTable);
        if (row < 0) return;
        long id = ((Number) appointmentModel.getValueAt(row, 0)).longValue();
        String selectedStatus = String.valueOf(appointmentModel.getValueAt(row, 7));
        if (!"PENDING".equals(selectedStatus) && !"CONFIRMED".equals(selectedStatus)) {
            showInfo("Only pending or confirmed appointments can be edited.");
            return;
        }
        DatabaseModule.Appointment selectedAppointment = appointments.stream()
                .filter(appointment -> appointment.id() == id).findFirst().orElse(null);
        if (selectedAppointment == null) {
            showInfo("Appointment was not found. Refresh and try again.");
            return;
        }
        List<DatabaseModule.Doctor> activeDoctors = doctors.stream().filter(DatabaseModule.Doctor::active).toList();
        if (activeDoctors.isEmpty()) {
            showInfo("An active doctor is required to edit an appointment.");
            return;
        }
        JComboBox<DatabaseModule.Doctor> doctor = new JComboBox<>(activeDoctors.toArray(DatabaseModule.Doctor[]::new));
        doctor.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof DatabaseModule.Doctor item)
                    setText(item.fullName() + " — " + item.specialty());
                return this;
            }
        });
        activeDoctors.stream().filter(item -> item.id() == selectedAppointment.doctorId())
                .findFirst().ifPresent(doctor::setSelectedItem);
        JTextField date = new JTextField(selectedAppointment.date().toString(), 15);
        JTextField time = new JTextField(selectedAppointment.time().format(DateTimeFormatter.ofPattern("HH:mm")), 15);
        JTextField reason = new JTextField(selectedAppointment.reason(), 23);
        JPanel form = formPanel("Doctor", doctor, "Date (yyyy-MM-dd)", date,
                "Time (HH:mm)", time, "Reason", reason);
        if (JOptionPane.showConfirmDialog(this, form, "Edit appointment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            LocalDate day = LocalDate.parse(date.getText().trim());
            LocalTime slot = LocalTime.parse(time.getText().trim());
            DatabaseModule.Doctor selectedDoctor = (DatabaseModule.Doctor) doctor.getSelectedItem();
            if (selectedDoctor == null) throw new IllegalArgumentException("Choose a doctor.");
            String updatedReason = reason.getText();
            runTask(() -> DatabaseModule.updateAppointment(admin, id, selectedDoctor, day, slot, updatedReason),
                    "Appointment updated.");
        } catch (java.time.format.DateTimeParseException e) {
            showError(new IllegalArgumentException("Enter the date as yyyy-MM-dd and time as HH:mm.", e));
        }
    }

    private void setSelectedAppointmentStatus(String status) {
        int row = selectedModelRow(appointmentTable);
        if (row < 0) return;
        long id = ((Number) appointmentModel.getValueAt(row, 0)).longValue();
        String current = String.valueOf(appointmentModel.getValueAt(row, 7));
        if (status.equals(current)) {
            showInfo("The appointment is already " + status.toLowerCase(java.util.Locale.ROOT) + ".");
            return;
        }
        boolean pastVisit = appointments.stream().filter(appointment -> appointment.id() == id).findFirst()
                .map(appointment -> LocalDateTime.of(appointment.date(), appointment.time()).isBefore(LocalDateTime.now()))
                .orElse(false);
        if ("CONFIRMED".equals(status) && !"PENDING".equals(current)
                || "COMPLETED".equals(status) && !"CONFIRMED".equals(current)
                    && !("PENDING".equals(current) && pastVisit)
                || "CANCELLED".equals(status) && !List.of("PENDING", "CONFIRMED").contains(current)) {
            showInfo("That action is not available for an appointment with status " + current + ".");
            return;
        }
        String patientName = String.valueOf(appointmentModel.getValueAt(row, 1));
        if (JOptionPane.showConfirmDialog(this, "Set " + patientName + "'s appointment to " + status + "?",
                "Confirm status update", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        runTask(() -> DatabaseModule.updateAppointmentStatus(admin, id, status), "Appointment status updated.");
    }

    private void viewSelectedAppointment() {
        int row = selectedModelRow(appointmentTable);
        if (row < 0) return;
        long id = ((Number) appointmentModel.getValueAt(row, 0)).longValue();
        appointments.stream().filter(appointment -> appointment.id() == id).findFirst().ifPresent(appointment ->
                JOptionPane.showMessageDialog(this,
                        "Appointment #" + appointment.id() + "\nPatient: " + appointment.patientName()
                                + "\nDoctor: " + appointment.doctorName() + "\nDepartment: " + appointment.specialty()
                                + "\nDate: " + appointment.date() + " at " + appointment.time().format(TIME)
                                + "\nStatus: " + appointment.status() + "\nReason: " + appointment.reason(),
                        "Appointment details", JOptionPane.INFORMATION_MESSAGE));
    }

    private void editMedicalRecord(DatabaseModule.MedicalRecord existing) {
        List<DatabaseModule.User> selectablePatients = patients.stream()
                .filter(DatabaseModule.User::active).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (existing != null) {
            patients.stream().filter(patient -> patient.id() == existing.patientId())
                    .filter(patient -> !patient.active()).findFirst().ifPresent(selectablePatients::add);
        }
        if (selectablePatients.isEmpty() || doctors.isEmpty()) {
            showInfo("Add a patient and doctor before creating a medical record.");
            return;
        }
        JComboBox<DatabaseModule.User> patient = new JComboBox<>(selectablePatients.toArray(DatabaseModule.User[]::new));
        patient.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof DatabaseModule.User user) setText(user.fullName() + " — " + user.email());
                return this;
            }
        });
        JComboBox<DatabaseModule.Doctor> doctor = new JComboBox<>(doctors.toArray(DatabaseModule.Doctor[]::new));
        JTextField subject = new JTextField(existing == null ? "" : existing.subject(), 24);
        JTextArea details = new JTextArea(existing == null ? "" : existing.details(), 8, 30);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        if (existing != null) {
            selectById(patient, existing.patientId());
            selectDoctorById(doctor, existing.doctorId());
        }
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(5, 5, 5, 5));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 0, 4, 0);
        addFormRow(form, gc, "Patient", patient);
        addFormRow(form, gc, "Doctor", doctor);
        addFormRow(form, gc, "Record subject", subject);
        addFormRow(form, gc, "Clinical notes", new JScrollPane(details));
        if (JOptionPane.showConfirmDialog(this, form, existing == null ? "Create medical record" : "Update medical record",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        DatabaseModule.User selectedPatient = (DatabaseModule.User) patient.getSelectedItem();
        DatabaseModule.Doctor selectedDoctor = (DatabaseModule.Doctor) doctor.getSelectedItem();
        if (selectedPatient == null || selectedDoctor == null) {
            showError(new IllegalArgumentException("Choose a patient and doctor."));
            return;
        }
        long id = existing == null ? 0 : existing.id();
        DatabaseModule.MedicalRecord record = new DatabaseModule.MedicalRecord(id, selectedPatient.id(),
                selectedPatient.fullName(), selectedDoctor.id(), selectedDoctor.fullName(),
                subject.getText(), details.getText(), null, null);
        runTask(() -> DatabaseModule.saveMedicalRecord(admin, record),
                existing == null ? "Medical record created." : "Medical record updated.");
    }

    private void editSelectedMedicalRecord() {
        int row = selectedModelRow(medicalTable);
        if (row < 0) return;
        long id = ((Number) medicalModel.getValueAt(row, 0)).longValue();
        records.stream().filter(record -> record.id() == id).findFirst().ifPresentOrElse(
                this::editMedicalRecord, () -> showInfo("Medical record was not found. Refresh and try again."));
    }

    private void showAppointments() {
        navigate("Appointments");
    }

    private void showTodayAppointments() {
        List<DatabaseModule.Appointment> today = appointments.stream()
                .filter(a -> a.date().equals(LocalDate.now()))
                .toList();
        if (today.isEmpty()) {
            showInfo("There are no appointments scheduled for today.");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (DatabaseModule.Appointment appointment : today) {
            text.append(appointment.time().format(TIME)).append(" · ").append(appointment.patientName())
                    .append(" with ").append(appointment.doctorName()).append(" — ").append(appointment.status()).append('\n');
        }
        JOptionPane.showMessageDialog(this, text.toString(), "Today's appointments", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showSettingsPassword() {
        JPasswordField current = new JPasswordField(22);
        JPasswordField fresh = new JPasswordField(22);
        JPasswordField repeated = new JPasswordField(22);
        JPanel form = formPanel("Current password", current, "New password (10–72 characters)", fresh,
                "Confirm new password", repeated);
        if (JOptionPane.showConfirmDialog(this, form, "Change administrator password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] oldValue = current.getPassword();
        char[] newValue = fresh.getPassword();
        char[] repeatedValue = repeated.getPassword();
        try {
            if (!java.util.Arrays.equals(newValue, repeatedValue))
                throw new IllegalArgumentException("The new passwords do not match.");
            DatabaseModule.changePassword(admin, oldValue, newValue);
            showSuccess("Administrator password changed.");
        } catch (Exception e) {
            showError(e);
        } finally {
            java.util.Arrays.fill(oldValue, '\0');
            java.util.Arrays.fill(newValue, '\0');
            java.util.Arrays.fill(repeatedValue, '\0');
        }
    }

    private void logout() {
        dispose();
        new LoginModule().setVisible(true);
    }

    private int selectedModelRow(JTable table) {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            showInfo("Select a row first.");
            return -1;
        }
        return table.convertRowIndexToModel(selected);
    }

    private static JPanel formPanel(Object... labelComponents) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 12, 8, 12));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(4, 0, 4, 0);
        for (int i = 0; i < labelComponents.length; i += 2) {
            Component comp = (Component) labelComponents[i + 1];
            if (comp instanceof JTextField tf) {
                UITheme.styleInputField(tf);
            } else if (comp instanceof JComboBox<?> cb) {
                UITheme.styleComboBox(cb);
            }
            addFormRow(form, gc, String.valueOf(labelComponents[i]), comp);
        }
        return form;
    }

    private static void addFormRow(JPanel form, GridBagConstraints gc, String label, Component field) {
        JLabel caption = new JLabel(label);
        caption.setFont(UITheme.FONT_SMALL_BOLD);
        caption.setForeground(UITheme.NAVY);
        gc.gridy++;
        form.add(caption, gc);
        gc.gridy++;
        form.add(field, gc);
    }

    private static JPanel settingLine(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(18, 0));
        row.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setForeground(MUTED);
        name.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel detail = new JLabel(value);
        detail.setForeground(INK);
        detail.setFont(new Font("Segoe UI", Font.BOLD, 12));
        row.add(name, BorderLayout.WEST);
        row.add(detail, BorderLayout.CENTER);
        return row;
    }

    private void selectById(JComboBox<DatabaseModule.User> box, long id) {
        for (int i = 0; i < box.getItemCount(); i++)
            if (box.getItemAt(i).id() == id) { box.setSelectedIndex(i); return; }
    }

    private void selectDoctorById(JComboBox<DatabaseModule.Doctor> box, long id) {
        for (int i = 0; i < box.getItemCount(); i++)
            if (box.getItemAt(i).id() == id) { box.setSelectedIndex(i); return; }
    }

    private void runTask(SqlAction action, String success) {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { action.run(); return null; }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    showSuccess(success);
                    refresh();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(e);
                } catch (ExecutionException e) {
                    showError(e);
                }
            }
        }.execute();
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "CarePoint", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "CarePoint", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(this, LoginModule.rootMessage(error), "CarePoint", JOptionPane.ERROR_MESSAGE);
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private final class NavigationIcon implements Icon {
        private final JButton owner;

        private NavigationIcon(JButton owner) {
            this.owner = owner;
        }

        @Override public int getIconWidth() { return 20; }
        @Override public int getIconHeight() { return 20; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(owner == activeNavigation || "Logout".equals(owner.getText())
                    ? Color.WHITE : new Color(174, 193, 224));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            String title = owner.getText();
            int left = x + 2, top = y + 2;
            switch (title) {
                case "Dashboard" -> {
                    g.drawLine(left + 1, top + 9, left + 8, top + 3);
                    g.drawLine(left + 8, top + 3, left + 15, top + 9);
                    g.drawRoundRect(left + 4, top + 8, 9, 8, 2, 2);
                    g.drawLine(left + 8, top + 11, left + 8, top + 16);
                }
                case "Patients" -> {
                    g.drawOval(left + 6, top + 2, 6, 6);
                    g.drawArc(left + 3, top + 8, 12, 9, 0, 180);
                    g.drawLine(left + 3, top + 12, left + 3, top + 15);
                    g.drawLine(left + 15, top + 12, left + 15, top + 15);
                }
                case "Doctors" -> {
                    g.drawRoundRect(left + 3, top + 3, 12, 12, 3, 3);
                    g.drawLine(left + 9, top + 5, left + 9, top + 13);
                    g.drawLine(left + 5, top + 9, left + 13, top + 9);
                }
                case "Appointments" -> {
                    g.drawRoundRect(left + 2, top + 4, 14, 12, 2, 2);
                    g.drawLine(left + 2, top + 8, left + 16, top + 8);
                    g.drawLine(left + 6, top + 2, left + 6, top + 6);
                    g.drawLine(left + 12, top + 2, left + 12, top + 6);
                }
                case "Medical Records" -> {
                    g.drawRoundRect(left + 4, top + 2, 11, 15, 2, 2);
                    g.drawLine(left + 6, top + 7, left + 13, top + 7);
                    g.drawLine(left + 6, top + 10, left + 13, top + 10);
                    g.drawLine(left + 6, top + 13, left + 11, top + 13);
                }
                case "Reports" -> {
                    g.drawLine(left + 3, top + 16, left + 16, top + 16);
                    g.drawLine(left + 4, top + 15, left + 4, top + 10);
                    g.drawLine(left + 8, top + 15, left + 8, top + 6);
                    g.drawLine(left + 12, top + 15, left + 12, top + 9);
                    g.drawLine(left + 16, top + 15, left + 16, top + 3);
                }
                case "Settings" -> {
                    g.drawOval(left + 5, top + 5, 8, 8);
                    g.drawOval(left + 8, top + 8, 2, 2);
                    g.drawLine(left + 9, top + 2, left + 9, top + 5);
                    g.drawLine(left + 9, top + 13, left + 9, top + 16);
                    g.drawLine(left + 2, top + 9, left + 5, top + 9);
                    g.drawLine(left + 13, top + 9, left + 16, top + 9);
                }
                case "Logout" -> {
                    g.drawRoundRect(left + 2, top + 2, 8, 15, 2, 2);
                    g.drawLine(left + 7, top + 9, left + 17, top + 9);
                    g.drawLine(left + 13, top + 5, left + 17, top + 9);
                    g.drawLine(left + 13, top + 13, left + 17, top + 9);
                }
                default -> { }
            }
            g.dispose();
        }
    }

    private static final class PlaceholderTable extends JTable {
        private final String emptyMessage;

        PlaceholderTable(DefaultTableModel model, String emptyMessage) {
            super(model);
            this.emptyMessage = emptyMessage;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getRowCount() != 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            String message = getModel().getRowCount() == 0 ? emptyMessage : "No rows match the current search";
            g.setFont(UITheme.FONT_BODY);
            g.setColor(MUTED);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(message, Math.max(12, (getWidth() - metrics.stringWidth(message)) / 2),
                    Math.max(metrics.getHeight() + 10, getHeight() / 2));
            g.dispose();
        }
    }

    private static final class RefreshIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            g.setColor(component.isEnabled() ? UITheme.NAVY : new Color(180, 195, 215));
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(x + 2, y + 2, 12, 12, 42, 280);
            g.drawLine(x + 12, y + 2, x + 14, y + 6);
            g.drawLine(x + 12, y + 2, x + 8, y + 3);
            g.dispose();
        }
    }

    private final class StatCard extends JPanel {
        private final String label;
        private final String detail;
        private final String symbol;
        private final Color start;
        private final Color end;
        private String value;

        StatCard(String label, String value, String detail, String symbol, Color start, Color end) {
            this.label = label;
            this.value = value;
            this.detail = detail;
            this.symbol = symbol;
            this.start = start;
            this.end = end;
            setOpaque(false);
            setPreferredSize(new Dimension(210, 112));
        }

        void setValue(String value) {
            this.value = value;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            g.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            g.setColor(new Color(255, 255, 255, 28));
            g.fillOval(getWidth() - 72, -28, 100, 100);
            g.setColor(new Color(255, 255, 255, 225));
            g.setFont(UITheme.FONT_TINY);
            g.drawString(label, 17, 24);
            g.setFont(new Font("Segoe UI", Font.BOLD, 28));
            g.drawString(value, 17, 63);
            g.setFont(UITheme.FONT_SMALL);
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(detail, 17, getHeight() - 16);
            g.setColor(new Color(255, 255, 255, 230));
            g.setFont(new Font("Segoe UI", Font.BOLD, 20));
            g.drawString(symbol, getWidth() - 44, 46);
            g.dispose();
        }
    }

    private final class OverviewChart extends JPanel {
        enum Kind { BARS, DONUT }
        private final String title;
        private final Kind kind;
        private List<DatabaseModule.MonthSummary> months = List.of();
        private List<DatabaseModule.SpecialtyCount> specialties = List.of();

        OverviewChart(String title, Kind kind) {
            this.title = title;
            this.kind = kind;
            setOpaque(false);
            setPreferredSize(new Dimension(230, 285));
            setToolTipText(kind == Kind.DONUT
                    ? "Distinct active patients with appointments, grouped by the booked doctor's current specialty. "
                    + "Patients may appear under more than one specialty."
                    : "Appointment counts by month and database status. Hover over a month for exact counts.");
        }

        void setMonths(List<DatabaseModule.MonthSummary> values) {
            months = List.copyOf(values);
            repaint();
        }

        void setSpecialties(List<DatabaseModule.SpecialtyCount> values) {
            specialties = List.copyOf(values);
            repaint();
        }

        @Override public String getToolTipText(MouseEvent event) {
            if (kind == Kind.BARS) {
                int left = 43, right = getWidth() - 12;
                if (months.isEmpty() || event.getX() < left || event.getX() > right) return null;
                int groupWidth = Math.max(1, (right - left) / months.size());
                int index = Math.min(months.size() - 1, (event.getX() - left) / groupWidth);
                DatabaseModule.MonthSummary month = months.get(index);
                String name = YearMonth.parse(month.month()).format(DateTimeFormatter.ofPattern("MMMM yyyy"));
                return "<html>" + name + "<br>Pending: " + month.pending() + "<br>Confirmed: "
                        + month.confirmed() + "<br>Completed: " + month.completed()
                        + "<br>Cancelled: " + month.cancelled() + "</html>";
            }
            List<DatabaseModule.SpecialtyCount> slices = displayedSpecialties();
            long total = slices.stream().mapToLong(DatabaseModule.SpecialtyCount::patients).sum();
            if (total == 0) return null;
            int diameter = Math.min(110, Math.max(90, Math.min(getHeight() - 100, getWidth() - 150)));
            int x = 18, y = 56;
            double dx = event.getX() - (x + diameter / 2.0);
            double dy = event.getY() - (y + diameter / 2.0);
            if (dx * dx + dy * dy <= diameter * diameter / 4.0) {
                double angle = Math.toDegrees(Math.atan2(-dy, dx));
                if (angle < 0) angle += 360;
                double cursor = 0;
                for (DatabaseModule.SpecialtyCount item : slices) {
                    double extent = 360.0 * item.patients() / total;
                    if (angle >= cursor && angle < cursor + extent)
                        return item.specialty() + ": " + item.patients()
                                + (title.startsWith("Active doctors") ? " doctors · " : " patients · ")
                                + percentage(item.patients(), total);
                    cursor += extent;
                }
            }
            return null;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(INK);
            g.setFont(UITheme.FONT_BODY_BOLD);
            g.drawString(title, 16, 25);
            if (kind == Kind.BARS) drawBars(g);
            else drawDonut(g);
            g.dispose();
        }

        private void drawBars(Graphics2D g) {
            int left = 43, top = 52, bottom = getHeight() - 56, right = getWidth() - 12;
            int chartHeight = Math.max(80, bottom - top);
            int count = Math.max(1, months.size());
            long max = months.stream().mapToLong(m -> m.pending() + m.confirmed() + m.completed() + m.cancelled()).max().orElse(0);
            if (max == 0) {
                drawEmptyChart(g, "No appointment history available");
                drawLegend(g, List.of("Pending", "Confirmed", "Completed", "Cancelled"),
                        List.of(CHART_COLORS[3], CHART_COLORS[0], CHART_COLORS[1], CHART_COLORS[4]), 12, getHeight() - 7);
                return;
            }
            long axisMax = Math.max(4, ((max + 3) / 4) * 4);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            g.setColor(MUTED);
            g.drawString("Visits", 6, top - 8);
            for (int line = 0; line <= 4; line++) {
                int y = top + chartHeight * line / 4;
                g.setColor(new Color(235, 239, 247));
                g.drawLine(left, y, right, y);
                g.setColor(MUTED);
                g.drawString(String.valueOf(axisMax * (4 - line) / 4), 5, y + 3);
            }
            int groupWidth = Math.max(1, (right - left) / count);
            int barWidth = Math.max(1, Math.min(7, (groupWidth - 6) / 4));
            for (int i = 0; i < months.size(); i++) {
                DatabaseModule.MonthSummary month = months.get(i);
                long[] values = {month.pending(), month.confirmed(), month.completed(), month.cancelled()};
                Color[] colors = {CHART_COLORS[3], CHART_COLORS[0], CHART_COLORS[1], CHART_COLORS[4]};
                int center = left + groupWidth * i + groupWidth / 2;
                for (int j = 0; j < values.length; j++) {
                    if (values[j] == 0) continue;
                    int height = (int) Math.round(chartHeight * values[j] / (double) axisMax);
                    int x = center - (2 * barWidth + 3) + j * (barWidth + 2);
                    g.setColor(colors[j]);
                    g.fillRoundRect(x, bottom - height, barWidth, Math.max(1, height), 5, 5);
                }
                g.setColor(MUTED);
                String label;
                try { label = YearMonth.parse(month.month()).format(DateTimeFormatter.ofPattern("MMM")); }
                catch (RuntimeException e) { label = month.month(); }
                g.drawString(label, center - g.getFontMetrics().stringWidth(label) / 2, getHeight() - 34);
            }
            drawLegend(g, List.of("Pending", "Confirmed"),
                    List.of(CHART_COLORS[3], CHART_COLORS[0]), 12, getHeight() - 20);
            drawLegend(g, List.of("Completed", "Cancelled"),
                    List.of(CHART_COLORS[1], CHART_COLORS[4]), 12, getHeight() - 6);
        }

        private void drawDonut(Graphics2D g) {
            int diameter = Math.min(110, Math.max(90, Math.min(getHeight() - 100, getWidth() - 150)));
            int x = 18, y = 56;
            List<DatabaseModule.SpecialtyCount> slices = displayedSpecialties();
            long total = slices.stream().mapToLong(DatabaseModule.SpecialtyCount::patients).sum();
            if (total == 0) {
                drawEmptyChart(g, "No specialty-linked patient visits");
                return;
            }
            if (total > 0) {
                int angle = 0;
                for (int i = 0; i < slices.size(); i++) {
                    DatabaseModule.SpecialtyCount item = slices.get(i);
                    int extent = (int) Math.round(360.0 * item.patients() / total);
                    g.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                    g.fillArc(x, y, diameter, diameter, angle, extent);
                    angle += extent;
                }
                g.setColor(Color.WHITE);
                int inset = diameter / 4;
                g.fillOval(x + inset, y + inset, diameter - inset * 2, diameter - inset * 2);
                g.setColor(INK);
                g.setFont(new Font("Segoe UI", Font.BOLD, 20));
                String count = String.valueOf(total);
                g.drawString(count, x + (diameter - g.getFontMetrics().stringWidth(count)) / 2,
                        y + diameter / 2 + 7);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
                String caption = title.startsWith("Active doctors") ? "doctor totals" : "patient totals";
                g.setColor(MUTED);
                g.drawString(caption, x + (diameter - g.getFontMetrics().stringWidth(caption)) / 2,
                        y + diameter / 2 + 23);
                int legendX = x + diameter + 14;
                for (int i = 0; i < slices.size(); i++) {
                    DatabaseModule.SpecialtyCount item = slices.get(i);
                    int ly = y + 13 + i * 25;
                    g.setColor(CHART_COLORS[i % CHART_COLORS.length]);
                    g.fillRoundRect(legendX, ly - 7, 9, 9, 3, 3);
                    g.setColor(INK);
                    g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
                    String label = item.specialty();
                    if (label.length() > 9) label = label.substring(0, 8) + "…";
                    g.drawString(label + " " + item.patients() + " · " + percentage(item.patients(), total),
                            legendX + 14, ly);
                }
            }
        }

        private List<DatabaseModule.SpecialtyCount> displayedSpecialties() {
            if (specialties.size() <= 6) return specialties;
            List<DatabaseModule.SpecialtyCount> visible = new ArrayList<>(specialties.subList(0, 5));
            long other = specialties.subList(5, specialties.size()).stream()
                    .mapToLong(DatabaseModule.SpecialtyCount::patients).sum();
            visible.add(new DatabaseModule.SpecialtyCount("Other", other));
            return visible;
        }

        private void drawLegend(Graphics2D g, List<String> labels, List<Color> colors, int x, int y) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            int cursor = x;
            for (int i = 0; i < labels.size(); i++) {
                g.setColor(colors.get(i));
                g.fillRoundRect(cursor, y - 7, 8, 8, 3, 3);
                g.setColor(MUTED);
                g.drawString(labels.get(i), cursor + 12, y);
                cursor += 12 + g.getFontMetrics().stringWidth(labels.get(i)) + 7;
            }
        }

        private void drawEmptyChart(Graphics2D g, String message) {
            g.setColor(MUTED);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.drawString(message, Math.max(12, (getWidth() - g.getFontMetrics().stringWidth(message)) / 2),
                    getHeight() / 2);
        }
    }

    private static final class HealthcareIllustration extends JPanel {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            UITheme.applyQualityHints(g);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(255, 255, 255, 48));
            g.fillOval(w / 2 - 55, 4, 106, 106);
            g.setColor(new Color(248, 202, 170));
            g.fillOval(w / 2 - 17, 15, 35, 38);
            g.setColor(new Color(43, 39, 54));
            g.fillArc(w / 2 - 19, 9, 39, 28, 0, 180);
            g.setColor(new Color(249, 251, 255));
            int cx = w / 2;
            int[] xs = {cx - 38, cx - 25, cx, cx + 25, cx + 39, cx + 43, cx - 43};
            int[] ys = {h - 7, 62, 54, 62, h - 7, h, h};
            g.fillPolygon(xs, ys, xs.length);
            g.setColor(new Color(43, 187, 170));
            g.fillRoundRect(cx - 4, 58, 8, 30, 4, 4);
            g.setColor(new Color(247, 193, 96));
            g.fillOval(w - 32, 12, 9, 9);
            g.setColor(new Color(141, 205, 255));
            g.fillOval(18, 45, 7, 7);
            g.dispose();
        }
    }

    @FunctionalInterface
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update(javax.swing.event.DocumentEvent event);
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { update(e); }
    }
}
