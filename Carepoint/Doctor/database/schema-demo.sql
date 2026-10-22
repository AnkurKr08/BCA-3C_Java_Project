CREATE DATABASE IF NOT EXISTS carepoint_demo
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE carepoint_demo;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    role ENUM('PATIENT', 'ADMIN') NOT NULL DEFAULT 'PATIENT',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_users_email (email),
    INDEX idx_users_role_active (role, active)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS doctors (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL,
    availability VARCHAR(200) NOT NULL DEFAULT 'Mon-Fri',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doctors_specialty_active (specialty, active)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS appointments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'PENDING',
    active_booking_date DATE GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN appointment_date ELSE NULL END) STORED,
    active_booking_time TIME GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN appointment_time ELSE NULL END) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES users(id),
    CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT uq_doctor_active_slot UNIQUE (doctor_id, active_booking_date, active_booking_time),
    INDEX idx_appointments_patient_date (patient_id, appointment_date),
    INDEX idx_appointments_doctor_date (doctor_id, appointment_date),
    INDEX idx_appointments_status (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS appointment_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    changed_by BIGINT NOT NULL,
    previous_status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL,
    new_status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_status_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
    CONSTRAINT fk_status_history_actor FOREIGN KEY (changed_by) REFERENCES users(id),
    INDEX idx_status_history_changed (changed_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS medical_records (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    subject VARCHAR(150) NOT NULL,
    details TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_medical_records_patient FOREIGN KEY (patient_id) REFERENCES users(id),
    CONSTRAINT fk_medical_records_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT fk_medical_records_author FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_medical_records_patient_updated (patient_id, updated_at)
) ENGINE=InnoDB;

INSERT INTO doctors (full_name, specialty, consultation_fee, availability)
SELECT 'Dr. Maya Patel', 'Cardiology', 850.00, 'Mon-Fri, 9:00 AM-5:00 PM'
WHERE NOT EXISTS (SELECT 1 FROM doctors WHERE full_name = 'Dr. Maya Patel' AND specialty = 'Cardiology');
INSERT INTO doctors (full_name, specialty, consultation_fee, availability)
SELECT 'Dr. Arjun Mehta', 'Dermatology', 700.00, 'Mon-Sat, 9:00 AM-5:00 PM'
WHERE NOT EXISTS (SELECT 1 FROM doctors WHERE full_name = 'Dr. Arjun Mehta' AND specialty = 'Dermatology');
INSERT INTO doctors (full_name, specialty, consultation_fee, availability)
SELECT 'Dr. Sara Khan', 'Pediatrics', 650.00, 'Mon-Fri, 9:00 AM-5:00 PM'
WHERE NOT EXISTS (SELECT 1 FROM doctors WHERE full_name = 'Dr. Sara Khan' AND specialty = 'Pediatrics');
INSERT INTO doctors (full_name, specialty, consultation_fee, availability)
SELECT 'Dr. Daniel Roy', 'Neurology', 1000.00, 'Tue-Sat, 9:00 AM-5:00 PM'
WHERE NOT EXISTS (SELECT 1 FROM doctors WHERE full_name = 'Dr. Daniel Roy' AND specialty = 'Neurology');
