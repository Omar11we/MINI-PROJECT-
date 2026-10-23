package lfrlms.service;

import java.util.ArrayList;
import lfrlms.dao.BranchDao;
import lfrlms.dao.BrandDao;
import lfrlms.dao.VehicleCategoryDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.model.Branch;
import lfrlms.model.Brand;
import lfrlms.model.VehicleCategory;
import lfrlms.util.LookupCache;
import lfrlms.util.Validator;

// Layer: Service (business logic).
// Rubric: Collections.
/** Loads small lookup tables into the display-name cache. */
public class LookupService {
    private static final int PAGE_SIZE = 100;
    private final BrandDao brandDao;
    private final VehicleCategoryDao categoryDao;
    private final BranchDao branchDao;

    public LookupService() {
        this(new BrandDao(), new VehicleCategoryDao(), new BranchDao());
    }

    public LookupService(BrandDao brandDao, VehicleCategoryDao categoryDao,
            BranchDao branchDao) {
        this.brandDao = brandDao;
        this.categoryDao = categoryDao;
        this.branchDao = branchDao;
    }

    /** Reloads every brand, category and branch name into a new cache. */
    public LookupCache refresh() throws LfrlmsException {
        LookupCache cache = new LookupCache();
        int pageNumber = 1;
        while (true) {
            ArrayList<Brand> brands = brandDao.findPage("brandName", true,
                    pageNumber, PAGE_SIZE);
            for (Brand brand : brands) {
                cache.putBrandName(brand.getBrandId(), brand.getBrandName());
            }
            if (brands.size() < PAGE_SIZE) {
                break;
            }
            pageNumber++;
        }
        pageNumber = 1;
        while (true) {
            ArrayList<VehicleCategory> categories = categoryDao.findPage("categoryName",
                    true, pageNumber, PAGE_SIZE);
            for (VehicleCategory category : categories) {
                cache.putCategoryName(category.getCategoryId(), category.getCategoryName());
            }
            if (categories.size() < PAGE_SIZE) {
                break;
            }
            pageNumber++;
        }
        pageNumber = 1;
        while (true) {
            ArrayList<Branch> branches = branchDao.findPage("branchName", true,
                    pageNumber, PAGE_SIZE);
            for (Branch branch : branches) {
                cache.putBranchName(branch.getBranchId(), branch.getBranchName());
            }
            if (branches.size() < PAGE_SIZE) {
                return cache;
            }
            pageNumber++;
        }
    }

    /** Returns up to 100 brands sorted by name. */
    public ArrayList<Brand> listBrands() throws LfrlmsException {
        return brandDao.findPage("brandName", true, 1, PAGE_SIZE);
    }

    /** Returns up to 100 vehicle categories sorted by name. */
    public ArrayList<VehicleCategory> listCategories() throws LfrlmsException {
        return categoryDao.findPage("categoryName", true, 1, PAGE_SIZE);
    }

    /** Returns up to 100 branches sorted by name. */
    public ArrayList<Branch> listBranches() throws LfrlmsException {
        return branchDao.findPage("branchName", true, 1, PAGE_SIZE);
    }

    /** Validates and inserts or updates a brand, returning its id. */
    public int saveBrand(Brand brand) throws LfrlmsException {
        if (brand == null || !Validator.isValid(brand.getBrandName())) {
            throw new ValidationException("Enter a brand name.");
        }
        if (brand.getBrandId() <= 0) {
            return brandDao.insert(brand);
        }
        if (!brandDao.update(brand)) {
            throw new ValidationException("The brand could not be updated.");
        }
        return brand.getBrandId();
    }

    /** Validates and inserts or updates a vehicle category, returning its id. */
    public int saveCategory(VehicleCategory category) throws LfrlmsException {
        if (category == null || !Validator.isValid(category.getCategoryName())) {
            throw new ValidationException("Enter a vehicle category name.");
        }
        if (category.getCategoryId() <= 0) {
            return categoryDao.insert(category);
        }
        if (!categoryDao.update(category)) {
            throw new ValidationException("The vehicle category could not be updated.");
        }
        return category.getCategoryId();
    }

    /** Validates and inserts or updates a branch, returning its id. */
    public int saveBranch(Branch branch) throws LfrlmsException {
        if (branch == null || !Validator.isValid(branch.getBranchName())
                || !Validator.isValid(branch.getStreet()) || !Validator.isValid(branch.getCity())
                || !Validator.isValidPincode(branch.getPincode())
                || !Validator.isValidPhone(branch.getPhone())) {
            throw new ValidationException("Enter valid branch details.");
        }
        if (branch.getBranchId() <= 0) {
            return branchDao.insert(branch);
        }
        if (!branchDao.update(branch)) {
            throw new ValidationException("The branch could not be updated.");
        }
        return branch.getBranchId();
    }

    /** Deletes a brand by id. */
    public boolean deleteBrand(int brandId) throws LfrlmsException {
        return brandDao.delete(brandId);
    }

    /** Deletes a vehicle category by id. */
    public boolean deleteCategory(int categoryId) throws LfrlmsException {
        return categoryDao.delete(categoryId);
    }

    /** Deletes a branch by id. */
    public boolean deleteBranch(int branchId) throws LfrlmsException {
        return branchDao.delete(branchId);
    }
}
