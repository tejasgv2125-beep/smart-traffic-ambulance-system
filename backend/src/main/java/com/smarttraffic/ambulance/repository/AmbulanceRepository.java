package com.smarttraffic.ambulance.repository;

import com.smarttraffic.ambulance.model.Ambulance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmbulanceRepository extends JpaRepository<Ambulance, String> {
    Ambulance findByAmbulanceId(String ambulanceId);
}