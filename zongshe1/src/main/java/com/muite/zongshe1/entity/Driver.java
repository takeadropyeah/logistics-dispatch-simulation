package com.muite.zongshe1.entity;

import java.io.Serializable;

/**
 * 驾驶员实体类
 */
public class Driver implements Serializable {
    private Integer id;
    private String name;
    private String phone;
    private String licenseType; // 准驾车型：A2, B2等
    private String status;      // 状态：空闲, 驾驶中, 休假
    private Integer truckId;    // 当前关联车辆ID

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTruckId() {
        return truckId;
    }

    public void setTruckId(Integer truckId) {
        this.truckId = truckId;
    }
}
