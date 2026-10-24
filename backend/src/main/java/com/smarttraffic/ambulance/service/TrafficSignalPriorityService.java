package com.smarttraffic.ambulance.service;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.model.TrafficSignal;
import com.smarttraffic.ambulance.repository.AmbulanceRepository;
import com.smarttraffic.ambulance.repository.TrafficSignalRepository;
import com.smarttraffic.ambulance.websocket.WebSocketMessagePublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Software-only traffic signal priority simulation.
 * Every cycle releases previous priorities first, then grants priority to the
 * nearest signal within range of each moving emergency ambulance. This avoids
 * stale signals remaining green after an ambulance has passed.
 */
@Service
public class TrafficSignalPriorityService {
    private static final double PRIORITY_RADIUS_KM = 0.30;

    private final AmbulanceRepository ambulanceRepository;
    private final TrafficSignalRepository trafficSignalRepository;
    private final WebSocketMessagePublisher webSocketMessagePublisher;

    public TrafficSignalPriorityService(AmbulanceRepository ambulanceRepository,
                                        TrafficSignalRepository trafficSignalRepository,
                                        WebSocketMessagePublisher webSocketMessagePublisher) {
        this.ambulanceRepository = ambulanceRepository;
        this.trafficSignalRepository = trafficSignalRepository;
        this.webSocketMessagePublisher = webSocketMessagePublisher;
    }

    @Scheduled(fixedDelayString = "${ambulance.simulation.interval:5000}")
    @Transactional
    public void evaluateSignalPriorities() {
        List<TrafficSignal> allSignals = trafficSignalRepository.findAll();
        // Always release last cycle's priority before computing the current cycle.
        for (TrafficSignal signal : allSignals) {
            if (signal.isPriorityStatus() || signal.getAssociatedAmbulanceId() != null) {
                signal.setPriorityStatus(false);
                signal.setAssociatedAmbulanceId(null);
                signal.setCurrentState(signal.getNormalState() == null
                        ? TrafficSignal.SignalState.RED : signal.getNormalState());
                signal.setLastUpdateTime(LocalDateTime.now());
                trafficSignalRepository.save(signal);
                webSocketMessagePublisher.publishSignalUpdate(signal, null);
            }
        }

        List<Ambulance> active = ambulanceRepository.findAll().stream()
                .filter(Ambulance::isEmergencyStatus)
                .filter(a -> a.getCurrentSpeed() > 0)
                .toList();

        Map<String, String> assignedSignals = new HashMap<>();
        for (Ambulance ambulance : active) {
            TrafficSignal nearest = allSignals.stream()
                    .filter(s -> !assignedSignals.containsKey(s.getSignalId()))
                    .filter(s -> distanceKm(ambulance.getCurrentLatitude(), ambulance.getCurrentLongitude(),
                            s.getLatitude(), s.getLongitude()) <= PRIORITY_RADIUS_KM)
                    .min(Comparator.comparingDouble(s -> distanceKm(
                            ambulance.getCurrentLatitude(), ambulance.getCurrentLongitude(),
                            s.getLatitude(), s.getLongitude())))
                    .orElse(null);

            if (nearest != null) {
                nearest.setPriorityStatus(true);
                nearest.setAssociatedAmbulanceId(ambulance.getAmbulanceId());
                // GREEN is the priority state for the emergency vehicle's approach.
                nearest.setCurrentState(TrafficSignal.SignalState.GREEN);
                nearest.setLastUpdateTime(LocalDateTime.now());
                trafficSignalRepository.save(nearest);
                assignedSignals.put(nearest.getSignalId(), ambulance.getAmbulanceId());
                webSocketMessagePublisher.publishSignalUpdate(nearest, ambulance);
            }
        }
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
