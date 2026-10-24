package com.smarttraffic.ambulance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.model.Hospital;
import com.smarttraffic.ambulance.model.TrafficSignal;
import com.smarttraffic.ambulance.repository.AmbulanceRepository;
import com.smarttraffic.ambulance.repository.HospitalRepository;
import com.smarttraffic.ambulance.repository.TrafficSignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RoutingService {

    private static final double SIGNAL_ROUTE_THRESHOLD_METERS = 100.0;

    private final AmbulanceRepository ambulanceRepository;
    private final HospitalRepository hospitalRepository;
    private final TrafficSignalRepository trafficSignalRepository;
    private final TrafficSignalService trafficSignalService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RoutingService(
            AmbulanceRepository ambulanceRepository,
            HospitalRepository hospitalRepository,
            TrafficSignalRepository trafficSignalRepository,
            TrafficSignalService trafficSignalService) {

        this.ambulanceRepository = ambulanceRepository;
        this.hospitalRepository = hospitalRepository;
        this.trafficSignalRepository = trafficSignalRepository;
        this.trafficSignalService = trafficSignalService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Calculates the road route for an ambulance and synchronizes
     * traffic signal priorities according to its emergency status.
     */
    @Transactional
    public Map<String, Object> getRouteForAmbulance(String ambulanceId) {

        Ambulance ambulance = findAmbulance(ambulanceId);
        Hospital hospital = findDestinationHospital(ambulance);

        Map<String, Object> result =
                calculateRoadRoute(ambulance, hospital);

        @SuppressWarnings("unchecked")
        List<Map<String, Double>> coordinates =
                (List<Map<String, Double>>) result.get("coordinates");

        List<double[]> routePoints = convertCoordinates(coordinates);

        List<Map<String, Object>> signalsOnRoute =
                findSignalsNearRoute(routePoints);

        Set<String> routeSignalIds = new HashSet<>();

        for (Map<String, Object> signal : signalsOnRoute) {
            routeSignalIds.add(
                    String.valueOf(signal.get("signalId"))
            );
        }

        List<TrafficSignal> prioritizedSignals;

        if (ambulance.isEmergencyStatus()) {

            prioritizedSignals =
                    trafficSignalService.synchronizeRoutePriorities(
                            ambulanceId,
                            routeSignalIds
                    );

        } else {

            trafficSignalService.synchronizeRoutePriorities(
                    ambulanceId,
                    Set.of()
            );

            prioritizedSignals = new ArrayList<>();
        }

        Set<String> prioritizedSignalIds = new HashSet<>();

        for (TrafficSignal signal : prioritizedSignals) {
            prioritizedSignalIds.add(signal.getSignalId());
        }

        for (Map<String, Object> signal : signalsOnRoute) {

            String signalId =
                    String.valueOf(signal.get("signalId"));

            signal.put(
                    "priorityActivated",
                    ambulance.isEmergencyStatus()
                            && prioritizedSignalIds.contains(signalId)
            );
        }

        result.put("signalsOnRoute", signalsOnRoute);
        result.put("signalsOnRouteCount", signalsOnRoute.size());
        result.put(
                "prioritizedSignalsCount",
                prioritizedSignals.size()
        );

        return result;
    }

    /**
     * Activates emergency routing when an ambulance's emergency
     * status is true.
     *
     * If the emergency is inactive, the ambulance's signal priorities
     * are released.
     */
    @Transactional
    public Map<String, Object> activateEmergencyRoute(
            String ambulanceId) {

        Ambulance ambulance = findAmbulance(ambulanceId);

        if (!ambulance.isEmergencyStatus()) {

            trafficSignalService.synchronizeRoutePriorities(
                    ambulanceId,
                    Set.of()
            );

            Map<String, Object> result = new HashMap<>();

            result.put("ambulanceId", ambulanceId);
            result.put("emergencyStatus", false);
            result.put(
                    "message",
                    "Emergency is inactive. Signal priorities released."
            );
            result.put("prioritizedSignalsCount", 0);

            return result;
        }

        Map<String, Object> route =
                getRouteForAmbulance(ambulanceId);

        route.put(
                "message",
                "Emergency route calculated and signal priorities synchronized."
        );

        return route;
    }

    /**
     * Returns road-route coordinates without changing traffic signals.
     * Used by GPS simulation to move the ambulance along the road route.
     */
    public List<double[]> getRoadRouteCoordinates(
            String ambulanceId) {

        Ambulance ambulance = findAmbulance(ambulanceId);
        Hospital hospital = findDestinationHospital(ambulance);

        Map<String, Object> routeData =
                calculateRoadRoute(ambulance, hospital);

        @SuppressWarnings("unchecked")
        List<Map<String, Double>> coordinates =
                (List<Map<String, Double>>) routeData.get("coordinates");

        return convertCoordinates(coordinates);
    }

    /**
     * Converts latitude/longitude maps into coordinate arrays.
     */
    private List<double[]> convertCoordinates(
            List<Map<String, Double>> coordinates) {

        List<double[]> points = new ArrayList<>();

        for (Map<String, Double> coordinate : coordinates) {

            points.add(
                    new double[]{
                            coordinate.get("latitude"),
                            coordinate.get("longitude")
                    }
            );
        }

        return points;
    }

    /**
     * Finds an ambulance by its ID.
     */
    private Ambulance findAmbulance(String ambulanceId) {

        Ambulance ambulance =
                ambulanceRepository.findByAmbulanceId(ambulanceId);

        if (ambulance == null) {
            throw new IllegalArgumentException(
                    "Ambulance not found: " + ambulanceId
            );
        }

        return ambulance;
    }

    /**
     * Finds the hospital assigned as the ambulance's destination.
     * Supports either a hospital ID or a hospital name.
     */
    private Hospital findDestinationHospital(
            Ambulance ambulance) {

        String destination =
                ambulance.getDestinationHospital();

        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException(
                    "No destination hospital assigned to ambulance "
                            + ambulance.getAmbulanceId()
            );
        }

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
                                    "Destination hospital not found: "
                                            + destination
                            )
                    );
        }

        return hospital;
    }

    /**
     * Calculates road-route information using OSRM.
     * This method does not modify traffic signal priorities.
     */
    private Map<String, Object> calculateRoadRoute(
            Ambulance ambulance,
            Hospital hospital) {

        double startLat =
                ambulance.getCurrentLatitude();

        double startLng =
                ambulance.getCurrentLongitude();

        double endLat =
                hospital.getLatitude();

        double endLng =
                hospital.getLongitude();

        if (!validCoordinates(startLat, startLng)
                || !validCoordinates(endLat, endLng)) {

            throw new IllegalArgumentException(
                    "Ambulance or hospital coordinates are invalid"
            );
        }

        try {

            String url = String.format(
                    Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    startLng,
                    startLat,
                    endLng,
                    endLat
            );

            String response =
                    restTemplate.getForObject(url, String.class);

            if (response == null || response.isBlank()) {
                throw new IllegalStateException(
                        "OSRM returned an empty response"
                );
            }

            JsonNode root =
                    objectMapper.readTree(response);

            if (!"Ok".equals(root.path("code").asText())
                    || !root.path("routes").isArray()
                    || root.path("routes").isEmpty()) {

                throw new IllegalStateException(
                        "OSRM could not calculate a road route"
                );
            }

            JsonNode route =
                    root.path("routes").get(0);

            JsonNode coordinates =
                    route.path("geometry").path("coordinates");

            List<Map<String, Double>> routeCoordinates =
                    new ArrayList<>();

            for (JsonNode point : coordinates) {

                Map<String, Double> coordinate =
                        new HashMap<>();

                // GeoJSON coordinates are [longitude, latitude].
                coordinate.put(
                        "latitude",
                        point.get(1).asDouble()
                );

                coordinate.put(
                        "longitude",
                        point.get(0).asDouble()
                );

                routeCoordinates.add(coordinate);
            }

            Map<String, Object> result =
                    new HashMap<>();

            result.put(
                    "ambulanceId",
                    ambulance.getAmbulanceId()
            );

            result.put(
                    "emergencyStatus",
                    ambulance.isEmergencyStatus()
            );

            result.put(
                    "hospitalId",
                    hospital.getHospitalId()
            );

            result.put(
                    "hospitalName",
                    hospital.getHospitalName()
            );

            result.put(
                    "distanceKm",
                    Math.round(
                            route.path("distance").asDouble()
                                    / 1000.0 * 100.0
                    ) / 100.0
            );

            result.put(
                    "durationMinutes",
                    Math.ceil(
                            route.path("duration").asDouble() / 60.0
                    )
            );

            result.put(
                    "coordinates",
                    routeCoordinates
            );

            return result;

        } catch (IllegalArgumentException exception) {

            throw exception;

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to calculate road route. "
                            + "Check internet access and OSRM availability.",
                    exception
            );
        }
    }

    /**
     * Finds traffic signals located within the configured distance
     * of the calculated road route.
     */
    private List<Map<String, Object>> findSignalsNearRoute(
            List<double[]> routePoints) {

        List<Map<String, Object>> result =
                new ArrayList<>();

        if (routePoints.isEmpty()) {
            return result;
        }

        List<TrafficSignal> signals =
                trafficSignalRepository.findAll();

        for (TrafficSignal signal : signals) {

            double signalLat = signal.getLatitude();
            double signalLng = signal.getLongitude();

            if (!validCoordinates(signalLat, signalLng)) {
                continue;
            }

            double nearestDistance =
                    Double.MAX_VALUE;

            for (int i = 0; i < routePoints.size(); i++) {

                double[] point = routePoints.get(i);

                nearestDistance = Math.min(
                        nearestDistance,
                        distanceMeters(
                                signalLat,
                                signalLng,
                                point[0],
                                point[1]
                        )
                );

                if (i > 0) {

                    double[] previous =
                            routePoints.get(i - 1);

                    nearestDistance = Math.min(
                            nearestDistance,
                            distanceToSegmentMeters(
                                    signalLat,
                                    signalLng,
                                    previous[0],
                                    previous[1],
                                    point[0],
                                    point[1]
                            )
                    );
                }

                if (nearestDistance
                        <= SIGNAL_ROUTE_THRESHOLD_METERS) {
                    break;
                }
            }

            if (nearestDistance
                    <= SIGNAL_ROUTE_THRESHOLD_METERS) {

                Map<String, Object> signalInfo =
                        new HashMap<>();

                signalInfo.put(
                        "signalId",
                        signal.getSignalId()
                );

                signalInfo.put(
                        "signalName",
                        signal.getSignalName()
                );

                signalInfo.put(
                        "latitude",
                        signalLat
                );

                signalInfo.put(
                        "longitude",
                        signalLng
                );

                signalInfo.put(
                        "currentState",
                        signal.getCurrentState()
                );

                signalInfo.put(
                        "priorityStatus",
                        signal.isPriorityStatus()
                );

                signalInfo.put(
                        "distanceFromRouteMeters",
                        Math.round(nearestDistance * 10.0) / 10.0
                );

                result.add(signalInfo);
            }
        }

        result.sort((a, b) ->
                Double.compare(
                        ((Number) a.get(
                                "distanceFromRouteMeters"
                        )).doubleValue(),
                        ((Number) b.get(
                                "distanceFromRouteMeters"
                        )).doubleValue()
                )
        );

        return result;
    }

    /**
     * Calculates the distance between two geographical points
     * using the Haversine formula.
     */
    private double distanceMeters(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        double earthRadius = 6_371_000.0;

        double dLat =
                Math.toRadians(lat2 - lat1);

        double dLon =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        return earthRadius * 2
                * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1.0 - a)
        );
    }

    /**
     * Calculates the shortest distance between a signal and a
     * road-route segment.
     */
    private double distanceToSegmentMeters(
            double pointLat,
            double pointLon,
            double startLat,
            double startLon,
            double endLat,
            double endLon) {

        double meanLat = Math.toRadians(
                (pointLat + startLat + endLat) / 3.0
        );

        double scaleX =
                111_320.0 * Math.cos(meanLat);

        double scaleY = 110_540.0;

        double px =
                (pointLon - startLon) * scaleX;

        double py =
                (pointLat - startLat) * scaleY;

        double ex =
                (endLon - startLon) * scaleX;

        double ey =
                (endLat - startLat) * scaleY;

        double segmentLengthSquared =
                ex * ex + ey * ey;

        if (segmentLengthSquared == 0) {
            return distanceMeters(
                    pointLat,
                    pointLon,
                    startLat,
                    startLon
            );
        }

        double t =
                (px * ex + py * ey)
                        / segmentLengthSquared;

        t = Math.max(0.0, Math.min(1.0, t));

        double closestLat =
                startLat + t * (endLat - startLat);

        double closestLon =
                startLon + t * (endLon - startLon);

        return distanceMeters(
                pointLat,
                pointLon,
                closestLat,
                closestLon
        );
    }

    /**
     * Validates latitude and longitude values.
     */
    private boolean validCoordinates(
            double latitude,
            double longitude) {

        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }
}