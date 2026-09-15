# OAuth2.0 Google Authentication - Implementation Guide

## Overview

This Spring Boot application now supports **OAuth2.0 authentication with Google** alongside the existing JWT-based local authentication. Users can sign in using their Google accounts, and the application will automatically create user accounts and assign roles.

## Features

✅ **Google OAuth2 Login** - Sign in with Google account  
✅ **Automatic User Registration** - New users are automatically created  
✅ **JWT Token Generation** - OAuth2 users receive JWT tokens  
✅ **Role Assignment** - Default ROLE_USER assigned to new OAuth2 users  
✅ **Secure Token Handling** - Tokens passed via redirect URL  
✅ **Email Verification** - OAuth2 users are automatically verified  
✅ **Profile Information** - Stores name, email, and profile picture  

## Architecture

### Technology Stack
- **Spring Boot 3.4.1** - Latest stable version
- **Spring Security 6.x** - OAuth2 Client support
- **OAuth2 Client** - Google OAuth2 integration
- **JWT (JJWT 0.12.6)** - Token generation and validation
- **PostgreSQL** - User data persistence

### Key Components

1. **CustomOAuth2UserService** - Processes OAuth2 user information
2. **OAuth2AuthenticationSuccessHandler** - Generates JWT after successful OAuth2 login
3. **OAuth2AuthenticationFailureHandler** - Handles OAuth2 authentication failures
4. **GoogleOAuth2UserInfo** - Extracts user info from Google's response
5. **UserDetailsImpl** - Unified user principal for both local and OAuth2 users

## Google Cloud Console Setup

### Step 1: Create OAuth2 Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Navigate to **APIs & Services** → **Credentials**
4. Click **Create Credentials** → **OAuth 2.0 Client ID**
5. Configure OAuth consent screen:
   - User Type: **External**
   - App name: Your application name
   - User support email: Your email
   - Developer contact: Your email
   - Scopes: `email`, `profile`

### Step 2: Configure OAuth Client

1. Application type: **Web application**
2. Name: `Auth Forge`
3. **Authorized JavaScript origins:**
   ```
   http://localhost:8080
   ```
4. **Authorized redirect URIs:**
   ```
   http://localhost:8080/login/oauth2/code/google
   ```
5. Click **Create**
6. Copy **Client ID** and **Client Secret**

### Step 3: Environment Variables

Set the following environment variables (or update `application-dev.yml`):

```bash
export GOOGLE_CLIENT_ID="your-google-client-id-here"
export GOOGLE_CLIENT_SECRET="your-google-client-secret-here"
```

Or update `application-dev.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: YOUR_ACTUAL_CLIENT_ID
            client-secret: YOUR_ACTUAL_CLIENT_SECRET
```

## Configuration Files

### application-dev.yml

The OAuth2 configuration has been added:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID:your-google-client-id-here}
            client-secret: ${GOOGLE_CLIENT_SECRET:your-google-client-secret-here}
            scope:
              - email
              - profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Google

yousuf:
  app:
    oauth2:
      authorized-redirect-uris:
        - http://localhost:8080/oauth2/redirect
        - http://localhost:3000/oauth2/redirect
      cookie-expire-seconds: 180
```

## Database Schema Changes

### User Table Updates

New columns added to support OAuth2:

```sql
ALTER TABLE users 
ADD COLUMN provider VARCHAR(20),
ADD COLUMN provider_id VARCHAR(100),
ADD COLUMN image_url VARCHAR(500),
ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;

-- Password is now nullable for OAuth2 users
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
```

### User Entity Fields

```java
@Enumerated(EnumType.STRING)
@Column(length = 20)
private AuthProvider provider;  // LOCAL, GOOGLE, GITHUB, FACEBOOK

@Column(length = 100)
private String providerId;      // OAuth2 provider user ID

@Column(length = 500)
private String imageUrl;        // Profile picture URL

@Column(columnDefinition = "boolean default false")
private Boolean emailVerified;  // Email verification status
```

## API Endpoints

### OAuth2 Login Flow

#### 1. Initiate Google Login
```http
GET /oauth2/authorize/google
```

**Response:** Redirects to Google's OAuth2 consent screen

#### 2. Callback After Authentication
```http
GET /login/oauth2/code/google?code=...&state=...
```

**Response:** Redirects to configured redirect URI with JWT token

**Example Redirect:**
```
http://localhost:8080/oauth2/redirect?token=eyJhbGciOiJIUzUxMiJ9...&userId=1&username=John+Doe&email=john@example.com
```

#### 3. Get Current OAuth2 User
```http
GET /oauth2/user
Authorization: Bearer <jwt-token>
```

**Response:**
```json
{
  "id": 1,
  "username": "M. Yousuf",
  "email": "m.yousuf@gmail.com",
  "roles": [
    {
      "authority": "ROLE_USER"
    }
  ]
}
```

## Usage Examples

### Frontend Integration (React Example)

```javascript
// Initiate Google login
const handleGoogleLogin = () => {
  window.location.href = 'http://localhost:8080/oauth2/authorize/google';
};

// Handle OAuth2 redirect
useEffect(() => {
  const urlParams = new URLSearchParams(window.location.search);
  const token = urlParams.get('token');
  const userId = urlParams.get('userId');
  const username = urlParams.get('username');
  const email = urlParams.get('email');
  
  if (token) {
    // Store token in localStorage
    localStorage.setItem('jwt_token', token);
    localStorage.setItem('user', JSON.stringify({ userId, username, email }));
    
    // Redirect to dashboard
    navigate('/dashboard');
  }
}, []);

// Make authenticated requests
const fetchProtectedData = async () => {
  const token = localStorage.getItem('jwt_token');
  
  const response = await fetch('http://localhost:8080/test/user', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  
  return response.json();
};
```

### Postman/cURL Testing

**Step 1: Get Authorization URL**
```bash
# Open this URL in browser
http://localhost:8080/oauth2/authorize/google
```

**Step 2: Extract Token from Redirect**
After Google login, you'll be redirected to:
```
http://localhost:8080/oauth2/redirect?token=YOUR_JWT_TOKEN&userId=1&username=M+Yousuf&email=m.yousuf@example.com
```

**Step 3: Use Token for API Calls**
```bash
curl -X GET http://localhost:8080/test/user \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Security Features

### 1. Provider Validation
- Users cannot mix local and OAuth2 authentication
- If user exists with Google, they must use Google to login
- If user exists with local account, they must use password

### 2. Automatic User Creation
```java
// New OAuth2 users are created automatically
User user = new User();
user.setProvider(AuthProvider.GOOGLE);
user.setProviderId(oAuth2UserInfo.getId());
user.setUsername(oAuth2UserInfo.getName());
user.setEmail(oAuth2UserInfo.getEmail());
user.setImageUrl(oAuth2UserInfo.getImageUrl());
user.setEmailVerified(true);
user.setPassword(""); // No password for OAuth2 users
user.setRoles(Set.of(ROLE_USER));
```

### 3. JWT Token Generation
- Same JWT tokens used for both local and OAuth2 users
- Tokens include user ID, username, and roles
- 24-hour expiration (configurable)

### 4. Session Management
- Stateless authentication
- No server-side sessions
- Token-based authorization

## Error Handling

### Common Errors and Solutions

#### Error: "Email not found from OAuth2 provider"
**Cause:** Google account doesn't have a public email  
**Solution:** Ensure email scope is requested and user grants permission

#### Error: "Looks like you're signed up with LOCAL account"
**Cause:** User previously registered with username/password  
**Solution:** Use local login endpoint `/auth/signin` instead

#### Error: "Invalid client credentials"
**Cause:** Wrong Client ID or Client Secret  
**Solution:** Verify credentials in Google Cloud Console

#### Error: "Redirect URI mismatch"
**Cause:** Redirect URI not configured in Google Console  
**Solution:** Add exact redirect URI in Google Cloud Console

## Testing

### Manual Testing Steps

1. **Start Application**
   ```bash
   mvn spring-boot:run
   ```

2. **Open Browser**
   ```
   http://localhost:8080/oauth2/authorize/google
   ```

3. **Complete Google Login**
   - Select Google account
   - Grant permissions
   - Get redirected with token

4. **Verify User Created**
   ```sql
   SELECT * FROM users WHERE provider = 'GOOGLE';
   ```

5. **Test Authenticated Endpoints**
   ```bash
   curl -H "Authorization: Bearer <token>" \
     http://localhost:8080/test/user
   ```

### Automated Testing

Test files can be found in:
- `OAuth2ControllerTest.java`
- `CustomOAuth2UserServiceTest.java`
- `OAuth2AuthenticationSuccessHandlerTest.java`

## Production Deployment

### 1. Environment Variables

```bash
# Google OAuth2
export GOOGLE_CLIENT_ID="production-client-id"
export GOOGLE_CLIENT_SECRET="production-client-secret"

# Redirect URIs
export OAUTH2_REDIRECT_URI="https://yourdomain.com/oauth2/redirect"
```

### 2. Update Redirect URIs

In Google Cloud Console, add production redirect URIs:
```
https://yourdomain.com/login/oauth2/code/google
https://yourdomain.com/oauth2/redirect
```

### 3. Update application-prod.yml

```yaml
yousuf:
  app:
    oauth2:
      authorized-redirect-uris:
        - https://yourdomain.com/oauth2/redirect
        - https://app.yourdomain.com/oauth2/redirect
```

### 4. HTTPS Configuration

OAuth2 requires HTTPS in production. Configure SSL certificate:

```yaml
server:
  port: 443
  ssl:
    key-store: classpath:keystore.p12
    key-store-password: your-password
    key-store-type: PKCS12
```

## Troubleshooting

### Enable Debug Logging

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    com.learning.security.services.CustomOAuth2UserService: DEBUG
    com.learning.security.auth: DEBUG
```

### Check Database Schema

```sql
-- Verify columns exist
DESCRIBE users;

-- Check OAuth2 users
SELECT id, username, email, provider, provider_id, email_verified 
FROM users 
WHERE provider = 'GOOGLE';
```

### Verify Configuration

```bash
# Check if OAuth2 client is configured
curl http://localhost:8080/oauth2/authorization/google -v
```

## Migration from Existing Users

If you have existing local users who want to use Google login:

```sql
-- Option 1: Link Google account to existing local account
UPDATE users 
SET provider = 'GOOGLE',
    provider_id = 'google-user-id',
    email_verified = TRUE
WHERE email = 'user@example.com' AND provider = 'LOCAL';

-- Option 2: Keep separate accounts (recommended)
-- Let users have both local and OAuth2 accounts
```

## Best Practices

1. **Always use HTTPS** in production
2. **Validate redirect URIs** to prevent open redirect vulnerabilities
3. **Store tokens securely** (HttpOnly cookies for web apps)
4. **Implement token refresh** for long-lived sessions
5. **Log OAuth2 events** for security auditing
6. **Rate limit OAuth2 endpoints** to prevent abuse
7. **Use state parameter** to prevent CSRF attacks (handled by Spring Security)

## Additional OAuth2 Providers

To add more providers (GitHub, Facebook, etc.):

### 1. Create Provider-specific UserInfo class

```java
public class GitHubOAuth2UserInfo extends OAuth2UserInfo {
    // Implementation
}
```

### 2. Update configuration

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
```

### 3. Update CustomOAuth2UserService

```java
private OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
    return switch (registrationId.toLowerCase()) {
        case "google" -> new GoogleOAuth2UserInfo(attributes);
        case "github" -> new GitHubOAuth2UserInfo(attributes);
        default -> throw new OAuth2AuthenticationProcessingException("Unsupported provider");
    };
}
```

## Support

For issues or questions:
- Check application logs in `logs/` directory
- Review Spring Security documentation
- Check Google OAuth2 documentation
- Raise issue in project repository

## References

- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Google OAuth2 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [JJWT Documentation](https://github.com/jwtk/jjwt)
- [Spring Boot OAuth2 Best Practices](https://spring.io/guides/tutorials/spring-boot-oauth2/)

---

**Version:** 1.0.0  
**Last Updated:** December 18, 2025  
**Spring Boot:** 3.4.1  
**Spring Security:** 6.x
