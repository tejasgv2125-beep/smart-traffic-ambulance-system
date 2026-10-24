
        package com.smarttraffic.ambulance.service;

import com.smarttraffic.ambulance.model.TrafficSignal;
import com.smarttraffic.ambulance.repository.TrafficSignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TrafficSignalService {

    private final TrafficSignalRepository trafficSignalRepository;

    public TrafficSignalService(
            TrafficSignalRepository trafficSignalRepository) {
        this.trafficSignalRepository = trafficSignalRepository;
    }

    @Transactional
    public TrafficSignal saveTrafficSignal(TrafficSignal trafficSignal) {
        if (trafficSignal == null) {
            throw new IllegalArgumentException(
                    "Traffic signal details are required.");
        }

        trafficSignal.setLastUpdateTime(LocalDateTime.now());
        return trafficSignalRepository.save(trafficSignal);
    }

    @Transactional(readOnly = true)
    public TrafficSignal getTrafficSignalById(String signalId) {
        if (signalId == null || signalId.isBlank()) {
            throw new IllegalArgumentException("Signal ID is required.");
        }

        return trafficSignalRepository.findBySignalId(signalId.trim());
    }

    @Transactional(readOnly = true)
    public List<TrafficSignal> getAllTrafficSignals() {
        return trafficSignalRepository.findAll();
    }

    @Transactional
    public TrafficSignal updateTrafficSignalState(
            String signalId,
            TrafficSignal.SignalState newState) {

        if (newState == null) {
            throw new IllegalArgumentException(
                    "Traffic signal state is required.");
        }

        TrafficSignal signal =
                trafficSignalRepository.findBySignalId(signalId);

        if (signal == null) {
            return null;
        }

        // Do not let a normal state update override an active emergency.
        if (!signal.isPriorityStatus()) {
            signal.setCurrentState(newState);
            signal.setNormalState(newState);
        }

        signal.setLastUpdateTime(LocalDateTime.now());
        return trafficSignalRepository.save(signal);
    }

    @Transactional
    public TrafficSignal activateEmergencyPriority(
            String signalId,
            String ambulanceId) {

        if (signalId == null || signalId.isBlank()
                || ambulanceId == null || ambulanceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Signal ID and ambulance ID are required.");
        }

        TrafficSignal signal =
                trafficSignalRepository.findBySignalId(signalId.trim());

        if (signal == null) {
            return null;
        }

        String currentAmbulance =
                signal.getAssociatedAmbulanceId();

        // Never replace an existing ambulance's priority assignment.
        if (signal.isPriorityStatus()
                && currentAmbulance != null
                && !currentAmbulance.equals(ambulanceId.trim())) {
            return signal;
        }

        signal.setPriorityStatus(true);
        signal.setAssociatedAmbulanceId(ambulanceId.trim());
        signal.setCurrentState(TrafficSignal.SignalState.GREEN);
        signal.setLastUpdateTime(LocalDateTime.now());

        return trafficSignalRepository.save(signal);
    }

    @Transactional
    public TrafficSignal deactivateEmergencyPriority(String signalId) {
        if (signalId == null || signalId.isBlank()) {
            throw new IllegalArgumentException("Signal ID is required.");
        }

        TrafficSignal signal =
                trafficSignalRepository.findBySignalId(signalId.trim());

        if (signal == null) {
            return null;
        }

        signal.setPriorityStatus(false);
        signal.setAssociatedAmbulanceId(null);

        if (signal.getNormalState() != null) {
            signal.setCurrentState(signal.getNormalState());
        }

        signal.setLastUpdateTime(LocalDateTime.now());
        return trafficSignalRepository.save(signal);
    }

    /**
     * Synchronizes signal priorities with the current ambulance route.
     * Signals assigned to another ambulance are left unchanged.
     */
    @Transactional
    public List<TrafficSignal> synchronizeRoutePriorities(
            String ambulanceId,
            Set<String> routeSignalIds) {

        if (ambulanceId == null || ambulanceId.isBlank()) {
            throw new IllegalArgumentException(
                    "Ambulance ID is required.");
        }

        String normalizedAmbulanceId = ambulanceId.trim();
        Set<String> normalizedSignalIds =
                routeSignalIds == null ? Set.of() : routeSignalIds;

        List<TrafficSignal> allSignals =
                trafficSignalRepository.findAll();

        LocalDateTime now = LocalDateTime.now();

        for (TrafficSignal signal : allSignals) {
            boolean belongsToAmbulance =
                    normalizedAmbulanceId.equals(
                            signal.getAssociatedAmbulanceId());

            boolean shouldBePrioritized =
                    normalizedSignalIds.contains(signal.getSignalId());

            if (shouldBePrioritized) {
                String assignedAmbulance =
                        signal.getAssociatedAmbulanceId();

                // Preserve another ambulance's current assignment.
                if (assignedAmbulance != null
                        && !normalizedAmbulanceId.equals(
                        assignedAmbulance)) {
                    continue;
                }

                signal.setPriorityStatus(true);
                signal.setAssociatedAmbulanceId(
                        normalizedAmbulanceId);
                signal.setCurrentState(
                        TrafficSignal.SignalState.GREEN);
                signal.setLastUpdateTime(now);

                trafficSignalRepository.save(signal);

            } else if (belongsToAmbulance) {
                signal.setPriorityStatus(false);
                signal.setAssociatedAmbulanceId(null);

                if (signal.getNormalState() != null) {
                    signal.setCurrentState(signal.getNormalState());
                }

                signal.setLastUpdateTime(now);
                trafficSignalRepository.save(signal);
            }
        }

        return allSignals.stream()
                .filter(signal ->
                        normalizedSignalIds.contains(
                                signal.getSignalId())
                                && signal.isPriorityStatus()
                                && normalizedAmbulanceId.equals(
                                signal.getAssociatedAmbulanceId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TrafficSignal> getSignalsUnderPriority() {
        return trafficSignalRepository.findAll().stream()
                .filter(TrafficSignal::isPriorityStatus)
                .collect(Collectors.toList());
    }
}

