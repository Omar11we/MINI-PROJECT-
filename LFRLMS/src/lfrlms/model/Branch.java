package lfrlms.model;

/**
 * Purpose: Represents a fleet location.
 * Concept: Encapsulation and copy construction.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class Branch {
    private int branchId;
    private String branchName;
    private String street;
    private String city;
    private String pincode;
    private String phone;

    public Branch() {
    }

    public Branch(int branchId, String branchName, String street,
            String city, String pincode, String phone) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.street = street;
        this.city = city;
        this.pincode = pincode;
        this.phone = phone;
    }

    public Branch(Branch other) {
        if (other == null) {
            throw new IllegalArgumentException("Branch to copy cannot be null.");
        }
        this.branchId = other.branchId;
        this.branchName = other.branchName;
        this.street = other.street;
        this.city = other.city;
        this.pincode = other.pincode;
        this.phone = other.phone;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
