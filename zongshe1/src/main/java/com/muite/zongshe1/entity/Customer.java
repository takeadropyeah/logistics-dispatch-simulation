package com.muite.zongshe1.entity;

import java.io.Serializable;

/**
 * 客户实体类（发货人/收货人）
 */
public class Customer implements Serializable {
    private Integer id;
    private String name;
    private String address;
    private String contactPerson;
    private String phone;
    private String type; // 发货方, 收货方

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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
