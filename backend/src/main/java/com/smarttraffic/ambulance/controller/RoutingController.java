
        package com.smarttraffic.ambulance.controller;

import com.smarttraffic.ambulance.service.RoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = "*")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/ambulance/{ambulanceId}")
    public ResponseEntity<?> getAmbulanceRoute(
            @PathVariable String ambulanceId) {
        try {
            Map<String, Object> route =
                    routingService.getRouteForAmbulance(ambulanceId);

            return ResponseEntity.ok(route);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", exception.getMessage()));

        } catch (Exception exception) {
            return ResponseEntity.status(502)
                    .body(Map.of("error", exception.getMessage()));
        }
    }
}

