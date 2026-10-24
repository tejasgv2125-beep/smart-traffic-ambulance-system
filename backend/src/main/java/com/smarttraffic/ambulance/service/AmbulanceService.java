package com.smarttraffic.ambulance.service;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.repository.AmbulanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AmbulanceService {

    private final AmbulanceRepository ambulanceRepository;

    public AmbulanceService(AmbulanceRepository ambulanceRepository) {
        this.ambulanceRepository = ambulanceRepository;
    }

    /** Registers an ambulance and initializes its live-tracking fields. */
    @Transactional
    public Ambulance registerAmbulance(Ambulance ambulance) {
        if (ambulance == null) {
            throw new IllegalArgumentException("Ambulance details are required.");
        }

        String ambulanceId = normalizeId(ambulance.getAmbulanceId());

        if (ambulanceRepository.existsById(ambulanceId)) {
            throw new IllegalArgumentException(
                    "Ambulance ID already exists: " + ambulanceId);
        }

        validateCoordinates(
                ambulance.getCurrentLatitude(),
                ambulance.getCurrentLongitude());

        if (ambulance.getDestinationHospital() == null
                || ambulance.getDestinationHospital().isBlank()) {
            throw new IllegalArgumentException(
                    "Destination hospital is required.");
        }

        ambulance.setAmbulanceId(ambulanceId);
        ambulance.setDestinationHospital(ambulance.getDestinationHospital().trim());
        ambulance.setEmergencyStatus(false);
        ambulance.setCurrentSpeed(0.0);
        ambulance.setRemainingDistance(0.0);
        ambulance.setEtaMinutes(0);
        ambulance.setRouteProgress(0.0);
        ambulance.setLastLocationUpdate(LocalDateTime.now());

        return ambulanceRepository.save(ambulance);
    }

    /** Saves an ambulance entity and refreshes its last-update timestamp. */
    @Transactional
    public Ambulance saveAmbulance(Ambulance ambulance) {
        if (ambulance == null) {
            throw new IllegalArgumentException("Ambulance details are required.");
        }

        ambulance.setLastLocationUpdate(LocalDateTime.now());
        return ambulanceRepository.save(ambulance);
    }

    @Transactional(readOnly = true)
    public Ambulance getAmbulanceById(String ambulanceId) {
        return ambulanceRepository.findByAmbulanceId(normalizeId(ambulanceId));
    }

    @Transactional(readOnly = true)
    public List<Ambulance> getAllAmbulances() {
        return ambulanceRepository.findAll();
    }

    @Transactional
    public Ambulance updateAmbulanceLocation(
            String ambulanceId,
            double latitude,
            double longitude) {

        validateCoordinates(latitude, longitude);

        Ambulance ambulance = ambulanceRepository.findByAmbulanceId(
                normalizeId(ambulanceId));

        // Keep the existing contract: callers handle a missing ambulance by checking null.
        if (ambulance == null) {
            return null;
        }

        ambulance.setCurrentLatitude(latitude);
        ambulance.setCurrentLongitude(longitude);
        ambulance.setLastLocationUpdate(LocalDateTime.now());
        return ambulanceRepository.save(ambulance);
    }

    @Transactional
    public Ambulance updateAmbulanceStatus(
            String ambulanceId,
            boolean emergencyStatus) {

        Ambulance ambulance = ambulanceRepository.findByAmbulanceId(
                normalizeId(ambulanceId));

        if (ambulance == null) {
            return null;
        }

        ambulance.setEmergencyStatus(emergencyStatus);
        ambulance.setLastLocationUpdate(LocalDateTime.now());

        // GPS simulation owns route completion/progress; don't clear those fields here.
        if (!emergencyStatus) {
            ambulance.setCurrentSpeed(0.0);
        }

        return ambulanceRepository.save(ambulance);
    }

    @Transactional
    public Ambulance updateAmbulanceProgress(
            String ambulanceId,
            double remainingDistance,
            int etaMinutes,
            double routeProgress) {

        if (!Double.isFinite(remainingDistance) || remainingDistance < 0) {
            throw new IllegalArgumentException(
                    "Remaining distance must be zero or greater.");
        }

        if (etaMinutes < 0) {
            throw new IllegalArgumentException("ETA must be zero or greater.");
        }

        if (!Double.isFinite(routeProgress)
                || routeProgress < 0
                || routeProgress > 100) {
            throw new IllegalArgumentException(
                    "Route progress must be between 0 and 100.");
        }

        Ambulance ambulance = ambulanceRepository.findByAmbulanceId(
                normalizeId(ambulanceId));

        if (ambulance == null) {
            return null;
        }

        ambulance.setRemainingDistance(remainingDistance);
        ambulance.setEtaMinutes(etaMinutes);
        ambulance.setRouteProgress(routeProgress);
        ambulance.setLastLocationUpdate(LocalDateTime.now());
        return ambulanceRepository.save(ambulance);
    }

    private String normalizeId(String ambulanceId) {
        if (ambulanceId == null || ambulanceId.isBlank()) {
            throw new IllegalArgumentException("Ambulance ID is required.");
        }
        return ambulanceId.trim();
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180
                || (latitude == 0.0 && longitude == 0.0)) {
            throw new IllegalArgumentException(
                    "Valid GPS coordinates are required.");
        }
    }
}
