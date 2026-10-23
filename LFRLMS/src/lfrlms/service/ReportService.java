package lfrlms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import lfrlms.dao.ReportDao;
import lfrlms.exception.LfrlmsException;

// Layer: Service (business logic).
// Rubric: Collections.
/** Exposes the report queries without moving SQL or database work into the GUI. */
public class ReportService {
    private final ReportDao reportDao;

    public ReportService() {
        this(new ReportDao());
    }

    public ReportService(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    /** Lists vehicles free for a period. */
    public ArrayList<HashMap<String, Object>> availableVehicles(LocalDateTime from,
            LocalDateTime to, Integer branchId, Integer categoryId)
            throws LfrlmsException {
        return reportDao.availableVehicles(from, to, branchId, categoryId);
    }

    /** Returns one page of active rentals. */
    public ArrayList<HashMap<String, Object>> activeRentals(int pageNumber, int pageSize)
            throws LfrlmsException {
        return reportDao.activeRentals(pageNumber, pageSize);
    }

    /** Counts active rentals. */
    public long countActiveRentals() throws LfrlmsException {
        return reportDao.countActiveRentals();
    }

    /** Returns one page of active leases. */
    public ArrayList<HashMap<String, Object>> activeLeases(int pageNumber, int pageSize)
            throws LfrlmsException {
        return reportDao.activeLeases(pageNumber, pageSize);
    }

    /** Counts active leases. */
    public long countActiveLeases() throws LfrlmsException {
        return reportDao.countActiveLeases();
    }

    /** Returns successful payment totals per month. */
    public TreeMap<String, BigDecimal> revenueByMonth(LocalDate from, LocalDate to)
            throws LfrlmsException {
        return reportDao.revenueByMonth(from, to);
    }

    /** Returns one page of customers with their outstanding balance. */
    public ArrayList<HashMap<String, Object>> outstandingBalances(int pageNumber,
            int pageSize) throws LfrlmsException {
        return reportDao.outstandingBalances(pageNumber, pageSize);
    }

    /** Counts the customers in the outstanding-balance report. */
    public long countOutstandingBalances() throws LfrlmsException {
        return reportDao.countOutstandingBalances();
    }

    /** Ranks vehicles by rental count. */
    public ArrayList<HashMap<String, Object>> mostRentedVehicles(int limit)
            throws LfrlmsException {
        return reportDao.mostRentedVehicles(limit);
    }

    /** Returns one page of maintenance due on or before a date. */
    public ArrayList<HashMap<String, Object>> maintenanceDue(LocalDate throughDate,
            int pageNumber, int pageSize) throws LfrlmsException {
        return reportDao.maintenanceDue(throughDate, pageNumber, pageSize);
    }

    /** Counts maintenance due on or before a date. */
    public long countMaintenanceDue(LocalDate throughDate) throws LfrlmsException {
        return reportDao.countMaintenanceDue(throughDate);
    }

    /** Returns one page of a customer's rentals, leases and payments. */
    public ArrayList<HashMap<String, Object>> customerTransactionHistory(int customerId,
            int pageNumber, int pageSize) throws LfrlmsException {
        return reportDao.customerTransactionHistory(customerId, pageNumber, pageSize);
    }

    /** Counts a customer's rentals, leases and payments. */
    public long countCustomerTransactionHistory(int customerId) throws LfrlmsException {
        return reportDao.countCustomerTransactionHistory(customerId);
    }

    /** Returns a customer's outstanding balance. */
    public BigDecimal customerOutstanding(int customerId) throws LfrlmsException {
        return reportDao.customerOutstanding(customerId);
    }

    /** Counts rentals grouped by status. */
    public ArrayList<HashMap<String, Object>> rentalStatusSummary()
            throws LfrlmsException {
        return reportDao.rentalStatusSummary();
    }
}
