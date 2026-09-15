# ✅ OAuth2 Setup Checklist

Use this checklist to quickly set up OAuth2 authentication in your Auth Forge application.

## 📋 Pre-Setup (Before You Start)

- [ ] Java 17+ installed and working
- [ ] Maven installed and in PATH
- [ ] PostgreSQL installed and running
- [ ] Google account for OAuth2 setup
- [ ] Code editor/IDE open
- [ ] Terminal/Command prompt ready

## 🔧 Part 1: Google Cloud Console (Estimated: 5 minutes)

### Step 1.1: Create Project

- [ ] Navigate to: https://console.cloud.google.com/
- [ ] Click project dropdown at top
- [ ] Click "New Project"
- [ ] Enter project name: `spring-security-oauth2`
- [ ] Click "Create"
- [ ] Wait for project creation to complete

### Step 1.2: Enable APIs

- [ ] Go to "APIs & Services" > "Library"
- [ ] Search for "Google+ API"
- [ ] Click on result
- [ ] Click "Enable"
- [ ] Wait for API to be enabled

### Step 1.3: Configure OAuth Consent Screen

- [ ] Go to "APIs & Services" > "OAuth consent screen"
- [ ] Select "External" user type
- [ ] Click "Create"
- [ ] Fill in App name: `Spring Security OAuth2 Demo`
- [ ] Fill in User support email: (your email)
- [ ] Fill in Developer contact: (your email)
- [ ] Click "Save and Continue"
- [ ] Click "Add or Remove Scopes"
- [ ] Select scopes: `email`, `profile`, `openid`
- [ ] Click "Update"
- [ ] Click "Save and Continue"
- [ ] Click "Add Users" (for testing)
- [ ] Add your email address
- [ ] Click "Add"
- [ ] Click "Save and Continue"
- [ ] Review and click "Back to Dashboard"

### Step 1.4: Create OAuth2 Credentials

- [ ] Go to "APIs & Services" > "Credentials"
- [ ] Click "Create Credentials" > "OAuth client ID"
- [ ] Select "Web application"
- [ ] Enter name: `Spring Boot OAuth2 Client`
- [ ] Under "Authorized JavaScript origins", click "Add URI"
- [ ] Add: `http://localhost:8080`
- [ ] Under "Authorized redirect URIs", click "Add URI"
- [ ] Add: `http://localhost:8080/login/oauth2/code/google`
- [ ] Click "Create"
- [ ] **IMPORTANT**: Copy Client ID to a safe place
- [ ] **IMPORTANT**: Copy Client Secret to a safe place
- [ ] Click "OK"

**✅ Google Cloud Console setup complete!**

---

## 💻 Part 2: Application Configuration (Estimated: 2 minutes)

### Step 2.1: Set Environment Variables

Choose your operating system:

#### Linux/Mac:

- [ ] Open terminal
- [ ] Run these commands (replace with your actual values):

```bash
export GOOGLE_CLIENT_ID="your-client-id-here"
export GOOGLE_CLIENT_SECRET="your-client-secret-here"
export DB_URL="jdbc:postgresql://localhost:5432/demo"
export DB_USERNAME="postgres"
export DB_PASSWORD="password"
```

- [ ] Verify with: `echo $GOOGLE_CLIENT_ID`

#### Windows (PowerShell):

- [ ] Open PowerShell
- [ ] Run these commands (replace with your actual values):

```powershell
$env:GOOGLE_CLIENT_ID="your-client-id-here"
$env:GOOGLE_CLIENT_SECRET="your-client-secret-here"
$env:DB_URL="jdbc:postgresql://localhost:5432/demo"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="password"
```

- [ ] Verify with: `echo $env:GOOGLE_CLIENT_ID`

#### Windows (CMD):

- [ ] Open Command Prompt
- [ ] Run these commands (replace with your actual values):

```cmd
set GOOGLE_CLIENT_ID=your-client-id-here
set GOOGLE_CLIENT_SECRET=your-client-secret-here
set DB_URL=jdbc:postgresql://localhost:5432/demo
set DB_USERNAME=postgres
set DB_PASSWORD=password
```

- [ ] Verify with: `echo %GOOGLE_CLIENT_ID%`

### Step 2.2: Alternative - Edit Configuration File

If you prefer not to use environment variables:

- [ ] Open: `src/main/resources/application-dev.yml`
- [ ] Find section: `spring.security.oauth2.client.registration.google`
- [ ] Replace `${GOOGLE_CLIENT_ID}` with your actual Client ID
- [ ] Replace `${GOOGLE_CLIENT_SECRET}` with your actual Client Secret
- [ ] Save file

**✅ Application configuration complete!**

---

## 🗄️ Part 3: Database Setup (Estimated: 2 minutes)

### Step 3.1: Create Database

- [ ] Open terminal/command prompt
- [ ] Connect to PostgreSQL:
  ```bash
  psql -U postgres
  ```
- [ ] Enter your PostgreSQL password
- [ ] Run command: `CREATE DATABASE demo;`
- [ ] Verify with: `\l` (should see 'demo' in list)
- [ ] Exit with: `\q`

### Step 3.2: Run Migration Script

- [ ] Navigate to project directory in terminal
- [ ] Run migration:
  ```bash
  psql -U postgres -d demo -f add_oauth2_columns.sql
  ```
- [ ] Check for success message
- [ ] Verify no errors displayed

**Alternative: Manual SQL Execution**

- [ ] Connect to database: `psql -U postgres -d demo`
- [ ] Copy SQL from `add_oauth2_columns.sql`
- [ ] Paste into psql terminal
- [ ] Press Enter to execute
- [ ] Verify success messages
- [ ] Exit with: `\q`

**✅ Database setup complete!**

---

## 🚀 Part 4: Build and Run (Estimated: 2 minutes)

### Step 4.1: Clean and Build

- [ ] Open terminal in project root directory
- [ ] Run Maven clean:
  ```bash
  mvn clean
  ```
- [ ] Wait for "BUILD SUCCESS"
- [ ] Run Maven package:
  ```bash
  mvn package -DskipTests
  ```
- [ ] Wait for "BUILD SUCCESS"
- [ ] Verify JAR file created in `target/` directory

### Step 4.2: Start Application

Choose one method:

#### Method A: Using Maven

- [ ] Run command:
  ```bash
  mvn spring-boot:run -Dspring-boot.run.profiles=dev
  ```

#### Method B: Using JAR File

- [ ] Run command:
  ```bash
  java -jar target/Security-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
  ```

### Step 4.3: Verify Application Started

- [ ] Look for message: "Started SecurityApplication"
- [ ] Check port: "Tomcat started on port 8080"
- [ ] No red ERROR messages in console
- [ ] Application is running (don't close terminal)

**✅ Application is running!**

---

## 🧪 Part 5: Test OAuth2 Login (Estimated: 3 minutes)

### Step 5.1: Open Demo Page

- [ ] Open web browser
- [ ] Navigate to: http://localhost:8080/oauth2-demo.html
- [ ] Page loads successfully
- [ ] See "Continue with Google" button
- [ ] See instructions and info box

### Step 5.2: Test Google Sign-In

- [ ] Click "Continue with Google" button
- [ ] Redirected to Google sign-in page
- [ ] See your app name: "Spring Security OAuth2 Demo"
- [ ] Enter your Google email
- [ ] Enter your Google password
- [ ] Click through any security prompts

### Step 5.3: Grant Permissions

- [ ] Review permissions requested (email, profile)
- [ ] Click "Allow" or "Continue"
- [ ] Wait for redirect

### Step 5.4: Verify Success

- [ ] Redirected back to demo page
- [ ] See green success box
- [ ] See your User ID
- [ ] See your Username
- [ ] See your Email
- [ ] See JWT Token (truncated)
- [ ] Check browser console (F12) - should see "Authentication test successful"

**✅ OAuth2 login working!**

---

## 🔍 Part 6: Verify Everything Works (Estimated: 2 minutes)

### Step 6.1: Copy JWT Token

- [ ] On demo page, copy the full JWT token
  - Option A: Click browser console (F12) and copy full token
  - Option B: Check localStorage: `localStorage.getItem('jwt_token')`

### Step 6.2: Test API Endpoints

#### Test 1: Get Current User

- [ ] Open new terminal/command prompt
- [ ] Run command (replace YOUR_TOKEN):
  ```bash
  curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/oauth2/user
  ```
- [ ] Should see JSON with your user details
- [ ] Verify email matches
- [ ] Verify provider is "GOOGLE"

#### Test 2: Access Protected Endpoint

- [ ] Run command:
  ```bash
  curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/test/user
  ```
- [ ] Should see: "User Access content."

#### Test 3: Check User Details

- [ ] Run command:
  ```bash
  curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/test/userdetails
  ```
- [ ] Should see your detailed user information

### Step 6.3: Check Database

- [ ] Open PostgreSQL: `psql -U postgres -d demo`
- [ ] Run query:
  ```sql
  SELECT id, username, email, provider, provider_id, email_verified 
  FROM users 
  WHERE provider = 'GOOGLE';
  ```
- [ ] Should see your user record
- [ ] Verify email matches
- [ ] Verify provider is 'GOOGLE'
- [ ] Exit with: `\q`

**✅ All verification tests passed!**

---

## 🎉 Setup Complete!

Congratulations! You've successfully set up OAuth2 authentication with Google!

### ✅ What You've Accomplished

- [x] Created Google Cloud Console project
- [x] Configured OAuth2 credentials
- [x] Set up OAuth consent screen
- [x] Configured application with credentials
- [x] Migrated database for OAuth2 support
- [x] Built and ran application
- [x] Tested OAuth2 login flow
- [x] Verified JWT token generation
- [x] Tested API endpoints
- [x] Confirmed database records

### 📊 Summary

| Component | Status |
|-----------|--------|
| Google Cloud Console | ✅ Configured |
| OAuth2 Credentials | ✅ Created |
| Application Config | ✅ Updated |
| Database Migration | ✅ Applied |
| Application Build | ✅ Successful |
| OAuth2 Login | ✅ Working |
| JWT Generation | ✅ Working |
| API Endpoints | ✅ Accessible |
| Database Records | ✅ Created |

### 🚀 Next Steps

Now that OAuth2 is working, you can:

1. **Customize the UI**: Edit `oauth2-demo.html` for your branding
2. **Add More Providers**: Implement Facebook, GitHub, etc.
3. **Deploy to Production**: Update redirect URIs for your domain
4. **Add Features**: Implement profile management, account linking
5. **Monitor Usage**: Add analytics for OAuth2 login success rates

### 📚 Useful Resources

- **Quick Start Guide**: [OAUTH2_QUICK_START.md](OAUTH2_QUICK_START.md)
- **Detailed Documentation**: [OAUTH2_SETUP.md](OAUTH2_SETUP.md)
- **Implementation Summary**: [OAUTH2_IMPLEMENTATION_SUMMARY.md](OAUTH2_IMPLEMENTATION_SUMMARY.md)
- **Main README**: [readme.md](readme.md)

---

## ⚠️ Troubleshooting

If something didn't work, refer to this quick troubleshooting guide:

### Issue: "Error 401: Invalid Client"

**Check:**
- [ ] Client ID is correct (no extra spaces)
- [ ] Client Secret is correct (no extra spaces)
- [ ] Environment variables are set
- [ ] Application was restarted after setting variables

### Issue: "Error 400: Redirect URI Mismatch"

**Check:**
- [ ] Redirect URI in Google Console is exactly: `http://localhost:8080/login/oauth2/code/google`
- [ ] No trailing slashes
- [ ] Correct port number (8080)
- [ ] Wait 5 minutes after changing Google Console settings

### Issue: "Error 403: Access Denied"

**Check:**
- [ ] Your Google account is added as test user
- [ ] OAuth consent screen is configured
- [ ] Scopes (email, profile) are selected
- [ ] App is in "Testing" mode

### Issue: Application Won't Start

**Check:**
- [ ] PostgreSQL is running: `pg_isready`
- [ ] Database exists: `psql -U postgres -l`
- [ ] Port 8080 is free
- [ ] Java 17+ is installed: `java -version`
- [ ] Maven is installed: `mvn -version`

### Issue: Token Not Working

**Check:**
- [ ] Token format: `Bearer <token>` (note the space)
- [ ] Token hasn't expired (check JWT expiration)
- [ ] JWT secret matches in configuration
- [ ] Authorization header is included in request

### Issue: Database Errors

**Check:**
- [ ] Migration script ran successfully
- [ ] Columns exist: `\d users` in psql
- [ ] Password column is nullable
- [ ] Indexes are created

---

## 📞 Need Help?

If you're still stuck:

1. **Check Documentation**: See [OAUTH2_SETUP.md](OAUTH2_SETUP.md) for detailed troubleshooting
2. **Review Logs**: Check application console for error messages
3. **Verify Configuration**: Double-check all configuration values
4. **Start Fresh**: Drop database and re-run migration if needed

---

**🎊 Enjoy your OAuth2-enabled application!**

Total setup time: ~15 minutes
Last updated: 2024
