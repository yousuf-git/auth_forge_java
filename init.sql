-- =====================================================
-- Database Initialization Script
-- Auth Forge
-- =====================================================
-- Creates 5 core tables:
-- 1. role - User roles (ADMIN, CUSTOMER, PLANT_MANAGER)
-- 2. permission - Granular permissions for role-based access
-- 3. role_permission - Many-to-many relationship between roles and permissions
-- 4. users - Application users with OAuth2 support
-- 5. refresh_tokens - Secure refresh token storage with rotation tracking
-- =====================================================

-- Drop tables if they exist (in correct order due to foreign keys)
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS role_permission CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS permission CASCADE;
DROP TABLE IF EXISTS role CASCADE;

-- Drop types if they exist
DROP TYPE IF EXISTS AUTH_PROVIDER CASCADE;
DROP TYPE IF EXISTS REVOCATION_REASON CASCADE;

-- =====================================================
-- Create Enum Types
-- =====================================================
CREATE TYPE AUTH_PROVIDER AS ENUM ('LOCAL', 'GOOGLE', 'GITHUB', 'FACEBOOK');

-- Revocation reasons for differentiating legitimate revocation from theft
CREATE TYPE REVOCATION_REASON AS ENUM (
    'TOKEN_ROTATION',        -- Token rotated during normal refresh flow
    'MANUAL_LOGOUT',         -- User explicitly logged out
    'MAX_DEVICES_EXCEEDED',  -- Token removed due to max session limit
    'THEFT_DETECTED',        -- Revoked token was reused (security incident)
    'ADMIN_REVOKED'          -- Admin manually revoked the token
);

-- =====================================================
-- Table: role
-- =====================================================
-- Stores user roles for role-based access control
-- =====================================================
CREATE TABLE role (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Create indexes for role table
CREATE INDEX idx_role_name ON role(name);
CREATE INDEX idx_role_is_active ON role(is_active);

-- =====================================================
-- Table: permission
-- =====================================================
-- Stores granular permissions that can be assigned to roles
-- =====================================================
CREATE TABLE permission (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL
);

-- Create indexes for permission table
CREATE INDEX idx_permission_name ON permission(name);
CREATE INDEX idx_permission_is_active ON permission(is_active);

-- =====================================================
-- Table: role_permission
-- =====================================================
-- Many-to-many relationship between roles and permissions
-- =====================================================
CREATE TABLE role_permission (
    role_id INTEGER NOT NULL,
    permission_id INTEGER NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) 
        REFERENCES role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) 
        REFERENCES permission(id) ON DELETE CASCADE
);

-- Create indexes for role_permission table
CREATE INDEX idx_role_permission_role_id ON role_permission(role_id);
CREATE INDEX idx_role_permission_permission_id ON role_permission(permission_id);

-- =====================================================
-- Table: users
-- =====================================================
-- Stores application users with support for both local and OAuth2 authentication
-- =====================================================
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(120),  -- Nullable for OAuth2 users
    phone VARCHAR(20) UNIQUE,
    provider AUTH_PROVIDER,  -- Authentication provider (LOCAL, GOOGLE, GITHUB, FACEBOOK)
    provider_id VARCHAR(100),  -- OAuth2 provider user ID
    image_url VARCHAR(500),
    email_verified BOOLEAN DEFAULT FALSE,
    role_id INTEGER,
    
    -- Audit and tracking fields
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by INTEGER,
    modified_at TIMESTAMP,
    modified_by INTEGER,
    
    -- Account locking fields
    is_locked BOOLEAN DEFAULT FALSE NOT NULL,
    locked_at TIMESTAMP,
    locked_by INTEGER,
    unlocked_at TIMESTAMP,
    unlocked_by INTEGER,
    
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) 
        REFERENCES role(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_modified_by FOREIGN KEY (modified_by) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_locked_by FOREIGN KEY (locked_by) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_users_unlocked_by FOREIGN KEY (unlocked_by) 
        REFERENCES users(id) ON DELETE SET NULL
);

-- Create indexes for users table
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_provider ON users(provider);
CREATE INDEX idx_users_provider_id ON users(provider_id);
CREATE INDEX idx_users_role_id ON users(role_id);
CREATE INDEX idx_users_email_verified ON users(email_verified);
CREATE INDEX idx_users_is_locked ON users(is_locked);
CREATE INDEX idx_users_created_by ON users(created_by);
CREATE INDEX idx_users_modified_by ON users(modified_by);

-- =====================================================
-- Table: refresh_tokens
-- =====================================================
-- Stores refresh tokens with security features:
-- - Token rotation (new token on each use)
-- - Family tracking (detect token theft)
-- - Device tracking (multi-device sessions)
-- - Max sessions per user enforcement
-- =====================================================
CREATE TABLE refresh_tokens (
    id VARCHAR(36) PRIMARY KEY,  -- UUID
    user_id INTEGER NOT NULL,
    oauth_client_id VARCHAR(100),
    
    -- Security: Hashed token (SHA-256, never store plain tokens)
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    
    -- Token rotation tracking
    family_id VARCHAR(36) NOT NULL,  -- Groups tokens from same login
    parent_id VARCHAR(36),  -- Parent token ID (rotation lineage)
    rotation_counter INTEGER DEFAULT 0 NOT NULL,
    
    -- Session tracking
    device_id VARCHAR(255),
    ip_address VARCHAR(45),  -- IPv6 compatible
    user_agent VARCHAR(500),
    
    -- Token lifecycle
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    revocation_reason REVOCATION_REASON,  -- Why token was revoked
    last_used_at TIMESTAMP,
    
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_refresh_tokens_parent FOREIGN KEY (parent_id) 
        REFERENCES refresh_tokens(id) ON DELETE SET NULL
);

-- Create indexes for refresh_tokens table (critical for performance)
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_revoked_at ON refresh_tokens(revoked_at);
CREATE INDEX idx_refresh_tokens_revocation_reason ON refresh_tokens(revocation_reason);
CREATE INDEX idx_refresh_tokens_parent_id ON refresh_tokens(parent_id);

-- =====================================================
-- Insert Default Roles
-- =====================================================
INSERT INTO role (name, description, is_active) VALUES
    ('ROLE_ADMIN', 'Administrator with full system access', TRUE),
    ('ROLE_PLANT_MANAGER', 'Plant manager with operational access', TRUE),
    ('ROLE_CUSTOMER', 'Regular customer with basic access', TRUE);

-- =====================================================
-- Insert Default Permissions
-- =====================================================
INSERT INTO permission (name, description, is_active) VALUES
    -- User management
    ('USER_READ', 'View user information', TRUE),
    ('USER_CREATE', 'Create new users', TRUE),
    ('USER_UPDATE', 'Update user information', TRUE),
    ('USER_DELETE', 'Delete users', TRUE),
    
    -- Role management
    ('ROLE_READ', 'View roles', TRUE),
    ('ROLE_CREATE', 'Create new roles', TRUE),
    ('ROLE_UPDATE', 'Update roles', TRUE),
    ('ROLE_DELETE', 'Delete roles', TRUE),
    
    -- Permission management
    ('PERMISSION_READ', 'View permissions', TRUE),
    ('PERMISSION_CREATE', 'Create new permissions', TRUE),
    ('PERMISSION_UPDATE', 'Update permissions', TRUE),
    ('PERMISSION_DELETE', 'Delete permissions', TRUE),
    
    -- Session management
    ('SESSION_READ', 'View user sessions', TRUE),
    ('SESSION_REVOKE', 'Revoke user sessions', TRUE),
    
    -- System management
    ('SYSTEM_CONFIG', 'Configure system settings', TRUE),
    ('AUDIT_LOG_READ', 'View audit logs', TRUE);

-- =====================================================
-- Assign Permissions to Roles
-- =====================================================

-- ADMIN: Full access to everything
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'ROLE_ADMIN';

-- PLANT_MANAGER: User management and operational permissions
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'ROLE_PLANT_MANAGER'
AND p.name IN (
    'USER_READ', 
    'USER_CREATE', 
    'USER_UPDATE',
    'ROLE_READ',
    'SESSION_READ'
);

-- CUSTOMER: Basic read permissions only
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r, permission p
WHERE r.name = 'ROLE_CUSTOMER'
AND p.name IN (
    'USER_READ'  -- Can only view their own profile
);

-- =====================================================
-- Verification Queries (commented out)
-- =====================================================
-- Uncomment these queries to verify the setup:

-- SELECT * FROM role;
-- SELECT * FROM permission;
-- SELECT * FROM role_permission;
-- SELECT r.name as role_name, p.name as permission_name 
-- FROM role r 
-- JOIN role_permission rp ON r.id = rp.role_id 
-- JOIN permission p ON p.id = rp.permission_id 
-- ORDER BY r.name, p.name;

-- =====================================================
-- Database Initialization Complete
-- =====================================================
-- Next steps:
-- 1. Run this script against your PostgreSQL database
-- 2. Start your Spring Boot application
-- 3. Application will use these tables automatically
-- 4. First user can be created via /auth/signup endpoint
-- =====================================================
