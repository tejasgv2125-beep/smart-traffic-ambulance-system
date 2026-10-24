package com.smarttraffic.ambulance.service;

import org.springframework.stereotype.Service;

@Service
public class ETAService {

    /**
     * Calculate ETA in minutes based on distance and speed
     * @param distanceKm distance in kilometers
     * @param speedKmh speed in kilometers per hour
     * @return ETA in minutes
     */
    public int calculateETA(double distanceKm, double speedKmh) {
        if (speedKmh <= 0) {
            return 0; // Cannot calculate ETA with zero or negative speed
        }
        double hours = distanceKm / speedKmh;
        return (int) Math.ceil(hours * 60); // Convert to minutes and round up
    }

    /**
     * Calculate remaining distance based on total distance and progress percentage
     * @param totalDistanceKm total route distance in kilometers
     * @param progressPercent route progress as percentage (0-100)
     * @return remaining distance in kilometers
     */
    public double calculateRemainingDistance(double totalDistanceKm, double progressPercent) {
        if (progressPercent < 0) progressPercent = 0;
        if (progressPercent > 100) progressPercent = 100;
        double completedDistance = totalDistanceKm * (progressPercent / 100.0);
        return Math.max(0, totalDistanceKm - completedDistance);
    }

    /**
     * Calculate progress percentage based on total distance and remaining distance
     * @param totalDistanceKm total route distance in kilometers
     * @param remainingDistanceKm remaining distance in kilometers
     * @return progress percentage (0-100)
     */
    public double calculateProgressPercent(double totalDistanceKm, double remainingDistanceKm) {
        if (totalDistanceKm <= 0) {
            return 0;
        }
        double completedDistance = Math.max(0, totalDistanceKm - remainingDistanceKm);
        return Math.min(100, (completedDistance / totalDistanceKm) * 100);
    }

    /**
     * Validate if ETA is reasonable (not excessively large)
     * @param etaMinutes ETA in minutes
     * @return true if ETA is reasonable (< 24 hours)
     */
    public boolean isETAReasonable(int etaMinutes) {
        return etaMinutes >= 0 && etaMinutes <= (24 * 60); // Less than 24 hours
    }
}