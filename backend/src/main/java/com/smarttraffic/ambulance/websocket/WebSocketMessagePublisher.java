package com.smarttraffic.ambulance.websocket;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.model.TrafficSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketMessagePublisher {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // Publish ambulance location updates
    public void publishAmbulanceUpdate(Ambulance ambulance) {
        messagingTemplate.convertAndSend("/topic/ambulance/update", ambulance);
    }

    // Publish ambulance arrival notifications
    public void publishAmbulanceArrived(Ambulance ambulance) {
        messagingTemplate.convertAndSend("/topic/ambulance/arrived", ambulance);
    }

    // Publish traffic signal updates
    public void publishSignalUpdate(TrafficSignal signal, Ambulance ambulance) {
        messagingTemplate.convertAndSend("/topic/signal/update",
            new SignalUpdateEvent(signal, ambulance));
    }

    // Publish general system events
    public void publishSystemEvent(String eventType, String message) {
        messagingTemplate.convertAndSend("/topic/system/event",
            new SystemEvent(eventType, message));
    }

    // DTO for signal update events
    public static class SignalUpdateEvent {
        private TrafficSignal signal;
        private String ambulanceId;
        private String eventType;

        public SignalUpdateEvent(TrafficSignal signal, Ambulance ambulance) {
            this.signal = signal;
            this.ambulanceId = ambulance != null ? ambulance.getAmbulanceId() : null;
            this.eventType = ambulance != null ? "priority_activated" : "priority_released";
        }

        // Getters and setters
        public TrafficSignal getSignal() { return signal; }
        public String getAmbulanceId() { return ambulanceId; }
        public String getEventType() { return eventType; }
    }

    // DTO for system events
    public static class SystemEvent {
        private String type;
        private String message;
        private String timestamp;

        public SystemEvent(String type, String message) {
            this.type = type;
            this.message = message;
            this.timestamp = java.time.LocalDateTime.now().toString();
        }

        // Getters and setters
        public String getType() { return type; }
        public String getMessage() { return message; }
        public String getTimestamp() { return timestamp; }
    }
}