package com.muite.zongshe1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muite.zongshe1.entity.Truck;
import com.muite.zongshe1.mapper.TruckMapper;
import com.muite.zongshe1.utils.VehicleSimulationSocket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/hardware")
@CrossOrigin(origins = "*")
@Tag(name = "硬件接口模拟", description = "模拟接收ARM开发板/Modbus上报的数据")
public class HardwareInputController {

    private static final Logger log = LoggerFactory.getLogger(HardwareInputController.class);

    @Autowired
    private TruckMapper truckMapper;

    @PostMapping("/gps")
    @Operation(summary = "接收GPS数据", description = "模拟硬件上报GPS位置信息")
    public String receiveGpsData(@RequestBody Map<String, Object> data) {
        try {
            // 预期格式: { "deviceId": 1, "lat": 39.9, "lon": 116.4 }
            if (!data.containsKey("deviceId") || !data.containsKey("lat") || !data.containsKey("lon")) {
                return "Error: Missing required fields (deviceId, lat, lon)";
            }

            Integer truckId = Integer.valueOf(data.get("deviceId").toString());
            Double lat = Double.valueOf(data.get("lat").toString());
            Double lon = Double.valueOf(data.get("lon").toString());

            Truck truck = truckMapper.selectByPrimaryKey(truckId.longValue());
            if (truck == null) {
                return "Error: Truck not found with ID " + truckId;
            }

            // 更新位置（格式：纬度,经度）
            String location = lat + "," + lon;
            truck.setLocation(location);
            truckMapper.updateByPrimaryKey(truck);

            log.info("接收到硬件GPS数据 - 车辆ID: {}, 位置: {}", truckId, location);

            // 广播更新
            Map<String, Object> message = new HashMap<>();
            message.put("truckId", truck.getId());
            message.put("location", location);
            message.put("timestamp", new Date());
            message.put("source", "hardware_mock");
            message.put("status", truck.getStatus());
            
            VehicleSimulationSocket.broadcast(new ObjectMapper().writeValueAsString(message));

            return "Success";
        } catch (Exception e) {
            log.error("处理硬件数据失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
