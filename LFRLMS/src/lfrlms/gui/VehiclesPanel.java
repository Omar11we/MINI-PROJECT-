package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Branch;
import lfrlms.model.Brand;
import lfrlms.model.Rental;
import lfrlms.model.Vehicle;
import lfrlms.model.VehicleCategory;
import lfrlms.service.LookupService;
import lfrlms.service.RentalService;
import lfrlms.service.VehicleService;
import lfrlms.util.Constants;
import lfrlms.util.DateUtil;
import lfrlms.util.LookupCache;
import lfrlms.util.Validator;

/**
 * Purpose: Searches and manages fleet vehicles through service calls.
 * Concept: Swing listeners, server pagination and background threads.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class VehiclesPanel extends JPanel {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private final VehicleService vehicleService;
    private final LookupService lookupService;
    private final RentalService rentalService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<LookupData> lookupTask;
    private final BackgroundTask<Integer> writeTask;
    private final JTextField textField;
    private final JTextField minimumRentalField;
    private final JTextField maximumRentalField;
    private final JTextField minimumLeaseField;
    private final JTextField maximumLeaseField;
    private final JTextField availableFromField;
    private final JTextField availableToField;
    private final JComboBox<LookupFilterChoice> brandBox;
    private final JComboBox<LookupFilterChoice> categoryBox;
    private final JComboBox<LookupFilterChoice> branchBox;
    private final JComboBox<String> statusBox;
    private final SharedTableModel tableModel;
    private final JTable table;
    private final PaginationBar pagination;
    private final JLabel messageLabel;
    private final javax.swing.Timer searchTimer;
    private ArrayList<Vehicle> visibleVehicles;
    private ArrayList<Brand> brands;
    private ArrayList<VehicleCategory> categories;
    private ArrayList<Branch> branches;
    private LookupCache lookupCache;
    private String sortKey;
    private boolean ascending;
    private boolean loadingLookups;
    private boolean lookupsReady;

    public VehiclesPanel() {
        this(new VehicleService(), new LookupService(), new RentalService());
    }

    public VehiclesPanel(VehicleService vehicleService, LookupService lookupService,
            RentalService rentalService) {
        this.vehicleService = vehicleService;
        this.lookupService = lookupService;
        this.rentalService = rentalService;
        searchTask = new BackgroundTask<SearchData>();
        lookupTask = new BackgroundTask<LookupData>();
        writeTask = new BackgroundTask<Integer>();
        textField = new JTextField(14);
        minimumRentalField = new JTextField(10);
        maximumRentalField = new JTextField(6);
        minimumLeaseField = new JTextField(6);
        maximumLeaseField = new JTextField(6);
        availableFromField = new HintTextField(21, "yyyy-MM-dd HH:mm");
        availableToField = new HintTextField(21, "yyyy-MM-dd HH:mm");
        availableFromField.setToolTipText("Format: yyyy-MM-dd HH:mm (24-hour time).");
        availableToField.setToolTipText("Format: yyyy-MM-dd HH:mm (24-hour time).");
        brandBox = new JComboBox<LookupFilterChoice>();
        categoryBox = new JComboBox<LookupFilterChoice>();
        branchBox = new JComboBox<LookupFilterChoice>();
        statusBox = new JComboBox<String>(new String[] {
            "Any status", Constants.VEHICLE_AVAILABLE, Constants.VEHICLE_RESERVED,
            Constants.VEHICLE_RENTED, Constants.VEHICLE_LEASED,
            Constants.VEHICLE_MAINTENANCE, Constants.VEHICLE_INACTIVE});
        tableModel = new SharedTableModel(new String[] {"ID", "Registration", "Brand",
            "Category", "Branch", "Model", "Year", "Rental / day", "Lease / month",
            "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new JLabel("Loading lookup filters...");
        searchTimer = new javax.swing.Timer(300, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pagination.setPageNumber(1);
                searchVehicles();
            }
        });
        searchTimer.setRepeats(false);
        visibleVehicles = new ArrayList<Vehicle>();
        brands = new ArrayList<Brand>();
        categories = new ArrayList<VehicleCategory>();
        branches = new ArrayList<Branch>();
        lookupCache = new LookupCache();
        sortKey = "registrationNo";
        ascending = true;
        loadingLookups = false;
        lookupsReady = false;
        buildPanel();
        installListeners();
        loadLookups();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(6, 6));
        Theme.applyPadding(this);
        add(buildFilters(), BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        applyStatusRenderer();
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFilters() {
        JPanel filters = new JPanel(new GridBagLayout());
        Theme.applyPadding(filters);
        addFilter(filters, 0, 0, "Text", textField);
        addFilter(filters, 0, 1, "Brand", brandBox);
        addFilter(filters, 0, 2, "Category", categoryBox);
        addFilter(filters, 0, 3, "Branch", branchBox);
        addFilter(filters, 0, 4, "Status", statusBox);
        addFilter(filters, 1, 0, "Rental rate min", minimumRentalField);
        addFilter(filters, 1, 1, "Rental rate max", maximumRentalField);
        addFilter(filters, 1, 2, "Lease rate min", minimumLeaseField);
        addFilter(filters, 1, 3, "Lease rate max", maximumLeaseField);
        addFilter(filters, 1, 4, "Available from", availableFromField);
        addFilter(filters, 1, 5, "Available to", availableToField);
        return filters;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton deactivateButton = new JButton("Deactivate / Delete");
        JButton detailsButton = new JButton("Details");
        JButton createRentalButton = new JButton("Create Rental");
        JButton createLeaseButton = new JButton("Create Lease");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deactivateButton);
        actions.add(detailsButton);
        actions.add(createRentalButton);
        actions.add(createLeaseButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        configureSelectionButtons(editButton, deactivateButton, detailsButton,
                createRentalButton, createLeaseButton);
        bindAction(addButton, "add");
        bindAction(editButton, "edit");
        bindAction(deactivateButton, "deactivate");
        bindAction(detailsButton, "details");
        bindAction(createRentalButton, "create-rental");
        bindAction(createLeaseButton, "create-lease");
        bindAction(refreshButton, "refresh");
        bindAction(exportButton, "export");
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchVehicles();
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(lower, BorderLayout.SOUTH);
        return footer;
    }

    private void configureSelectionButtons(final JButton editButton,
            final JButton deactivateButton, final JButton detailsButton,
            final JButton createRentalButton, final JButton createLeaseButton) {
        editButton.setEnabled(false);
        deactivateButton.setEnabled(false);
        detailsButton.setEnabled(false);
        createRentalButton.setEnabled(false);
        createLeaseButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    boolean selected = table.getSelectedRow() >= 0;
                    editButton.setEnabled(selected);
                    deactivateButton.setEnabled(selected);
                    detailsButton.setEnabled(selected);
                    createRentalButton.setEnabled(selected);
                    createLeaseButton.setEnabled(selected);
                }
            }
        });
    }

    private void bindAction(JButton button, final String action) {
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                handleAction(action);
            }
        });
    }

    private void handleAction(String action) {
        if ("add".equals(action)) {
            showVehicleForm(null);
        } else if ("edit".equals(action)) {
            showVehicleForm(selectedVehicle());
        } else if ("deactivate".equals(action)) {
            deactivateOrDelete();
        } else if ("details".equals(action)) {
            showDetails();
        } else if ("create-rental".equals(action)) {
            openContractDialog(true);
        } else if ("create-lease".equals(action)) {
            openContractDialog(false);
        } else if ("refresh".equals(action)) {
            loadLookups();
            searchVehicles();
        } else if ("export".equals(action)) {
            CsvExport.exportCurrentPage(VehiclesPanel.this, table, "vehicles.csv");
        }
    }

    private void installListeners() {
        attachSearchListener(textField);
        attachSearchListener(minimumRentalField);
        attachSearchListener(maximumRentalField);
        attachSearchListener(minimumLeaseField);
        attachSearchListener(maximumLeaseField);
        attachSearchListener(availableFromField);
        attachSearchListener(availableToField);
        ActionListener filterChanged = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (!loadingLookups) {
                    scheduleSearch();
                }
            }
        };
        brandBox.addActionListener(filterChanged);
        categoryBox.addActionListener(filterChanged);
        branchBox.addActionListener(filterChanged);
        statusBox.addActionListener(filterChanged);
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int viewColumn = table.columnAtPoint(event.getPoint());
                int column = table.convertColumnIndexToModel(viewColumn);
                String key = sortKeyForColumn(column);
                if (key != null) {
                    if (key.equals(sortKey)) {
                        ascending = !ascending;
                    } else {
                        sortKey = key;
                        ascending = true;
                    }
                    searchVehicles();
                }
            }
        });
    }

    private void attachSearchListener(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleSearch();
            }
        });
    }

    private void scheduleSearch() {
        if (lookupsReady) {
            searchTimer.restart();
        }
    }

    private void loadLookups() {
        lookupTask.execute(this, new BackgroundTask.Work<LookupData>() {
            @Override
            public LookupData run() throws LfrlmsException {
                return new LookupData(lookupService.listBrands(),
                        lookupService.listCategories(), lookupService.listBranches());
            }
        }, new BackgroundTask.Completion<LookupData>() {
            @Override
            public void completed(LookupData data, Exception failure) {
                if (failure != null) {
                    lookupsReady = false;
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(VehiclesPanel.this, "Load vehicle filters",
                            failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadLookups();
                                }
                            });
                    return;
                }
                brands = data.brands;
                categories = data.categories;
                branches = data.branches;
                replaceLookupFilters();
                buildLookupCache();
                lookupsReady = true;
                searchVehicles();
            }
        });
    }

    private void replaceLookupFilters() {
        loadingLookups = true;
        brandBox.removeAllItems();
        categoryBox.removeAllItems();
        branchBox.removeAllItems();
        brandBox.addItem(new LookupFilterChoice(-1, "Any brand"));
        categoryBox.addItem(new LookupFilterChoice(-1, "Any category"));
        branchBox.addItem(new LookupFilterChoice(-1, "Any branch"));
        for (Brand brand : brands) {
            brandBox.addItem(new LookupFilterChoice(brand.getBrandId(), brand.getBrandName()));
        }
        for (VehicleCategory category : categories) {
            categoryBox.addItem(new LookupFilterChoice(category.getCategoryId(),
                    category.getCategoryName()));
        }
        for (Branch branch : branches) {
            branchBox.addItem(new LookupFilterChoice(branch.getBranchId(), branch.getBranchName()));
        }
        loadingLookups = false;
    }

    private void buildLookupCache() {
        lookupCache = new LookupCache();
        for (Brand brand : brands) {
            lookupCache.putBrandName(brand.getBrandId(), brand.getBrandName());
        }
        for (VehicleCategory category : categories) {
            lookupCache.putCategoryName(category.getCategoryId(), category.getCategoryName());
        }
        for (Branch branch : branches) {
            lookupCache.putBranchName(branch.getBranchId(), branch.getBranchName());
        }
    }

    private void searchVehicles() {
        final VehicleFilters filters;
        try {
            filters = readFilters();
        } catch (RuntimeException exception) {
            messageLabel.setText(exception.getMessage());
            return;
        }
        final int page = pagination.getPageNumber();
        final int pageSize = pagination.getPageSize();
        final String requestedSort = sortKey;
        final boolean requestedAscending = ascending;
        messageLabel.setText("Searching vehicles...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = vehicleService.countSearchResults(filters.text,
                        filters.brandId, filters.categoryId, filters.branchId, filters.status,
                        filters.minimumRentalRate, filters.maximumRentalRate,
                        filters.minimumLeaseRate, filters.maximumLeaseRate,
                        filters.availableFrom, filters.availableTo);
                ArrayList<Vehicle> vehicles = vehicleService.search(filters.text,
                        filters.brandId, filters.categoryId, filters.branchId, filters.status,
                        filters.minimumRentalRate, filters.maximumRentalRate,
                        filters.minimumLeaseRate, filters.maximumLeaseRate,
                        filters.availableFrom, filters.availableTo, requestedSort,
                        requestedAscending, page, pageSize);
                return new SearchData(vehicles, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(VehiclesPanel.this, "Search vehicles", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchVehicles();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchVehicles();
                    return;
                }
                visibleVehicles = data.vehicles;
                showVehicles(data.vehicles);
                messageLabel.setText(data.count + " vehicles found.");
            }
        });
    }

    private VehicleFilters readFilters() {
        BigDecimal minRental = parseMoney(minimumRentalField.getText(), "Minimum rental rate");
        BigDecimal maxRental = parseMoney(maximumRentalField.getText(), "Maximum rental rate");
        BigDecimal minLease = parseMoney(minimumLeaseField.getText(), "Minimum lease rate");
        BigDecimal maxLease = parseMoney(maximumLeaseField.getText(), "Maximum lease rate");
        validateRange(minRental, maxRental, "Rental");
        validateRange(minLease, maxLease, "Lease");
        LocalDateTime from = parseDateTime(availableFromField.getText(), "Available from");
        LocalDateTime to = parseDateTime(availableToField.getText(), "Available to");
        if ((from == null) != (to == null) || (from != null && !Validator.isValid(from, to))) {
            throw new IllegalArgumentException(
                    "Enter both availability dates; 'to' must be after 'from'.");
        }
        String status = (String) statusBox.getSelectedItem();
        if (status != null && status.startsWith("Any")) {
            status = null;
        }
        return new VehicleFilters(textField.getText().trim(), selectedId(brandBox),
                selectedId(categoryBox), selectedId(branchBox), status, minRental,
                maxRental, minLease, maxLease, from, to);
    }

    private BigDecimal parseMoney(String value, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            if (!Validator.isNonNegative(amount)) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a non-negative amount.");
        }
    }

    private void validateRange(BigDecimal minimum, BigDecimal maximum, String label) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(label + " minimum cannot exceed maximum.");
        }
    }

    private LocalDateTime parseDateTime(String value, String label) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return DateUtil.parseDateTime(text.replace(' ', 'T'));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(label
                    + " must use yyyy-MM-dd HH:mm (for example, 2026-10-12 14:30).");
        }
    }

    private void showVehicles(ArrayList<Vehicle> vehicles) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Vehicle vehicle : vehicles) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(vehicle.getVehicleId()));
            row.add(vehicle.getRegistrationNo());
            row.add(displayName(lookupCache.getBrandName(vehicle.getBrandId())));
            row.add(displayName(lookupCache.getCategoryName(vehicle.getCategoryId())));
            row.add(displayName(lookupCache.getBranchName(vehicle.getBranchId())));
            row.add(vehicle.getModel());
            row.add(Integer.valueOf(vehicle.getModelYear()));
            row.add(vehicle.getRentalRate() == null ? "—" : vehicle.getRentalRate().toPlainString());
            row.add(vehicle.getLeaseRate() == null ? "—" : vehicle.getLeaseRate().toPlainString());
            row.add(vehicle.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        applyStatusRenderer();
    }

    private void applyStatusRenderer() {
        int modelColumn = tableModel.findColumn("Status");
        if (modelColumn >= 0) {
            int viewColumn = table.convertColumnIndexToView(modelColumn);
            if (viewColumn >= 0) {
                table.getColumnModel().getColumn(viewColumn)
                        .setCellRenderer(new StatusColourRenderer());
            }
        }
    }

    private String displayName(String value) {
        return value == null ? "(missing)" : value;
    }

    private void showVehicleForm(Vehicle existing) {
        if (!lookupsReady) {
            JOptionPane.showMessageDialog(this, "Vehicle filters are still loading.");
            return;
        }
        Vehicle value = VehicleFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), existing,
                brands, categories, branches);
        if (value == null) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(vehicleService.save(value));
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(VehiclesPanel.this, "Save vehicle", failure, null);
                    return;
                }
                messageLabel.setText("Vehicle saved.");
                searchVehicles();
            }
        });
    }

    private void deactivateOrDelete() {
        final Vehicle vehicle = selectedVehicle();
        if (vehicle == null) {
            return;
        }
        boolean inactive = Constants.VEHICLE_INACTIVE.equals(vehicle.getStatus());
        Object[] options = inactive
                ? new Object[] {"Delete permanently", "Cancel"}
                : new Object[] {"Deactivate", "Delete permanently", "Cancel"};
        String prompt = inactive
                ? "Permanently delete " + vehicle.getRegistrationNo() + "?"
                : "Choose an action for " + vehicle.getRegistrationNo() + ".";
        int choice = JOptionPane.showOptionDialog(this, prompt, "Vehicle action",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (choice < 0 || choice == options.length - 1) {
            return;
        }
        final boolean deactivate = !inactive && choice == 0;
        int confirmed = JOptionPane.showConfirmDialog(this,
                deactivate ? "Deactivate this vehicle?" : "Delete this vehicle permanently?",
                "Confirm vehicle action", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                if (deactivate) {
                    vehicleService.deactivate(vehicle.getVehicleId());
                    return Integer.valueOf(vehicle.getVehicleId());
                }
                return Integer.valueOf(vehicleService.delete(vehicle.getVehicleId()) ? 1 : 0);
            }
        }, new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer result, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(VehiclesPanel.this, "Update vehicle", failure, null);
                    return;
                }
                messageLabel.setText(deactivate ? "Vehicle deactivated." : "Vehicle deleted.");
                searchVehicles();
            }
        });
    }

    private void showDetails() {
        Vehicle vehicle = selectedVehicle();
        if (vehicle == null) {
            return;
        }
        VehicleDetailsDialog dialog = new VehicleDetailsDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), vehicle,
                lookupCache.getBrandName(vehicle.getBrandId()),
                lookupCache.getCategoryName(vehicle.getCategoryId()),
                lookupCache.getBranchName(vehicle.getBranchId()), rentalService);
        dialog.setVisible(true);
    }

    private void openContractDialog(boolean rental) {
        Vehicle vehicle = selectedVehicle();
        if (vehicle == null) {
            return;
        }
        boolean saved = ContractFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), rental,
                Integer.valueOf(vehicle.getVehicleId()));
        if (saved) {
            searchVehicles();
        }
    }

    private Vehicle selectedVehicle() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= visibleVehicles.size()) {
            return null;
        }
        return visibleVehicles.get(row);
    }

    private int selectedId(JComboBox<LookupFilterChoice> box) {
        LookupFilterChoice choice = (LookupFilterChoice) box.getSelectedItem();
        return choice == null || choice.id <= 0 ? -1 : choice.id;
    }

    private String sortKeyForColumn(int column) {
        if (column == 1) {
            return "registrationNo";
        }
        if (column == 2) {
            return "brandName";
        }
        if (column == 3) {
            return "categoryName";
        }
        if (column == 4) {
            return "branchName";
        }
        if (column == 5) {
            return "model";
        }
        if (column == 6) {
            return "year";
        }
        if (column == 7) {
            return "rentalRate";
        }
        if (column == 8) {
            return "leaseRate";
        }
        if (column == 9) {
            return "status";
        }
        return "id";
    }

    private void addFilter(JPanel panel, int row, int column,
            String label, java.awt.Component control) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column * 2;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_END;
        constraints.insets = new Insets(3, 5, 3, 3);
        panel.add(new JLabel(label + ":"), constraints);
        constraints = new GridBagConstraints();
        constraints.gridx = column * 2 + 1;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(3, 2, 3, 5);
        panel.add(control, constraints);
    }

    private static class LookupFilterChoice {
        private final int id;
        private final String name;

        private LookupFilterChoice(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class HintTextField extends JTextField {
        private final String hint;

        private HintTextField(int columns, String hint) {
            super(columns);
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !isFocusOwner()) {
                graphics.setColor(new Color(130, 130, 130));
                int textY = (getHeight() - getFontMetrics(getFont()).getHeight()) / 2
                        + getFontMetrics(getFont()).getAscent();
                graphics.drawString(hint, getInsets().left + 2, textY);
            }
        }
    }

    private static class LookupData {
        private final ArrayList<Brand> brands;
        private final ArrayList<VehicleCategory> categories;
        private final ArrayList<Branch> branches;

        private LookupData(ArrayList<Brand> brands, ArrayList<VehicleCategory> categories,
                ArrayList<Branch> branches) {
            this.brands = brands;
            this.categories = categories;
            this.branches = branches;
        }
    }

    private static class SearchData {
        private final ArrayList<Vehicle> vehicles;
        private final long count;

        private SearchData(ArrayList<Vehicle> vehicles, long count) {
            this.vehicles = vehicles;
            this.count = count;
        }
    }

    private static class VehicleFilters {
        private final String text;
        private final Integer brandId;
        private final Integer categoryId;
        private final Integer branchId;
        private final String status;
        private final BigDecimal minimumRentalRate;
        private final BigDecimal maximumRentalRate;
        private final BigDecimal minimumLeaseRate;
        private final BigDecimal maximumLeaseRate;
        private final LocalDateTime availableFrom;
        private final LocalDateTime availableTo;

        private VehicleFilters(String text, int brandId, int categoryId, int branchId,
                String status, BigDecimal minimumRentalRate, BigDecimal maximumRentalRate,
                BigDecimal minimumLeaseRate, BigDecimal maximumLeaseRate,
                LocalDateTime availableFrom, LocalDateTime availableTo) {
            this.text = text;
            this.brandId = brandId <= 0 ? null : Integer.valueOf(brandId);
            this.categoryId = categoryId <= 0 ? null : Integer.valueOf(categoryId);
            this.branchId = branchId <= 0 ? null : Integer.valueOf(branchId);
            this.status = status;
            this.minimumRentalRate = minimumRentalRate;
            this.maximumRentalRate = maximumRentalRate;
            this.minimumLeaseRate = minimumLeaseRate;
            this.maximumLeaseRate = maximumLeaseRate;
            this.availableFrom = availableFrom;
            this.availableTo = availableTo;
        }
    }
}
