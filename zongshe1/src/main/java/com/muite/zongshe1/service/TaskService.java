package com.muite.zongshe1.service;

import com.muite.zongshe1.entity.Task;

import java.util.List;

public interface TaskService {
    List<Task> selectAll();

    void insert(Task task);

    Task selectById(Integer id);
}
