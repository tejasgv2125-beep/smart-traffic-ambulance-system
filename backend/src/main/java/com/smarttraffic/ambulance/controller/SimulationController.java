package com.smarttraffic.ambulance.controller;

import com.smarttraffic.ambulance.service.GPSSimulationService;
import com.smarttraffic.ambulance.service.TrafficSignalPriorityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    @Autowired
    private GPSSimulationService gpsSimulationService;

    @Autowired
    private TrafficSignalPriorityService trafficSignalPriorityService;

    // Start simulation
    @PostMapping("/start")
    public ResponseEntity<String> startSimulation() {
        gpsSimulationService.startSimulation();
        return ResponseEntity.ok("Simulation started");
    }

    // Stop simulation
    @PostMapping("/stop")
    public ResponseEntity<String> stopSimulation() {
        gpsSimulationService.stopSimulation();
        return ResponseEntity.ok("Simulation stopped");
    }

    // Reset simulation
    @PostMapping("/reset")
    public ResponseEntity<String> resetSimulation() {
        gpsSimulationService.resetSimulation();
        return ResponseEntity.ok("Simulation reset");
    }

    // Get simulation status
    @GetMapping("/status")
    public ResponseEntity<String> getSimulationStatus() {
        // In a real implementation, we'd return more detailed status
        return ResponseEntity.ok("Simulation status: " +
            (gpsSimulationService instanceof GPSSimulationService ? "initialized" : "unknown"));
    }
}