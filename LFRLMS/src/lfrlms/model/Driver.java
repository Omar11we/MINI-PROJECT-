package lfrlms.model;

import lfrlms.util.Constants;

/**
 * Purpose: Represents a chauffeur who can be assigned to a corporate rental.
 * Concept: Encapsulation and copy construction.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class Driver {
    private int driverId;
    private String firstName;
    private String lastName;
    private String phone;
    private String licenseNo;
    private String status;

    public Driver() {
        status = Constants.DRIVER_ACTIVE;
    }

    public Driver(int driverId, String firstName, String lastName,
            String phone, String licenseNo, String status) {
        this.driverId = driverId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.licenseNo = licenseNo;
        this.status = status;
    }

    public Driver(Driver other) {
        if (other == null) {
            throw new IllegalArgumentException("Driver to copy cannot be null.");
        }
        this.driverId = other.driverId;
        this.firstName = other.firstName;
        this.lastName = other.lastName;
        this.phone = other.phone;
        this.licenseNo = other.licenseNo;
        this.status = other.status;
    }

    public int getDriverId() {
        return driverId;
    }

    public void setDriverId(int driverId) {
        this.driverId = driverId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
