package com.muite.zongshe1.entity;


/**
 * 
 * @TableName point
 */

public class Point {
    /**
     * 
     */
    private String location;

    /**
     * 
     */
    private String name;

    /**
     * 
     */
    private String type;

    public Point() {
    }

    public Point(String name,String location, String type) {
        this.location = location;
        this.name = name;
        this.type = type;
    }

    // 在Point.java中新增构造方法
    public Point(String location) {
        this.location = location;
        this.name = ""; // 默认空名称
        this.type = "route_point"; // 默认为路径点类型
    }


    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        Point other = (Point) that;
        return (this.getLocation() == null ? other.getLocation() == null : this.getLocation().equals(other.getLocation()))
            && (this.getName() == null ? other.getName() == null : this.getName().equals(other.getName()))
            && (this.getType() == null ? other.getType() == null : this.getType().equals(other.getType()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getLocation() == null) ? 0 : getLocation().hashCode());
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", location=").append(location);
        sb.append(", name=").append(name);
        sb.append(", type=").append(type);
        sb.append("]");
        return sb.toString();
    }


    public String getLocation() {
        return location;
    }


    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * 获取
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * 设置
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取
     * @return type
     */
    public String getType() {
        return type;
    }

    /**
     * 设置
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }
}