# Doctor Appointment Booking System (CarePoint)

CarePoint is the in-app name of this Java 17 Swing doctor appointment application. It connects to MySQL 8 through JDBC.

## Requirements

- JDK 17 or newer
- Maven 3.8+
- MySQL 8

## Database setup

Start MySQL Server 8, then create the database and tables using the supplied non-destructive script from Windows PowerShell in the project folder. For the default MySQL Installer location:

```powershell
Get-Content -Raw .\database\schema.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p
```

Enter the MySQL administrator password when prompted. If MySQL is installed elsewhere, substitute its `bin\mysql.exe` path. The production script creates missing tables without inserting sample patients, doctors, appointments, or records, and it does not drop or delete existing data. It includes an InnoDB unique constraint that prevents two active bookings for one doctor's date/time slot. Canceled slots become available again. The application checks that the required tables and columns exist; it does not create or alter tables at runtime.

The schema includes `appointment_status_history` and `medical_records`. `CREATE TABLE IF NOT EXISTS` preserves existing records. A new production database starts without doctors; an administrator can add genuine directory records in the application, or use the separate demo database below for fictional records.

For an existing installation created with the earlier appointment statuses, back up the database and stop CarePoint before migrating the appointment status columns and active-slot generated columns. MySQL schema changes commit independently, so do not run the application while this migration is in progress:

```powershell
Get-Content -Raw .\database\migrate-appointment-statuses.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p
```

Existing `BOOKED` appointments and status-history values become `CONFIRMED`. New patient bookings start as `PENDING`, which an administrator can confirm. Pending and confirmed appointments both reserve their doctor's slot.

Create an application database user from a MySQL administrator session. Replace the example password with a strong password you choose; do not use the administrator/root account to run the application:

```sql
CREATE USER IF NOT EXISTS 'carepoint_app'@'localhost' IDENTIFIED BY 'replace-with-a-strong-local-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON carepoint.* TO 'carepoint_app'@'localhost';
```

Configure the URL for the existing `carepoint` database and set the credentials in the same PowerShell terminal used to run Maven. Replace the password placeholder with the password chosen above:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/carepoint?serverTimezone=UTC'
$env:DB_USER = 'carepoint_app'
$dbSecret = Read-Host 'MySQL application-user password' -AsSecureString
$env:DB_PASSWORD = (New-Object System.Net.NetworkCredential('', $dbSecret)).Password
Remove-Variable dbSecret
```

These environment variables are not saved to the source tree. For the connection check, run:

```powershell
mvn compile -Dexec.mainClass=com.carepoint.DatabaseConnection exec:java
```

The check reports success only after connecting and getting `1` from MySQL's `SELECT 1`. The database user should have only the privileges needed for this application.

`DB_URL`, `DB_USER`, and `DB_PASSWORD` must be present in the environment of the process that starts Java. A successful MySQL command-line login does not pass that command's connection options or environment to CarePoint. In VS Code, a debug launch inherits the environment of the VS Code process; fully exit VS Code and reopen it from the configured PowerShell session, or configure the variables in a local, uncommitted launch environment. Opening a terminal and setting variables after VS Code has already started does not update the environment of its existing debug launches. Never put database passwords in committed launch configuration.

Clinic hours default to 09:00-17:00 in 30-minute intervals. Optionally set `CLINIC_OPEN` and `CLINIC_CLOSE` as `HH:mm` environment variables (for example `08:30` and `18:00`) to configure the slot window.

### Initial administrator

If no administrator exists, CarePoint creates one only when both `CAREPOINT_ADMIN_EMAIL` and `CAREPOINT_ADMIN_PASSWORD` are supplied. Set these before the first sign-in with your own unique email and a strong temporary password:

```powershell
$env:CAREPOINT_ADMIN_EMAIL = 'admin@example.test'
$adminSecret = Read-Host 'Temporary administrator password' -AsSecureString
$env:CAREPOINT_ADMIN_PASSWORD = (New-Object System.Net.NetworkCredential('', $adminSecret)).Password
Remove-Variable adminSecret
```

The password is BCrypt-hashed, and the new admin must change it at first sign-in. There is deliberately no shared or hardcoded demo login; the values you set are the initial local/demo credentials. Remove the bootstrap password from the launch environment after setup. If no admin exists and the variables are missing, patient sign-in/registration remains available and CarePoint logs the required setup.

## Run

```sh
mvn clean compile exec:java
```

Run the command from the project folder in the same PowerShell session where the database environment variables were set. The login window opens maximized. Create a patient account on the login screen. Administrators can manage doctors and appointments from the admin dashboard.

## Optional isolated demo database

Demo records are never inserted automatically and the seeder refuses to run unless both the explicit opt-in and the exact `carepoint_demo` database are selected. Create its schema separately; this database does not share data with `carepoint`:

```powershell
Get-Content -Raw .\database\schema-demo.sql | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -u root -p
```

Grant the application account access to the demo database if desired:

```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON carepoint_demo.* TO 'carepoint_app'@'localhost';
```

Configure `DB_URL` to `jdbc:mysql://localhost:3306/carepoint_demo?serverTimezone=UTC` in the same PowerShell terminal. First launch CarePoint with your own `CAREPOINT_ADMIN_EMAIL` and `CAREPOINT_ADMIN_PASSWORD`, sign in once to create the administrator, and close the app. Then run the one-time seed command:

```powershell
$env:CAREPOINT_DEMO_SEED = 'true'
mvn compile -Dexec.mainClass=com.carepoint.DemoDataSeeder exec:java
Remove-Item Env:CAREPOINT_DEMO_SEED
```

The seed is idempotent and creates fictional `.test` patients, additional doctors, appointments across past and future months, and medical-record examples labeled `DEMO DATA ONLY`. Generated patient credentials are random and are not printed or shared. The seeder requires an existing active administrator for the medical-record foreign key. For the normal app, leave `DB_URL` pointed at `carepoint` and do not set `CAREPOINT_DEMO_SEED`.

## Features

- BCrypt-backed patient registration and authentication; database-backed admin authentication
- Role-specific patient and administrator dashboards and authorization checks
- Searchable doctor directory, specialty filter, appointment records, cancellations
- Transactional appointment creation, future-date and clinic-hour validation, and database-backed slot conflict protection
- Admin dashboard with live database-backed patient, doctor, and appointment summaries, 12-month four-status charts, specialty distribution, upcoming appointments, and recent activity
- Patient/doctor administration, appointment booking and editing, status changes, administrator-only medical-record creation/updates, and clinic reports
- Appointment status history is recorded when a patient or administrator changes an appointment status. Previously changed statuses are not reconstructed from old rows.
- Patient and doctor deletion from the admin interface is a soft deactivation so appointment and medical-record history is retained.
- Responsive Swing layouts, searchable and filterable administration tables, report date filters and CSV export, and custom-painted local healthcare artwork

Password recovery is not connected to an email service and is therefore not presented as a working reset feature. The “Forgot password?” link explains this limitation.

## Database dependency

A running MySQL server, initialized database, and configured `DB_URL`, `DB_USER`, and `DB_PASSWORD` are required for sign-in, registration, doctor directory, booking, cancellation, and admin features. If MySQL Server or the `mysql` command-line client is not installed, install MySQL Server 8 (for example, with MySQL Installer), start its Windows service, then use the included MySQL command-line client to run `database\schema.sql` as shown above. Offline compilation does not require a running MySQL server.
