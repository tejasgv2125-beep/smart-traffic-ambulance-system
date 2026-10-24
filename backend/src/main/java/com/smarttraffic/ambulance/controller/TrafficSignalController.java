package com.smarttraffic.ambulance.controller;

import com.smarttraffic.ambulance.model.TrafficSignal;
import com.smarttraffic.ambulance.service.TrafficSignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
@CrossOrigin(origins = "*")
public class TrafficSignalController {

    @Autowired
    private TrafficSignalService trafficSignalService;

    // Get all traffic signals
    @GetMapping
    public List<TrafficSignal> getAllTrafficSignals() {
        return trafficSignalService.getAllTrafficSignals();
    }

    // Get traffic signal by ID
    @GetMapping("/{id}")
    public ResponseEntity<TrafficSignal> getTrafficSignalById(@PathVariable String id) {
        TrafficSignal signal = trafficSignalService.getTrafficSignalById(id);
        if (signal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(signal);
    }

    // Get signals under emergency priority
    @GetMapping("/priority")
    public List<TrafficSignal> getSignalsUnderPriority() {
        return trafficSignalService.getSignalsUnderPriority();
    }

    // Update traffic signal state
    @PutMapping("/{id}/state")
    public ResponseEntity<TrafficSignal> updateSignalState(
            @PathVariable String id,
            @RequestParam com.smarttraffic.ambulance.model.TrafficSignal.SignalState state) {
        TrafficSignal signal = trafficSignalService.updateTrafficSignalState(id, state);
        if (signal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(signal);
    }

    // Activate emergency priority for a signal
    @PutMapping("/{id}/priority/activate")
    public ResponseEntity<TrafficSignal> activateEmergencyPriority(
            @PathVariable String id,
            @RequestParam String ambulanceId) {
        // In a real implementation, we'd validate the ambulance exists
        TrafficSignal signal = trafficSignalService.activateEmergencyPriority(id, ambulanceId);
        if (signal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(signal);
    }

    // Deactivate emergency priority for a signal
    @PutMapping("/{id}/priority/deactivate")
    public ResponseEntity<TrafficSignal> deactivateEmergencyPriority(@PathVariable String id) {
        TrafficSignal signal = trafficSignalService.deactivateEmergencyPriority(id);
        if (signal == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(signal);
    }
}