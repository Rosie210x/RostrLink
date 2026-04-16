CREATE DATABASE IF NOT EXISTS rostrlink;
USE rostrlink;

-- Users & auth
CREATE TABLE users (
                       id VARCHAR(36) PRIMARY KEY,
                       username VARCHAR(100) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       email VARCHAR(100),
                       role ENUM('ADMIN','USER','TEACHER','PARENT','DRIVER','STUDENT','KIOSK') NOT NULL,
                       locked BOOLEAN DEFAULT FALSE,
                       failed_attempts INT DEFAULT 0,
                       locked_until DATETIME NULL,
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                       updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                       deleted_at DATETIME NULL
);

-- OTP tokens (forgot password only)
CREATE TABLE otp_tokens (
                            id VARCHAR(36) PRIMARY KEY,
                            user_id VARCHAR(36) NOT NULL,
                            otp VARCHAR(6) NOT NULL,
                            expires_at DATETIME NOT NULL,
                            used BOOLEAN DEFAULT FALSE,
                            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Parents
CREATE TABLE parents (
                         id VARCHAR(36) PRIMARY KEY,
                         user_id VARCHAR(36) NOT NULL,
                         full_name VARCHAR(100),
                         phone VARCHAR(20),
                         created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                         deleted_at DATETIME NULL,
                         FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Teachers
CREATE TABLE teachers (
                          id VARCHAR(36) PRIMARY KEY,
                          user_id VARCHAR(36) NOT NULL,
                          full_name VARCHAR(100),
                          phone VARCHAR(20),
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                          deleted_at DATETIME NULL,
                          FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Drivers
CREATE TABLE drivers (
                         id VARCHAR(36) PRIMARY KEY,
                         user_id VARCHAR(36) NOT NULL,
                         full_name VARCHAR(100),
                         phone VARCHAR(20),
                         license_number VARCHAR(50),
                         created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                         deleted_at DATETIME NULL,
                         FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Classes
CREATE TABLE classes (
                         id VARCHAR(36) PRIMARY KEY,
                         name VARCHAR(100) NOT NULL,
                         teacher_id VARCHAR(36),
                         created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                         deleted_at DATETIME NULL,
                         FOREIGN KEY (teacher_id) REFERENCES teachers(id)
);

-- Students
CREATE TABLE students (
                          id VARCHAR(36) PRIMARY KEY,
                          full_name VARCHAR(100) NOT NULL,
                          date_of_birth DATE,
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                          deleted_at DATETIME NULL
);

-- Student - Class relationship
CREATE TABLE student_classes (
                                 id VARCHAR(36) PRIMARY KEY,
                                 student_id VARCHAR(36) NOT NULL,
                                 class_id VARCHAR(36) NOT NULL,
                                 created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 FOREIGN KEY (student_id) REFERENCES students(id),
                                 FOREIGN KEY (class_id) REFERENCES classes(id)
);

-- Student - Parent relationship
CREATE TABLE student_parents (
                                 id VARCHAR(36) PRIMARY KEY,
                                 student_id VARCHAR(36) NOT NULL,
                                 parent_id VARCHAR(36) NOT NULL,
                                 FOREIGN KEY (student_id) REFERENCES students(id),
                                 FOREIGN KEY (parent_id) REFERENCES parents(id)
);

-- Devices
CREATE TABLE kiosks (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(100),
                        location VARCHAR(200),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        deleted_at DATETIME NULL
);

CREATE TABLE nfc_tags (
                          id VARCHAR(36) PRIMARY KEY,
                          tag_code VARCHAR(100) UNIQUE,
                          student_id VARCHAR(36),
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                          deleted_at DATETIME NULL,
                          FOREIGN KEY (student_id) REFERENCES students(id)
);

CREATE TABLE wands (
                       id VARCHAR(36) PRIMARY KEY,
                       wand_code VARCHAR(100) UNIQUE,
                       driver_id VARCHAR(36),
                       created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                       deleted_at DATETIME NULL,
                       FOREIGN KEY (driver_id) REFERENCES drivers(id)
);

-- Events
CREATE TABLE events (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(200),
                        type VARCHAR(50),
                        driver_id VARCHAR(36),
                        status ENUM('PENDING','STARTED','ENDED') DEFAULT 'PENDING',
                        started_at DATETIME NULL,
                        ended_at DATETIME NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        deleted_at DATETIME NULL,
                        FOREIGN KEY (driver_id) REFERENCES drivers(id)
);

CREATE TABLE event_students (
                                id VARCHAR(36) PRIMARY KEY,
                                event_id VARCHAR(36) NOT NULL,
                                student_id VARCHAR(36) NOT NULL,
                                status ENUM('PENDING','ACCEPTED','DECLINED') DEFAULT 'PENDING',
                                FOREIGN KEY (event_id) REFERENCES events(id),
                                FOREIGN KEY (student_id) REFERENCES students(id)
);

-- Logs
CREATE TABLE teacher_logs (
                              id VARCHAR(36) PRIMARY KEY,
                              teacher_id VARCHAR(36) NOT NULL,
                              type ENUM('CHECK_IN','CHECK_OUT') NOT NULL,
                              logged_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (teacher_id) REFERENCES teachers(id)
);

CREATE TABLE student_logs (
                              id VARCHAR(36) PRIMARY KEY,
                              student_id VARCHAR(36) NOT NULL,
                              teacher_id VARCHAR(36) NOT NULL,
                              type ENUM('CHECK_IN','CHECK_OUT') NOT NULL,
                              logged_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (student_id) REFERENCES students(id),
                              FOREIGN KEY (teacher_id) REFERENCES teachers(id)
);

CREATE TABLE parent_logs (
                             id VARCHAR(36) PRIMARY KEY,
                             parent_id VARCHAR(36) NOT NULL,
                             student_id VARCHAR(36) NOT NULL,
                             kiosk_id VARCHAR(36) NOT NULL,
                             type ENUM('CHECK_IN','CHECK_OUT') NOT NULL,
                             logged_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (parent_id) REFERENCES parents(id),
                             FOREIGN KEY (student_id) REFERENCES students(id),
                             FOREIGN KEY (kiosk_id) REFERENCES kiosks(id)
);

CREATE TABLE driver_logs (
                             id VARCHAR(36) PRIMARY KEY,
                             driver_id VARCHAR(36) NOT NULL,
                             student_id VARCHAR(36) NOT NULL,
                             event_id VARCHAR(36),
                             type ENUM('PICKUP','DROPOFF') NOT NULL,
                             logged_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                             FOREIGN KEY (driver_id) REFERENCES drivers(id),
                             FOREIGN KEY (student_id) REFERENCES students(id),
                             FOREIGN KEY (event_id) REFERENCES events(id)
);

-- Alerts
CREATE TABLE alerts (
                        id VARCHAR(36) PRIMARY KEY,
                        type VARCHAR(100),
                        message TEXT,
                        created_by VARCHAR(36),
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE alert_targets (
                               id VARCHAR(36) PRIMARY KEY,
                               alert_id VARCHAR(36) NOT NULL,
                               user_id VARCHAR(36) NOT NULL,
                               is_read BOOLEAN DEFAULT FALSE,
                               FOREIGN KEY (alert_id) REFERENCES alerts(id),
                               FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE alert_settings (
                                id VARCHAR(36) PRIMARY KEY,
                                user_id VARCHAR(36) NOT NULL UNIQUE,
                                enabled BOOLEAN DEFAULT TRUE,
                                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Packages & billing
CREATE TABLE packages (
                          id VARCHAR(36) PRIMARY KEY,
                          name VARCHAR(100),
                          price DECIMAL(10,2),
                          duration_days INT,
                          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                          deleted_at DATETIME NULL
);

CREATE TABLE payment_bills (
                               id VARCHAR(36) PRIMARY KEY,
                               package_id VARCHAR(36),
                               amount DECIMAL(10,2),
                               status ENUM('PENDING','PAID','CANCELLED') DEFAULT 'PENDING',
                               created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                               FOREIGN KEY (package_id) REFERENCES packages(id)
);

-- Seed admin user (password: Admin@123)
INSERT INTO users (id, username, password, email, role)
VALUES (
           UUID(),
           'admin',
           '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lh8i',
           'admin@rostrlink.com',
           'ADMIN'
       );