package lfrlms.service;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Purpose: Carries one alert poll's counts and display items to the GUI.
 * Concept: Encapsulation and collection copies.
 * Layer: Service (business logic).
 * Rubric: Collections.
 */
public class AlertSnapshot {
    private final HashMap<String, Integer> counts;
    private final ArrayList<String> items;

    public AlertSnapshot(HashMap<String, Integer> counts, ArrayList<String> items) {
        this.counts = new HashMap<String, Integer>(counts);
        this.items = new ArrayList<String>(items);
    }

    /** Returns a copy of the alert counts. */
    public HashMap<String, Integer> getCounts() {
        return new HashMap<String, Integer>(counts);
    }

    /** Returns a copy of the alert messages. */
    public ArrayList<String> getItems() {
        return new ArrayList<String>(items);
    }
}
