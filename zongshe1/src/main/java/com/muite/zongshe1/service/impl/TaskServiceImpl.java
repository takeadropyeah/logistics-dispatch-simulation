package com.muite.zongshe1.service.impl;

import com.muite.zongshe1.entity.Task;
import com.muite.zongshe1.mapper.TaskMapper;
import com.muite.zongshe1.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskServiceImpl implements TaskService {
    @Autowired
    TaskMapper taskMapper;
    @Override
    public List<Task> selectAll() {
        return taskMapper.selectAll();
    }

    @Override
    public void insert(Task task) {
        taskMapper.insert(task);
    }

    @Override
    public Task selectById(Integer id) {
        return taskMapper.selectByPrimaryKey(id);
    }
}
