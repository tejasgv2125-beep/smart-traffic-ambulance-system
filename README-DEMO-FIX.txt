DEMO FIX NOTES

This version changes the demo behavior as requested:
- Starting GPS simulation moves all three ambulances toward their assigned hospitals using OSRM road routes.
- Only an ambulance with emergencyStatus=true should receive emergency signal priority; the other two are demo/normal traffic vehicles.
- The nine demo traffic-signal markers are positioned along the AMB001 demonstration route so they appear arranged along the road.
- Traffic signal markers are visible as state-colored dots.

IMPORTANT: This is a software simulation. The demo signal coordinates are intentionally arranged along the AMB001 demo route; they are not a survey of real physical traffic-light installations.

RUN
1. Extract this ZIP to a NEW folder.
2. Open backend as a Maven project in IntelliJ and run SmartTrafficAmbulanceApplication.
3. Keep existing MySQL configuration. On backend startup, DataInitializer updates signal coordinates in the database.
4. In frontend, run: npm install
5. Then run: npm run dev -- --port 8081 (or another free port).
6. Open the Vite URL. Activate AMB001 or press Start Simulation. All three ambulances should move.
7. If old map data remains, hard-refresh the browser (Ctrl+F5).

If the frontend folder already has node_modules, do not copy it into this ZIP; run npm install in the extracted frontend folder.
