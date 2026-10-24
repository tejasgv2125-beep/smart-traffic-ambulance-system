package com.smarttraffic.ambulance.repository;

import com.smarttraffic.ambulance.model.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, String> {
    Hospital findByHospitalId(String hospitalId);
}