// ============================================================
// SMART TRAFFIC SIGNAL CONTROL SYSTEM
// Dashboard JavaScript
// ============================================================

let map = null;
let stompClient = null;
let reconnectTimer = null;

const ambulanceMarkers = {};
const signalMarkers = {};
const hospitalMarkers = {};

const ambulanceData = {};
const signalData = {};

let completedJourneys = 0;
let mapInitialized = false;

// ============================================================
// INITIALIZATION
// ============================================================

document.addEventListener("DOMContentLoaded", function () {
    initializeMap();
    loadInitialData();
    connectWebSocket();
    startClock();
    setupAmbulanceSelection();

    addEventLogEntry(
        "system",
        "Dashboard initialized. Loading live backend data."
    );
});

// ============================================================
// MAP INITIALIZATION
// ============================================================

function initializeMap() {

    const mapElement = document.getElementById("map");

    if (!mapElement) {
        console.error("Map element with ID 'map' was not found.");
        return;
    }

    if (typeof L === "undefined") {
        console.error(
            "Leaflet is not loaded. Check the Leaflet CSS and JavaScript imports in index.html."
        );

        addEventLogEntry(
            "error",
            "Map library failed to load."
        );

        return;
    }

    try {
        map = L.map("map").setView([12.9716, 77.5946], 12);

        L.tileLayer(
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
            {
                maxZoom: 19,
                attribution:
                    '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }
        ).addTo(map);

        mapInitialized = true;

        // Fix map rendering when its container is resized.
        setTimeout(function () {
            map.invalidateSize();
        }, 300);

        console.log("Leaflet map initialized.");

    } catch (error) {
        console.error("Map initialization failed:", error);

        addEventLogEntry(
            "error",
            "Unable to initialize the map."
        );
    }
}

// ============================================================
// LOAD INITIAL DATA
// ============================================================

async function loadInitialData() {

    await Promise.allSettled([
        loadAmbulances(),
        loadTrafficSignals(),
        loadHospitals()
    ]);

    updateSummaryCards();
}

// ============================================================
// API HELPER
// ============================================================

async function fetchJson(url) {

    const response = await fetch(url, {
        headers: {
            "Accept": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(
            "Request failed: " + url + " (HTTP " + response.status + ")"
        );
    }

    return response.json();
}

// ============================================================
// AMBULANCES
// ============================================================

async function loadAmbulances() {

    try {

        const ambulances = await fetchJson("/api/ambulances");

        if (!Array.isArray(ambulances)) {
            console.error("Unexpected ambulance API response:", ambulances);
            return;
        }

        ambulances.forEach(function (ambulance) {
            ambulanceData[ambulance.ambulanceId] = ambulance;

            initializeAmbulanceOnMap(ambulance);
            createOrUpdateAmbulancePanel(ambulance);
        });

        updateSummaryCards();
        fitMapToMarkers();

        console.log("Ambulances loaded:", ambulances.length);

    } catch (error) {

        console.error("Error loading ambulances:", error);

        addEventLogEntry(
            "error",
            "Unable to load ambulance information from the API."
        );
    }
}

// Create or update an ambulance marker.
function initializeAmbulanceOnMap(ambulance) {

    if (!mapInitialized || !map) {
        return;
    }

    const id = ambulance.ambulanceId;
    const latitude = Number(ambulance.currentLatitude);
    const longitude = Number(ambulance.currentLongitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        console.warn("Invalid coordinates for ambulance:", id);
        return;
    }

    const position = [latitude, longitude];

    if (ambulanceMarkers[id]) {

        ambulanceMarkers[id].setLatLng(position);
        updateAmbulanceMarkerStyle(ambulance);

        return;
    }

    const marker = L.circleMarker(position, {
        radius: 9,
        fillColor: getAmbulanceColor(
            id,
            ambulance.emergencyStatus
        ),
        color: "#ffffff",
        weight: 2,
        opacity: 1,
        fillOpacity: 0.95
    }).addTo(map);

    marker.bindTooltip(
        getAmbulanceTooltip(ambulance),
        {
            permanent: true,
            direction: "top",
            offset: [0, -10]
        }
    );

    marker.on("click", function () {
        showAmbulanceDetails(ambulance);
    });

    ambulanceMarkers[id] = marker;
}

// Update ambulance marker position and appearance.
function updateAmbulanceOnMap(ambulance) {

    if (!mapInitialized || !map) {
        return;
    }

    const id = ambulance.ambulanceId;

    initializeAmbulanceOnMap(ambulance);

    const marker = ambulanceMarkers[id];

    if (!marker) {
        return;
    }

    marker.setLatLng([
        Number(ambulance.currentLatitude),
        Number(ambulance.currentLongitude)
    ]);

    marker.setTooltipContent(getAmbulanceTooltip(ambulance));

    updateAmbulanceMarkerStyle(ambulance);
}

// Change marker color according to emergency status.
function updateAmbulanceMarkerStyle(ambulance) {

    const marker = ambulanceMarkers[ambulance.ambulanceId];

    if (!marker) {
        return;
    }

    marker.setStyle({
        fillColor: getAmbulanceColor(
            ambulance.ambulanceId,
            ambulance.emergencyStatus
        )
    });
}

// Tooltip displayed on the map.
function getAmbulanceTooltip(ambulance) {

    return `
        <strong>${escapeHtml(ambulance.ambulanceId)}</strong><br>
        Driver: ${escapeHtml(ambulance.driverName || "Unknown")}<br>
        Status: ${ambulance.emergencyStatus ? "EMERGENCY" : "NORMAL"}<br>
        Speed: ${Number(ambulance.currentSpeed || 0).toFixed(1)} km/h<br>
        ETA: ${Number(ambulance.etaMinutes || 0)} min
    `;
}

// ============================================================
// AMBULANCE MONITORING PANEL
// ============================================================

function createOrUpdateAmbulancePanel(ambulance) {

    const container = document.getElementById("ambulance-panels");

    if (!container) {
        console.warn("Element 'ambulance-panels' was not found.");
        return;
    }

    const id = ambulance.ambulanceId;

    let card = document.getElementById(
        "ambulance-card-" + id
    );

    if (!card) {

        card = document.createElement("div");
        card.id = "ambulance-card-" + id;
        card.className = "ambulance-card";

        container.appendChild(card);
    }

    card.innerHTML = `
        <div class="ambulance-header">

            <div class="ambulance-id">
                ${escapeHtml(id)}
            </div>

            <div class="ambulance-status ${
        ambulance.emergencyStatus
            ? "status-emergency"
            : "status-normal"
    }">
                ${ambulance.emergencyStatus ? "EMERGENCY" : "NORMAL"}
            </div>

        </div>

        <div class="ambulance-info">

            <div>
                <label>Driver:</label>
                <span>${escapeHtml(ambulance.driverName || "Unknown")}</span>
            </div>

            <div>
                <label>Destination:</label>
                <span>${escapeHtml(ambulance.destinationHospital || "Not assigned")}</span>
            </div>

            <div>
                <label>Distance:</label>
                <span>${formatNumber(ambulance.remainingDistance, 1)} km</span>
            </div>

            <div>
                <label>ETA:</label>
                <span>${Number(ambulance.etaMinutes || 0)} min</span>
            </div>

            <div>
                <label>Speed:</label>
                <span>${formatNumber(ambulance.currentSpeed, 1)} km/h</span>
            </div>

            <div>
                <label>Progress:</label>
                <span>${formatNumber(ambulance.routeProgress, 1)}%</span>
            </div>

        </div>
    `;
}

// Update ambulance panel when a WebSocket event arrives.
function updateAmbulancePanel(ambulance) {

    ambulanceData[ambulance.ambulanceId] = ambulance;

    createOrUpdateAmbulancePanel(ambulance);

    updateSummaryCards();
}

// Display ambulance details when a map marker is clicked.
function showAmbulanceDetails(ambulance) {

    const select = document.getElementById("ambulance-select");

    if (select) {
        select.value = ambulance.ambulanceId;
    }

    addEventLogEntry(
        "info",
        "Selected ambulance " + ambulance.ambulanceId
    );
}

// ============================================================
// TRAFFIC SIGNALS
// ============================================================

async function loadTrafficSignals() {

    try {

        const signals = await fetchJson("/api/signals");

        if (!Array.isArray(signals)) {
            console.warn("Unexpected traffic signal response:", signals);
            return;
        }

        signals.forEach(function (signal) {

            signalData[signal.signalId] = signal;

            initializeSignalOnMap(signal);
            createOrUpdateSignalPanel(signal);
        });

        updateSummaryCards();
        fitMapToMarkers();

        console.log("Traffic signals loaded:", signals.length);

    } catch (error) {

        console.error("Error loading traffic signals:", error);

        addEventLogEntry(
            "error",
            "Unable to load traffic signal information. Check /api/signals."
        );
    }
}

// Create or update a traffic signal marker.
function initializeSignalOnMap(signal) {

    if (!mapInitialized || !map) {
        return;
    }

    const latitude = Number(signal.latitude);
    const longitude = Number(signal.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        console.warn(
            "Invalid coordinates for traffic signal:",
            signal.signalId
        );
        return;
    }

    const id = signal.signalId;
    const position = [latitude, longitude];

    if (signalMarkers[id]) {

        signalMarkers[id].setLatLng(position);

        signalMarkers[id].setStyle({
            fillColor: getSignalColor(signal.currentState)
        });

        signalMarkers[id].setTooltipContent(
            getSignalTooltip(signal)
        );

        return;
    }

    const marker = L.circleMarker(position, {
        radius: 7,
        fillColor: getSignalColor(signal.currentState),
        color: "#ffffff",
        weight: 2,
        fillOpacity: 0.95
    }).addTo(map);

    marker.bindTooltip(
        getSignalTooltip(signal),
        {
            permanent: true,
            direction: "top"
        }
    );

    signalMarkers[id] = marker;
}

function getSignalTooltip(signal) {

    return `
        <strong>${escapeHtml(signal.signalName || signal.signalId)}</strong><br>
        State: ${escapeHtml(signal.currentState || "UNKNOWN")}<br>
        Priority: ${signal.priorityStatus ? "ACTIVE" : "INACTIVE"}
    `;
}

// Create or update a traffic signal card.
function createOrUpdateSignalPanel(signal) {

    const container = document.getElementById("signal-panels");

    if (!container) {
        console.warn("Element 'signal-panels' was not found.");
        return;
    }

    const id = signal.signalId;

    let card = document.getElementById(
        "signal-card-" + id
    );

    if (!card) {

        card = document.createElement("div");
        card.id = "signal-card-" + id;
        card.className = "signal-card";

        container.appendChild(card);
    }

    card.innerHTML = `
        <div class="signal-info">

            <div class="signal-name">
                ${escapeHtml(signal.signalName || id)}
            </div>

            <div class="signal-details">

                <div class="signal-detail">
                    <span>ID:</span>
                    <span>${escapeHtml(id)}</span>
                </div>

                <div class="signal-detail">
                    <span>State:</span>
                    <span class="signal-state state-${escapeHtml(
        String(signal.currentState || "UNKNOWN").toLowerCase()
    )}">
                        ${escapeHtml(signal.currentState || "UNKNOWN")}
                    </span>
                </div>

                <div class="signal-detail">
                    <span>Priority:</span>
                    <span class="signal-priority">
                        ${signal.priorityStatus ? "ACTIVE" : "INACTIVE"}
                    </span>
                </div>

                <div class="signal-detail">
                    <span>Ambulance:</span>
                    <span>${escapeHtml(signal.associatedAmbulanceId || "None")}</span>
                </div>

            </div>
        </div>
    `;
}

// Update a traffic signal from WebSocket data.
function updateSignalPanel(signal) {

    signalData[signal.signalId] = signal;

    initializeSignalOnMap(signal);
    createOrUpdateSignalPanel(signal);

    updateSummaryCards();
}

// ============================================================
// HOSPITALS
// ============================================================

async function loadHospitals() {

    try {

        const hospitals = await fetchJson("/api/hospitals");

        if (!Array.isArray(hospitals)) {
            console.warn("Unexpected hospital API response:", hospitals);
            return;
        }

        hospitals.forEach(function (hospital) {
            initializeHospitalOnMap(hospital);
        });

        fitMapToMarkers();

        console.log("Hospitals loaded:", hospitals.length);

    } catch (error) {

        console.error("Error loading hospitals:", error);
    }
}

function initializeHospitalOnMap(hospital) {

    if (!mapInitialized || !map) {
        return;
    }

    const latitude = Number(hospital.latitude);
    const longitude = Number(hospital.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return;
    }

    const id = hospital.hospitalId;

    if (hospitalMarkers[id]) {
        hospitalMarkers[id].setLatLng([latitude, longitude]);
        return;
    }

    const icon = L.divIcon({
        className: "hospital-icon",
        html: `
            <div style="
                width: 20px;
                height: 20px;
                background: #dc3545;
                border: 3px solid white;
                border-radius: 50%;
                box-shadow: 0 1px 5px rgba(0,0,0,0.35);
            "></div>
        `,
        iconSize: [26, 26],
        iconAnchor: [13, 13]
    });

    const marker = L.marker(
        [latitude, longitude],
        { icon: icon }
    ).addTo(map);

    marker.bindTooltip(
        `<strong>${escapeHtml(hospital.hospitalName || "Hospital")}</strong>`
    );

    hospitalMarkers[id] = marker;
}

// ============================================================
// WEBSOCKET CONNECTION
// ============================================================

function connectWebSocket() {

    if (typeof SockJS === "undefined" ||
        typeof Stomp === "undefined") {

        console.error(
            "SockJS or STOMP is missing. Check the script imports in index.html."
        );

        setConnectionStatus(false);
        scheduleReconnect();

        return;
    }

    try {

        const socket = new SockJS("/ws");

        stompClient = Stomp.over(socket);

        // Prevent excessive STOMP debug messages in the console.
        stompClient.debug = null;

        stompClient.connect(
            {},
            function () {

                setConnectionStatus(true);

                console.log("WebSocket connected.");

                stompClient.subscribe(
                    "/topic/ambulance/update",
                    function (message) {

                        try {

                            const ambulance = JSON.parse(message.body);

                            updateAmbulanceOnMap(ambulance);
                            updateAmbulancePanel(ambulance);

                            addEventLogEntry(
                                "simulation",
                                "Ambulance " +
                                ambulance.ambulanceId +
                                " location updated."
                            );

                        } catch (error) {
                            console.error(
                                "Invalid ambulance update:",
                                error
                            );
                        }
                    }
                );

                stompClient.subscribe(
                    "/topic/ambulance/arrived",
                    function (message) {

                        try {

                            const ambulance = JSON.parse(message.body);

                            updateAmbulanceOnMap(ambulance);
                            updateAmbulancePanel(ambulance);

                            completedJourneys++;

                            addEventLogEntry(
                                "arrival",
                                "Ambulance " +
                                ambulance.ambulanceId +
                                " reached its destination."
                            );

                            updateSummaryCards();

                        } catch (error) {
                            console.error(
                                "Invalid arrival message:",
                                error
                            );
                        }
                    }
                );

                stompClient.subscribe(
                    "/topic/signal/update",
                    function (message) {

                        try {

                            const event = JSON.parse(message.body);

                            if (event.signal) {

                                updateSignalPanel(event.signal);

                                addEventLogEntry(
                                    "priority",
                                    "Traffic signal " +
                                    event.signal.signalId +
                                    " updated."
                                );
                            }

                        } catch (error) {
                            console.error(
                                "Invalid signal update:",
                                error
                            );
                        }
                    }
                );

                stompClient.subscribe(
                    "/topic/system/event",
                    function (message) {

                        try {

                            const event = JSON.parse(message.body);

                            addEventLogEntry(
                                String(event.type || "system").toLowerCase(),
                                event.message || "System event received."
                            );

                        } catch (error) {
                            console.error(
                                "Invalid system event:",
                                error
                            );
                        }
                    }
                );

                // Refresh REST data after the connection is established.
                loadInitialData();
            },
            function (error) {

                console.error("WebSocket connection failed:", error);

                setConnectionStatus(false);

                scheduleReconnect();
            }
        );

    } catch (error) {

        console.error("Unable to create WebSocket connection:", error);

        setConnectionStatus(false);

        scheduleReconnect();
    }
}

function scheduleReconnect() {

    if (reconnectTimer) {
        return;
    }

    reconnectTimer = setTimeout(function () {

        reconnectTimer = null;

        connectWebSocket();

    }, 5000);
}

function setConnectionStatus(isConnected) {

    const element = document.getElementById("connection-status");

    if (!element) {
        return;
    }

    element.textContent = isConnected
        ? "WS: Connected"
        : "WS: Disconnected";

    element.style.backgroundColor = isConnected
        ? "#198754"
        : "#dc3545";
}

// ============================================================
// SUMMARY CARDS
// ============================================================

function updateSummaryCards() {

    const ambulances = Object.values(ambulanceData);
    const signals = Object.values(signalData);

    const totalAmbulances = ambulances.length;

    const activeEmergencies = ambulances.filter(function (ambulance) {
        return Boolean(ambulance.emergencyStatus);
    }).length;

    const activePriorities = signals.filter(function (signal) {
        return Boolean(signal.priorityStatus);
    }).length;

    setText("total-ambulances", totalAmbulances);
    setText("active-emergencies", activeEmergencies);
    setText("active-priorities", activePriorities);
    setText("completed-journeys", completedJourneys);
}

function setText(elementId, value) {

    const element = document.getElementById(elementId);

    if (element) {
        element.textContent = value;
    }
}

// ============================================================
// MAP VIEW
// ============================================================

function fitMapToMarkers() {

    if (!mapInitialized || !map) {
        return;
    }

    const points = [];

    Object.values(ambulanceMarkers).forEach(function (marker) {
        points.push(marker.getLatLng());
    });

    Object.values(signalMarkers).forEach(function (marker) {
        points.push(marker.getLatLng());
    });

    Object.values(hospitalMarkers).forEach(function (marker) {
        points.push(marker.getLatLng());
    });

    if (points.length === 1) {

        map.setView(points[0], 14);

    } else if (points.length > 1) {

        map.fitBounds(
            L.latLngBounds(points),
            {
                padding: [30, 30],
                maxZoom: 14
            }
        );
    }
}

// ============================================================
// AMBULANCE SELECTION
// ============================================================

function setupAmbulanceSelection() {

    const select = document.getElementById("ambulance-select");

    if (!select) {
        return;
    }

    select.addEventListener("change", function () {

        const ambulanceId = select.value;
        const marker = ambulanceMarkers[ambulanceId];

        if (marker && mapInitialized && map) {

            map.setView(marker.getLatLng(), 15);

            marker.setStyle({ radius: 13 });

            setTimeout(function () {

                marker.setStyle({ radius: 9 });

            }, 1000);
        }
    });
}

// ============================================================
// CLOCK
// ============================================================

function startClock() {

    function updateTime() {

        const now = new Date();

        const element = document.getElementById("datetime");

        if (element) {
            element.textContent = now.toLocaleString();
        }
    }

    updateTime();

    setInterval(updateTime, 1000);
}

// ============================================================
// EVENT LOG
// ============================================================

function addEventLogEntry(type, message) {

    const eventLog = document.getElementById("event-log");

    if (!eventLog) {
        console.info("[" + type + "] " + message);
        return;
    }

    const entry = document.createElement("div");

    entry.className = "event-log-entry";

    const time = document.createElement("span");
    time.className = "event-time";
    time.textContent = new Date().toLocaleTimeString();

    const eventType = document.createElement("span");
    eventType.className =
        "event-type type-" + String(type).toLowerCase();
    eventType.textContent = String(type).toUpperCase();

    const eventMessage = document.createElement("span");
    eventMessage.className = "event-message";
    eventMessage.textContent = message;

    entry.appendChild(time);
    entry.appendChild(eventType);
    entry.appendChild(eventMessage);

    eventLog.insertBefore(entry, eventLog.firstChild);

    while (eventLog.children.length > 50) {
        eventLog.removeChild(eventLog.lastChild);
    }
}

// ============================================================
// UTILITIES
// ============================================================

function getAmbulanceColor(ambulanceId, isEmergency) {

    const colors = {
        "AMB001": "#dc3545",
        "AMB002": "#0d6efd",
        "AMB003": "#198754"
    };

    if (!isEmergency) {
        return "#6c757d";
    }

    return colors[ambulanceId] || "#6c757d";
}

function getSignalColor(state) {

    const colors = {
        "RED": "#dc3545",
        "YELLOW": "#ffc107",
        "GREEN": "#198754"
    };

    return colors[String(state || "").toUpperCase()] || "#6c757d";
}

function formatNumber(value, decimals) {

    const number = Number(value);

    if (!Number.isFinite(number)) {
        return "0.0";
    }

    return number.toFixed(decimals);
}

function escapeHtml(value) {

    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}