USE carepoint;

-- Expand first so legacy BOOKED values remain valid while they are migrated.
ALTER TABLE appointments
    MODIFY status ENUM('BOOKED', 'PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')
        NOT NULL DEFAULT 'PENDING';
ALTER TABLE appointment_status_history
    MODIFY previous_status ENUM('BOOKED', 'PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL,
    MODIFY new_status ENUM('BOOKED', 'PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL;

UPDATE appointments SET status = 'CONFIRMED' WHERE status = 'BOOKED';
UPDATE appointment_status_history SET previous_status = 'CONFIRMED' WHERE previous_status = 'BOOKED';
UPDATE appointment_status_history SET new_status = 'CONFIRMED' WHERE new_status = 'BOOKED';

ALTER TABLE appointments
    MODIFY active_booking_date DATE GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN appointment_date ELSE NULL END) STORED,
    MODIFY active_booking_time TIME GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'CONFIRMED') THEN appointment_time ELSE NULL END) STORED,
    MODIFY status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL DEFAULT 'PENDING';
ALTER TABLE appointment_status_history
    MODIFY previous_status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL,
    MODIFY new_status ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED') NOT NULL;
