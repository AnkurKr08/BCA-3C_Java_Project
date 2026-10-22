package com.carepoint;

import org.mindrot.jbcrypt.BCrypt;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DemoDataSeeder {
    private static final String[][] PATIENTS = {
            {"Avery Morgan", "+1-202-555-0101"}, {"Jordan Ellis", "+1-202-555-0102"},
            {"Riley Bennett", "+1-202-555-0103"}, {"Casey Parker", "+1-202-555-0104"},
            {"Quinn Harper", "+1-202-555-0105"}, {"Taylor Reed", "+1-202-555-0106"},
            {"Morgan Hayes", "+1-202-555-0107"}, {"Cameron Brooks", "+1-202-555-0108"},
            {"Drew Foster", "+1-202-555-0109"}, {"Skyler James", "+1-202-555-0110"},
            {"Alex Monroe", "+1-202-555-0111"}, {"Jamie Sutton", "+1-202-555-0112"},
            {"Peyton Wells", "+1-202-555-0113"}, {"Robin Cole", "+1-202-555-0114"},
            {"Dakota Price", "+1-202-555-0115"}, {"Emerson Lane", "+1-202-555-0116"},
            {"Finley Ross", "+1-202-555-0117"}, {"Reese Palmer", "+1-202-555-0118"},
            {"Sage Turner", "+1-202-555-0119"}, {"Harper Quinn", "+1-202-555-0120"}
    };

    private static final String[][] DOCTORS = {
            {"Dr. Rowan Blake", "Family Medicine", "600.00", "Mon-Fri, 9:00 AM-5:00 PM"},
            {"Dr. Ellis Jordan", "Orthopedics", "900.00", "Mon-Thu, 9:00 AM-5:00 PM"},
            {"Dr. Avery Chen", "Ophthalmology", "750.00", "Tue-Sat, 9:00 AM-5:00 PM"},
            {"Dr. Casey Morgan", "Psychiatry", "1100.00", "Mon-Fri, 9:00 AM-5:00 PM"},
            {"Dr. Riley Shah", "Endocrinology", "950.00", "Mon-Fri, 9:00 AM-5:00 PM"},
            {"Dr. Taylor Kim", "Family Medicine", "650.00", "Mon-Sat, 9:00 AM-5:00 PM"}
    };

    private DemoDataSeeder() {}

    public static void main(String[] args) throws Exception {
        if (!"true".equalsIgnoreCase(System.getenv("CAREPOINT_DEMO_SEED")))
            throw new IllegalStateException("Set CAREPOINT_DEMO_SEED=true to explicitly enable demo-data seeding.");
        try (Connection connection = DatabaseModule.connect()) {
            if (!"carepoint_demo".equalsIgnoreCase(connection.getCatalog()))
                throw new IllegalStateException("Demo data may only be seeded into the carepoint_demo database. "
                        + "Set DB_URL to jdbc:mysql://localhost:3306/carepoint_demo.");
            verifySchema(connection);
            connection.setAutoCommit(false);
            try {
                long adminId = findActiveAdmin(connection);
                List<Long> doctorIds = ensureDoctors(connection);
                List<Long> patientIds = ensurePatients(connection);
                int appointments = seedAppointments(connection, patientIds, doctorIds);
                int records = seedMedicalRecords(connection, adminId, patientIds, doctorIds);
                connection.commit();
                System.out.printf(Locale.ROOT, "Demo data is ready in carepoint_demo: %d patients, %d doctors, "
                                + "%d appointment rows added, and %d medical records added.%n",
                        patientIds.size(), doctorIds.size(), appointments, records);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static void verifySchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT id,full_name,email,phone,password_hash,role,must_change_password,active,created_at FROM users WHERE 1=0").close();
            statement.executeQuery("SELECT id,full_name,specialty,consultation_fee,availability,active FROM doctors WHERE 1=0").close();
            statement.executeQuery("SELECT id,patient_id,doctor_id,appointment_date,appointment_time,reason,status FROM appointments WHERE 1=0").close();
            statement.executeQuery("SELECT id,appointment_id,changed_by,previous_status,new_status,changed_at FROM appointment_status_history WHERE 1=0").close();
            statement.executeQuery("SELECT id,patient_id,doctor_id,subject,details,created_by,created_at,updated_at FROM medical_records WHERE 1=0").close();
        }
    }

    private static long findActiveAdmin(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM users WHERE role='ADMIN' AND active=TRUE ORDER BY id LIMIT 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next())
                throw new IllegalStateException("Create the initial administrator in the demo database before seeding demo records.");
            return result.getLong(1);
        }
    }

    private static List<Long> ensureDoctors(Connection connection) throws SQLException {
        for (String[] doctor : DOCTORS) {
            Long existingId = null;
            boolean existingActive = true;
            try (PreparedStatement find = connection.prepareStatement(
                    "SELECT id,active FROM doctors WHERE full_name=? AND specialty=? ORDER BY id LIMIT 1")) {
                find.setString(1, doctor[0]);
                find.setString(2, doctor[1]);
                try (ResultSet result = find.executeQuery()) {
                    if (result.next()) {
                        existingId = result.getLong("id");
                        existingActive = result.getBoolean("active");
                    }
                }
            }
            if (existingId != null) {
                if (!existingActive) {
                    try (PreparedStatement activate = connection.prepareStatement(
                            "UPDATE doctors SET active=TRUE WHERE id=?")) {
                        activate.setLong(1, existingId);
                        activate.executeUpdate();
                    }
                }
                continue;
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO doctors(full_name,specialty,consultation_fee,availability,active)
                    VALUES(?,?,?,?,TRUE)
                    """)) {
                insert.setString(1, doctor[0]);
                insert.setString(2, doctor[1]);
                insert.setBigDecimal(3, new BigDecimal(doctor[2]));
                insert.setString(4, doctor[3]);
                insert.executeUpdate();
            }
        }
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM doctors WHERE active=TRUE ORDER BY id");
             ResultSet result = query.executeQuery()) {
            while (result.next()) ids.add(result.getLong(1));
        }
        if (ids.isEmpty()) throw new IllegalStateException("At least one active doctor is required for demo appointments.");
        return ids;
    }

    private static List<Long> ensurePatients(Connection connection) throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < PATIENTS.length; i++) {
            String email = String.format(Locale.ROOT, "demo.patient.%02d@example.test", i + 1);
            Long id = null;
            boolean inactive = false;
            try (PreparedStatement find = connection.prepareStatement("SELECT id,role,active FROM users WHERE email=?")) {
                find.setString(1, email);
                try (ResultSet result = find.executeQuery()) {
                    if (result.next()) {
                        if (!"PATIENT".equals(result.getString("role")))
                            throw new IllegalStateException("A demo patient email is already used by a non-patient account: " + email);
                        id = result.getLong("id");
                        inactive = !result.getBoolean("active");
                    }
                }
            }
            if (id == null) {
                String unsharedRandomPassword = UUID.randomUUID().toString().replace("-", "");
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO users(full_name,email,phone,password_hash,role,active,created_at)
                        VALUES(?,?,?,?,'PATIENT',TRUE,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, PATIENTS[i][0]);
                    insert.setString(2, email);
                    insert.setString(3, PATIENTS[i][1]);
                    insert.setString(4, BCrypt.hashpw(unsharedRandomPassword, BCrypt.gensalt(12)));
                    insert.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now().minusMonths(i % 18).minusDays(i)));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("A demo patient was inserted without an identifier.");
                        id = keys.getLong(1);
                    }
                }
            } else if (inactive) {
                try (PreparedStatement activate = connection.prepareStatement(
                        "UPDATE users SET active=TRUE WHERE id=? AND role='PATIENT'")) {
                    activate.setLong(1, id);
                    activate.executeUpdate();
                }
            }
            ids.add(id);
        }
        return ids;
    }

    private static int seedAppointments(Connection connection, List<Long> patients, List<Long> doctors) throws SQLException {
        int inserted = 0;
        int sequence = 0;
        for (int monthOffset = -5; monthOffset <= 6; monthOffset++) {
            YearMonth month = YearMonth.now().plusMonths(monthOffset);
            for (int slot = 0; slot < 4; slot++) {
                LocalDate date = month.atDay(new int[]{5, 12, 20, 26}[slot]);
                LocalTime time = LocalTime.of(9, 0).plusMinutes((sequence % 16) * 30L);
                long patientId = patients.get(sequence % patients.size());
                long doctorId = doctors.get(sequence % doctors.size());
                String reason = String.format(Locale.ROOT, "DEMO: wellness visit %02d", sequence + 1);
                if (!appointmentExists(connection, patientId, doctorId, date, time)) {
                    String status = appointmentStatus(date, time, sequence);
                    if (!occupiedSlot(connection, doctorId, date, time)) {
                        try (PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,reason,status)
                                VALUES(?,?,?,?,?,?)
                                """)) {
                            insert.setLong(1, patientId);
                            insert.setLong(2, doctorId);
                            insert.setDate(3, java.sql.Date.valueOf(date));
                            insert.setTime(4, java.sql.Time.valueOf(time));
                            insert.setString(5, reason);
                            insert.setString(6, status);
                            inserted += insert.executeUpdate();
                        }
                    }
                }
                sequence++;
            }
        }
        return inserted;
    }

    private static String appointmentStatus(LocalDate date, LocalTime time, int sequence) {
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now()))
            return sequence % 4 == 0 ? "CANCELLED" : "COMPLETED";
        return sequence % 2 == 0 ? "PENDING" : "CONFIRMED";
    }

    private static boolean appointmentExists(Connection connection, long patientId, long doctorId,
                                             LocalDate date, LocalTime time) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT 1 FROM appointments
                WHERE patient_id=? AND doctor_id=? AND appointment_date=? AND appointment_time=?
                LIMIT 1
                """)) {
            query.setLong(1, patientId);
            query.setLong(2, doctorId);
            query.setDate(3, java.sql.Date.valueOf(date));
            query.setTime(4, java.sql.Time.valueOf(time));
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean occupiedSlot(Connection connection, long doctorId, LocalDate date, LocalTime time) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT 1 FROM appointments
                WHERE doctor_id=? AND appointment_date=? AND appointment_time=?
                  AND status IN ('PENDING','CONFIRMED')
                LIMIT 1
                """)) {
            query.setLong(1, doctorId);
            query.setDate(2, java.sql.Date.valueOf(date));
            query.setTime(3, java.sql.Time.valueOf(time));
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private static int seedMedicalRecords(Connection connection, long adminId,
                                          List<Long> patients, List<Long> doctors) throws SQLException {
        String[] scenarios = {
                "Fictional routine wellness consultation requested.",
                "Fictional follow-up visit for general care coordination.",
                "Fictional preventive-care discussion; no diagnosis recorded.",
                "Fictional consultation note for appointment workflow demonstration.",
                "Fictional patient education visit; no treatment prescribed.",
                "Fictional check-in note to demonstrate record editing.",
                "Fictional specialty consultation; not clinical advice.",
                "Fictional follow-up summary for demo interface testing."
        };
        int inserted = 0;
        for (int i = 0; i < scenarios.length; i++) {
            long patientId = patients.get(i);
            String subject = String.format(Locale.ROOT, "DEMO ONLY: sample note %02d", i + 1);
            try (PreparedStatement exists = connection.prepareStatement(
                    "SELECT 1 FROM medical_records WHERE patient_id=? AND subject=? LIMIT 1")) {
                exists.setLong(1, patientId);
                exists.setString(2, subject);
                try (ResultSet result = exists.executeQuery()) {
                    if (result.next()) continue;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO medical_records(patient_id,doctor_id,subject,details,created_by)
                    VALUES(?,?,?,?,?)
                    """)) {
                insert.setLong(1, patientId);
                insert.setLong(2, doctors.get(i % doctors.size()));
                insert.setString(3, subject);
                insert.setString(4, "DEMO DATA ONLY — fictional example, not a real medical history. " + scenarios[i]);
                insert.setLong(5, adminId);
                inserted += insert.executeUpdate();
            }
        }
        return inserted;
    }
}
