package com.smarttraffic.ambulance.repository;

import com.smarttraffic.ambulance.model.TrafficSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficSignalRepository extends JpaRepository<TrafficSignal, String> {
    TrafficSignal findBySignalId(String signalId);
}