
        package com.smarttraffic.ambulance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ambulances")
@Data
@NoArgsConstructor
public class Ambulance {

    @Id
    @Column(name = "ambulance_id", unique = true, nullable = false)
    private String ambulanceId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "current_latitude")
    private double currentLatitude;

    @Column(name = "current_longitude")
    private double currentLongitude;

    @Column(name = "destination_hospital")
    private String destinationHospital;

    @Column(name = "emergency_status")
    private boolean emergencyStatus;

    @Column(name = "current_speed")
    private double currentSpeed;

    @Column(name = "remaining_distance")
    private double remainingDistance;

    @Column(name = "eta_minutes")
    private int etaMinutes;

    @Column(name = "route_progress")
    private double routeProgress;

    @Column(name = "last_location_update")
    private LocalDateTime lastLocationUpdate;

    @Column(name = "assigned_route")
    private String assignedRoute;

    public Ambulance(
            String ambulanceId,
            String driverName,
            double currentLatitude,
            double currentLongitude,
            String destinationHospital) {

        this.ambulanceId = ambulanceId;
        this.driverName = driverName;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.destinationHospital = destinationHospital;
        this.emergencyStatus = false;
        this.currentSpeed = 0.0;
        this.remainingDistance = 0.0;
        this.etaMinutes = 0;
        this.routeProgress = 0.0;
        this.lastLocationUpdate = LocalDateTime.now();
    }
}
