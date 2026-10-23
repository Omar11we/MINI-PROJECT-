package lfrlms.model;

import java.time.LocalDate;
import java.time.Period;
import lfrlms.util.Constants;

/**
 * Purpose: Adds personal identity and licence details for an individual.
 * Concept: Inheritance, super constructors and encapsulation.
 * Layer: Model.
 * Rubric: Inheritance, constructors.
 */
public class IndividualCustomer extends Customer {
    private LocalDate dateOfBirth;
    private String licenseNo;
    private LocalDate licenseExpiry;

    public IndividualCustomer() {
        super();
    }

    public IndividualCustomer(int customerId, String firstName, String lastName,
            String email, String phone, String street, String city, String pincode,
            LocalDate dateOfBirth, String licenseNo, LocalDate licenseExpiry) {
        super(customerId, firstName, lastName, email, phone, street, city, pincode);
        this.dateOfBirth = dateOfBirth;
        this.licenseNo = licenseNo;
        this.licenseExpiry = licenseExpiry;
    }

    public IndividualCustomer(IndividualCustomer other) {
        super(other);
        this.dateOfBirth = other.dateOfBirth;
        this.licenseNo = other.licenseNo;
        this.licenseExpiry = other.licenseExpiry;
    }

    @Override
    public String getCustomerType() {
        return Constants.CUSTOMER_INDIVIDUAL;
    }

    @Override
    public String getDisplayName() {
        return getContactName();
    }

    public int getAge() {
        if (dateOfBirth == null) {
            return 0;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public LocalDate getLicenseExpiry() {
        return licenseExpiry;
    }

    public void setLicenseExpiry(LocalDate licenseExpiry) {
        this.licenseExpiry = licenseExpiry;
    }
}
