package com.muite.zongshe1.utils;

import com.muite.zongshe1.mapper.TruckMapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Component
@ServerEndpoint("/vehicle-simulation")
public class VehicleSimulationSocket {

    // 保存所有连接的会话（支持多前端）
    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    
    // 静态引用 Spring Bean，用于在 onOpen 中获取数据
    private static TruckMapper truckMapper;
    private static com.muite.zongshe1.mapper.TaskMapper taskMapper; // 新增TaskMapper引用
    
    @Autowired
    public void setTruckMapper(TruckMapper truckMapper) {
        VehicleSimulationSocket.truckMapper = truckMapper;
    }

        
    @Autowired
    public void setTaskMapper(com.muite.zongshe1.mapper.TaskMapper taskMapper) {
        VehicleSimulationSocket.taskMapper = taskMapper;
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("前端连接建立，当前连接数: " + sessions.size());
                
        // 连接建立后立即发送全量车辆数据，避免前端初始化时只有部分车辆
        try {
            if (truckMapper != null) {
                java.util.List<com.muite.zongshe1.entity.Truck> trucks = truckMapper.selectAll();
                            
                // 填充taskId
                if (taskMapper != null) {
                    for (com.muite.zongshe1.entity.Truck truck : trucks) {
                        com.muite.zongshe1.entity.Task task = taskMapper.selectByTruckId(truck.getId());
                        if (task != null) {
                            truck.setTaskId(task.getId());
                        }
                    }
                }
                 
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(trucks);
                session.getBasicRemote().sendText(json);
                System.out.println("已向新连接推送全量车辆数据，数量: " + trucks.size());
            }
        } catch (Exception e) {
            System.err.println("推送初始数据失败: " + e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("前端连接关闭，当前连接数: " + sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket 错误: " + error.getMessage());
    }

    // 工具方法：向所有前端推送消息
    public static void broadcast(String message) {
        synchronized (sessions) {
            for (Session session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.getBasicRemote().sendText(message);
                    } catch (IOException e) {
                        System.err.println("推送失败: " + e.getMessage());
                    }
                }
            }
        }
    }
}