package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import lfrlms.exception.LfrlmsException;
import lfrlms.model.Driver;
import lfrlms.service.DriverService;

/**
 * Purpose: Lists, edits, deactivates and deletes chauffeur records.
 * Concept: Paged Swing tables, listeners and CRUD service calls.
 * Layer: GUI (presentation).
 * Rubric: GUI, inner/anonymous classes.
 */
public class DriversPanel extends JPanel {
    private final DriverService driverService;
    private final BackgroundTask<SearchData> searchTask;
    private final BackgroundTask<Integer> writeTask;
    private final SharedTableModel tableModel;
    private final JTable table;
    private final PaginationBar pagination;
    private final javax.swing.JLabel messageLabel;
    private ArrayList<Driver> visibleDrivers;
    private String sortKey;
    private boolean ascending;
    private JButton editButton;
    private JButton deactivateButton;
    private JButton deleteButton;

    public DriversPanel() {
        this(new DriverService());
    }

    public DriversPanel(DriverService driverService) {
        this.driverService = driverService;
        searchTask = new BackgroundTask<SearchData>();
        writeTask = new BackgroundTask<Integer>();
        tableModel = new SharedTableModel(new String[] {
            "ID", "First name", "Last name", "Phone", "Licence number", "Status"});
        table = SharedTableModel.createTable(tableModel);
        pagination = new PaginationBar();
        messageLabel = new javax.swing.JLabel("Loading drivers...");
        visibleDrivers = new ArrayList<Driver>();
        sortKey = "lastName";
        ascending = true;
        buildPanel();
        installListeners();
        searchDrivers();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(6, 6));
        Theme.applyPadding(this);
        javax.swing.JLabel heading = new javax.swing.JLabel(
                "Chauffeur records — use Add or Edit to manage driver details.");
        heading.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 4, 8));
        add(heading, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        editButton = new JButton("Edit");
        deactivateButton = new JButton("Deactivate");
        deleteButton = new JButton("Delete");
        JButton refreshButton = new JButton("Refresh");
        JButton exportButton = new JButton("Export page CSV");
        setSelectionActionsEnabled(false);
        actions.add(addButton);
        actions.add(editButton);
        actions.add(deactivateButton);
        actions.add(deleteButton);
        actions.add(refreshButton);
        actions.add(exportButton);
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showDriverForm(null);
            }
        });
        editButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showDriverForm(selectedDriver());
            }
        });
        deactivateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                deactivateDriver();
            }
        });
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                deleteDriver();
            }
        });
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchDrivers();
            }
        });
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                CsvExport.exportCurrentPage(DriversPanel.this, table, "drivers.csv");
            }
        });
        JPanel lower = new JPanel(new BorderLayout());
        lower.add(messageLabel, BorderLayout.WEST);
        lower.add(pagination, BorderLayout.EAST);
        pagination.setPageChangeListener(new PaginationBar.PageChangeListener() {
            @Override
            public void pageChanged(int pageNumber, int pageSize) {
                searchDrivers();
            }
        });
        footer.add(actions, BorderLayout.NORTH);
        footer.add(lower, BorderLayout.SOUTH);
        return footer;
    }

    private void installListeners() {
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    updateSelectionActions();
                }
            }
        });
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
                    searchDrivers();
                }
            }
        });
    }

    private void searchDrivers() {
        final int page = pagination.getPageNumber();
        final int size = pagination.getPageSize();
        final String sort = sortKey;
        final boolean sortAscending = ascending;
        messageLabel.setText("Searching drivers...");
        searchTask.execute(this, new BackgroundTask.Work<SearchData>() {
            @Override
            public SearchData run() throws LfrlmsException {
                long count = driverService.countAll();
                ArrayList<Driver> drivers = driverService.listPage(
                        sort, sortAscending, page, size);
                return new SearchData(drivers, count);
            }
        }, new BackgroundTask.Completion<SearchData>() {
            @Override
            public void completed(SearchData data, Exception failure) {
                if (failure != null) {
                    messageLabel.setText(" ");
                    GuiSupport.showFailure(DriversPanel.this, "Load drivers", failure,
                            new Runnable() {
                                @Override
                                public void run() {
                                    searchDrivers();
                                }
                            });
                    return;
                }
                pagination.setTotalRows(data.count);
                if (pagination.getPageNumber() != page) {
                    searchDrivers();
                    return;
                }
                visibleDrivers = data.drivers;
                showDrivers(data.drivers);
                messageLabel.setText(data.count + " drivers found.");
            }
        });
    }

    private void showDrivers(ArrayList<Driver> drivers) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (Driver driver : drivers) {
            Vector<Object> row = new Vector<Object>();
            row.add(Integer.valueOf(driver.getDriverId()));
            row.add(driver.getFirstName());
            row.add(driver.getLastName());
            row.add(driver.getPhone());
            row.add(driver.getLicenseNo());
            row.add(driver.getStatus());
            rows.add(row);
        }
        tableModel.replaceRows(rows);
        int viewColumn = table.convertColumnIndexToView(tableModel.findColumn("Status"));
        if (viewColumn >= 0) {
            table.getColumnModel().getColumn(viewColumn)
                    .setCellRenderer(new StatusColourRenderer());
        }
        updateSelectionActions();
    }

    private void showDriverForm(Driver selected) {
        Driver value = DriverFormDialog.showDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), selected);
        if (value == null) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(driverService.save(value));
            }
        }, writeCompletion("Save driver"));
    }

    private void deactivateDriver() {
        final Driver driver = selectedDriver();
        if (driver == null || javax.swing.JOptionPane.showConfirmDialog(this,
                "Deactivate " + driver.getFirstName() + " " + driver.getLastName() + "?",
                "Confirm deactivation", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                driverService.deactivate(driver.getDriverId());
                return Integer.valueOf(driver.getDriverId());
            }
        }, writeCompletion("Deactivate driver"));
    }

    private void deleteDriver() {
        final Driver driver = selectedDriver();
        if (driver == null || JOptionPane.showConfirmDialog(this,
                "Permanently delete this driver? If referenced, deactivate instead.",
                "Confirm deletion", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        writeTask.execute(this, new BackgroundTask.Work<Integer>() {
            @Override
            public Integer run() throws LfrlmsException {
                return Integer.valueOf(driverService.delete(driver.getDriverId()) ? 1 : 0);
            }
        }, writeCompletion("Delete driver"));
    }

    private BackgroundTask.Completion<Integer> writeCompletion(final String action) {
        return new BackgroundTask.Completion<Integer>() {
            @Override
            public void completed(Integer id, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(DriversPanel.this, action, failure, null);
                    return;
                }
                messageLabel.setText(action + " completed.");
                searchDrivers();
            }
        };
    }

    private void updateSelectionActions() {
        Driver driver = selectedDriver();
        setSelectionActionsEnabled(driver != null);
        if (deactivateButton != null) {
            deactivateButton.setEnabled(driver != null
                    && !"Inactive".equals(driver.getStatus()));
        }
    }

    private void setSelectionActionsEnabled(boolean enabled) {
        if (editButton != null) {
            editButton.setEnabled(enabled);
            deactivateButton.setEnabled(enabled);
            deleteButton.setEnabled(enabled);
        }
    }

    private Driver selectedDriver() {
        int row = table.getSelectedRow();
        return row < 0 || row >= visibleDrivers.size() ? null : visibleDrivers.get(row);
    }

    private String sortKeyForColumn(int column) {
        if (column == 0) {
            return "id";
        }
        if (column == 1) {
            return "firstName";
        }
        if (column == 5) {
            return "status";
        }
        return "lastName";
    }

    private static class SearchData {
        private final ArrayList<Driver> drivers;
        private final long count;

        private SearchData(ArrayList<Driver> drivers, long count) {
            this.drivers = drivers;
            this.count = count;
        }
    }
}
