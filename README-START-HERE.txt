# Smart Traffic Signal Control System for Ambulance Using GPS — Updated Project

## What was changed
- Dashboard automatically focuses the currently active ambulance; if none is active, it prefers AMB001.
- Map no longer zooms to all city-wide markers when a route is selected, and the selected road route is not repeatedly refetched every GPS poll (reduces map jumping).
- Route API base uses the shared `VITE_API_BASE_URL` setting.
- Emergency UI action uses the existing Spring Boot emergency endpoint once (no duplicate route/simulation calls).
- Simulation controls use `/api/ambulances/simulation/{start|stop|reset}`, matching the provided backend controller.
- Backend startup updates the demo signal coordinates for S003 and S004 to points along the AMB001 demonstration route, including when rows already exist in MySQL.

## Start backend (IntelliJ)
1. Open the `backend` folder as a Maven project.
2. Verify `backend/src/main/resources/application.properties` has the correct local MySQL password/database for your machine.
3. Ensure MySQL is running and database `smart_traffic_ambulance` exists.
4. Run `SmartTrafficAmbulanceApplication.java`.
5. Backend uses port `8087`.

The existing `application.properties` has the database username/password from the uploaded project. Change the password if it differs on your computer.

## Start frontend
1. Open a terminal in `frontend`.
2. Run `npm install` (if dependencies are not installed).
3. Run `npm run dev`.
4. Open the local URL printed by Vite.

Default API URL is `http://localhost:8087`. If needed, create `frontend/.env`:
```
VITE_API_BASE_URL=http://localhost:8087
VITE_POLL_INTERVAL_MS=3000
```
Restart Vite after changing `.env`.

## Demo steps
1. Wait for the dashboard to show backend connected.
2. Select AMB001 in the map dropdown (the dashboard will choose the active ambulance automatically).
3. Activate emergency for AMB001.
4. The route should draw around the ambulance and Manipal Hospital. The ambulance marker moves as the GPS simulation updates.
5. S003/S004 demo coordinates are updated when the backend starts; if backend is already running, restart it to apply them.

## Important
This is a software simulation. It updates database state and dashboard markers; it does not control physical traffic lights. Public OSRM and OpenStreetMap tiles require internet access.

AUTOMATIC DEMO START
--------------------
GPS simulation starts automatically after the backend initializes its demo data. You do not need to press Start Simulation in the dashboard. Keep Spring Boot running; all configured ambulances move on their simulated routes, and emergency status controls signal priority.
