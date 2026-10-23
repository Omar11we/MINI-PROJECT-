package lfrlms.model;

import java.time.LocalDateTime;

/**
 * Purpose: Holds fields shared by individual and corporate customers.
 * Concept: Abstract class and encapsulation.
 * Layer: Model.
 * Rubric: interfaces/abstract classes.
 */
public abstract class Customer {
    private int customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String street;
    private String city;
    private String pincode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected Customer() {
    }

    protected Customer(int customerId, String firstName, String lastName,
            String email, String phone, String street, String city, String pincode) {
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.street = street;
        this.city = city;
        this.pincode = pincode;
    }

    protected Customer(Customer other) {
        if (other == null) {
            throw new IllegalArgumentException("Customer to copy cannot be null.");
        }
        this.customerId = other.customerId;
        this.firstName = other.firstName;
        this.lastName = other.lastName;
        this.email = other.email;
        this.phone = other.phone;
        this.street = other.street;
        this.city = other.city;
        this.pincode = other.pincode;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public abstract String getCustomerType();

    public abstract String getDisplayName();

    public String getContactName() {
        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;
        return (first + " " + last).trim();
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
