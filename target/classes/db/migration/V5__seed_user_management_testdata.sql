-- V5__seed_user_management_testdata.sql
-- Seed users and their roles for testing user management

-- Password for all seeded users: Password123!
-- BCrypt Hash: $2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S. (Placeholder, replace with valid if needed)
-- Note: Pattern requires 1 upper, 1 lower, 1 digit, 1 special, min 8 chars.

INSERT INTO users (email, first_name, last_name, password_hash, status) VALUES
('admin@rostrlink.com', 'Van', 'Nguyen', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('teacher1@rostrlink.com', 'Thi', 'Tran', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('teacher2@rostrlink.com', 'Van', 'Le', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('parent1@rostrlink.com', 'Van', 'Pham', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('parent2@rostrlink.com', 'Thi', 'Hoang', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('driver1@rostrlink.com', 'Thanh', 'Bui', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active'),
('kiosk1@kiosk.internal', 'Device', 'Kiosk', '$2a$12$LQv3c1yqBWVHxkd0LpAsGeS0VBCXvF.zP9hH6KqPZ3Jv7.mC1.6S.', 'active')
ON CONFLICT (email) DO NOTHING;

-- Map Users to Roles
-- Admin
INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{}'
FROM users u, roles r
WHERE u.email = 'admin@rostrlink.com' AND r.role_name = 'Admin'
ON CONFLICT DO NOTHING;

-- Teachers
INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"class_ids": [1, 2]}'
FROM users u, roles r
WHERE u.email = 'teacher1@rostrlink.com' AND r.role_name = 'Teacher'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"class_ids": [3]}'
FROM users u, roles r
WHERE u.email = 'teacher2@rostrlink.com' AND r.role_name = 'Teacher'
ON CONFLICT DO NOTHING;

-- Parents
INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"student_ids": [1, 2]}'
FROM users u, roles r
WHERE u.email = 'parent1@rostrlink.com' AND r.role_name = 'Parent'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"student_ids": [3]}'
FROM users u, roles r
WHERE u.email = 'parent2@rostrlink.com' AND r.role_name = 'Parent'
ON CONFLICT DO NOTHING;

-- Driver
INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"route_ids": [101]}'
FROM users u, roles r
WHERE u.email = 'driver1@rostrlink.com' AND r.role_name = 'Driver'
ON CONFLICT DO NOTHING;

-- Kiosk
INSERT INTO user_roles (user_id, role_id, scope)
SELECT u.user_id, r.role_id, '{"kiosk_id": "K001"}'
FROM users u, roles r
WHERE u.email = 'kiosk1@kiosk.internal' AND r.role_name = 'Kiosk'
ON CONFLICT DO NOTHING;
