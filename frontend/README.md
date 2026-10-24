# Smart Traffic Signal Control System

The existing ambulance dashboard runs locally with Vite, React and TanStack Start. It uses only your Spring Boot API and STOMP messages; it does not replace Spring Boot or MySQL, use Supabase, or insert demo records. The dashboard design, map, monitoring views, summaries, activity log, and automatic polling are preserved.

## Windows quick start

### 1. Download the project

- **Direct download (paid plans):** in Lovable, open **Project settings → Git → Download codebase**, or use **Download codebase** at the bottom of the Code editor's file tree.
- **GitHub option:** in Lovable, open **Project settings → Git**, connect GitHub and create the synced repository. Open that repository on GitHub, choose **Code → Download ZIP**.
- In Windows File Explorer, right-click the downloaded ZIP → **Extract All**. Open the extracted folder containing `package.json`. Do not run from inside the ZIP.

No backend/database export is needed: keep using your existing Spring Boot application and MySQL database.

### 2. Install Node.js and dependencies

Install **Node.js 24 LTS** from https://nodejs.org/ using its Windows installer (npm is included). Close and reopen your terminal afterward. In the extracted project folder, right-click → **Open in Terminal**. Use **Command Prompt** for the commands below, or PowerShell with `npm.cmd` in place of `npm` if PowerShell blocks npm scripts.

Check the installation and install the project's dependencies:

```bat
node --version
npm --version
npm install
```

Vite 8 requires a recent Node release; older Node 18 or early Node 20 installations are not supported. You do not need Bun, Docker, a Lovable account at runtime, or a replacement backend to run locally. Dependency downloads need internet access.

### 3. Set the frontend environment variables

In that same project folder, run this command (works in Command Prompt and PowerShell):

```bat
copy .env.example .env.local
notepad .env.local
```

Keep these values for your current server:

```dotenv
VITE_API_BASE_URL=http://localhost:8087
VITE_WS_URL=ws://localhost:8087/ws
VITE_POLL_INTERVAL_MS=5000
```

Save the file as exactly `.env.local`, not `.env.local.txt`. These are frontend settings, not Windows system environment variables. `VITE_API_BASE_URL` defaults to `http://localhost:8087` if unset; do not append `/api` or an endpoint. `VITE_WS_URL` is independently configurable; if unset/blank it derives `/ws` from the API host. If Spring Boot registers its STOMP endpoint with `.withSockJS()`, use `VITE_WS_URL=ws://localhost:8087/ws/websocket` instead. Confirm your actual WebSocket endpoint rather than changing the REST URL.

Keep private passwords/tokens out of `VITE_*` variables, which are visible in the browser. The recommended `.env.local` file is excluded from Git by the existing `*.local` rule. Vite also supports `.env`, but do not commit private settings. After changing `.env.local`, stop Vite with **Ctrl+C** and run it again.

### 4. Start Spring Boot, then the dashboard

Keep your existing Spring Boot server running at **http://localhost:8087**. On this same Windows computer, confirm these URLs return JSON in your browser:

- http://localhost:8087/api/ambulances
- http://localhost:8087/api/signals
- http://localhost:8087/api/hospitals

Back in the project terminal, run:

```bat
npm run dev
```

Keep the terminal open, then open **http://localhost:8080** on the same computer. Spring Boot uses port **8087**; the frontend uses **8080**. No HTTPS tunnel or hosted-preview URL changes are needed for this local workflow.

The dashboard reads all returned records (expected: 3 ambulances, 9 traffic signals, 4 hospitals). Counts are calculated from API responses, never hardcoded. All three REST endpoints must succeed for **Backend: connected**. Map markers require finite numeric coordinates. If WebSocket is unavailable, the dashboard continues REST polling every 5 seconds by default; while WebSocket is connected, REST reconciliation runs every 20 seconds by default.

### 5. Tests and build checks (optional)

```bat
npm test
npm run build
```

`npm run dev` is the local dashboard workflow. The production build retains the project's TanStack Start/Cloudflare target; build output is not a plain standalone HTML file to double-click in Windows.

## Troubleshooting locally

- **Port 8080 already in use:** stop the other application using that port, then retry. The frontend does not silently switch to a different origin.
- **Backend disconnected:** confirm Spring Boot is still running and all three URLs above return JSON. Open browser Developer Tools → Network to identify which request failed. A direct browser tab returning JSON proves reachability, not CORS permission.
- **CORS:** direct requests from `http://localhost:8080` to `http://localhost:8087` are cross-origin. Your existing server policy must permit `http://localhost:8080`; WebSocket may have a separate origin policy. This project does not modify Spring Boot/MySQL or bypass browser security. If that origin is not currently allowed, ask the backend owner to permit it.
- **WebSocket offline (polling):** REST still works. Verify `VITE_WS_URL`, the STOMP endpoint path and WebSocket origin policy. A SockJS endpoint may require `/ws/websocket`.
- **Blank map tiles:** OpenStreetMap tiles need internet access, even though your API is local.
- **PowerShell says scripts are disabled:** run `npm.cmd install` and `npm.cmd run dev`, or use Command Prompt; changing your system security policy is unnecessary.
- **Missing npm / unsupported Node:** install Node.js 24 LTS, reopen the terminal, and retry `npm install` in the folder containing `package.json`.

## Why the hosted preview remains disconnected

The hosted HTTPS preview is not the local workflow. Browser requests to `localhost` target the device viewing the app; hosted server-side requests would target the hosted server, not your Windows computer. HTTPS/local-network restrictions and CORS can prevent preview requests even when local API tabs work. We do not repeatedly change localhost addresses or introduce a hosted proxy. Run the downloaded frontend and Spring Boot together locally as described above.

## Exact API contract

- Ambulances: `ambulanceId`, `driverName`, `currentLatitude`, `currentLongitude`, `destinationHospital`, `emergencyStatus`, `currentSpeed`, `remainingDistance`, `etaMinutes`, `routeProgress`, `lastLocationUpdate`, `assignedRoute`.
- Signals: `signalId`, `signalName`, `latitude`, `longitude`, `currentState`, `normalState`, `priorityStatus`, `associatedAmbulanceId`, `lastUpdateTime`.
- Hospitals: `hospitalId`, `hospitalName`, `latitude`, `longitude`, `address`, `contactNumber`.

All rows and counts are derived from responses; 3 ambulances, 9 signals and 4 hospitals are expected inventory, not hardcoded defaults. Map markers require finite numeric coordinates. All three REST endpoints must succeed for the backend indicator to read connected; partial failures retain previously received records. Active emergency values currently recognized are `ACTIVE`, `EMERGENCY`, `EN_ROUTE`, `ENROUTE`, `CRITICAL`, and `ON_DUTY`. Priority accepts boolean `true` or exact string values `TRUE`, `ACTIVE`, `YES`, `HIGH`, `PRIORITY`, `PRIORITY_ACTIVE` (case-insensitive). Send actual status values if your enums differ; field names alone do not specify enum values.
