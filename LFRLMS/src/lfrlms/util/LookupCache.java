package lfrlms.util;

import java.util.HashMap;

/**
 * Purpose: Caches display names for brand, category and branch identifiers.
 * Concept: HashMap lookup collections.
 * Layer: Utility.
 * Rubric: Collections, wrapper classes.
 */
public class LookupCache {
    private final HashMap<Integer, String> brandNames;
    private final HashMap<Integer, String> categoryNames;
    private final HashMap<Integer, String> branchNames;

    public LookupCache() {
        brandNames = new HashMap<Integer, String>();
        categoryNames = new HashMap<Integer, String>();
        branchNames = new HashMap<Integer, String>();
    }

    public void putBrandName(int id, String name) {
        brandNames.put(Integer.valueOf(id), name);
    }

    public String getBrandName(int id) {
        return brandNames.get(Integer.valueOf(id));
    }

    public void putCategoryName(int id, String name) {
        categoryNames.put(Integer.valueOf(id), name);
    }

    public String getCategoryName(int id) {
        return categoryNames.get(Integer.valueOf(id));
    }

    public void putBranchName(int id, String name) {
        branchNames.put(Integer.valueOf(id), name);
    }

    public String getBranchName(int id) {
        return branchNames.get(Integer.valueOf(id));
    }

    public void clear() {
        brandNames.clear();
        categoryNames.clear();
        branchNames.clear();
    }
}
