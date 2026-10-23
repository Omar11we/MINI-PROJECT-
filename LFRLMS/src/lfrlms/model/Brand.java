package lfrlms.model;

/**
 * Purpose: Represents a vehicle manufacturer.
 * Concept: Encapsulation and copy construction.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class Brand {
    private int brandId;
    private String brandName;

    public Brand() {
    }

    public Brand(int brandId, String brandName) {
        this.brandId = brandId;
        this.brandName = brandName;
    }

    public Brand(Brand other) {
        if (other == null) {
            throw new IllegalArgumentException("Brand to copy cannot be null.");
        }
        this.brandId = other.brandId;
        this.brandName = other.brandName;
    }

    public int getBrandId() {
        return brandId;
    }

    public void setBrandId(int brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
}
