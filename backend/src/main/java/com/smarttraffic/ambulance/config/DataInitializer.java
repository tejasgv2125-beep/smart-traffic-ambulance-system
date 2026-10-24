package com.smarttraffic.ambulance.config;

import com.smarttraffic.ambulance.model.Ambulance;
import com.smarttraffic.ambulance.model.Hospital;
import com.smarttraffic.ambulance.model.TrafficSignal;
import com.smarttraffic.ambulance.model.TrafficSignal.SignalState;
import com.smarttraffic.ambulance.repository.AmbulanceRepository;
import com.smarttraffic.ambulance.repository.HospitalRepository;
import com.smarttraffic.ambulance.repository.TrafficSignalRepository;
import com.smarttraffic.ambulance.service.GPSSimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Autowired
    private AmbulanceRepository ambulanceRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private TrafficSignalRepository trafficSignalRepository;

    @Autowired
    private GPSSimulationService gpsSimulationService;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // Initialize hospitals
            initializeHospitals();

            // Initialize ambulances
            initializeAmbulances();
            // Existing database rows are reset to separate demo starts on each app launch.
            resetDemoAmbulanceStarts();

            // Initialize traffic signals
            initializeTrafficSignals();

            // Keep the two demonstration signals positioned on the AMB001
            // demo road route, including when the database already contains rows.
            synchronizeDemoSignalCoordinates();

            // Start demo GPS movement automatically after startup; no dashboard button required.
            // The simulation moves all configured ambulances, while emergency status
            // independently controls traffic-signal priority.
            gpsSimulationService.startSimulation();
        };
    }

    private void initializeHospitals() {
        // Only initialize if no hospitals exist
        if (hospitalRepository.count() == 0) {
            Hospital manipalHospital = new Hospital(
                    "H001", "Manipal Hospital", 12.9352, 77.6245,
                    "#98, Rustam Bagh, Old Airport Road, Bengaluru, Karnataka 560017",
                    "080 2526 4000"
            );

            Hospital apolloHospital = new Hospital(
                    "H002", "Apollo Hospital", 12.9716, 77.5946,
                    "154/11, Opp. I.I.M., Bannerghatta Road, Bengaluru, Karnataka 560076",
                    "080 2630 4050"
            );

            Hospital fortisHospital = new Hospital(
                    "H003", "Fortis Hospital", 13.0074, 77.5546,
                    "154/9, Opp. IIMB, Bannerghatta Road, Bengaluru, Karnataka 560076",
                    "080 6621 4444"
            );

            Hospital narayanaHealth = new Hospital(
                    "H004", "Narayana Health", 12.9165, 77.6101,
                    "258/A, Bommasandra Industrial Area, Anekal Taluk, Bengaluru, Karnataka 560099",
                    "080 2783 0000"
            );

            hospitalRepository.save(manipalHospital);
            hospitalRepository.save(apolloHospital);
            hospitalRepository.save(fortisHospital);
            hospitalRepository.save(narayanaHealth);
        }
    }

    private void initializeAmbulances() {
        // Only initialize if no ambulances exist
        if (ambulanceRepository.count() == 0) {
            // Ambulance AMB001: Starting from Electronic City to Manipal Hospital
            Ambulance ambulance1 = new Ambulance(
                    "AMB001", "Dr. Rajesh Kumar", 12.8456, 77.6603, "Manipal Hospital"
            );
            ambulance1.setEmergencyStatus(false);
            ambulance1.setCurrentSpeed(0.0);
            ambulance1.setRemainingDistance(12.5); // km
            ambulance1.setEtaMinutes(25); // minutes
            ambulance1.setRouteProgress(0.0);
            ambulance1.setLastLocationUpdate(LocalDateTime.now());

            // Ambulance AMB002: Starting from Koramangala to Apollo Hospital
            Ambulance ambulance2 = new Ambulance(
                    "AMB002", "Dr. Priya Sharma", 12.9352, 77.6245, "Apollo Hospital"
            );
            ambulance2.setEmergencyStatus(false);
            ambulance2.setCurrentSpeed(0.0);
            ambulance2.setRemainingDistance(8.2); // km
            ambulance2.setEtaMinutes(18); // minutes
            ambulance2.setRouteProgress(0.0);
            ambulance2.setLastLocationUpdate(LocalDateTime.now());

            // Ambulance AMB003: Starting from Whitefield to Fortis Hospital
            Ambulance ambulance3 = new Ambulance(
                    "AMB003", "Dr. Amit Patel", 12.9698, 77.7500, "Fortis Hospital"
            );
            ambulance3.setEmergencyStatus(false);
            ambulance3.setCurrentSpeed(0.0);
            ambulance3.setRemainingDistance(15.3); // km
            ambulance3.setEtaMinutes(30); // minutes
            ambulance3.setRouteProgress(0.0);
            ambulance3.setLastLocationUpdate(LocalDateTime.now());

            ambulanceRepository.save(ambulance1);
            ambulanceRepository.save(ambulance2);
            ambulanceRepository.save(ambulance3);
        }
    }

    private void initializeTrafficSignals() {
        // Only initialize if no traffic signals exist
        if (trafficSignalRepository.count() == 0) {
            // Traffic signals along common routes in Bengaluru
            TrafficSignal signal1 = new TrafficSignal(
                    "S001", "Electronic City Signal", 12.8456, 77.6603, SignalState.RED
            );
            signal1.setNormalState(SignalState.RED);

            TrafficSignal signal2 = new TrafficSignal(
                    "S002", "Hosur Road Junction", 12.8956, 77.6403, SignalState.GREEN
            );
            signal2.setNormalState(SignalState.GREEN);

            TrafficSignal signal3 = new TrafficSignal(
                    "S003", "Manipal Hospital Signal", 12.9352, 77.6245, SignalState.RED
            );
            signal3.setNormalState(SignalState.RED);

            TrafficSignal signal4 = new TrafficSignal(
                    "S004", "Koramangala Signal", 12.9352, 77.6245, SignalState.RED
            );
            signal4.setNormalState(SignalState.RED);

            TrafficSignal signal5 = new TrafficSignal(
                    "S005", "MG Road Junction", 12.9556, 77.6003, SignalState.GREEN
            );
            signal5.setNormalState(SignalState.GREEN);

            TrafficSignal signal6 = new TrafficSignal(
                    "S006", "Apollo Hospital Signal", 12.9716, 77.5946, SignalState.RED
            );
            signal6.setNormalState(SignalState.RED);

            TrafficSignal signal7 = new TrafficSignal(
                    "S007", "Whitefield Signal", 12.9698, 77.7500, SignalState.RED
            );
            signal7.setNormalState(SignalState.RED);

            TrafficSignal signal8 = new TrafficSignal(
                    "S008", "ITPL Junction", 12.9856, 77.6803, SignalState.GREEN
            );
            signal8.setNormalState(SignalState.GREEN);

            TrafficSignal signal9 = new TrafficSignal(
                    "S009", "Fortis Hospital Signal", 13.0074, 77.5546, SignalState.RED
            );
            signal9.setNormalState(SignalState.RED);

            trafficSignalRepository.save(signal1);
            trafficSignalRepository.save(signal2);
            trafficSignalRepository.save(signal3);
            trafficSignalRepository.save(signal4);
            trafficSignalRepository.save(signal5);
            trafficSignalRepository.save(signal6);
            trafficSignalRepository.save(signal7);
            trafficSignalRepository.save(signal8);
            trafficSignalRepository.save(signal9);
        }
    }


    private void resetDemoAmbulanceStarts() {
        // Distinct simulated start points and destinations keep the demo routes separate.
        resetAmbulance("AMB001", 12.949670, 77.639373, "Manipal Hospital");
        resetAmbulance("AMB002", 12.986500, 77.610000, "Apollo Hospital");
        resetAmbulance("AMB003", 12.997000, 77.570000, "Fortis Hospital");
    }

    private void resetAmbulance(String id, double latitude, double longitude, String destination) {
        Ambulance ambulance = ambulanceRepository.findByAmbulanceId(id);
        if (ambulance == null) return;
        ambulance.setCurrentLatitude(latitude);
        ambulance.setCurrentLongitude(longitude);
        ambulance.setDestinationHospital(destination);
        ambulance.setEmergencyStatus(false);
        ambulance.setCurrentSpeed(0.0);
        ambulance.setRemainingDistance(0.0);
        ambulance.setEtaMinutes(0);
        ambulance.setRouteProgress(0.0);
        ambulance.setLastLocationUpdate(LocalDateTime.now());
        ambulanceRepository.save(ambulance);
    }

    private void synchronizeDemoSignalCoordinates() {
        // Demo traffic lights are distributed along the OSRM road route from
        // 12.949670, 77.639373 to Manipal Hospital (H001), so they appear as
        // a clear sequence along the route instead of unrelated map positions.
        double[][] routeSignals = {
                {12.949585, 77.639420},
                {12.947010, 77.640746},
                {12.945936, 77.640081},
                {12.943270, 77.638443},
                {12.940835, 77.636945},
                {12.938854, 77.635220},
                {12.937612, 77.627469},
                {12.936085, 77.625543},
                {12.935201, 77.624499}
        };
        for (int i = 1; i <= routeSignals.length; i++) {
            TrafficSignal signal = trafficSignalRepository.findBySignalId(String.format("S%03d", i));
            if (signal != null) {
                signal.setLatitude(routeSignals[i - 1][0]);
                signal.setLongitude(routeSignals[i - 1][1]);
                trafficSignalRepository.save(signal);
            }
        }
    }

}