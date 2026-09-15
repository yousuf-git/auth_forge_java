# Auth Forge - API Endpoints Reference

> Auto-generated endpoint documentation for coding agent context.
> Base URL: `http://localhost:8080`

---

## Table of Contents

- [Global Information](#global-information)
- [Auth Endpoints (Public)](#auth-endpoints-public)
  - [Email & OTP Flows](#email--otp-flows)
- [User Endpoints (Authenticated)](#user-endpoints-authenticated)
- [Admin Endpoints (ROLE_ADMIN)](#admin-endpoints-role_admin)
- [Manager Endpoints (ROLE_PLANT_MANAGER or ROLE_ADMIN)](#manager-endpoints-role_plant_manager-or-role_admin)
- [OAuth2 Endpoints](#oauth2-endpoints)
- [Test Endpoints](#test-endpoints)
- [Other Endpoints](#other-endpoints)

---

## Global Information

### Authentication Mechanism

| Property | Value |
|---|---|
| Access Token Type | JWT Bearer token |
| Access Token Header | `Authorization: Bearer <token>` |
| Refresh Token Storage | HttpOnly secure cookie named `refreshToken` |
| Access Token Default Expiry | 5 minutes (configurable) |
| Refresh Token Default Expiry | 7 days (configurable) |
| Token Rotation | Refresh tokens are rotated on each use; old token is revoked |
| Token Theft Detection | If a revoked refresh token is reused, the entire token family is revoked |

### Roles

| Role | Description |
|---|---|
| `ROLE_CUSTOMER` | Default role assigned to new users |
| `ROLE_PLANT_MANAGER` | Manager role with elevated access |
| `ROLE_ADMIN` | Administrator with full access to all endpoints |

### Auth Providers

| Provider | Status |
|---|---|
| `LOCAL` | Email/password authentication (active) |
| `GOOGLE` | Google OAuth2 (active) |
| `GITHUB` | GitHub OAuth2 (infrastructure ready) |
| `FACEBOOK` | Facebook OAuth2 (infrastructure ready) |

### Email Verification & OTP

Registration uses a **two-step flow**: signup creates the user (unverified), and email verification completes registration with auth tokens.

| Feature | Description |
|---|---|
| `emailVerified` | Boolean flag in user profile indicating verification status |
| OTP Storage | **Redis** with automatic TTL-based expiration |
| Registration Flow | Signup returns 202 + OTP sent; Verify-email returns auth tokens |
| Login Restriction | Users with `emailVerified=false` cannot login (403 with `action: VERIFY_EMAIL`) |
| Password Reset Flow | Forgot-password sends OTP; Reset-password changes password + revokes all sessions |
| OTP Properties | 10-minute expiry, max 5 failed attempts, 60-second cooldown, 5/hour per user |
| Anti-Enumeration | Forgot-password always returns generic success message |

### Error Response Formats

| Scenario | Status Code | Response Body |
|---|---|---|
| Validation errors | 400 | `{ "error": "Validation failed", "fields": { "field1": "msg", ... } }` |
| Business errors | 400 / 401 / 403 / 404 | `{ "message": "Error description" }` |
| Data conflict | 409 | `{ "message": "Data conflict: a record with the same unique value already exists." }` |
| Rate limiting | 429 | `{ "message": "Too many requests. Please try again later." }` + `Retry-After` header |
| File too large | 413 | `{ "message": "File size exceeds the allowed limit." }` |
| Server errors | 500 | `{ "message": "An unexpected error occurred. Please try again later." }` |

### Rate Limiting

| Endpoint | Limit | Retry-After |
|---|---|---|
| `POST /auth/signin` | 10 requests/minute per IP | 60 seconds |
| `POST /auth/signup` | 10 requests/minute per IP | 60 seconds |
| `POST /auth/refresh` | 20 requests/minute per IP | 60 seconds |

### Security Headers

All responses include:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

---

## Auth Endpoints (Public)

**Base path:** `/auth`
**Authentication:** None required

---

### 1. POST /auth/signup

Register a new user account. Creates user with `emailVerified=false` and sends a verification OTP.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `firstName` | string | `@NotBlank` | Yes | User's first name |
| `lastName` | string | `@NotBlank` | Yes | User's last name |
| `email` | string | `@Email` | Yes | Valid email address |
| `password` | string | `@Size(min=6, max=30)` | Yes | User password |
| `phone` | string | — | No | Phone number |
| `role` | string | — | No | Role name to assign (looked up from DB) |

**Response 202 (Accepted):**

```json
{
  "message": "Registration successful. A verification code has been sent to your email.",
  "email": "user@example.com",
  "otpExpiresInSeconds": 600
}
```

**Response 202 (Accepted) — Existing unverified user (OTP resent):**

```json
{
  "message": "A verification code has been sent to your email. Please verify to complete registration.",
  "email": "user@example.com",
  "otpExpiresInSeconds": 600
}
```

**Response 409 (Conflict):**

```json
{ "message": "Email already registered." }
```

**Response 400 (Bad Request):**

```json
{ "message": "Role 'invalid_role' not found." }
```

Or validation errors:

```json
{ "error": "Validation failed", "fields": { "email": "must be a valid email", "password": "size must be between 6 and 30" } }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "password": "secret123",
    "phone": "+1234567890"
  }'
```

---

### 2. POST /auth/signin

Authenticate with email and password. **Requires email to be verified.**

**Request Body:**

| Field | Type | Validation | Required |
|---|---|---|---|
| `email` | string | `@Email` | Yes |
| `password` | string | `@Size(min=6, max=30)` | Yes |

**Response 200 (OK):** AuthResponse

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "role": "ROLE_CUSTOMER",
  "scopes": "read:user write:user",
  "expiresIn": 300000,
  "emailVerified": true
}
```

**AuthResponse Fields:**

| Field | Type | Description |
|---|---|---|
| `accessToken` | string | JWT access token |
| `tokenType` | string | Always "Bearer" |
| `userId` | int | User ID |
| `email` | string | User email |
| `role` | string | User role (e.g., ROLE_CUSTOMER) |
| `scopes` | string | Space-separated permissions (e.g., "read:user write:user") |
| `expiresIn` | long | Token expiration time in milliseconds |
| `emailVerified` | boolean | Whether email is verified |

**Access Token Payload:**

The JWT access token contains the following claims:

```json
{
  "iss": "M. Yousuf",
  "sub": "user@example.com",
  "role": "ROLE_CUSTOMER",
  "scopes": "read:user write:user",
  "user_id": 1,
  "iat": 1705312200,
  "exp": 1705312500
}
```

Also sets `refreshToken` as an HttpOnly cookie.

**Response 403 (Forbidden) — Email not verified:**

```json
{
  "message": "Email not verified. Please verify your email before logging in.",
  "email": "user@example.com",
  "emailVerified": false,
  "action": "VERIFY_EMAIL"
}
```

**Response 401 (Unauthorized):**

```json
{ "message": "Invalid email or password." }
```

**Response 403 (Forbidden) — Account locked:**

```json
{ "message": "Account is locked. Please contact administrator." }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/signin \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "user@example.com",
    "password": "secret123"
  }'
```

---

### 3. POST /auth/refresh

Refresh the access token using the refresh token cookie. Implements token rotation.

**Request Body:** None (reads `refreshToken` from HttpOnly cookie)

**Response 200 (OK):** AuthResponse

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "role": "ROLE_CUSTOMER",
  "scopes": "read:user write:user",
  "expiresIn": 300000,
  "emailVerified": true
}
```

Also rotates the refresh token cookie (old cookie replaced with new one).

**Response 401 (Unauthorized):**

```json
{ "message": "Session expired. Please login again." }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -c cookies.txt
```

---

### 4. POST /auth/logout

Logout the current session. Revokes the current refresh token.

**Request Body:** None (reads `refreshToken` from cookie)

**Response 200 (OK):**

```json
{ "message": "Logged out successfully." }
```

Clears the `refreshToken` cookie.

---

### 5. POST /auth/logout-all

Logout from all devices. Revokes all refresh tokens for the current user.

**Request Body:** None

**Response 200 (OK):**

```json
{ "message": "Logged out from all devices." }
```

Clears the `refreshToken` cookie and revokes all user sessions.

---

## Email & OTP Flows

These endpoints handle email verification, password recovery, and OTP management. OTPs are stored in **Redis** with automatic expiration.

### 6. POST /auth/verify-email

Verify user's email address using OTP. On success, returns authentication tokens (auto-login).

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | Yes | Email address to verify |
| `otp` | string | `@Size(min=6, max=6)` | Yes | 6-digit OTP from email |

**Response 200 (OK) — AuthResponse (user is now logged in):**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "role": "ROLE_CUSTOMER",
  "scopes": "read:user write:user",
  "expiresIn": 300000,
  "emailVerified": true
}
```

Also sets `refreshToken` as an HttpOnly cookie.

**Response 200 (OK) — Already verified:**

```json
{ "message": "Email is already verified. You can login." }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/verify-email \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{
    "email": "user@example.com",
    "otp": "123456"
  }'
```

**Error Scenarios:**

| Status | Message | Cause |
|---|---|---|
| 400 | No account found with this email. | Email not in system |
| 400 | OTP expired or not found. Please request a new one. | OTP not in Redis (expired) |
| 400 | Invalid OTP. X attempt(s) remaining. | Wrong OTP code |
| 400 | Maximum verification attempts exceeded. | 5 failed attempts — OTP invalidated |

---

### 7. POST /auth/resend-otp

Resend OTP to user's email. Use when previous OTP expires or for post-registration verification.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | Yes | Email address to send OTP to |
| `type` | string | `@NotBlank` | Yes | `EMAIL_VERIFICATION` or `PASSWORD_RESET` |

**Response 200 (OK):**

```json
{
  "message": "OTP sent successfully. Please check your email.",
  "otpExpiresInSeconds": 600
}
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/resend-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "type": "EMAIL_VERIFICATION"
  }'
```

**Error Scenarios:**

| Status | Message | Cause |
|---|---|---|
| 400 | Invalid OTP type. | Type is not `EMAIL_VERIFICATION` or `PASSWORD_RESET` |
| 400 | No account found with this email. | Email not in system (EMAIL_VERIFICATION type only) |
| 400 | Please wait X seconds... | Cooldown not elapsed (60s) |
| 400 | Too many OTP requests. | Exceeded 5 per hour |
| 200 | Email is already verified. | User already verified (EMAIL_VERIFICATION type) |
| 200 | If an account exists... | Generic message for PASSWORD_RESET (anti-enumeration) |

---

### 8. POST /auth/forgot-password

Initiate password reset flow. Sends a 6-digit OTP to the user's email if the account exists.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | Yes | Registered email address |

**Response 200 (OK):**

```json
{ "message": "If an account exists with this email, we've sent a password reset code." }
```

**Note:** Always returns the same success message regardless of whether the account exists (anti-enumeration protection).

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'
```

---

### 9. POST /auth/reset-password

Reset password using OTP received via email.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | Yes | Registered email address |
| `otp` | string | `@Size(min=6, max=6)` | Yes | 6-digit OTP from email |
| `newPassword` | string | `@Size(min=6, max=30)` | Yes | New password |

**Response 200 (OK):**

```json
{ "message": "Password has been reset successfully. Please login with your new password." }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "otp": "123456",
    "newPassword": "newPassword123"
  }'
```

**Error Scenarios:**

| Status | Message | Cause |
|---|---|---|
| 400 | Invalid email or OTP. | Email doesn't exist |
| 400 | OTP expired or not found. | OTP not in Redis |
| 400 | Invalid OTP. X attempt(s) remaining. | Wrong OTP |
| 400 | Maximum verification attempts exceeded. | Too many failed attempts |

---

### OTP Configuration

All OTP-based flows share these settings (configurable in `application-dev.yml` under `yousuf.app.otp`):

| Setting | Default | Purpose |
|---|---|---|
| OTP Expiry | 10 minutes | How long OTP code remains valid |
| Max Attempts | 5 | Maximum wrong attempts before OTP is invalidated |
| Cooldown Period | 60 seconds | Minimum time between consecutive OTP requests |
| Rate Limit | 5 per hour | Maximum OTPs sent per user per hour |

---

## User Endpoints (Authenticated)

**Base path:** `/api/user`
**Authentication:** Bearer token (any authenticated user)

---

### 10. GET /api/user/profile

Get the currently authenticated user's profile.

**Response 200 (OK):** UserDTO

```json
{
  "id": 1,
  "firstName": "John",
  "lastName": "Doe",
  "email": "user@example.com",
  "phone": "+1234567890",
  "cnic": "12345-6789012-1",
  "country": "Pakistan",
  "city": "Karachi",
  "province": "Sindh",
  "area": "Gulshan",
  "address": "123 Main Street",
  "provider": "LOCAL",
  "emailVerified": true,
  "roleName": "ROLE_CUSTOMER",
  "isLocked": false,
  "createdAt": "2025-01-15T10:30:00Z",
  "modifiedAt": "2025-01-15T10:30:00Z",
  "imageUrl": "https://example.com/avatar.png"
}
```

**UserDTO Fields:**

| Field | Type | Description |
|---|---|---|
| `id` | int | User ID |
| `firstName` | string | User's first name |
| `lastName` | string | User's last name |
| `email` | string | Email address |
| `phone` | string | Phone number (nullable) |
| `cnic` | string | CNIC/National ID (nullable, unique) |
| `country` | string | Country (nullable) |
| `city` | string | City (nullable) |
| `province` | string | Province/State (nullable) |
| `area` | string | Area (nullable) |
| `address` | string | Address (nullable) |
| `provider` | string | Auth provider: `LOCAL`, `GOOGLE`, `GITHUB`, `FACEBOOK` |
| `emailVerified` | boolean | Whether email is verified |
| `roleName` | string | Assigned role name |
| `isLocked` | boolean | Whether account is locked |
| `createdAt` | timestamp | Account creation time |
| `modifiedAt` | timestamp | Last modification time |
| `imageUrl` | string | Profile image URL (nullable) |

---

### 11. GET /api/user/sessions

Get all active sessions for the current user.

**Response 200 (OK):** Array of SessionDTO

```json
[
  {
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "deviceId": "device-fingerprint-string",
    "ipAddress": "192.168.1.100",
    "userAgent": "Chrome 120 on Windows 10",
    "issuedAt": "2025-01-15T10:30:00Z",
    "lastUsedAt": "2025-01-15T11:00:00Z",
    "expiresAt": "2025-01-22T10:30:00Z",
    "isCurrentSession": true
  }
]
```

**SessionDTO Fields:**

| Field | Type | Description |
|---|---|---|
| `sessionId` | string (UUID) | Unique session identifier |
| `deviceId` | string | Device fingerprint |
| `ipAddress` | string | IP address of the session |
| `userAgent` | string | Parsed user-agent string |
| `issuedAt` | instant | When the session was created |
| `lastUsedAt` | instant | When the session was last used |
| `expiresAt` | instant | When the session expires |
| `isCurrentSession` | boolean | Whether this is the requesting session |

---

### 12. DELETE /api/user/sessions/{sessionId}

Revoke a specific session belonging to the current user.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | string (UUID) | The session ID to revoke |

**Response 200 (OK):**

```json
{ "message": "Session revoked successfully" }
```

If revoking the current session, the `refreshToken` cookie is also cleared.

**Response 400 (Bad Request):**

```json
{ "message": "Session already revoked" }
```

**Response 404 (Not Found):** If session not found or does not belong to the current user.

---

### 13. DELETE /api/user/sessions/other

Revoke all sessions except the current one.

**Response 200 (OK):**

```json
{ "message": "Revoked N other session(s)" }
```

---

### 14. POST /api/user/change-password

Change the current user's password. For LOCAL provider users only.

**Authentication:** Bearer token (any authenticated user)

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `currentPassword` | string | — | Yes | User's current password |
| `newPassword` | string | `@Size(min=6, max=30)` | Yes | New password (must differ from current) |

**Response 200 (OK):**

```json
{ "message": "Password changed successfully. All other sessions have been logged out." }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/api/user/change-password \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "oldPassword123",
    "newPassword": "newPassword456"
  }'
```

**Error Scenarios:**

| Status | Message | Cause |
|---|---|---|
| 400 | Current password is incorrect. | Current password does not match |
| 400 | New password must be different... | New password same as current |
| 400 | Password cannot be changed for... | OAuth user |
| 401 | Unauthorized | No valid Bearer token |

---

## Admin Endpoints (ROLE_ADMIN)

**Base path:** `/api/admin`
**Authentication:** Bearer token with `ROLE_ADMIN`

---

### User Management

---

### 15. GET /api/admin/users

List all users in the system.

**Response 200 (OK):** Array of UserDTO

```json
[
  {
    "id": 1,
    "email": "admin@example.com",
    "phone": null,
    "provider": "LOCAL",
    "emailVerified": true,
    "roleName": "ROLE_ADMIN",
    "isLocked": false,
    "createdAt": "2025-01-01T00:00:00Z",
    "modifiedAt": "2025-01-01T00:00:00Z",
    "imageUrl": null
  }
]
```

---

### 16. GET /api/admin/users/{id}

Get a specific user by ID.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `id` | int | Positive integer | User ID |

**Response 200 (OK):** UserDTO

**Response 404 (Not Found):** If user does not exist.

---

### 17. POST /api/admin/users

Create a new user (admin-created).

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | Yes | Email address |
| `password` | string | `@Size(min=6, max=30)` | Yes | User password |
| `firstName` | string | — | Yes | User's first name |
| `lastName` | string | — | Yes | User's last name |
| `phone` | string | `@Size(max=20)` | No | Phone number |
| `cnic` | string | `@Size(max=25)` | No | CNIC/National ID |
| `country` | string | — | No | Country |
| `city` | string | — | No | City |
| `province` | string | — | No | Province/State |
| `area` | string | — | No | Area |
| `address` | string | — | No | Address |
| `roleId` | int | — | No | Role ID to assign |
| `isLocked` | boolean | — | No | Whether account is locked |

**Response 201 (Created):** UserDTO

**Response 400 (Bad Request):**

```json
{ "message": "Email already exists" }
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/api/admin/users \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin_access_token>" \
  -d '{
    "email": "newemployee@example.com",
    "password": "tempPass123",
    "firstName": "John",
    "lastName": "Smith",
    "phone": "+1234567890",
    "cnic": "12345-6789012-1",
    "country": "Pakistan",
    "city": "Karachi",
    "roleId": 2,
    "isLocked": false
  }'
```

---

### 18. PUT /api/admin/users/{id}

Update an existing user. All fields are optional; only provided fields are updated.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `id` | int | Positive integer | User ID |

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `email` | string | `@Email` | No | New email address |
| `password` | string | `@Size(min=6, max=30)` | No | New password |
| `firstName` | string | — | Yes | User's first name |
| `lastName` | string | — | Yes | User's last name |
| `phone` | string | `@Size(max=20)` | No | New phone number |
| `cnic` | string | `@Size(max=25)` | No | CNIC/National ID |
| `country` | string | — | No | Country |
| `city` | string | — | No | City |
| `province` | string | — | No | Province/State |
| `area` | string | — | No | Area |
| `address` | string | — | No | Address |
| `roleId` | int | — | No | New role ID |
| `isLocked` | boolean | — | No | Lock/unlock account |

**Response 200 (OK):** UserDTO

**Response 400 (Bad Request):**

```json
{ "message": "Email already exists" }
```

**Response 404 (Not Found):** If user does not exist.

---

### 19. DELETE /api/admin/users/{id}

Delete a user.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `id` | int | Positive integer | User ID |

**Response 200 (OK):**

```json
{ "message": "User deleted successfully" }
```

**Response 404 (Not Found):** If user does not exist.

---

### Role Management

---

### 20. GET /api/admin/roles

List all roles.

**Response 200 (OK):** Array of Role objects

```json
[
  {
    "id": 1,
    "name": "ROLE_CUSTOMER",
    "description": "Default customer role",
    "createdAt": "2025-01-01T00:00:00Z",
    "isActive": true,
    "permissions": [
      {
        "id": 1,
        "name": "READ_PROFILE",
        "description": "Can read own profile",
        "createdAt": "2025-01-01T00:00:00Z",
        "isActive": true
      }
    ]
  }
]
```

**Role Object Fields:**

| Field | Type | Description |
|---|---|---|
| `id` | int | Role ID |
| `name` | string | Role name (e.g., `ROLE_CUSTOMER`) |
| `description` | string | Role description (nullable) |
| `createdAt` | timestamp | Creation time |
| `isActive` | boolean | Whether the role is active |
| `permissions` | array | Array of Permission objects assigned to this role |

---

### 21. GET /api/admin/roles/{id}

Get a specific role by ID.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | int | Role ID |

**Response 200 (OK):** Role object

**Response 404 (Not Found):** If role does not exist.

---

### 22. POST /api/admin/roles

Create a new role.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `name` | string | `@NotBlank @Size(max=100)` | Yes | Role name |
| `description` | string | `@Size(max=255) & start with prefix ROLE_` | No | Role description |
| `permissionIds` | int[] | — | No | Array of permission IDs to assign |

**Response 201 (Created):** Role object

**Response 400 (Bad Request):**

```json
{ "message": "Role with this name already exists" }
```

---

### 23. PUT /api/admin/roles/{id}

Update an existing role. All fields are optional.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | int | Role ID |

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `name` | string | `@Size(max=100)` | No | New role name |
| `description` | string | `@Size(max=255)` | No | New description |
| `permissionIds` | int[] | — | No | New set of permission IDs |

**Response 200 (OK):** Role object

**Response 400 (Bad Request):**

```json
{ "message": "Role name already exists" }
```

**Response 404 (Not Found):** If role does not exist.

---

### 24. DELETE /api/admin/roles/{id}

Delete a role. Fails if users are still assigned to it.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | int | Role ID |

**Response 200 (OK):**

```json
{ "message": "Role deleted successfully" }
```

**Response 400 (Bad Request):**

```json
{ "message": "Cannot delete role: N users are assigned to this role" }
```

**Response 404 (Not Found):** If role does not exist.

---

### Permission Management

---

### 25. GET /api/admin/permissions

List all permissions.

**Response 200 (OK):** Array of Permission objects

```json
[
  {
    "id": 1,
    "name": "READ_PROFILE",
    "description": "Can read own profile",
    "createdAt": "2025-01-01T00:00:00Z",
    "isActive": true
  }
]
```

**Permission Object Fields:**

| Field | Type | Description |
|---|---|---|
| `id` | int | Permission ID |
| `name` | string | Permission name |
| `description` | string | Permission description (nullable) |
| `createdAt` | timestamp | Creation time |
| `isActive` | boolean | Whether the permission is active |

---

### 26. GET /api/admin/permissions/{id}

Get a specific permission by ID.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | int | Permission ID |

**Response 200 (OK):** Permission object

**Response 404 (Not Found):** If permission does not exist.

---

### 27. POST /api/admin/permissions

Create a new permission.

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `name` | string | `@NotBlank @Size(max=50)` | Yes | Permission name |
| `description` | string | `@Size(max=255)` | No | Permission description |

**Response 201 (Created):** Permission object

**Response 400 (Bad Request):**

```json
{ "message": "Permission already exists" }
```

---

### 28. DELETE /api/admin/permissions/{id}

Delete a permission.

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | int | Permission ID |

**Response 200 (OK):**

```json
{ "message": "Permission deleted successfully" }
```

**Response 404 (Not Found):** If permission does not exist.

---

### Session Management (Admin)

---

### 29. GET /api/admin/sessions

List all active sessions across all users. Admin view includes `userId` and `userEmail` fields.

**Response 200 (OK):** Array of SessionDTO (with additional admin fields)

```json
[
  {
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "userId": 1,
    "userEmail": "user@example.com",
    "deviceId": "device-fingerprint-string",
    "ipAddress": "192.168.1.100",
    "userAgent": "Chrome 120 on Windows 10",
    "issuedAt": "2025-01-15T10:30:00Z",
    "lastUsedAt": "2025-01-15T11:00:00Z",
    "expiresAt": "2025-01-22T10:30:00Z",
    "isCurrentSession": false
  }
]
```

---

### 30. GET /api/admin/users/{userId}/sessions

List all active sessions for a specific user.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `userId` | int | Positive integer | User ID |

**Response 200 (OK):** Array of SessionDTO

**Response 404 (Not Found):** If user does not exist.

---

### 31. DELETE /api/admin/sessions/{sessionId}

Revoke a specific session (any user's session).

**Path Parameters:**

| Parameter | Type | Description |
|---|---|---|
| `sessionId` | string (UUID) | Session ID to revoke |

**Response 200 (OK):**

```json
{ "message": "Session revoked successfully" }
```

**Response 400 (Bad Request):**

```json
{ "message": "Session already revoked" }
```

**Response 404 (Not Found):** If session does not exist.

---

### 32. DELETE /api/admin/users/{userId}/sessions

Revoke all sessions for a specific user.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `userId` | int | Positive integer | User ID |

**Response 200 (OK):**

```json
{ "message": "All sessions revoked for user" }
```

**Response 404 (Not Found):** If user does not exist.

---

### 33. GET /api/admin/sessions/stats

Get session statistics across the system.

**Response 200 (OK):**

```json
{
  "totalActiveSessions": 42,
  "usersWithActiveSessions": 15,
  "totalUsers": 100
}
```

| Field | Type | Description |
|---|---|---|
| `totalActiveSessions` | long | Total number of active sessions |
| `usersWithActiveSessions` | long | Number of users with at least one active session |
| `totalUsers` | long | Total number of registered users |

---

## Manager Endpoints (ROLE_PLANT_MANAGER or ROLE_ADMIN)

**Base path:** `/api/manager`
**Authentication:** Bearer token with `ROLE_PLANT_MANAGER` or `ROLE_ADMIN`

---

### 34. GET /api/manager/customers

List all customers (users with `ROLE_CUSTOMER`).

**Response 200 (OK):** Array of UserDTO

---

### 35. PUT /api/manager/reset-password/{userId}

Reset a user's password.

**Path Parameters:**

| Parameter | Type | Validation | Description |
|---|---|---|---|
| `userId` | int | Positive integer | Target user ID |

**Request Body:**

| Field | Type | Validation | Required | Description |
|---|---|---|---|---|
| `newPassword` | string | `@NotBlank @Size(min=6, max=30)` | Yes | New password |

**Response 200 (OK):**

```json
{ "message": "Password reset successfully" }
```

**Response 404 (Not Found):** If user does not exist.

---

## OAuth2 Endpoints

**Base path:** `/oauth2`
**Authentication:** Varies per endpoint

The OAuth2 endpoints implement standard OAuth2.0 flows as per RFC 6749 (Token Endpoint) and RFC 7662 (Token Introspection).

---

### 36. GET /oauth2/authorize/google

Initiate Google OAuth2 login flow. Redirects the user to Google's authorization page.

**Authentication:** None required

**Flow:**

1. Client redirects user to `GET /oauth2/authorize/google`
2. Server redirects to Google login page
3. User authenticates and consents
4. Google redirects back to `/login/oauth2/code/google` with authorization code
5. Server exchanges code for Google tokens
6. Server finds or creates user in database
7. Server returns AuthResponse with access token + sets refresh token cookie

**Response:** Redirect to Google OAuth2 authorization page

**curl Example:**

```bash
# Open in browser — not suitable for curl directly
# Your frontend should redirect the user to:
http://localhost:8080/oauth2/authorize/google
```

---

### 37. POST /oauth2/token

OAuth2 Token Endpoint (RFC 6749). Issue access tokens via password credentials or refresh token grant.

**Content-Type:** `application/x-www-form-urlencoded`
**Authentication:** None required

**Supported Grant Types:**

| Grant Type | Description |
|---|---|
| `password` | Resource Owner Password Credentials (RFC 6749 Section 4.3) |
| `refresh_token` | Refresh Token Grant (RFC 6749 Section 6) |

**Request Parameters (password grant):**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `grant_type` | string | Yes | Must be `password` |
| `username` | string | Yes | User email address |
| `password` | string | Yes | User password |
| `scope` | string | No | Requested scope (optional) |

**Request Parameters (refresh_token grant):**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `grant_type` | string | Yes | Must be `refresh_token` |
| `refresh_token` | string | No | Refresh token (reads from cookie if not provided) |

**Response 200 (OK) — Token Response (RFC 6749 Section 5.1):**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "scope": "ROLE_CUSTOMER",
  "scopes": "read:user write:user",
  "user_id": 1,
  "email": "user@example.com"
}
```

**Token Response Fields:**

| Field | Type | Description |
|---|---|---|
| `access_token` | string | JWT access token |
| `token_type` | string | Always "Bearer" |
| `expires_in` | int | Token expiration in seconds |
| `refresh_token` | string | Refresh token value |
| `scope` | string | User role (for backward compatibility) |
| `scopes` | string | Space-separated permissions |
| `user_id` | int | User ID |
| `email` | string | User email |

Also sets `refreshToken` as an HttpOnly cookie.

**Error Responses (RFC 6749 Section 5.2):**

| Status | Error Code | Description |
|---|---|---|
| 400 | `unsupported_grant_type` | Grant type not `password` or `refresh_token` |
| 400 | `invalid_request` | Missing required parameters |
| 401 | `invalid_grant` | Invalid credentials |
| 403 | `invalid_grant` | Email not verified or account locked |

```json
{
  "error": "invalid_grant",
  "error_description": "Invalid username or password."
}
```

**curl Examples:**

```bash
# Password Grant
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -c cookies.txt \
  -d "grant_type=password&username=user@example.com&password=secret123"

# Refresh Token Grant (from parameter)
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=<your_refresh_token>"

# Refresh Token Grant (from cookie)
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -b cookies.txt \
  -c cookies.txt \
  -d "grant_type=refresh_token"
```

---

### 38. POST /oauth2/introspect

Token Introspection Endpoint (RFC 7662). Allows Resource Servers to validate access tokens and retrieve associated metadata.

**Content-Type:** `application/x-www-form-urlencoded`
**Authentication:** None required (token itself is validated)

**Request Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `token` | string | Yes | The access token to introspect |
| `token_type_hint` | string | No | Hint about the token type (ignored) |

**Response 200 (OK) — Active Token (RFC 7662 Section 2.2):**

```json
{
  "active": true,
  "sub": "user@example.com",
  "username": "user@example.com",
  "scope": "ROLE_CUSTOMER",
  "user_id": 1,
  "iss": "M. Yousuf",
  "iat": 1705312200,
  "exp": 1705312500,
  "token_type": "Bearer"
}
```

**Response 200 (OK) — Inactive/Invalid Token:**

```json
{
  "active": false
}
```

**curl Example:**

```bash
curl -X POST http://localhost:8080/oauth2/introspect \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=eyJhbGciOiJSUzI1NiJ9..."
```

---

### 39. GET /oauth2/introspect

Token Introspection via GET (convenience endpoint). While RFC 7662 specifies POST, this GET endpoint is provided for simpler Resource Server integrations.

**Authentication:** None required

**Query Parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `token` | string | Yes | The access token to introspect |

**Response:** Same as POST /oauth2/introspect

**curl Example:**

```bash
curl "http://localhost:8080/oauth2/introspect?token=eyJhbGciOiJSUzI1NiJ9..."
```

---

### 40. GET /oauth2/user

Get the current OAuth2 authenticated user's info.

**Authentication:** Bearer token

**Response 200 (OK):**

```json
{
  "id": 1,
  "username": "user@example.com",
  "email": "user@example.com",
  "roles": [{"authority": "ROLE_CUSTOMER"}]
}
```

---

### 41. GET /oauth2/redirect

OAuth2 redirect handler (used internally by the OAuth2 flow).

**Response 200 (OK):**

```json
{
  "message": "OAuth2 authentication successful",
  "note": "Frontend should extract token from URL parameters"
}
```

---

## Test Endpoints

**Base path:** `/test`
**Note:** These endpoints are for development and testing purposes.

---

### 42. GET /test/all

Public test endpoint. No authentication required.

**Response 200 (OK):**

```
This endpoint is available for all
```

---

### 43. GET /test/user

Test endpoint accessible by users.

**Authentication:** `ROLE_USER`, `ROLE_ADMIN`, or `ROLE_MODERATOR`

**Response 200 (OK):**

```
User's Content is here :)
```

---

### 44. GET /test/mod

Test endpoint accessible by moderators.

**Authentication:** `ROLE_ADMIN` or `ROLE_MODERATOR`

**Response 200 (OK):**

```
Mod's Content is here :)
```

---

### 45. GET /test/admin

Test endpoint accessible by admins only.

**Authentication:** `ROLE_ADMIN`

**Response 200 (OK):**

```
Admin's Content is here :)
```

---

## Other Endpoints

---

### 46. GET /greet

Public greeting endpoint. No authentication required.

**Response 200 (OK):**

```
Greetings !
```

---

### 47. GET /api/public-key

Public endpoint to retrieve the RSA public key for external JWT validation.

**Authentication:** None required

**Response 200 (OK):** 

```
-----BEGIN PUBLIC KEY-----
MIIBIj......TQIDAQAB
-----END PUBLIC KEY-----
```

---

## Quick Reference: Endpoint Summary

### Public Endpoints (No Auth)

| Method | Path | Description |
|---|---|---|
| POST | `/auth/signup` | Register new user (returns 202 + OTP sent) |
| POST | `/auth/signin` | Login (requires verified email) |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/logout` | Logout current session |
| POST | `/auth/logout-all` | Logout all sessions |
| POST | `/auth/verify-email` | Verify email with OTP (returns auth tokens) |
| POST | `/auth/resend-otp` | Resend OTP to email |
| POST | `/auth/forgot-password` | Initiate password reset (send OTP) |
| POST | `/auth/reset-password` | Reset password with OTP |
| GET | `/oauth2/authorize/google` | Initiate Google OAuth2 login |
| POST | `/oauth2/token` | OAuth2 token endpoint (password/refresh_token grants) |
| POST | `/oauth2/introspect` | Token introspection (RFC 7662) |
| GET | `/oauth2/introspect` | Token introspection via GET |
| GET | `/api/public-key/jwks.json` | RSA public key (JWKS) |
| GET | `/test/all` | Public test endpoint |
| GET | `/greet` | Greeting endpoint |
| GET | `/oauth2/redirect` | OAuth2 redirect handler |

### Authenticated Endpoints (Any Role)

| Method | Path | Description |
|---|---|---|
| GET | `/api/user/profile` | Get current user profile |
| GET | `/api/user/sessions` | List own sessions |
| DELETE | `/api/user/sessions/{sessionId}` | Revoke own session |
| DELETE | `/api/user/sessions/other` | Revoke all other sessions |
| POST | `/api/user/change-password` | Change password (LOCAL provider only) |
| GET | `/oauth2/user` | Get OAuth2 user info |

### Manager Endpoints (ROLE_PLANT_MANAGER / ROLE_ADMIN)

| Method | Path | Description |
|---|---|---|
| GET | `/api/manager/customers` | List all customers |
| PUT | `/api/manager/reset-password/{userId}` | Reset user password |

### Admin Endpoints (ROLE_ADMIN)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/users` | List all users |
| GET | `/api/admin/users/{id}` | Get user by ID |
| POST | `/api/admin/users` | Create user |
| PUT | `/api/admin/users/{id}` | Update user |
| DELETE | `/api/admin/users/{id}` | Delete user |
| GET | `/api/admin/roles` | List all roles |
| GET | `/api/admin/roles/{id}` | Get role by ID |
| POST | `/api/admin/roles` | Create role |
| PUT | `/api/admin/roles/{id}` | Update role |
| DELETE | `/api/admin/roles/{id}` | Delete role |
| GET | `/api/admin/permissions` | List all permissions |
| GET | `/api/admin/permissions/{id}` | Get permission by ID |
| POST | `/api/admin/permissions` | Create permission |
| DELETE | `/api/admin/permissions/{id}` | Delete permission |
| GET | `/api/admin/sessions` | List all active sessions |
| GET | `/api/admin/users/{userId}/sessions` | List user's sessions |
| DELETE | `/api/admin/sessions/{sessionId}` | Revoke session |
| DELETE | `/api/admin/users/{userId}/sessions` | Revoke all user sessions |
| GET | `/api/admin/sessions/stats` | Session statistics |

### Test Endpoints

| Method | Path | Auth Required | Description |
|---|---|---|---|
| GET | `/test/all` | None | Public test |
| GET | `/test/user` | ROLE_USER / ROLE_ADMIN / ROLE_MODERATOR | User test |
| GET | `/test/mod` | ROLE_ADMIN / ROLE_MODERATOR | Moderator test |
| GET | `/test/admin` | ROLE_ADMIN | Admin test |
