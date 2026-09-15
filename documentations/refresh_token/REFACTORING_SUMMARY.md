# Refactoring Summary: User and Role Entity Changes

## Overview
This document summarizes the refactoring changes made to the Auth Forge authentication system based on the following requirements:
1. Role name can be any string (not restricted to enum values)
2. Remove username from User entity (use email as unique identifier)
3. Change User-Role relationship from many-to-many to many-to-one (User has one Role)

---

## Changes Made

### 1. Entity Changes

#### Role Entity (`Role.java`)
**Before:**
- `name` field: `ERole` enum type
- Relationship: Many-to-many with User (via `user_roles` table)

**After:**
- `name` field: `String` type with `@NotBlank`, `@NotNull`, unique constraint
- Column length: 100 characters
- Relationship: One-to-many with User (Role has many Users)
- Removed dependency on `ERole` enum

**Database Impact:**
```sql
-- Role name column changed
ALTER TABLE roles ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE roles ADD CONSTRAINT roles_name_unique UNIQUE (name);
```

---

#### User Entity (`User.java`)
**Before:**
- Had `username` field (String, unique)
- Relationship: Many-to-many with Role (via `user_roles` table)
- `roles` field: `Set<Role>`

**After:**
- Removed `username` field entirely
- Relationship: Many-to-one with Role (User has one Role)
- `role` field: `Role` with `@ManyToOne` and `@JoinColumn(name = "role_id")`
- Primary identifier: `email` (still unique)

**Database Impact:**
```sql
-- Remove username column
ALTER TABLE users DROP COLUMN username;

-- Add role_id column with foreign key
ALTER TABLE users ADD COLUMN role_id INTEGER NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id);

-- Drop user_roles junction table
DROP TABLE user_roles;
```

---

### 2. Repository Changes

#### RoleRepo (`RoleRepo.java`)
**Before:**
```java
Optional<Role> findByName(ERole roleName);
```

**After:**
```java
Optional<Role> findByName(String roleName);
Boolean existsByName(String roleName);
```

---

#### UserRepo (`UserRepo.java`)
**Before:**
```java
Optional<User> findByUsername(String username);
Optional<User> findByEmail(String email);
Boolean existsByUsername(String username);
Boolean existsByEmail(String email);
```

**After:**
```java
Optional<User> findByEmail(String email);
Boolean existsByEmail(String email);
```
- Removed all username-related methods

---

### 3. Service Changes

#### UserDetailsServiceImpl (`UserDetailsServiceImpl.java`)
**Before:**
```java
public UserDetails loadUserByUsername(String username) {
    return userRepo.findByUsername(username)
        .map(UserDetailsImpl::build)
        .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));
}
```

**After:**
```java
public UserDetails loadUserByUsername(String email) {
    return userRepo.findByEmail(email)
        .map(UserDetailsImpl::build)
        .orElseThrow(() -> new UsernameNotFoundException("User Not Found with email: " + email));
}
```
- Now loads user by email instead of username
- Method name kept as `loadUserByUsername` for Spring Security interface compatibility

---

#### UserDetailsImpl (`UserDetailsImpl.java`)
**Before:**
```java
List<GrantedAuthority> authorities = user.getRoles().stream()
    .map(role -> new SimpleGrantedAuthority(role.getName().name()))
    .collect(Collectors.toList());

return new UserDetailsImpl(
    user.getId(), 
    user.getUsername(), 
    user.getEmail(),
    user.getPassword(),
    true,
    null,
    authorities);
```

**After:**
```java
List<GrantedAuthority> authorities = List.of(
    new SimpleGrantedAuthority(user.getRole().getName())
);

return new UserDetailsImpl(
    user.getId(), 
    user.getEmail(),  // username field now populated with email
    user.getEmail(),
    user.getPassword(),
    true,
    null,
    authorities);
```
- Changed from processing `Set<Role>` to single `Role`
- Role name is now String, not enum
- `getName()` method returns email instead of username

---

#### CustomOAuth2UserService (`CustomOAuth2UserService.java`)
**Before:**
```java
user.setUsername(oAuth2UserInfo.getName());
user.setEmail(oAuth2UserInfo.getEmail());
Role userRole = roleRepo.findByName(ERole.ROLE_CUSTOMER)...
user.setRoles(Set.of(userRole));
```

**After:**
```java
user.setEmail(oAuth2UserInfo.getEmail());
Role userRole = roleRepo.findByName("ROLE_CUSTOMER")...
user.setRole(userRole);
```
- Removed username setting
- Changed to single role assignment
- Uses String for role name

---

### 4. Controller Changes

#### AuthController (`AuthController.java`)
**Before:**
```java
// Check username exists
if (userRepo.existsByUsername(request.getUsername())) {...}

// Set username
user.setUsername(request.getUsername());

// Set multiple roles
private boolean setRoles(User user, Set<String> rolesFromReq) {
    Set<Role> roles = new HashSet<>();
    // ... add multiple roles
    user.setRoles(roles);
}

// Sign in with username
Authentication authentication = authManager.authenticate(
    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
);
```

**After:**
```java
// No username check (removed)

// No username setting (removed)

// Set single role
private boolean setRole(User user, String roleFromReq) {
    String roleName;
    switch (roleFromReq.toLowerCase()) {
        case "customer": roleName = "ROLE_CUSTOMER"; break;
        case "admin": roleName = "ROLE_ADMIN"; break;
        case "manager": roleName = "ROLE_PLANT_MANAGER"; break;
    }
    Role role = roleRepo.findByName(roleName)...
    user.setRole(role);
}

// Sign in with email
Authentication authentication = authManager.authenticate(
    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
);
```

---

### 5. DTO Changes

#### SignUpRequest (`SignUpRequest.java`)
**Before:**
```java
private String username;  // @NotBlank, @Size(min=5, max=20)
private String email;     // @NotBlank, @Email
private String password;  // @NotBlank, @Size(min=6, max=30)
private Set<String> roles;
```

**After:**
```java
private String email;     // @NotBlank, @Email
private String password;  // @NotBlank, @Size(min=6, max=30)
private String role;      // single role string
```

**Example JSON:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "role": "customer"
}
```

---

#### LoginRequest (`LoginRequest.java`)
**Before:**
```java
private String username;  // @NotBlank
private String password;  // @NotBlank
```

**After:**
```java
private String email;     // @NotBlank
private String password;  // @NotBlank
```

**Example JSON:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

---

## Database Migration Steps

### Prerequisites
```sql
-- Create backups
CREATE TABLE users_backup AS SELECT * FROM users;
CREATE TABLE roles_backup AS SELECT * FROM roles;
CREATE TABLE user_roles_backup AS SELECT * FROM user_roles;
```

### Migration Script
Run the provided `database_migration_refactor.sql` script which:
1. Changes `roles.name` from ENUM to VARCHAR(100)
2. Adds `role_id` column to `users` table
3. Migrates data from `user_roles` to `users.role_id`
4. Drops `user_roles` junction table
5. Removes `username` column from `users` table
6. Creates necessary indexes

### Execute Migration
```bash
psql -U postgres -d your_database -f database_migration_refactor.sql
```

---

## API Changes

### Signup Endpoint
**Endpoint:** `POST /auth/signup`

**Before:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123",
  "roles": ["admin", "user"]
}
```

**After:**
```json
{
  "email": "john@example.com",
  "password": "password123",
  "role": "admin"
}
```

---

### Signin Endpoint
**Endpoint:** `POST /auth/signin`

**Before:**
```json
{
  "username": "john_doe",
  "password": "password123"
}
```

**After:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

---

## Role Name Convention

### Standard Role Names
The application now uses these standard role names:
- `ROLE_CUSTOMER` - Default user role
- `ROLE_ADMIN` - Administrator role
- `ROLE_PLANT_MANAGER` - Manager/moderator role

### Custom Roles
You can now add any custom role name as a string:
```sql
INSERT INTO roles (name, description, is_active) 
VALUES ('ROLE_SUPERVISOR', 'Supervisor role', true);
```

The `ROLE_` prefix is recommended for Spring Security compatibility.

---

## Testing Considerations

### Unit Tests
All test files will need updates:
- Replace `new Role(1, ERole.ROLE_USER)` with `Role.builder().id(1).name("ROLE_USER").build()`
- Replace `user.setUsername()` with email-based identification
- Replace `Set<Role>` with single `Role` in test data setup
- Update mock repository calls from `findByUsername()` to `findByEmail()`

### Integration Tests
- Update all API test requests to use email instead of username
- Update role assignments from arrays to single string
- Verify authentication works with email as principal

---

## Breaking Changes

### API Breaking Changes
1. **Signup endpoint** no longer accepts `username` field
2. **Signup endpoint** accepts single `role` string instead of `roles` array
3. **Signin endpoint** uses `email` instead of `username`

### Code Breaking Changes
1. All code referencing `ERole` enum must be updated to use String
2. All code accessing `user.getRoles()` must change to `user.getRole()`
3. All code calling `userRepo.findByUsername()` must change to `findByEmail()`

---

## Rollback Procedure

If you need to rollback:
1. Restore database from backups
2. Git revert the code changes
3. Rebuild and redeploy the application

---

## Benefits of Changes

1. **Flexibility**: Role names can now be any string, not limited to predefined enums
2. **Simplicity**: One-to-many relationship is simpler than many-to-many
3. **Performance**: No junction table reduces query complexity
4. **Email-based auth**: More common pattern, email is already unique
5. **Easier integration**: Custom roles can be added without code changes

---

## Compilation Status
✅ **BUILD SUCCESS** - All changes compiled successfully with no errors.

---

## Next Steps

1. **Run database migration**: Execute `database_migration_refactor.sql`
2. **Update test files**: Fix all test cases to use new structure
3. **Test API endpoints**: Verify signup and signin work with new payload structure
4. **Update documentation**: Update API documentation and client integration guides
5. **Inform API consumers**: Notify all API clients about breaking changes

---

*Last Updated: December 18, 2025*
