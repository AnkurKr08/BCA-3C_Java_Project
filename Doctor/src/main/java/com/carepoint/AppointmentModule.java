package com.carepoint;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;

public final class AppointmentModule extends JDialog {
    private final DatabaseModule.User patient;
    private final JComboBox<DatabaseModule.Doctor> doctorBox = new JComboBox<>();
    private final JSpinner datePicker = new JSpinner(new SpinnerDateModel(
            Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
            Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()), null, Calendar.DAY_OF_MONTH));
    private final JComboBox<LocalTime> slotBox = new JComboBox<>();
    private final JTextArea reasonField = new JTextArea(4, 24);
    private final JLabel feedback = new JLabel("Choose a doctor and date, then check available times.");
    private final JButton checkSlots = new JButton("Check times");
    private final JButton confirm = new JButton("Review & book");

    public AppointmentModule(JFrame owner, DatabaseModule.User patient, Long selectedDoctor) {
        super(owner, "Book an appointment", ModalityType.APPLICATION_MODAL);
        this.patient = patient;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(owner);
        setContentPane(buildForm());
        setSize(600, 690);
        setMinimumSize(new Dimension(560, 600));
        setLocationRelativeTo(owner);
        checkSlots.addActionListener(e -> loadSlots());
        confirm.addActionListener(e -> reviewAndBook());
        loadDoctors(selectedDoctor);
    }

    private JPanel buildForm() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(UITheme.BG);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));
        JLabel heading = new JLabel("Book your visit");
        heading.setFont(UITheme.FONT_TITLE);
        heading.setForeground(UITheme.NAVY);
        JLabel caption = new JLabel("Appointments are available in 30-minute slots, " + DatabaseModule.clinicHours() + ".");
        caption.setFont(UITheme.FONT_BODY);
        caption.setForeground(UITheme.TEXT_MUTED);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(heading); top.add(Box.createVerticalStrut(5)); top.add(caption);
        root.add(top, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(UITheme.createCardBorder());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(5, 0, 4, 0);
        UITheme.styleComboBox(doctorBox);
        addRow(form, gc, "Doctor", doctorBox);
        datePicker.setEditor(new JSpinner.DateEditor(datePicker, "yyyy-MM-dd"));
        UITheme.styleSpinner(datePicker);
        datePicker.setToolTipText("Select a date; past dates are unavailable.");
        addRow(form, gc, "Appointment date", datePicker);
        UITheme.styleComboBox(slotBox);
        UITheme.styleSecondaryButton(checkSlots);
        JPanel slotLine = new JPanel(new BorderLayout(10, 0));
        slotLine.setOpaque(false);
        slotLine.add(slotBox, BorderLayout.CENTER);
        slotLine.add(checkSlots, BorderLayout.EAST);
        addRow(form, gc, "Available time", slotLine);
        reasonField.setFont(UITheme.FONT_BODY);
        reasonField.setForeground(UITheme.TEXT_MAIN);
        reasonField.setLineWrap(true);
        reasonField.setWrapStyleWord(true);
        reasonField.setBorder(BorderFactory.createCompoundBorder(
                new UITheme.RoundedLineBorder(UITheme.BORDER, 8, 1),
                new EmptyBorder(8, 10, 8, 10)));
        JScrollPane reasonScroll = new JScrollPane(reasonField);
        reasonScroll.setBorder(null);
        addRow(form, gc, "Reason for visit (up to 500 characters)", reasonScroll);
        feedback.setFont(UITheme.FONT_SMALL);
        feedback.setForeground(UITheme.TEXT_MUTED);
        gc.gridy++; gc.weighty = 1; gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(8, 0, 4, 0);
        form.add(feedback, gc);
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton close = new JButton("Close");
        UITheme.styleSecondaryButton(close);
        close.addActionListener(e -> dispose());
        UITheme.stylePrimaryButton(confirm);
        actions.add(close);
        actions.add(confirm);
        root.add(actions, BorderLayout.SOUTH);
        return root;
    }

    private static void addRow(JPanel form, GridBagConstraints gc, String label, Component field) {
        gc.gridy++;
        JLabel heading = new JLabel(label);
        heading.setFont(UITheme.FONT_SMALL_BOLD);
        heading.setForeground(UITheme.NAVY);
        form.add(heading, gc);
        gc.gridy++;
        form.add(field, gc);
        gc.gridy++;
        form.add(Box.createVerticalStrut(4), gc);
    }

    private void loadDoctors(Long selectedId) {
        setBusy(true, "Loading doctors…");
        new SwingWorker<List<DatabaseModule.Doctor>, Void>() {
            @Override protected List<DatabaseModule.Doctor> doInBackground() throws Exception { return DatabaseModule.doctors(false); }
            @Override protected void done() {
                setBusy(false, "Choose a doctor and date, then check available times.");
                try {
                    for (DatabaseModule.Doctor doctor : get()) doctorBox.addItem(doctor);
                    if (selectedId != null)
                        for (int i = 0; i < doctorBox.getItemCount(); i++)
                            if (doctorBox.getItemAt(i).id() == selectedId) doctorBox.setSelectedIndex(i);
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void loadSlots() {
        DatabaseModule.Doctor doctor = (DatabaseModule.Doctor) doctorBox.getSelectedItem();
        LocalDate date;
        try {
            datePicker.commitEdit();
            date = ((Date) datePicker.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (ParseException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Select a valid appointment date.", "Invalid date", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true, "Checking availability…");
        new SwingWorker<List<LocalTime>, Void>() {
            @Override protected List<LocalTime> doInBackground() throws Exception { return DatabaseModule.availableSlots(doctor, date); }
            @Override protected void done() {
                setBusy(false, "");
                slotBox.removeAllItems();
                try {
                    List<LocalTime> available = get();
                    for (LocalTime time : available) slotBox.addItem(time);
                    feedback.setText(available.isEmpty() ? "No appointment times are available on this date." : available.size() + " times available.");
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void reviewAndBook() {
        DatabaseModule.Doctor doctor = (DatabaseModule.Doctor) doctorBox.getSelectedItem();
        LocalDate date;
        try {
            datePicker.commitEdit();
            date = ((Date) datePicker.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (ParseException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, "Select a valid appointment date.", "Invalid date", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LocalTime time = (LocalTime) slotBox.getSelectedItem();
        String reason = reasonField.getText().trim();
        if (doctor == null || time == null) {
            JOptionPane.showMessageDialog(this, "Choose an available doctor, date, and time.", "Appointment details needed", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String details = "Doctor: " + doctor.fullName() + "\nSpecialty: " + doctor.specialty()
                + "\nDate: " + date + "\nTime: " + time + "\nConsultation fee: " + doctor.fee()
                + "\nReason: " + reason;
        if (JOptionPane.showConfirmDialog(this, details, "Review appointment",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        setBusy(true, "Confirming your appointment…");
        new SwingWorker<Long, Void>() {
            @Override protected Long doInBackground() throws Exception { return DatabaseModule.book(patient, doctor, date, time, reason); }
            @Override protected void done() {
                setBusy(false, "");
                try {
                    long id = get();
                    JOptionPane.showMessageDialog(AppointmentModule.this,
                            "Your appointment is confirmed.\nConfirmation number: " + id,
                            "Booking confirmed", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        checkSlots.setEnabled(!busy);
        confirm.setEnabled(!busy);
        doctorBox.setEnabled(!busy);
        datePicker.setEnabled(!busy);
        slotBox.setEnabled(!busy);
        feedback.setText(message);
    }

    private void showError(Exception error) {
        JOptionPane.showMessageDialog(this, LoginModule.rootMessage(error), "Unable to complete booking",
                JOptionPane.ERROR_MESSAGE);
    }
}
