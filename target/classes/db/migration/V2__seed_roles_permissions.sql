-- V2__seed_roles_permissions.sql

-- Seed roles
INSERT INTO roles (role_name, description) VALUES
                                               ('Admin', 'System administrator'),
                                               ('User', 'Regular user'),
                                               ('Parent', 'Parent userRole'),
                                               ('Teacher', 'Teacher userRole'),
                                               ('Driver', 'Driver userRole'),
                                               ('Kiosk', 'Kiosk device')
    ON CONFLICT (role_name) DO NOTHING;

-- Seed minimal permissions
INSERT INTO permissions (module, action, description) VALUES
                                                          ('users','read','Read user'),
                                                          ('users','update','Update user'),
                                                          ('students','read','Read student'),
                                                          ('students','update','Update student'),
                                                          ('events','read','Read event'),
                                                          ('events','create','Create event'),
                                                          ('sessions','manage','Manage sessions'),
                                                          ('otp','send','Send OTP')
    ON CONFLICT (module, action) DO NOTHING;

-- Grant all permissions to Admin
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM roles r CROSS JOIN permissions p
WHERE r.role_name = 'Admin'
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Parent: read students and events
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM roles r JOIN permissions p ON p.module IN ('students','events')
WHERE r.role_name = 'Parent'
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Teacher: students read/update, events read/create
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM roles r JOIN permissions p ON (p.module = 'students' OR (p.module='events' AND p.action IN ('read','create')))
WHERE r.role_name = 'Teacher'
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Driver: events read/create, sessions manage (for driver devices)
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM roles r JOIN permissions p ON (p.module='events' OR (p.module='sessions' AND p.action='manage'))
WHERE r.role_name = 'Driver'
    ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Kiosk: events read, sessions manage (limited)
INSERT INTO role_permissions (role_id, permission_id, allowed)
SELECT r.role_id, p.permission_id, TRUE
FROM roles r JOIN permissions p ON (p.module='events' OR p.module='sessions')
WHERE r.role_name = 'Kiosk'
    ON CONFLICT (role_id, permission_id) DO NOTHING;
