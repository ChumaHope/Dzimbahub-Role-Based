# DzimbaHub — shortest free presentation deployment

This repository deploys the frontend and backend separately. Sign-up requests are approved by an administrator, and sign-in uses a password plus a text-message code. The backend uses stateless JWT authentication and BCrypt password hashing. Public signup permits TENANT and LANDLORD roles; the first ADMIN is created from the `ADMIN_*` settings.

**New required Render settings:** `CORS_ORIGINS` (your Vercel URL, e.g. `https://dzimbahub.vercel.app`), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_PHONE`, `SEED_DEMO_DATA=false`, `SMS_PROVIDER=twilio`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM`. See README.md for details.

## Recommended services

- **GitHub** — source repository
- **Supabase** — PostgreSQL database; enable PostGIS from Database → Extensions
- **Render** — Spring Boot REST API (free service sleeps after inactivity)
- **Vercel** — Next.js website

## A. Push the project to GitHub

1. Create a new GitHub repository named `dzimbahub` (a private repository is fine if you connect it to your hosting accounts).
2. Upload/push the *contents* of this folder to the repository root. The root should contain `backend/`, `frontend/`, and `render.yaml`.
3. Do not commit passwords or `.env` files. `.gitignore` is included.

If using Git locally, open a terminal in this folder:

```bash
git init
git add .
git commit -m "Prepare DzimbaHub presentation deployment"
git branch -M main
git remote add origin https://github.com/YOUR-USERNAME/dzimbahub.git
git push -u origin main
```

## B. Create the database (Supabase)

1. Create a Supabase project and save its database password securely.
2. In Database → Extensions, enable `postgis` if available/needed for your project demonstration.
3. Open Connect and choose the **Session pooler** connection details if the direct database connection is IPv6-only or inaccessible from Render.
4. Record the host, port, database name, username and password. Do not put these in GitHub.

The backend expects these Render environment variables:

- `DB_URL`: JDBC URL, e.g. `jdbc:postgresql://POOLER_HOST:5432/postgres?sslmode=require`
- `DB_USER`: the database username shown by Supabase (session-pooler usernames often include the project reference)
- `DB_PASSWORD`: the database password

Use the exact host, port, database name and username supplied by your Supabase dashboard; the example above is a template, not a real connection string.

## C. Deploy the Spring Boot API (Render)

1. Sign in to Render and connect GitHub.
2. Choose **New → Blueprint** and select the `dzimbahub` repository. Render will read `render.yaml`; alternatively create a Web Service manually with root directory `backend`, runtime Docker, and health check path `/api/health`.
3. Add `DB_URL`, `DB_USER`, and `DB_PASSWORD` in the Render service environment settings.
4. Deploy. Wait for the service status to become **Live**.
5. Open `https://YOUR-RENDER-SERVICE.onrender.com/api/health`. Expected response:

```json
{"status":"UP","service":"DzimbaHub API"}
```

Copy the Render base URL (without `/api`) for the next step.

## D. Deploy the Next.js frontend (Vercel)

1. Sign in to Vercel and import the same GitHub repository.
2. In project settings, set **Root Directory** to `frontend`.
3. Add this environment variable for Production (and Preview if desired):

   `NEXT_PUBLIC_API_URL=https://YOUR-RENDER-SERVICE.onrender.com/api`

   Replace the example with your real Render URL. Do not include a trailing slash.
4. Deploy and wait for the deployment to complete. Vercel will give you a public `https://...vercel.app` URL.
5. Open that URL and test Properties, Recommendations, Property Detail, viewing request, enquiry, reviews and Dashboard.

## E. Before the presentation

- Open the Render `/api/health` endpoint 1–2 minutes before the demo to wake a sleeping free backend.
- Open the Vercel website and test the key workflow once.
- Keep the Supabase database active and do not change its password without updating Render environment variables.
- The seeded Harare properties are sample/demo listings, not verified real listings.

## Free-tier limitations

Render free web services spin down after 15 minutes without traffic and may take around a minute to wake. Render free PostgreSQL databases expire after 30 days, so this guide uses Supabase for the database instead. Free services are suitable for a short academic demo but do not guarantee uninterrupted production uptime. See Render's current free-tier documentation before the presentation.

## Troubleshooting

- **Frontend shows API errors:** check `NEXT_PUBLIC_API_URL` in Vercel and redeploy after changing it.
- **Browser CORS error:** confirm the API is live; the current demo backend allows cross-origin requests. For a production release, restrict CORS to your exact Vercel domain.
- **Backend deploy fails:** inspect Render build/runtime logs; confirm the database URL is a JDBC URL, SSL is enabled, and credentials match Supabase.
- **No listings appear:** wait for the API to start and inspect Render logs for database connection or schema errors. The app seeds demo listings when the properties table is empty.


## Authentication smoke test

After deployment, open `/signup`, create a tenant or landlord account, then sign out and log in at `/login`. The API endpoints are `POST /api/auth/signup`, `POST /api/auth/login`, and authenticated `GET /api/auth/me`. Signup requires a name, valid email, and password of at least 8 characters. The API returns a bearer token; the frontend stores it in browser local storage for this academic demonstration. For a production system, consider secure HttpOnly cookies, email verification, password reset, rate limiting, and a reviewed account recovery process.
