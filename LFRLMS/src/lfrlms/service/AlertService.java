package lfrlms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import lfrlms.dao.CustomerDao;
import lfrlms.dao.LeaseDao;
import lfrlms.dao.MaintenanceDao;
import lfrlms.dao.RentalDao;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Customer;
import lfrlms.model.IndividualCustomer;
import lfrlms.model.Lease;
import lfrlms.model.Maintenance;
import lfrlms.model.Rental;
import lfrlms.util.Constants;

// Layer: Service (business logic).
// Rubric: Collections.
/** Purpose: Collects alert counts and list entries from the existing DAOs. */
public class AlertService {
    private static final int PAGE_SIZE = 100;
    private final RentalDao rentalDao;
    private final LeaseDao leaseDao;
    private final MaintenanceDao maintenanceDao;
    private final CustomerDao customerDao;

    public AlertService() {
        this(new RentalDao(), new LeaseDao(), new MaintenanceDao(), new CustomerDao());
    }

    public AlertService(RentalDao rentalDao, LeaseDao leaseDao,
            MaintenanceDao maintenanceDao, CustomerDao customerDao) {
        this.rentalDao = rentalDao;
        this.leaseDao = leaseDao;
        this.maintenanceDao = maintenanceDao;
        this.customerDao = customerDao;
    }

    /** Returns only the counts from a fresh alert snapshot. */
    public HashMap<String, Integer> getAlertCounts() throws LfrlmsException {
        return getAlertSnapshot().getCounts();
    }

    /** Collects every alert count and message in one pass. */
    public AlertSnapshot getAlertSnapshot() throws LfrlmsException {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        HashMap<String, Integer> counts = new HashMap<String, Integer>();
        ArrayList<String> items = new ArrayList<String>();
        addOverdueRentalAlerts(now, counts, items);
        addConfirmedRentalAlerts(now, counts, items);
        addLeaseAlerts(today, counts, items);
        addMaintenanceAlerts(today, counts, items);
        addLicenseAlerts(counts, items);
        return new AlertSnapshot(counts, items);
    }

    private void addOverdueRentalAlerts(LocalDateTime now,
            HashMap<String, Integer> counts, ArrayList<String> items)
            throws LfrlmsException {
        int count = 0;
        ArrayList<Rental> rows = loadRentals(Constants.RENTAL_ACTIVE, now);
        for (Rental rental : rows) {
            if (rental.getExpectedReturnDatetime() != null
                    && rental.getExpectedReturnDatetime().isBefore(now)) {
                count++;
                items.add("Overdue rental #" + rental.getRentalId()
                        + " (vehicle #" + rental.getVehicleId() + ")");
            }
        }
        counts.put("overdueRentals", Integer.valueOf(count));
    }

    private void addConfirmedRentalAlerts(LocalDateTime now,
            HashMap<String, Integer> counts, ArrayList<String> items)
            throws LfrlmsException {
        int count = 0;
        ArrayList<Rental> rows = loadRentals(Constants.RENTAL_CONFIRMED,
                now.plusHours(24));
        for (Rental rental : rows) {
            LocalDateTime start = rental.getScheduledStartDatetime();
            if (start != null) {
                count++;
                String prefix = start.isBefore(now)
                        ? "Past-start Confirmed rental #" : "Upcoming rental #";
                items.add(prefix + rental.getRentalId() + " (vehicle #"
                        + rental.getVehicleId() + ")");
            }
        }
        counts.put("upcomingRentals", Integer.valueOf(count));
    }

    private void addLeaseAlerts(LocalDate today, HashMap<String, Integer> counts,
            ArrayList<String> items) throws LfrlmsException {
        int count = 0;
        LocalDate through = today.plusDays(30);
        ArrayList<Lease> rows = loadActiveLeases();
        for (Lease lease : rows) {
            LocalDate end = lease.getEndDate();
            if (end != null && !end.isBefore(today) && !end.isAfter(through)) {
                count++;
                items.add("Lease #" + lease.getLeaseId() + " (vehicle #"
                        + lease.getVehicleId() + ") ends " + end);
            }
        }
        counts.put("leasesEnding", Integer.valueOf(count));
    }

    private void addMaintenanceAlerts(LocalDate today,
            HashMap<String, Integer> counts, ArrayList<String> items)
            throws LfrlmsException {
        int count = 0;
        LocalDate through = today.plusDays(7);
        ArrayList<Maintenance> rows = loadOpenMaintenance();
        for (Maintenance maintenance : rows) {
            LocalDate due = maintenance.getNextServiceDate();
            if (due != null && !due.isAfter(through)) {
                count++;
                items.add("Maintenance #" + maintenance.getMaintenanceId()
                        + " (vehicle #" + maintenance.getVehicleId() + ") due " + due);
            }
        }
        counts.put("maintenanceDue", Integer.valueOf(count));
    }

    private void addLicenseAlerts(HashMap<String, Integer> counts,
            ArrayList<String> items) throws LfrlmsException {
        int count = 0;
        ArrayList<Customer> rows = loadExpiringLicenses();
        for (Customer customer : rows) {
            if (customer instanceof IndividualCustomer) {
                IndividualCustomer individual = (IndividualCustomer) customer;
                count++;
                items.add("Driving licence expires for " + individual.getDisplayName()
                        + " (customer #" + individual.getCustomerId() + ")");
            }
        }
        counts.put("licensesExpiring", Integer.valueOf(count));
    }

    private ArrayList<Rental> loadRentals(String status, LocalDateTime before)
            throws LfrlmsException {
        ArrayList<Rental> result = new ArrayList<Rental>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Rental> rows = rentalDao.search(null, status, null, before,
                    false, "scheduledStart", true, pageNumber, PAGE_SIZE);
            result.addAll(rows);
            if (rows.size() < PAGE_SIZE) {
                return result;
            }
            pageNumber++;
        }
    }

    private ArrayList<Lease> loadActiveLeases() throws LfrlmsException {
        ArrayList<Lease> result = new ArrayList<Lease>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Lease> rows = leaseDao.search(null, Constants.LEASE_ACTIVE,
                    null, null, false, "endDate", true, pageNumber, PAGE_SIZE);
            result.addAll(rows);
            if (rows.size() < PAGE_SIZE) {
                return result;
            }
            pageNumber++;
        }
    }

    private ArrayList<Maintenance> loadOpenMaintenance() throws LfrlmsException {
        ArrayList<Maintenance> result = new ArrayList<Maintenance>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Maintenance> rows = maintenanceDao.search(null,
                    Constants.MAINTENANCE_IN_PROGRESS, null, null,
                    "serviceDate", true, pageNumber, PAGE_SIZE);
            result.addAll(rows);
            if (rows.size() < PAGE_SIZE) {
                return result;
            }
            pageNumber++;
        }
    }

    private ArrayList<Customer> loadExpiringLicenses() throws LfrlmsException {
        ArrayList<Customer> result = new ArrayList<Customer>();
        int pageNumber = 1;
        while (true) {
            ArrayList<Customer> rows = customerDao.search(null,
                    Constants.CUSTOMER_INDIVIDUAL, Integer.valueOf(30),
                    "lastName", true, pageNumber, PAGE_SIZE);
            result.addAll(rows);
            if (rows.size() < PAGE_SIZE) {
                return result;
            }
            pageNumber++;
        }
    }
}
