package lfrlms.model;

import lfrlms.util.Constants;

/**
 * Purpose: Adds company identity while keeping licence fields off corporate records.
 * Concept: Inheritance, super constructors and encapsulation.
 * Layer: Model.
 * Rubric: Inheritance, constructors.
 */
public class CorporateCustomer extends Customer {
    private String companyName;
    private String gstNo;

    public CorporateCustomer() {
        super();
    }

    public CorporateCustomer(int customerId, String firstName, String lastName,
            String email, String phone, String street, String city, String pincode,
            String companyName, String gstNo) {
        super(customerId, firstName, lastName, email, phone, street, city, pincode);
        this.companyName = companyName;
        this.gstNo = gstNo;
    }

    public CorporateCustomer(CorporateCustomer other) {
        super(other);
        this.companyName = other.companyName;
        this.gstNo = other.gstNo;
    }

    @Override
    public String getCustomerType() {
        return Constants.CUSTOMER_CORPORATE;
    }

    @Override
    public String getDisplayName() {
        return companyName == null ? "" : companyName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getGstNo() {
        return gstNo;
    }

    public void setGstNo(String gstNo) {
        this.gstNo = gstNo;
    }
}
