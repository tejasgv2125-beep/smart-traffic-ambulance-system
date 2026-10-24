
        package com.smarttraffic.ambulance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_signals")
@Data
@NoArgsConstructor
public class TrafficSignal {

    @Id
    @Column(name = "signal_id", unique = true, nullable = false)
    private String signalId;

    @Column(name = "signal_name")
    private String signalName;

    @Column(name = "latitude")
    private double latitude;

    @Column(name = "longitude")
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_state")
    private SignalState currentState;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_state")
    private SignalState normalState;

    @Column(name = "priority_status")
    private boolean priorityStatus;

    @Column(name = "associated_ambulance_id")
    private String associatedAmbulanceId;

    @Column(name = "last_update_time")
    private LocalDateTime lastUpdateTime;

    public TrafficSignal(
            String signalId,
            String signalName,
            double latitude,
            double longitude,
            SignalState normalState) {

        this.signalId = signalId;
        this.signalName = signalName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.normalState = normalState;
        this.currentState = normalState;
        this.priorityStatus = false;
        this.associatedAmbulanceId = null;
        this.lastUpdateTime = LocalDateTime.now();
    }

    /**
     * Getter compatible with code using getPriorityStatus().
     */
    public boolean getPriorityStatus() {
        return priorityStatus;
    }

    /**
     * Getter compatible with code using isPriorityStatus().
     */
    public boolean isPriorityStatus() {
        return priorityStatus;
    }

    public enum SignalState {
        RED,
        YELLOW,
        GREEN
    }
}
