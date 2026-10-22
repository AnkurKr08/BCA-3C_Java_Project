package com.carepoint;

import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class DatabaseModule {
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private DatabaseModule() {}

    public record User(long id, String fullName, String email, String phone, String role,
                       boolean mustChangePassword, boolean active, LocalDateTime createdAt) {}
    public record Doctor(long id, String fullName, String specialty, BigDecimal fee, String availability, boolean active) {
        @Override public String toString() { return fullName + "  ·  " + specialty; }
    }
    public record Appointment(long id, long patientId, String patientName, long doctorId, String doctorName,
                              String specialty, LocalDate date, LocalTime time, String reason, String status, BigDecimal fee) {}
    public record Stats(long patients, long doctors, long totalAppointments, long pending, long confirmed,
                        long completed, long cancelled, long today, long upcoming) {}
    public record MonthSummary(String month, long pending, long confirmed, long completed, long cancelled) {}
    public record SpecialtyCount(String specialty, long patients) {}
    public record Activity(String type, String description, LocalDateTime createdAt) {}
    public record MedicalRecord(long id, long patientId, String patientName, long doctorId, String doctorName,
                                String subject, String details, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public static LocalTime clinicOpen() { return clinicTime("CLINIC_OPEN", LocalTime.of(9, 0)); }
    public static LocalTime clinicClose() { return clinicTime("CLINIC_CLOSE", LocalTime.of(17, 0)); }
    public static String clinicHours() {
        LocalTime open = clinicOpen();
        LocalTime close = clinicClose();
        if (!open.isBefore(close)) throw new IllegalArgumentException("CLINIC_OPEN must be earlier than CLINIC_CLOSE.");
        return open.format(DateTimeFormatter.ofPattern("h:mm a")) + "-" + close.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    private static LocalTime clinicTime(String key, LocalTime fallback) {
        String value = getenvTrimmed(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            LocalTime time = LocalTime.parse(value);
            if (time.getSecond() != 0 || time.getNano() != 0 || time.getMinute() % 30 != 0)
                throw new IllegalArgumentException(key + " must be a 30-minute interval in HH:mm format.");
            return time;
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(key + " must use HH:mm format, for example 09:00.", e);
        }
    }

    private static String getenvTrimmed(String key) {
        String value = System.getenv(key);
        return value == null ? null : value.trim();
    }

    public static Connection connect() throws SQLException {
        return DatabaseConnection.connect();
    }

    public static void initialize() throws SQLException {
        try (Connection connection = connect()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeQuery("SELECT id,full_name,email,phone,password_hash,role,must_change_password,active,created_at FROM users WHERE 1=0").close();
                statement.executeQuery("SELECT id,full_name,specialty,consultation_fee,availability,active FROM doctors WHERE 1=0").close();
                statement.executeQuery("SELECT id,patient_id,doctor_id,appointment_date,appointment_time,reason,status,active_booking_date,active_booking_time FROM appointments WHERE 1=0").close();
                statement.executeQuery("SELECT id,appointment_id,changed_by,previous_status,new_status,changed_at FROM appointment_status_history WHERE 1=0").close();
                statement.executeQuery("SELECT id,patient_id,doctor_id,subject,details,created_by,created_at,updated_at FROM medical_records WHERE 1=0").close();
            }
            requireEnumValue(connection, "appointments", "status", "PENDING");
            requireEnumValue(connection, "appointments", "status", "CONFIRMED");
            requireEnumValue(connection, "appointment_status_history", "previous_status", "PENDING");
            requireEnumValue(connection, "appointment_status_history", "new_status", "CONFIRMED");
            bootstrapAdmin(connection);
        } catch (SQLException e) {
            if ("42S02".equals(e.getSQLState()) || "42S22".equals(e.getSQLState())) {
                throw new SQLException("The CarePoint database is missing a required table or column. "
                        + "Run database/schema.sql; for an existing installation, also run "
                        + "database/migrate-appointment-statuses.sql. Database detail: " + e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
            }
            throw e;
        }
    }

    private static void requireEnumValue(Connection connection, String table, String column, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COLUMN_TYPE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getString(1).contains("'" + value + "'"))
                    throw new SQLException("CarePoint appointment statuses are not up to date. Run "
                            + "database/migrate-appointment-statuses.sql before using the application.");
            }
        }
    }

    private static void bootstrapAdmin(Connection c) throws SQLException {
        try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role='ADMIN'");
             ResultSet rs = count.executeQuery()) {
            rs.next();
            if (rs.getLong(1) > 0) return;
        }
        String email = getenvTrimmed("CAREPOINT_ADMIN_EMAIL");
        String password = getenvTrimmed("CAREPOINT_ADMIN_PASSWORD");
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            System.err.println("CarePoint: no administrator exists. Set CAREPOINT_ADMIN_EMAIL and CAREPOINT_ADMIN_PASSWORD before startup to bootstrap one.");
            return;
        }
        validateIdentity("Administrator", email, "+10000000000", password);
        try (PreparedStatement insert = c.prepareStatement("""
                INSERT INTO users(full_name,email,phone,password_hash,role,must_change_password)
                VALUES('CarePoint Administrator',?,?,?,'ADMIN',TRUE)
                """)) {
            insert.setString(1, normalizeEmail(email));
            insert.setString(2, "+10000000000");
            insert.setString(3, BCrypt.hashpw(password, BCrypt.gensalt(12)));
            insert.executeUpdate();
        }
    }

    public static User authenticate(String email, char[] password) throws SQLException {
        if (email == null || email.length() > 254 || !EMAIL.matcher(email.trim()).matches()
                || password == null || password.length == 0
                || new String(password).getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Enter a valid email address and password.");
        }
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT id,full_name,email,phone,password_hash,role,must_change_password,active,created_at FROM users WHERE email=? AND active=TRUE")) {
            ps.setString(1, normalizeEmail(email));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !BCrypt.checkpw(new String(password), rs.getString("password_hash"))) {
                    throw new IllegalArgumentException("The email or password you entered is incorrect.");
                }
                return new User(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("phone"), rs.getString("role"), rs.getBoolean("must_change_password"),
                        rs.getBoolean("active"), rs.getTimestamp("created_at").toLocalDateTime());
            }
        }
    }

    public static void registerPatient(String name, String email, String phone, char[] password) throws SQLException {
        if (password == null) throw new IllegalArgumentException("Enter a password.");
        validateIdentity(name, email, phone, new String(password));
        initialize();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?, 'PATIENT')")) {
            ps.setString(1, name.trim());
            ps.setString(2, normalizeEmail(email));
            ps.setString(3, phone.trim());
            ps.setString(4, BCrypt.hashpw(new String(password), BCrypt.gensalt(12)));
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
    }

    private static void validateIdentity(String name, String email, String phone, String password) {
        if (name == null || name.trim().length() < 2 || name.trim().length() > 120) throw new IllegalArgumentException("Enter a name between 2 and 120 characters.");
        if (email == null || email.length() > 254 || !EMAIL.matcher(email.trim()).matches()) throw new IllegalArgumentException("Enter a valid email address.");
        if (phone == null || !phone.trim().matches("[+0-9() .-]{7,30}")) throw new IllegalArgumentException("Enter a valid phone number.");
        validatePassword(password);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Password must contain at least 10 characters and fit within BCrypt's 72-byte limit.");
    }

    public static void changePassword(User actor, char[] current, char[] replacement) throws SQLException {
        if (actor == null || current == null || replacement == null) {
            throw new IllegalArgumentException("Enter the current and new password.");
        }
        if (current.length == 0) throw new IllegalArgumentException("Enter your current password.");
        String currentPassword = new String(current);
        String newPassword = new String(replacement);
        validatePassword(newPassword);
        try (Connection c = connect();
             PreparedStatement select = c.prepareStatement("SELECT password_hash FROM users WHERE id=? AND active=TRUE")) {
            select.setLong(1, actor.id());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next() || !BCrypt.checkpw(currentPassword, rs.getString(1)))
                    throw new IllegalArgumentException("Current password is incorrect.");
            }
            try (PreparedStatement update = c.prepareStatement("UPDATE users SET password_hash=?,must_change_password=FALSE WHERE id=?")) {
                update.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt(12)));
                update.setLong(2, actor.id());
                update.executeUpdate();
            }
        }
    }

    private static void requireAdmin(User actor) throws SQLException {
        if (actor == null || !"ADMIN".equals(actor.role())) throw new SecurityException("Administrator access is required.");
        requireActiveRole(actor, "ADMIN");
    }

    private static void requirePatient(User actor) throws SQLException {
        if (actor == null || !"PATIENT".equals(actor.role())) throw new SecurityException("Patient access is required.");
        requireActiveRole(actor, "PATIENT");
    }

    private static void requireActiveRole(User actor, String role) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM users WHERE id=? AND role=? AND active=TRUE")) {
            ps.setLong(1, actor.id());
            ps.setString(2, role);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SecurityException("The signed-in account is not authorized for this action.");
            }
        }
    }

    public static List<Doctor> doctors(boolean includeInactive) throws SQLException {
        List<Doctor> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id,full_name,specialty,consultation_fee,availability,active FROM doctors " +
                        (includeInactive ? "" : "WHERE active=TRUE ") + "ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Doctor(rs.getLong(1), rs.getString(2), rs.getString(3),
                    rs.getBigDecimal(4), rs.getString(5), rs.getBoolean(6)));
        }
        return rows;
    }

    public static List<String> specialties() throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT DISTINCT specialty FROM doctors WHERE active=TRUE ORDER BY specialty");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(rs.getString(1));
        }
        return rows;
    }

    public static List<LocalTime> availableSlots(Doctor doctor, LocalDate date) throws SQLException {
        if (doctor == null || !doctor.active() || date == null || date.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Select an active doctor and a future date.");
        LocalTime opening = clinicOpen();
        LocalTime closing = clinicClose();
        if (!opening.isBefore(closing)) throw new IllegalArgumentException("CLINIC_OPEN must be earlier than CLINIC_CLOSE.");
        List<LocalTime> slots = new ArrayList<>();
        String sql = "SELECT appointment_time FROM appointments WHERE doctor_id=? AND appointment_date=? " +
                "AND status IN ('PENDING','CONFIRMED')";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, doctor.id());
            ps.setDate(2, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                List<LocalTime> booked = new ArrayList<>();
                while (rs.next()) booked.add(rs.getTime(1).toLocalTime());
                for (LocalTime t = opening; t.isBefore(closing); t = t.plusMinutes(30))
                    if (!booked.contains(t) && (date.isAfter(LocalDate.now()) || t.isAfter(LocalTime.now()))) slots.add(t);
            }
        }
        return slots;
    }

    public static long book(User actor, Doctor doctor, LocalDate date, LocalTime time, String reason) throws SQLException {
        requirePatient(actor);
        return insertAppointment(actor, actor.id(), doctor, date, time, reason);
    }

    public static long bookForPatient(User actor, long patientId, Doctor doctor, LocalDate date,
                                      LocalTime time, String reason) throws SQLException {
        requireAdmin(actor);
        return insertAppointment(actor, patientId, doctor, date, time, reason);
    }

    private static long insertAppointment(User actor, long patientId, Doctor doctor, LocalDate date,
                                          LocalTime time, String reason) throws SQLException {
        validateSlot(doctor, date, time, reason);
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT id FROM users WHERE id=? AND role='PATIENT' AND active=TRUE FOR UPDATE")) {
                    check.setLong(1, patientId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) throw new SecurityException("The patient account is inactive or no longer available.");
                    }
                }
                try (PreparedStatement check = c.prepareStatement("SELECT active FROM doctors WHERE id=? FOR UPDATE")) {
                    check.setLong(1, doctor.id());
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next() || !rs.getBoolean(1)) throw new IllegalArgumentException("This doctor is no longer available.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,reason,status)
                        VALUES(?,?,?,?,?,'PENDING')
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, patientId);
                    ps.setLong(2, doctor.id());
                    ps.setDate(3, java.sql.Date.valueOf(date));
                    ps.setTime(4, Time.valueOf(time));
                    ps.setString(5, reason.trim());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Booking succeeded without an appointment identifier.");
                        long id = keys.getLong(1);
                        c.commit();
                        return id;
                    }
                }
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLIntegrityConstraintViolationException || (e instanceof SQLException sql && "23000".equals(sql.getSQLState())))
                    throw new IllegalArgumentException("That appointment slot was just booked. Please choose another time.");
                throw e;
            }
        }
    }

    private static void validateSlot(Doctor doctor, LocalDate date, LocalTime time, String reason) {
        if (doctor == null || !doctor.active()) throw new IllegalArgumentException("Choose an active doctor.");
        if (date == null || date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Appointments must be booked for today or a future date.");
        LocalTime opening = clinicOpen();
        LocalTime closing = clinicClose();
        if (!opening.isBefore(closing)) throw new IllegalArgumentException("CLINIC_OPEN must be earlier than CLINIC_CLOSE.");
        if (time == null || time.isBefore(opening) || !time.isBefore(closing)
                || time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0)
            throw new IllegalArgumentException("Choose an available 30-minute clinic slot between "
                    + clinicHours() + ".");
        if (date.equals(LocalDate.now()) && !time.isAfter(LocalTime.now())) throw new IllegalArgumentException("Choose a future appointment time.");
        if (reason == null || reason.trim().isEmpty() || reason.trim().length() > 500)
            throw new IllegalArgumentException("Enter a visit reason of up to 500 characters.");
    }

    public static List<Appointment> patientAppointments(User actor, boolean history) throws SQLException {
        requirePatient(actor);
        String sql = """
                SELECT a.id,a.patient_id,u.full_name,a.doctor_id,d.full_name,d.specialty,a.appointment_date,
                       a.appointment_time,a.reason,a.status,d.consultation_fee
                FROM appointments a JOIN users u ON u.id=a.patient_id JOIN doctors d ON d.id=a.doctor_id
                WHERE a.patient_id=? AND
                """ + (history
                ? "(a.status NOT IN ('PENDING','CONFIRMED') OR TIMESTAMP(a.appointment_date,a.appointment_time)<CURRENT_TIMESTAMP) "
                : "a.status IN ('PENDING','CONFIRMED') AND TIMESTAMP(a.appointment_date,a.appointment_time)>=CURRENT_TIMESTAMP ") +
                "ORDER BY a.appointment_date,a.appointment_time";
        return queryAppointments(sql, actor.id(), false);
    }

    public static List<Appointment> adminAppointments(User actor) throws SQLException {
        requireAdmin(actor);
        return queryAppointments("""
                SELECT a.id,a.patient_id,u.full_name,a.doctor_id,d.full_name,d.specialty,a.appointment_date,
                       a.appointment_time,a.reason,a.status,d.consultation_fee
                FROM appointments a JOIN users u ON u.id=a.patient_id JOIN doctors d ON d.id=a.doctor_id
                ORDER BY a.appointment_date DESC,a.appointment_time DESC
                """, 0, true);
    }

    private static List<Appointment> queryAppointments(String sql, long patientId, boolean admin) throws SQLException {
        List<Appointment> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!admin) ps.setLong(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Appointment(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6), rs.getDate(7).toLocalDate(),
                        rs.getTime(8).toLocalTime(), rs.getString(9), rs.getString(10), rs.getBigDecimal(11)));
            }
        }
        return rows;
    }

    public static void cancel(User actor, long appointmentId) throws SQLException {
        if (actor == null || !("PATIENT".equals(actor.role()) || "ADMIN".equals(actor.role())))
            throw new SecurityException("You are not authorized to cancel appointments.");
        requireActiveRole(actor, actor.role());
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String oldStatus;
                try (PreparedStatement select = c.prepareStatement("""
                        SELECT status FROM appointments
                        WHERE id=? AND TIMESTAMP(appointment_date,appointment_time)>CURRENT_TIMESTAMP
                          AND EXISTS(SELECT 1 FROM users actor WHERE actor.id=? AND actor.role=? AND actor.active=TRUE)
                          AND (?<>'PATIENT' OR patient_id=?)
                        FOR UPDATE
                        """)) {
                    select.setLong(1, appointmentId);
                    select.setLong(2, actor.id());
                    select.setString(3, actor.role());
                    select.setString(4, actor.role());
                    select.setLong(5, actor.id());
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("This appointment cannot be canceled or no longer exists.");
                        oldStatus = rs.getString(1);
                    }
                }
                if (!List.of("PENDING", "CONFIRMED").contains(oldStatus))
                    throw new IllegalArgumentException("Only a pending or confirmed appointment can be canceled.");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE appointments SET status='CANCELLED' WHERE id=? AND status=?")) {
                    update.setLong(1, appointmentId);
                    update.setString(2, oldStatus);
                    if (update.executeUpdate() != 1) throw new IllegalArgumentException("Appointment status changed; refresh and try again.");
                }
                recordStatusChange(c, actor.id(), appointmentId, oldStatus, "CANCELLED");
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void recordStatusChange(Connection connection, long actorId, long appointmentId,
                                           String previousStatus, String newStatus) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO appointment_status_history(appointment_id,changed_by,previous_status,new_status)
                VALUES(?,?,?,?)
                """)) {
            insert.setLong(1, appointmentId);
            insert.setLong(2, actorId);
            insert.setString(3, previousStatus);
            insert.setString(4, newStatus);
            insert.executeUpdate();
        }
    }

    public static List<User> patients(User actor) throws SQLException {
        requireAdmin(actor);
        List<User> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT id,full_name,email,phone,role,must_change_password,active,created_at FROM users WHERE role='PATIENT' ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new User(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getBoolean(6), rs.getBoolean(7), rs.getTimestamp(8).toLocalDateTime()));
        }
        return rows;
    }

    public static void updatePatient(User actor, long patientId, String name, String email, String phone) throws SQLException {
        requireAdmin(actor);
        if (name == null || name.trim().length() < 2 || name.trim().length() > 120)
            throw new IllegalArgumentException("Enter a name between 2 and 120 characters.");
        if (email == null || email.length() > 254 || !EMAIL.matcher(email.trim()).matches())
            throw new IllegalArgumentException("Enter a valid email address.");
        if (phone == null || !phone.trim().matches("[+0-9() .-]{7,30}"))
            throw new IllegalArgumentException("Enter a valid phone number.");
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET full_name=?,email=?,phone=? WHERE id=? AND role='PATIENT'")) {
            ps.setString(1, name.trim());
            ps.setString(2, normalizeEmail(email));
            ps.setString(3, phone.trim());
            ps.setLong(4, patientId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Patient record was not found.");
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("Another account already uses that email address.");
        }
    }

    public static void deactivatePatient(User actor, long patientId) throws SQLException {
        requireAdmin(actor);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET active=FALSE WHERE id=? AND role='PATIENT' AND active=TRUE")) {
            ps.setLong(1, patientId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Patient record was not found or is already inactive.");
        }
    }

    public static void setPatientActive(User actor, long patientId, boolean active) throws SQLException {
        requireAdmin(actor);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE users SET active=? WHERE id=? AND role='PATIENT' AND active<>?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, patientId);
            ps.setBoolean(3, active);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Patient record was not found or already has that status.");
        }
    }

    public static void createPatient(User actor, String name, String email, String phone, char[] password) throws SQLException {
        requireAdmin(actor);
        if (password == null) throw new IllegalArgumentException("Enter a password.");
        validateIdentity(name, email, phone, new String(password));
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?, 'PATIENT')")) {
            ps.setString(1, name.trim());
            ps.setString(2, normalizeEmail(email));
            ps.setString(3, phone.trim());
            ps.setString(4, BCrypt.hashpw(new String(password), BCrypt.gensalt(12)));
            ps.executeUpdate();
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
    }

    public static Stats stats(User actor) throws SQLException {
        requireAdmin(actor);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT (SELECT COUNT(*) FROM users WHERE role='PATIENT'),
                       (SELECT COUNT(*) FROM doctors WHERE active=TRUE),
                       (SELECT COUNT(*) FROM appointments),
                       (SELECT COUNT(*) FROM appointments WHERE status='PENDING'),
                       (SELECT COUNT(*) FROM appointments WHERE status='CONFIRMED'),
                       (SELECT COUNT(*) FROM appointments WHERE status='COMPLETED'),
                       (SELECT COUNT(*) FROM appointments WHERE status='CANCELLED'),
                       (SELECT COUNT(*) FROM appointments WHERE appointment_date=CURRENT_DATE),
                       (SELECT COUNT(*) FROM appointments WHERE status IN ('PENDING','CONFIRMED')
                            AND TIMESTAMP(appointment_date,appointment_time)>=CURRENT_TIMESTAMP)
                """); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new Stats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                    rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9));
        }
    }

    public static List<MonthSummary> appointmentOverview(User actor) throws SQLException {
        requireAdmin(actor);
        Map<String, MonthSummary> byMonth = new HashMap<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT DATE_FORMAT(appointment_date, '%Y-%m') AS month_key,
                       SUM(status='PENDING') AS pending,
                       SUM(status='CONFIRMED') AS confirmed,
                       SUM(status='COMPLETED') AS completed,
                       SUM(status='CANCELLED') AS cancelled
                FROM appointments
                WHERE appointment_date >= DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 11 MONTH)
                  AND appointment_date < DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 MONTH)
                GROUP BY month_key ORDER BY month_key
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                MonthSummary summary = new MonthSummary(rs.getString(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5));
                byMonth.put(summary.month(), summary);
            }
        }
        List<MonthSummary> rows = new ArrayList<>();
        YearMonth firstMonth = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String month = firstMonth.plusMonths(i).toString();
            rows.add(byMonth.getOrDefault(month, new MonthSummary(month, 0, 0, 0, 0)));
        }
        return rows;
    }

    public static List<SpecialtyCount> patientCountsBySpecialty(User actor) throws SQLException {
        requireAdmin(actor);
        List<SpecialtyCount> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT d.specialty, COUNT(DISTINCT a.patient_id) AS patients
                FROM appointments a JOIN doctors d ON d.id=a.doctor_id
                JOIN users u ON u.id=a.patient_id
                WHERE u.role='PATIENT' AND u.active=TRUE
                GROUP BY d.specialty ORDER BY patients DESC, d.specialty
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new SpecialtyCount(rs.getString(1), rs.getLong(2)));
        }
        return rows;
    }

    public static List<Activity> recentActivity(User actor, int limit) throws SQLException {
        requireAdmin(actor);
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Activity limit must be between 1 and 50.");
        List<Activity> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT activity_type, description, created_at FROM (
                    SELECT 'Patient registration' AS activity_type,
                           CONCAT(full_name, ' registered as a patient') AS description,
                           created_at
                    FROM users WHERE role='PATIENT'
                    UNION ALL
                    SELECT 'Doctor added', CONCAT(full_name, ' joined ', specialty), created_at
                    FROM doctors
                    UNION ALL
                    SELECT 'Appointment booked', CONCAT('Appointment #', id, ' booked for ', appointment_date), created_at
                    FROM appointments
                    UNION ALL
                    SELECT 'Appointment status changed',
                           CONCAT('Appointment #', appointment_id, ' changed from ', previous_status, ' to ', new_status),
                           changed_at
                    FROM appointment_status_history
                    UNION ALL
                    SELECT IF(updated_at>created_at, 'Medical record updated', 'Medical record created'),
                           CONCAT('Clinical notes: ', subject), updated_at
                    FROM medical_records
                ) AS activity
                ORDER BY created_at DESC LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Activity(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime()));
            }
        }
        return rows;
    }

    public static void saveMedicalRecord(User actor, MedicalRecord record) throws SQLException {
        requireAdmin(actor);
        if (record == null || record.patientId() <= 0 || record.doctorId() <= 0
                || record.subject() == null || record.subject().isBlank() || record.subject().trim().length() > 150
                || record.details() == null || record.details().isBlank() || record.details().length() > 10000)
            throw new IllegalArgumentException("Choose a patient and doctor, enter a subject, and provide details (up to 10,000 characters).");
        try (Connection c = connect()) {
            String patientQuery = record.id() == 0
                    ? "SELECT 1 FROM users WHERE id=? AND role='PATIENT' AND active=TRUE"
                    : "SELECT 1 FROM users WHERE id=? AND role='PATIENT'";
            try (PreparedStatement patient = c.prepareStatement(patientQuery)) {
                patient.setLong(1, record.patientId());
                try (ResultSet rs = patient.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Choose an active patient.");
                }
            }
            try (PreparedStatement doctor = c.prepareStatement("SELECT 1 FROM doctors WHERE id=?")) {
                doctor.setLong(1, record.doctorId());
                try (ResultSet rs = doctor.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Choose an existing doctor.");
                }
            }
            String sql = record.id() == 0
                    ? "INSERT INTO medical_records(patient_id,doctor_id,subject,details,created_by) VALUES(?,?,?,?,?)"
                    : "UPDATE medical_records SET patient_id=?,doctor_id=?,subject=?,details=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, record.patientId());
                ps.setLong(2, record.doctorId());
                ps.setString(3, record.subject().trim());
                ps.setString(4, record.details().trim());
                if (record.id() == 0) ps.setLong(5, actor.id());
                else ps.setLong(5, record.id());
                if (ps.executeUpdate() == 0 && record.id() != 0)
                    throw new IllegalArgumentException("Medical record no longer exists.");
            }
        }
    }

    public static List<MedicalRecord> medicalRecords(User actor) throws SQLException {
        requireAdmin(actor);
        List<MedicalRecord> rows = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT m.id,m.patient_id,p.full_name,m.doctor_id,d.full_name,m.subject,m.details,m.created_at,m.updated_at
                FROM medical_records m JOIN users p ON p.id=m.patient_id JOIN doctors d ON d.id=m.doctor_id
                ORDER BY m.updated_at DESC,m.id DESC
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new MedicalRecord(rs.getLong(1), rs.getLong(2), rs.getString(3),
                    rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7),
                    rs.getTimestamp(8).toLocalDateTime(), rs.getTimestamp(9).toLocalDateTime()));
        }
        return rows;
    }

    public static void updateAppointmentStatus(User actor, long appointmentId, String status) throws SQLException {
        requireAdmin(actor);
        if (status == null || !List.of("PENDING", "CONFIRMED", "COMPLETED", "CANCELLED").contains(status))
            throw new IllegalArgumentException("Choose Pending, Confirmed, Completed, or Cancelled.");
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String oldStatus;
                try (PreparedStatement select = c.prepareStatement("SELECT status FROM appointments WHERE id=? FOR UPDATE")) {
                    select.setLong(1, appointmentId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Appointment was not found.");
                        oldStatus = rs.getString(1);
                    }
                }
                if (status.equals(oldStatus)) {
                    c.rollback();
                    return;
                }
                boolean validTransition = ("PENDING".equals(oldStatus)
                        && List.of("CONFIRMED", "COMPLETED", "CANCELLED").contains(status))
                        || ("CONFIRMED".equals(oldStatus)
                        && List.of("COMPLETED", "CANCELLED").contains(status));
                if (!validTransition)
                    throw new IllegalArgumentException("Appointments can move from pending to confirmed or cancelled, and from confirmed to completed or cancelled.");
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE appointments SET status=? WHERE id=?
                          AND ((? NOT IN ('PENDING','CONFIRMED')) OR TIMESTAMP(appointment_date,appointment_time)>=CURRENT_TIMESTAMP)
                          AND (?<>'COMPLETED' OR TIMESTAMP(appointment_date,appointment_time)<CURRENT_TIMESTAMP)
                        """)) {
                    update.setString(1, status);
                    update.setLong(2, appointmentId);
                    update.setString(3, status);
                    update.setString(4, status);
                    if (update.executeUpdate() == 0)
                        throw new IllegalArgumentException("Appointments can only be confirmed before the visit and completed after the visit time.");
                }
                recordStatusChange(c, actor.id(), appointmentId, oldStatus, status);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLIntegrityConstraintViolationException
                        || (e instanceof SQLException sql && "23000".equals(sql.getSQLState())))
                    throw new IllegalArgumentException("That doctor already has an active appointment at this time.");
                throw e;
            }
        }
    }

    public static void rescheduleAppointment(User actor, long appointmentId, LocalDate date, LocalTime time) throws SQLException {
        requireAdmin(actor);
        if (date == null || date.isBefore(LocalDate.now()) || time == null)
            throw new IllegalArgumentException("Choose a valid future appointment date and time.");
        LocalTime opening = clinicOpen();
        LocalTime closing = clinicClose();
        if (!opening.isBefore(closing) || time.isBefore(opening) || !time.isBefore(closing)
                || time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0
                || (date.equals(LocalDate.now()) && !time.isAfter(LocalTime.now())))
            throw new IllegalArgumentException("Choose a future 30-minute slot during clinic hours (" + clinicHours() + ").");
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("""
                UPDATE appointments SET appointment_date=?,appointment_time=?
                WHERE id=? AND status IN ('PENDING','CONFIRMED')
                  AND TIMESTAMP(appointment_date,appointment_time)>=CURRENT_TIMESTAMP
                """)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setTime(2, Time.valueOf(time));
            ps.setLong(3, appointmentId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Only a future pending or confirmed appointment can be rescheduled.");
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("That doctor already has an active appointment at this time.");
        }
    }

    public static void updateAppointment(User actor, long appointmentId, Doctor doctor,
                                         LocalDate date, LocalTime time, String reason) throws SQLException {
        requireAdmin(actor);
        validateSlot(doctor, date, time, reason);
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement current = c.prepareStatement(
                        "SELECT status,appointment_date,appointment_time FROM appointments WHERE id=? FOR UPDATE")) {
                    current.setLong(1, appointmentId);
                    try (ResultSet rs = current.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Appointment was not found.");
                        String status = rs.getString("status");
                        LocalDate oldDate = rs.getDate("appointment_date").toLocalDate();
                        LocalTime oldTime = rs.getTime("appointment_time").toLocalTime();
                        if (!List.of("PENDING", "CONFIRMED").contains(status)
                                || LocalDateTime.of(oldDate, oldTime).isBefore(LocalDateTime.now()))
                            throw new IllegalArgumentException("Only future pending or confirmed appointments can be edited.");
                    }
                }
                try (PreparedStatement checkDoctor = c.prepareStatement(
                        "SELECT 1 FROM doctors WHERE id=? AND active=TRUE FOR UPDATE")) {
                    checkDoctor.setLong(1, doctor.id());
                    try (ResultSet rs = checkDoctor.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Choose an active doctor.");
                    }
                }
                try (PreparedStatement update = c.prepareStatement("""
                        UPDATE appointments
                        SET doctor_id=?,appointment_date=?,appointment_time=?,reason=?
                        WHERE id=? AND status IN ('PENDING','CONFIRMED')
                        """)) {
                    update.setLong(1, doctor.id());
                    update.setDate(2, Date.valueOf(date));
                    update.setTime(3, Time.valueOf(time));
                    update.setString(4, reason.trim());
                    update.setLong(5, appointmentId);
                    if (update.executeUpdate() != 1)
                        throw new IllegalArgumentException("Appointment changed; refresh and try again.");
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLIntegrityConstraintViolationException
                        || (e instanceof SQLException sql && "23000".equals(sql.getSQLState())))
                    throw new IllegalArgumentException("That doctor already has an active appointment at this time.");
                throw e;
            }
        }
    }

    public static void saveDoctor(User actor, Doctor doctor) throws SQLException {
        requireAdmin(actor);
        if (doctor == null || doctor.fullName() == null || doctor.fullName().trim().length() < 3 || doctor.fullName().length() > 120
                || doctor.specialty() == null || doctor.specialty().trim().isEmpty() || doctor.specialty().length() > 100
                || doctor.fee() == null || doctor.fee().scale() > 2 || doctor.fee().signum() < 0
                || doctor.fee().compareTo(new BigDecimal("1000000")) > 0
                || doctor.availability() == null || doctor.availability().trim().isEmpty() || doctor.availability().length() > 200)
            throw new IllegalArgumentException("Enter a valid doctor name, specialty, fee, and availability.");
        try (Connection c = connect()) {
            String sql = doctor.id() == 0
                    ? "INSERT INTO doctors(full_name,specialty,consultation_fee,availability,active) VALUES(?,?,?,?,?)"
                    : "UPDATE doctors SET full_name=?,specialty=?,consultation_fee=?,availability=?,active=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, doctor.fullName().trim());
                ps.setString(2, doctor.specialty().trim());
                ps.setBigDecimal(3, doctor.fee());
                ps.setString(4, doctor.availability().trim());
                ps.setBoolean(5, doctor.active());
                if (doctor.id() != 0) ps.setLong(6, doctor.id());
                if (ps.executeUpdate() == 0 && doctor.id() != 0) throw new IllegalArgumentException("Doctor record was not found.");
            }
        }
    }

    public static void deactivateDoctor(User actor, long doctorId) throws SQLException {
        requireAdmin(actor);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("UPDATE doctors SET active=FALSE WHERE id=?")) {
            ps.setLong(1, doctorId);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Doctor record was not found.");
        }
    }

    public static void setDoctorActive(User actor, long doctorId, boolean active) throws SQLException {
        requireAdmin(actor);
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE doctors SET active=? WHERE id=? AND active<>?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, doctorId);
            ps.setBoolean(3, active);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Doctor record was not found or already has that status.");
        }
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
