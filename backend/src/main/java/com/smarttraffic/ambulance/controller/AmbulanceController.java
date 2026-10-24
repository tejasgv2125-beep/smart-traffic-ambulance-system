package com.smarttraffic.ambulance.controller;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.service.AmbulanceService;
import com.smarttraffic.ambulance.service.GPSSimulationService;
import com.smarttraffic.ambulance.service.RoutingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ambulances")
@CrossOrigin(origins = "*")
public class AmbulanceController {

    private final AmbulanceService ambulanceService;
    private final GPSSimulationService gpsSimulationService;
    private final RoutingService routingService;

    @Value("${ambulance.simulation.start-latitude:12.949670}")
    private double simulationStartLatitude;

    @Value("${ambulance.simulation.start-longitude:77.639373}")
    private double simulationStartLongitude;

    public AmbulanceController(
            AmbulanceService ambulanceService,
            GPSSimulationService gpsSimulationService,
            RoutingService routingService) {
        this.ambulanceService = ambulanceService;
        this.gpsSimulationService = gpsSimulationService;
        this.routingService = routingService;
    }

    /** Registers a new ambulance. */
    @PostMapping
    public ResponseEntity<?> registerAmbulance(@RequestBody Ambulance ambulance) {
        try {
            Ambulance saved = ambulanceService.registerAmbulance(ambulance);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    /** Returns all registered ambulances. */
    @GetMapping
    public List<Ambulance> getAllAmbulances() {
        return ambulanceService.getAllAmbulances();
    }

    /** Returns an ambulance by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<Ambulance> getAmbulanceById(@PathVariable String id) {
        Ambulance ambulance = ambulanceService.getAmbulanceById(id);
        if (ambulance == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ambulance);
    }

    /** Updates an ambulance's GPS location. */
    @PutMapping("/{id}/location")
    public ResponseEntity<?> updateAmbulanceLocation(
            @PathVariable String id,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        try {
            Ambulance ambulance = ambulanceService.updateAmbulanceLocation(id, latitude, longitude);
            if (ambulance == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ambulance);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    /**
     * Activates/deactivates emergency mode. When activating, ensure the demo
     * ambulance is not already at its destination, calculate its road route,
     * synchronize signal priority, and start GPS simulation.
     */
    @PutMapping("/{id}/emergency")
    public ResponseEntity<?> updateEmergencyStatus(
            @PathVariable String id,
            @RequestParam boolean emergencyStatus) {
        try {
            Ambulance ambulance = ambulanceService.getAmbulanceById(id);
            if (ambulance == null) {
                return ResponseEntity.notFound().build();
            }

            if (emergencyStatus) {
                // If the ambulance is already at the destination (zero remaining
                // distance and 100% progress), set the demo start before routing.
                if (ambulance.getRemainingDistance() <= 0.0
                        && ambulance.getRouteProgress() >= 100.0) {
                    ambulance = ambulanceService.updateAmbulanceLocation(
                            id, simulationStartLatitude, simulationStartLongitude);
                    if (ambulance == null) {
                        return ResponseEntity.notFound().build();
                    }
                }

                ambulanceService.updateAmbulanceStatus(id, true);

                // Route is now calculated from the initialized start coordinates.
                Map<String, Object> route = routingService.activateEmergencyRoute(id);

                // Start movement only after route and signal priorities are ready.
                gpsSimulationService.startSimulation();

                Map<String, Object> response = new HashMap<>();
                response.put("ambulance", ambulanceService.getAmbulanceById(id));
                response.put("emergencyStatus", true);
                response.put("route", route);
                response.put("message", "Emergency activated. Route calculated, signal priorities synchronized, and GPS simulation started.");
                return ResponseEntity.ok(response);
            }

            ambulanceService.updateAmbulanceStatus(id, false);
            Map<String, Object> route = routingService.activateEmergencyRoute(id);

            Map<String, Object> response = new HashMap<>();
            response.put("ambulance", ambulanceService.getAmbulanceById(id));
            response.put("emergencyStatus", false);
            response.put("route", route);
            response.put("message", "Emergency deactivated. Signal priorities released.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Could not process emergency: " + exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /** Updates journey progress information. */
    @PutMapping("/{id}/progress")
    public ResponseEntity<?> updateAmbulanceProgress(
            @PathVariable String id,
            @RequestParam double remainingDistance,
            @RequestParam int etaMinutes,
            @RequestParam double routeProgress) {
        try {
            Ambulance ambulance = ambulanceService.updateAmbulanceProgress(
                    id, remainingDistance, etaMinutes, routeProgress);
            if (ambulance == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ambulance);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    /** Starts GPS simulation through the backend API. */
    @PostMapping("/simulation/start")
    public ResponseEntity<Map<String, String>> startSimulation() {
        gpsSimulationService.startSimulation();
        return ResponseEntity.ok(Map.of("message", "GPS simulation started"));
    }

    /** Stops GPS simulation through the backend API. */
    @PostMapping("/simulation/stop")
    public ResponseEntity<Map<String, String>> stopSimulation() {
        gpsSimulationService.stopSimulation();
        return ResponseEntity.ok(Map.of("message", "GPS simulation stopped"));
    }

    /** Resets cached journey information. */
    @PostMapping("/simulation/reset")
    public ResponseEntity<Map<String, String>> resetSimulation() {
        gpsSimulationService.resetSimulation();
        return ResponseEntity.ok(Map.of("message", "GPS simulation reset"));
    }
}
