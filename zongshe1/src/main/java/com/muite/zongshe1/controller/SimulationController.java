package com.muite.zongshe1.controller;

import com.muite.zongshe1.utils.SimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/simulation")
@CrossOrigin(origins = "*") // 允许跨域
public class SimulationController {

    @Autowired
    private SimulationService simulationService;

    @PostMapping("/start")
    public Map<String, Object> startSimulation() {
        simulationService.startSimulation();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Simulation started/resumed");
        result.put("running", true);
        return result;
    }

    @PostMapping("/pause")
    public Map<String, Object> pauseSimulation() {
        simulationService.pauseSimulation();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Simulation paused");
        result.put("running", false);
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> getSimulationStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", simulationService.isSimulationRunning());
        return result;
    }
    
    @PostMapping("/generate-vehicles")
    public Map<String, Object> generateVehicles(@RequestParam(defaultValue = "5") int count) {
        simulationService.generateRandomVehicles(count);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Successfully generated " + count + " vehicles");
        return result;
    }
    
    @PostMapping("/speed")
    public Map<String, Object> adjustSimulationSpeed(@RequestBody Map<String, Integer> request) {
        Integer speed = request.get("speed");
        if (speed == null) {
            speed = 1;
        }
        simulationService.setSimulationSpeed(speed);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "Simulation speed adjusted to " + speed + "x");
        result.put("speed", speed);
        return result;
    }
}
