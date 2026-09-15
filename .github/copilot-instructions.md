# Copilot Instructions - Auth Forge

## Architecture Overview

This is a **Spring Boot 3.4 + Java 21** authentication server with JWT-based stateless authentication, OAuth2 (Google), and role-based access control.

### Core Security Flow
1. **Authentication**: `AuthController` → `AuthenticationManager` → `UserDetailsServiceImpl` → `UserDetailsImpl`
2. **Authorization**: `AuthTokenFilter` (OncePerRequestFilter) extracts JWT → `JwtUtils` validates → sets `SecurityContext`
3. **Token Strategy**: Short-lived access tokens (5 min) + long-lived refresh tokens (7 days) with rotation

### Key Components
| Layer | Location | Purpose |
|-------|----------|---------|
| Security Config | `configs/WebSecurityConfig.java` | Filter chain, public endpoints, OAuth2 setup |
| JWT Filter | `auth/AuthTokenFilter.java` | Intercepts requests, validates tokens |
| Token Utils | `utils/JwtUtils.java` | Generate/validate/parse JWTs (jjwt 0.12.6) |
| Refresh Tokens | `services/RefreshTokenService.java` | Token rotation, family tracking, theft detection |
| Entry Points | `auth/AuthEntryPointJwt.java`, `JwtAccessDeniedHandler.java` | 401/403 responses |

## Project Conventions

### Entity Patterns
- Use **Lombok** (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- Audit fields: `createdAt`, `modifiedAt`, `createdBy`, `modifiedBy` with `@CreationTimestamp`/`@UpdateTimestamp`
- Roles: `User` has single `Role` via `@ManyToOne`; roles stored in DB (not enum)
- See: [models/User.java](src/main/java/com/learning/security/models/User.java), [models/Role.java](src/main/java/com/learning/security/models/Role.java)

### DTO Naming
- Requests: `LoginRequest`, `SignUpRequest` in `dtos/`
- Responses: `AuthResponse`, `ResponseMessage` (use for error messages)
- Pattern: `@Valid` on controller params; validation in DTOs with Jakarta annotations

### Exception Handling
- Global handler: `exceptions/GlobalExceptionHandler.java` with `@RestControllerAdvice`
- Custom JWT exceptions: `CustomJwtException` stored in request attribute for `AuthEntryPointJwt`

## Build & Run Commands

```bash
# Build (skip tests for speed)
./mvnw clean package -DskipTests

# Run with dev profile (default)
./mvnw spring-boot:run

# Run tests (uses H2 in-memory DB)
./mvnw test

# Docker build
docker build -t auth-forge .
```

## Configuration

- **Profiles**: `dev` (default, PostgreSQL), `test` (H2), `prod`
- **Environment vars via `.env`**: Uses `spring-dotenv` library
- **JWT Config** (in `application-dev.yml`):
  ```yaml
  yousuf.app:
    jwtSecret: <base64-encoded-secret>
    jwtExpirationTimeInMs: 300000        # 5 min access token
    refreshTokenExpirationTimeInMs: 604800000  # 7 days
    maxSessionsPerUser: 10
  ```

## API Endpoints

Public (no auth): `/auth/**`, `/oauth2/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/test/all`
Protected: Everything else requires valid JWT in `Authorization: Bearer <token>` header

### Auth Endpoints (`/auth`)
- `POST /signup` - Register + auto-login, returns access token + refresh cookie
- `POST /login` - Authenticate, returns tokens
- `POST /refresh` - Rotate refresh token (cookie-based)
- `POST /logout` - Revoke refresh token

## Testing Patterns

- Use `@SpringBootTest` + `@AutoConfigureMockMvc(addFilters = false)` to bypass security
- Mock repos with `@MockitoBean` (Spring 3.4+ replacement for `@MockBean`)
- Test profile auto-applies via `application-test.yml` (H2 database)
- Example: [controllers/AuthControllerTest.java](src/test/java/com/learning/security/controllers/AuthControllerTest.java)

## Database

- **Dev/Prod**: PostgreSQL (Supabase in dev)
- **Test**: H2 in-memory with `create-drop`
- **Init scripts**: `init.sql` creates tables + seed data; roles must exist before user creation
- Required roles in DB: `ROLE_ADMIN`, `ROLE_CUSTOMER`, `ROLE_PLANT_MANAGER`

## OAuth2 Integration

- Google OAuth2 configured in `WebSecurityConfig` with custom handlers
- `CustomOAuth2UserService` processes OAuth user info
- Success handler generates JWT tokens same as local auth
- OAuth users have `AuthProvider.GOOGLE` enum value

## Documentation

Comprehensive docs in `documentations/`:
- [DOCUMENTATION_INDEX.md](documentations/DOCUMENTATION_INDEX.md) - Start here
- OAuth2 setup, architecture diagrams, refresh token implementation details
