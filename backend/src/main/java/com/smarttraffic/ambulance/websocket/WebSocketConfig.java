package com.smarttraffic.ambulance.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register endpoint for WebSocket connections
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*") // Allow connections from any origin (for demo)
                .withSockJS(); // Enable SockJS fallback options
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Configure message broker for sending messages to clients
        registry.enableSimpleBroker("/topic", "/queue"); // Destinations for outgoing messages
        registry.setApplicationDestinationPrefixes("/app"); // Prefix for incoming messages from clients
    }
}