# DzimbaHub: Map-Centric Boarding House Management System

Three roles, each with its own dashboard and its own permitted actions, enforced by the API.

| Role | Gets an account by | Can do | Cannot do |
|---|---|---|---|
| **Tenant** | Requesting an account; an administrator approves it | Search, filter and map listings; get recommendations and save preferences; request/cancel viewings; send enquiries and read replies; rate a property (after an approved viewing, once); create a booking and confirm it with the **simulated** payment | Create or change listings; approve viewings; see other people's data; use admin tools |
| **Landlord** | Requesting an account; an administrator approves it | Create, edit and remove **own** listings; approve or decline viewing requests for own listings; read and reply to enquiries; see bookings for own listings | Change other landlords' listings; rate or book; use admin tools |
| **Administrator** | Created at start-up from `ADMIN_*` settings (never through public signup) | Approve or reject signup requests; suspend/reactivate users; hide, restore or delete any listing; view statistics and the activity log | Change another administrator or their own account status |

## Sign-up and sign-in flow
1. **Request an account** (`/signup`): name, email, phone number, role (tenant or landlord), password. The account is created as `PENDING` and **no token is issued**.
2. **Administrator approves** (Administration → Signup requests). The user is told by text message.
3. **Sign in, step 1** (`/login`): email and password. If the account is approved, a 6-digit code is sent to the registered phone by text message.
4. **Sign in, step 2**: enter the code. Only then is a JWT issued. Codes expire after 5 minutes, work once, and lock after 5 wrong tries.
5. Every request re-checks the account in the database, so a suspended user loses access immediately and the role always comes from the database.

Pending, rejected and suspended accounts cannot sign in.

## Text messages (SMS)
* `SMS_PROVIDER=console` (default): the text is written to the server log. For a local demo also set `OTP_EXPOSE_CODE=true` and the code appears on the login screen. **Never use this in production.**
* `SMS_PROVIDER=twilio`: real texts via Twilio. Set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM`. Trial Twilio accounts can only text verified numbers.

## Run locally
```bash
# database (PostGIS image)
docker compose up postgres -d

# backend (Java 21 + Maven)
cd backend
OTP_EXPOSE_CODE=true mvn spring-boot:run       # Windows PowerShell: $env:OTP_EXPOSE_CODE="true"; mvn spring-boot:run

# frontend
cd ../frontend && npm install && npm run dev
```
Open http://localhost:3000.

### Demo accounts (created when `SEED_DEMO_DATA=true`)
| Role | Email | Password |
|---|---|---|
| Administrator | admin@dzimbahub.local | Admin#12345 |
| Landlord | landlord1@dzimbahub.local | Demo#12345 |
| Landlord | landlord2@dzimbahub.local | Demo#12345 |
| Tenant | tenant@dzimbahub.local | Demo#12345 |

Demo phone numbers are fake; use `OTP_EXPOSE_CODE=true` or read the code from the backend log. To test the approval step, request a new account at `/signup`, then sign in as the administrator and approve it.

> Existing databases: accounts created before this version have no status and are treated as approved, but need a phone number before they can receive a code. Simplest for a demo database: drop the tables and let the app recreate them.

## Tests
```bash
cd backend
mvn test
```
Uses an in-memory H2 database, so no PostgreSQL is needed. See `docs/TEST_PLAN.md` for what is covered (signup approval, text-code sign-in, a role-by-endpoint permission matrix, and per-role workflows).

## Deployment
Frontend on Vercel, API on Render, PostgreSQL on Supabase (see `DEPLOYMENT.md`). **Required new settings on Render:** `CORS_ORIGINS` (your Vercel URL, otherwise the browser will block API calls), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_PHONE`, `SMS_PROVIDER` and the Twilio variables. Use `SEED_DEMO_DATA=false` for real use.

## Known limitations
* Payments are simulated; no payment provider is called.
* Location is stored as latitude/longitude; `db/postgis.sql` adds the PostGIS column, but queries do not use it yet.
* Recommendations use machine learning.
* Messages are enquiry/reply threads, not live chat.
