
package com.smarttraffic.ambulance.service;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.model.Hospital;
import com.smarttraffic.ambulance.repository.AmbulanceRepository;
import com.smarttraffic.ambulance.repository.HospitalRepository;
import com.smarttraffic.ambulance.websocket.WebSocketMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GPSSimulationService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double ARRIVAL_DISTANCE_KM = 0.05;

    private final AmbulanceRepository ambulanceRepository;
    private final HospitalRepository hospitalRepository;
    private final WebSocketMessagePublisher webSocketMessagePublisher;
    private final RoutingService routingService;
    private final TrafficSignalService trafficSignalService;

    /*
     * Stores the road route for each active ambulance.
     */
    private final Map<String, List<double[]>> routeCache =
            new ConcurrentHashMap<>();

    /*
     * Tracks the next road-route coordinate for each ambulance.
     */
    private final Map<String, Integer> routeIndexes =
            new ConcurrentHashMap<>();

    /*
     * Stores the original journey distance for progress calculation.
     */
    private final Map<String, Double> initialJourneyDistances =
            new ConcurrentHashMap<>();

    /*
     * Tracks the hospital assigned to each active journey.
     */
    private final Map<String, String> journeyDestinations =
            new ConcurrentHashMap<>();

    // Prevent completed demo journeys from restarting until simulation is restarted.
    private final Set<String> completedJourneys = ConcurrentHashMap.newKeySet();

    @Value("${ambulance.simulation.speed-kmh:40}")
    private double defaultSpeedKmh;

    @Value("${ambulance.simulation.interval:5000}")
    private long simulationIntervalMillis;

    @Value("${ambulance.simulation.start-latitude:12.949670}")
    private double simulationStartLatitude;

    @Value("${ambulance.simulation.start-longitude:77.639373}")
    private double simulationStartLongitude;

    /*
     * The simulation starts automatically when an ambulance
     * has an active emergency.
     */
    private volatile boolean simulationRunning = false;

    public GPSSimulationService(
            AmbulanceRepository ambulanceRepository,
            HospitalRepository hospitalRepository,
            WebSocketMessagePublisher webSocketMessagePublisher,
            RoutingService routingService,
            TrafficSignalService trafficSignalService) {

        this.ambulanceRepository = ambulanceRepository;
        this.hospitalRepository = hospitalRepository;
        this.webSocketMessagePublisher = webSocketMessagePublisher;
        this.routingService = routingService;
        this.trafficSignalService = trafficSignalService;
    }

    /**
     * Starts GPS simulation.
     */
    public void startSimulation() {
        completedJourneys.clear();
        simulationRunning = true;
    }

    /**
     * Stops GPS simulation.
     */
    public void stopSimulation() {
        simulationRunning = false;
    }

    /**
     * Clears simulation route information.
     */
    public void resetSimulation() {
        simulationRunning = false;
        completedJourneys.clear();
        clearAllJourneyCaches();
    }

    /**
     * Periodically updates the GPS position of emergency ambulances.
     *
     * No dashboard simulation button is required. When an ambulance
     * is marked as an emergency in the backend, simulation starts
     * automatically.
     */
    @Scheduled(fixedDelayString = "${ambulance.simulation.interval:5000}")
    public void updateAmbulancePositions() {

        List<Ambulance> ambulances =
                ambulanceRepository.findAll();

        boolean hasActiveEmergency = ambulances.stream()
                .anyMatch(Ambulance::isEmergencyStatus);

        if (hasActiveEmergency) {
            simulationRunning = true;
        }

        if (!simulationRunning) {
            return;
        }

        // Demonstration mode: once simulation starts, all three ambulances
        // move independently toward their assigned hospitals. Only ambulances
        // with emergencyStatus=true receive emergency signal priority.
        for (Ambulance ambulance : ambulances) {

            String ambulanceId = ambulance.getAmbulanceId();
            if (completedJourneys.contains(ambulanceId)) {
                continue;
            }

            try {
                updateAmbulancePosition(ambulance);
            } catch (Exception exception) {
                System.err.println(
                        "GPS simulation error for ambulance "
                                + ambulanceId
                                + ": "
                                + exception.getMessage()
                );
            }
        }
    }

    /**
     * Advances one ambulance along its calculated road route.
     */
    @Transactional
    public void updateAmbulancePosition(Ambulance ambulance) {

        String ambulanceId =
                ambulance.getAmbulanceId();

        String destination =
                ambulance.getDestinationHospital();

        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException(
                    "No destination hospital assigned to ambulance "
                            + ambulanceId
            );
        }

        Hospital hospital = findHospital(destination);

        double targetLat = hospital.getLatitude();
        double targetLng = hospital.getLongitude();

        validateCoordinates(targetLat, targetLng);

        /*
         * If there is no cached journey and the ambulance is already
         * at its destination, initialize the configured demo GPS start.
         * This is done in application code; no manual SQL update is needed.
         */
        String previousDestination = journeyDestinations.get(ambulanceId);
        if (previousDestination != null
                && !previousDestination.equals(destination)) {
            clearJourneyCache(ambulanceId);
        }

        if (!routeCache.containsKey(ambulanceId)) {
            double distanceToHospital = calculateDistance(
                    ambulance.getCurrentLatitude(),
                    ambulance.getCurrentLongitude(),
                    targetLat,
                    targetLng
            );

            if (distanceToHospital <= ARRIVAL_DISTANCE_KM) {
                initializeDemoStartingLocation(
                        ambulance,
                        targetLat,
                        targetLng
                );
            }
        }

        double currentLat = ambulance.getCurrentLatitude();
        double currentLng = ambulance.getCurrentLongitude();
        validateCoordinates(currentLat, currentLng);

        /*
         * Simulate changing road speed on every GPS tick. Each ambulance
         * has a different baseline, and the time-based variation represents
         * changing traffic/congestion conditions. This deliberately does not
         * reuse currentSpeed, because that would lock every ambulance at its
         * previously saved speed.
         */
        double speedKmh = calculateDynamicSpeedKmh(
                ambulanceId,
                ambulance.getRouteProgress()
        );

        if (!Double.isFinite(speedKmh) || speedKmh <= 0) {
            throw new IllegalArgumentException(
                    "Simulation speed must be greater than zero."
            );
        }

        /*
         * Rebuild the route if the destination has changed.
         */
        String cachedDestination =
                journeyDestinations.get(ambulanceId);

        if (cachedDestination != null
                && !cachedDestination.equals(destination)) {

            clearJourneyCache(ambulanceId);
        }

        journeyDestinations.put(
                ambulanceId,
                destination
        );

        /*
         * Obtain the road route from RoutingService.
         */
        List<double[]> route =
                routeCache.get(ambulanceId);

        if (route == null || route.size() < 2) {

            route = routingService.getRoadRouteCoordinates(
                    ambulanceId
            );

            if (route == null || route.size() < 2) {
                throw new IllegalStateException(
                        "A valid road route could not be calculated "
                                + "for ambulance "
                                + ambulanceId
                );
            }

            routeCache.put(
                    ambulanceId,
                    route
            );

            routeIndexes.put(
                    ambulanceId,
                    findNearestRouteIndex(
                            route,
                            currentLat,
                            currentLng
                    )
            );

            initialJourneyDistances.put(
                    ambulanceId,
                    calculateRouteLength(route)
            );
        }

        /*
         * If the ambulance is already close enough to the hospital,
         * complete the journey immediately.
         */
        if (calculateDistance(
                currentLat,
                currentLng,
                targetLat,
                targetLng
        ) <= ARRIVAL_DISTANCE_KM) {

            completeJourney(
                    ambulance,
                    targetLat,
                    targetLng
            );

            return;
        }

        int index = routeIndexes.getOrDefault(
                ambulanceId,
                0
        );

        /*
         * Calculate how far the ambulance can move during one
         * simulation interval.
         *
         * Distance in kilometres = speed in km/h * time in hours.
         */
        double distanceToMove =
                speedKmh
                        * Math.max(1L, simulationIntervalMillis)
                        / 3_600_000.0;

        /*
         * Advance along the route. If the ambulance reaches a
         * route coordinate before using the full distance allowance,
         * continue toward the next coordinate.
         */
        while (index < route.size()
                && distanceToMove > 0) {

            double[] nextPoint = route.get(index);

            double segmentDistance = calculateDistance(
                    currentLat,
                    currentLng,
                    nextPoint[0],
                    nextPoint[1]
            );

            if (segmentDistance <= 0.001) {

                currentLat = nextPoint[0];
                currentLng = nextPoint[1];

                index++;
                continue;
            }

            if (distanceToMove < segmentDistance) {

                double ratio =
                        distanceToMove / segmentDistance;

                currentLat +=
                        (nextPoint[0] - currentLat) * ratio;

                currentLng +=
                        (nextPoint[1] - currentLng) * ratio;

                distanceToMove = 0;

            } else {

                currentLat = nextPoint[0];
                currentLng = nextPoint[1];

                distanceToMove -= segmentDistance;

                index++;
            }
        }

        routeIndexes.put(
                ambulanceId,
                Math.min(index, route.size())
        );

        /*
         * Check whether the ambulance has reached the hospital
         * or reached the end of its calculated route.
         */
        double distanceToHospital = calculateDistance(
                currentLat,
                currentLng,
                targetLat,
                targetLng
        );

        if (index >= route.size()
                || distanceToHospital <= ARRIVAL_DISTANCE_KM) {

            completeJourney(
                    ambulance,
                    targetLat,
                    targetLng
            );

            return;
        }

        /*
         * Update live journey information.
         */
        double remainingDistance =
                calculateRemainingRouteDistance(
                        currentLat,
                        currentLng,
                        route,
                        index
                );

        ambulance.setCurrentLatitude(currentLat);
        ambulance.setCurrentLongitude(currentLng);
        ambulance.setCurrentSpeed(speedKmh);

        ambulance.setRemainingDistance(
                remainingDistance
        );

        ambulance.setLastLocationUpdate(
                LocalDateTime.now()
        );

        ambulance.setEtaMinutes(
                (int) Math.ceil(
                        remainingDistance / speedKmh * 60.0
                )
        );

        /*
         * Calculate journey progress as a percentage.
         */
        double originalDistance =
                initialJourneyDistances.getOrDefault(
                        ambulanceId,
                        remainingDistance
                );

        double progress =
                originalDistance > 0
                        ? (originalDistance - remainingDistance)
                          / originalDistance * 100.0
                        : 0.0;

        ambulance.setRouteProgress(
                Math.min(
                        99.9,
                        Math.max(0.0, progress)
                )
        );

        /*
         * Persist the new position and notify connected dashboards.
         */
        Ambulance saved =
                ambulanceRepository.save(ambulance);

        webSocketMessagePublisher.publishAmbulanceUpdate(
                saved
        );
    }


    /**
     * Returns a changing, simulated road speed for an ambulance.
     * Different ID-based offsets keep the ambulances from moving at the
     * same speed; time-based waves simulate traffic conditions changing
     * while the journey is in progress. The result is bounded to 18-55 km/h.
     */
    private double calculateDynamicSpeedKmh(
            String ambulanceId,
            Double routeProgress) {

        String id = ambulanceId == null ? "AMB" : ambulanceId;
        int idHash = Math.abs(id.hashCode());
        double phase = (idHash % 1000) / 100.0;

        double ambulanceOffset;
        if ("AMB001".equalsIgnoreCase(id)) {
            ambulanceOffset = 5.0;
        } else if ("AMB002".equalsIgnoreCase(id)) {
            ambulanceOffset = -4.0;
        } else if ("AMB003".equalsIgnoreCase(id)) {
            ambulanceOffset = 0.0;
        } else {
            ambulanceOffset = ((idHash % 11) - 5) * 1.0;
        }

        double elapsedSeconds = System.currentTimeMillis() / 1000.0;
        double progress = routeProgress == null ? 0.0 : routeProgress;
        double trafficWave = 6.0 * Math.sin(elapsedSeconds / 17.0 + phase)
                + 3.0 * Math.sin(elapsedSeconds / 7.0 + phase * 1.7);
        double routeCondition = 2.0 * Math.sin(progress / 9.0 + phase);

        double speed = defaultSpeedKmh + ambulanceOffset
                + trafficWave + routeCondition;
        return Math.max(18.0, Math.min(55.0, speed));
    }

    /**
     * Moves the simulated ambulance to its configured demo start only
     * when a new journey begins at the destination hospital.
     */
    private void initializeDemoStartingLocation(
            Ambulance ambulance,
            double targetLat,
            double targetLng) {

        double startLat = simulationStartLatitude;
        double startLng = simulationStartLongitude;
        // Separate start points for the three demo routes. AMB001 keeps the
        // configured start; AMB002/003 use distinct areas of Bengaluru.
        if ("AMB002".equalsIgnoreCase(ambulance.getAmbulanceId())) {
            startLat = 12.986500;
            startLng = 77.610000;
        } else if ("AMB003".equalsIgnoreCase(ambulance.getAmbulanceId())) {
            startLat = 12.997000;
            startLng = 77.570000;
        }
        validateCoordinates(startLat, startLng);

        double distanceToHospital = calculateDistance(
                startLat,
                startLng,
                targetLat,
                targetLng
        );

        if (distanceToHospital <= ARRIVAL_DISTANCE_KM) {
            throw new IllegalStateException(
                    "The configured simulation starting location must be "
                            + "more than 50 metres from the destination hospital."
            );
        }

        ambulance.setCurrentLatitude(startLat);
        ambulance.setCurrentLongitude(startLng);
        ambulance.setCurrentSpeed(0.0);
        ambulance.setRemainingDistance(0.0);
        ambulance.setEtaMinutes(0);
        ambulance.setRouteProgress(0.0);
        ambulance.setLastLocationUpdate(LocalDateTime.now());

        ambulanceRepository.saveAndFlush(ambulance);
    }

    /**
     * Finds a hospital by ID or hospital name.
     */
    private Hospital findHospital(String destination) {

        Hospital hospital =
                hospitalRepository.findByHospitalId(destination);

        if (hospital == null) {

            hospital = hospitalRepository.findAll()
                    .stream()
                    .filter(item ->
                            destination.equalsIgnoreCase(
                                    item.getHospitalName()
                            )
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Hospital not found: "
                                            + destination
                            )
                    );
        }

        return hospital;
    }

    /**
     * Completes the ambulance journey and releases its
     * traffic-signal priorities.
     */
    private void completeJourney(
            Ambulance ambulance,
            double targetLat,
            double targetLng) {

        String ambulanceId =
                ambulance.getAmbulanceId();

        ambulance.setCurrentLatitude(targetLat);
        ambulance.setCurrentLongitude(targetLng);
        ambulance.setCurrentSpeed(0.0);
        ambulance.setRemainingDistance(0.0);
        ambulance.setEtaMinutes(0);
        ambulance.setRouteProgress(100.0);
        ambulance.setEmergencyStatus(false);

        ambulance.setLastLocationUpdate(
                LocalDateTime.now()
        );

        /*
         * Release only the signal priorities associated with
         * this ambulance.
         */
        trafficSignalService.synchronizeRoutePriorities(
                ambulanceId,
                Collections.emptySet()
        );

        clearJourneyCache(ambulanceId);
        completedJourneys.add(ambulanceId);

        Ambulance saved =
                ambulanceRepository.save(ambulance);

        webSocketMessagePublisher.publishAmbulanceUpdate(
                saved
        );

        webSocketMessagePublisher.publishAmbulanceArrived(
                saved
        );
    }

    /**
     * Calculates the total length of a route in kilometres.
     */
    private double calculateRouteLength(
            List<double[]> route) {

        double total = 0.0;

        for (int i = 1; i < route.size(); i++) {

            total += calculateDistance(
                    route.get(i - 1)[0],
                    route.get(i - 1)[1],
                    route.get(i)[0],
                    route.get(i)[1]
            );
        }

        return total;
    }

    /**
     * Calculates the remaining distance along the road route.
     */
    private double calculateRemainingRouteDistance(
            double currentLat,
            double currentLng,
            List<double[]> route,
            int index) {

        if (index >= route.size()) {

            double[] lastPoint =
                    route.get(route.size() - 1);

            return calculateDistance(
                    currentLat,
                    currentLng,
                    lastPoint[0],
                    lastPoint[1]
            );
        }

        double remaining = calculateDistance(
                currentLat,
                currentLng,
                route.get(index)[0],
                route.get(index)[1]
        );

        for (int i = index + 1;
             i < route.size();
             i++) {

            remaining += calculateDistance(
                    route.get(i - 1)[0],
                    route.get(i - 1)[1],
                    route.get(i)[0],
                    route.get(i)[1]
            );
        }

        return remaining;
    }

    /**
     * Finds the closest coordinate on the route to the
     * ambulance's current GPS location.
     */
    private int findNearestRouteIndex(
            List<double[]> route,
            double latitude,
            double longitude) {

        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < route.size(); i++) {

            double distance = calculateDistance(
                    latitude,
                    longitude,
                    route.get(i)[0],
                    route.get(i)[1]
            );

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    /**
     * Clears cached route information for one ambulance.
     */
    private void clearJourneyCache(String ambulanceId) {

        routeCache.remove(ambulanceId);
        routeIndexes.remove(ambulanceId);
        initialJourneyDistances.remove(ambulanceId);
        journeyDestinations.remove(ambulanceId);
    }

    /**
     * Clears all cached route information.
     */
    private void clearAllJourneyCaches() {

        routeCache.clear();
        routeIndexes.clear();
        initialJourneyDistances.clear();
        journeyDestinations.clear();
        completedJourneys.clear();
    }

    /**
     * Calculates the distance between two GPS coordinates
     * using the Haversine formula.
     *
     * @return distance in kilometres
     */
    private double calculateDistance(
            double lat1,
            double lng1,
            double lat2,
            double lng2) {

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLng =
                Math.toRadians(lng2 - lng1);

        double latitude1 =
                Math.toRadians(lat1);

        double latitude2 =
                Math.toRadians(lat2);

        double a =
                Math.sin(dLat / 2)
                        * Math.sin(dLat / 2)
                        + Math.cos(latitude1)
                        * Math.cos(latitude2)
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(Math.max(0.0, 1.0 - a))
        );

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Validates GPS coordinates.
     */
    private void validateCoordinates(
            double latitude,
            double longitude) {

        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180
                || (latitude == 0 && longitude == 0)) {

            throw new IllegalArgumentException(
                    "Invalid GPS coordinates: "
                            + latitude + ", " + longitude
            );
        }
    }
}

