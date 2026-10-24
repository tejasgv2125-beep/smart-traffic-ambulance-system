package com.smarttraffic.ambulance.controller;

import com.smarttraffic.ambulance.model.Hospital;
import com.smarttraffic.ambulance.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hospitals")
@CrossOrigin(origins = "*")
public class HospitalController {

    @Autowired
    private HospitalRepository hospitalRepository;

    // Get all hospitals
    @GetMapping
    public List<Hospital> getAllHospitals() {
        return hospitalRepository.findAll();
    }

    // Get hospital by ID
    @GetMapping("/{id}")
    public ResponseEntity<Hospital> getHospitalById(@PathVariable String id) {
        Hospital hospital = hospitalRepository.findByHospitalId(id);
        if (hospital == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(hospital);
    }

    // Get hospital by name
    @GetMapping("/name/{name}")
    public ResponseEntity<Hospital> getHospitalByName(@PathVariable String name) {
        // In a real implementation, we'd add a method to repository
        // For now, we'll search through all hospitals
        List<Hospital> hospitals = hospitalRepository.findAll();
        for (Hospital hospital : hospitals) {
            if (hospital.getHospitalName().equalsIgnoreCase(name)) {
                return ResponseEntity.ok(hospital);
            }
        }
        return ResponseEntity.notFound().build();
    }
}