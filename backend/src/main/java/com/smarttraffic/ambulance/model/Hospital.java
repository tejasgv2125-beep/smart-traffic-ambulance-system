package com.smarttraffic.ambulance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hospitals")
@Data
@NoArgsConstructor
public class Hospital {

    @Id
    @Column(name = "hospital_id", unique = true, nullable = false)
    private String hospitalId;

    @Column(name = "hospital_name")
    private String hospitalName;

    @Column(name = "latitude")
    private double latitude;

    @Column(name = "longitude")
    private double longitude;

    @Column(name = "address")
    private String address;

    @Column(name = "contact_number")
    private String contactNumber;

    // Constructors
    public Hospital(String hospitalId, String hospitalName, double latitude, double longitude,
                    String address, String contactNumber) {
        this.hospitalId = hospitalId;
        this.hospitalName = hospitalName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.contactNumber = contactNumber;
    }
}