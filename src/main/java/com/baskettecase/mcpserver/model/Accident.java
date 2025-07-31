package com.baskettecase.mcpserver.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "accidents")
public class Accident {
    
    @Id
    @Column(name = "accident_id")
    private Integer accidentId;
    
    @Column(name = "policy_id")
    private Integer policyId;
    
    @Column(name = "vehicle_id")
    private Integer vehicleId;
    
    @Column(name = "driver_id")
    private Integer driverId;
    
    @Column(name = "accident_timestamp")
    private OffsetDateTime accidentTimestamp;
    
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;
    
    @Column(name = "g_force", precision = 5, scale = 2)
    private BigDecimal gForce;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public Accident() {}

    public Accident(Integer accidentId, Integer policyId, Integer vehicleId, Integer driverId,
                   OffsetDateTime accidentTimestamp, BigDecimal latitude, BigDecimal longitude,
                   BigDecimal gForce, String description) {
        this.accidentId = accidentId;
        this.policyId = policyId;
        this.vehicleId = vehicleId;
        this.driverId = driverId;
        this.accidentTimestamp = accidentTimestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gForce = gForce;
        this.description = description;
    }

    public Integer getAccidentId() {
        return accidentId;
    }

    public void setAccidentId(Integer accidentId) {
        this.accidentId = accidentId;
    }

    public Integer getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Integer policyId) {
        this.policyId = policyId;
    }

    public Integer getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Integer vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Integer getDriverId() {
        return driverId;
    }

    public void setDriverId(Integer driverId) {
        this.driverId = driverId;
    }

    public OffsetDateTime getAccidentTimestamp() {
        return accidentTimestamp;
    }

    public void setAccidentTimestamp(OffsetDateTime accidentTimestamp) {
        this.accidentTimestamp = accidentTimestamp;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getGForce() {
        return gForce;
    }

    public void setGForce(BigDecimal gForce) {
        this.gForce = gForce;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Accident{" +
                "accidentId=" + accidentId +
                ", policyId=" + policyId +
                ", vehicleId=" + vehicleId +
                ", driverId=" + driverId +
                ", accidentTimestamp=" + accidentTimestamp +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", gForce=" + gForce +
                ", description='" + description + '\'' +
                '}';
    }
}