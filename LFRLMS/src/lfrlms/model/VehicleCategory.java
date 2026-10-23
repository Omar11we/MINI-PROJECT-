package lfrlms.model;

/**
 * Purpose: Represents a category used to filter and describe vehicles.
 * Concept: Encapsulation and copy construction.
 * Layer: Model.
 * Rubric: constructors, overloading.
 */
public class VehicleCategory {
    private int categoryId;
    private String categoryName;
    private String description;

    public VehicleCategory() {
    }

    public VehicleCategory(int categoryId, String categoryName, String description) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
    }

    public VehicleCategory(VehicleCategory other) {
        if (other == null) {
            throw new IllegalArgumentException("Vehicle category to copy cannot be null.");
        }
        this.categoryId = other.categoryId;
        this.categoryName = other.categoryName;
        this.description = other.description;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
