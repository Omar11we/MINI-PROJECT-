package lfrlms.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import lfrlms.exception.LfrlmsException;
import lfrlms.exception.ValidationException;
import lfrlms.service.InsightsService;
import lfrlms.service.InsightsService.FeatureExample;
import lfrlms.util.QueryLog;

/**
 * Purpose: Shows the SQL activity and database objects without editing them.
 * Concept: Read-only tables, background tasks and prepared diagnostics.
 * Layer: GUI (presentation).
 * Rubric: GUI, StringBuffer.
 */
public class DbmsInsightsPanel extends JPanel {
    private final InsightsService insightsService;
    private final BackgroundTask<ArrayList<String>> tableListTask;
    private final BackgroundTask<ArrayList<QueryLog.QueryEntry>> queryTask;
    private final BackgroundTask<ArrayList<HashMap<String, Object>>> explainTask;
    private final BackgroundTask<CatalogSnapshot> catalogTask;
    private final BackgroundTask<ArrayList<FeatureExample>> checklistTask;
    private final SharedTableModel queryModel;
    private final SharedTableModel viewsModel;
    private final SharedTableModel functionsModel;
    private final SharedTableModel proceduresModel;
    private final SharedTableModel triggersModel;
    private final SharedTableModel checklistModel;
    private final JTable queryTable;
    private final JComboBox<String> tableSelector;
    private final JTextArea planArea;
    private final JTextArea createTableArea;
    private final JLabel queryMessage;
    private final JLabel catalogMessage;
    private final ArrayList<QueryLog.QueryEntry> queryEntries;

    public DbmsInsightsPanel() {
        insightsService = new InsightsService();
        tableListTask = new BackgroundTask<ArrayList<String>>();
        queryTask = new BackgroundTask<ArrayList<QueryLog.QueryEntry>>();
        explainTask = new BackgroundTask<ArrayList<HashMap<String, Object>>>();
        catalogTask = new BackgroundTask<CatalogSnapshot>();
        checklistTask = new BackgroundTask<ArrayList<FeatureExample>>();
        queryModel = new SharedTableModel(new String[] {
            "Time", "SQL text", "Parameters", "Milliseconds", "Rows"
        });
        viewsModel = definitionModel();
        functionsModel = definitionModel();
        proceduresModel = definitionModel();
        triggersModel = definitionModel();
        checklistModel = new SharedTableModel(new String[] {"SQL feature", "Example object"});
        queryTable = SharedTableModel.createTable(queryModel);
        queryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        applyQueryLogColumnWidths();
        tableSelector = new JComboBox<String>();
        planArea = readOnlyArea();
        createTableArea = readOnlyArea();
        queryMessage = new JLabel(" ");
        catalogMessage = new JLabel(" ");
        queryEntries = new ArrayList<QueryLog.QueryEntry>();
        buildPanel();
        loadQueryLog();
        loadChecklist();
        loadTableNames();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(8, 8));
        Theme.applyPadding(this);
        JTabbedPane sections = new JTabbedPane();
        sections.addTab("Query log", buildQueryTab());
        sections.addTab("Schema objects", buildCatalogTab());
        sections.addTab("SQL checklist", buildChecklistTab());
        add(sections, BorderLayout.CENTER);
    }

    private JPanel buildQueryTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Theme.applyPadding(panel);
        JButton refreshButton = new JButton("Refresh");
        JButton explainButton = new JButton("EXPLAIN selected query");
        refreshButton.addActionListener(event -> loadQueryLog());
        explainButton.addActionListener(event -> explainSelectedQuery());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        actions.add(refreshButton);
        actions.add(explainButton);
        actions.add(queryMessage);
        panel.add(actions, BorderLayout.NORTH);

        JScrollPane queryScroll = new JScrollPane(queryTable);
        planArea.setText("Select a logged SELECT statement and choose EXPLAIN.");
        JScrollPane planScroll = new JScrollPane(planArea);
        planScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Query plan"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queryScroll, planScroll);
        split.setResizeWeight(0.68);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCatalogTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Theme.applyPadding(panel);
        JPanel selectorBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        selectorBar.add(new JLabel("SHOW CREATE TABLE"));
        JButton refreshButton = new JButton("Refresh definitions");
        refreshButton.addActionListener(event -> loadCatalog());
        selectorBar.add(tableSelector);
        selectorBar.add(refreshButton);
        selectorBar.add(catalogMessage);
        panel.add(selectorBar, BorderLayout.NORTH);

        createTableArea.setLineWrap(false);
        JScrollPane createScroll = new JScrollPane(createTableArea);
        createScroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Table definition"));
        createScroll.setPreferredSize(new Dimension(900, 190));
        JTabbedPane objectTabs = new JTabbedPane();
        objectTabs.addTab("Views", definitionTable(viewsModel));
        objectTabs.addTab("Functions", definitionTable(functionsModel));
        objectTabs.addTab("Procedures", definitionTable(proceduresModel));
        objectTabs.addTab("Triggers", definitionTable(triggersModel));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createScroll, objectTabs);
        split.setResizeWeight(0.35);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildChecklistTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Theme.applyPadding(panel);
        panel.add(new JLabel("Examples of database features used by this project.",
                SwingConstants.LEFT), BorderLayout.NORTH);
        panel.add(new JScrollPane(SharedTableModel.createTable(checklistModel)),
                BorderLayout.CENTER);
        return panel;
    }

    private void loadQueryLog() {
        queryMessage.setText("Loading query history...");
        queryTask.execute(this, new BackgroundTask.Work<ArrayList<QueryLog.QueryEntry>>() {
            @Override
            public ArrayList<QueryLog.QueryEntry> run() {
                return insightsService.recentQueries();
            }
        }, new BackgroundTask.Completion<ArrayList<QueryLog.QueryEntry>>() {
            @Override
            public void completed(ArrayList<QueryLog.QueryEntry> result, Exception failure) {
                if (failure != null) {
                    queryMessage.setText("Query history is unavailable.");
                    GuiSupport.showFailure(DbmsInsightsPanel.this,
                            "Load SQL history", failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadQueryLog();
                                }
                            });
                    return;
                }
                queryEntries.clear();
                queryEntries.addAll(result);
                Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                for (QueryLog.QueryEntry entry : result) {
                    Vector<Object> row = new Vector<Object>();
                    row.add(entry.getRecordedAt());
                    row.add(entry.getSql());
                    row.add(entry.getParameters());
                    row.add(Long.valueOf(entry.getElapsedMillis()));
                    row.add(Integer.valueOf(entry.getRowCount()));
                    rows.add(row);
                }
                queryModel.replaceRows(rows);
                applyQueryLogColumnWidths();
                queryMessage.setText(result.isEmpty()
                        ? "No SQL statements recorded yet."
                        : result.size() + " recent statements");
                planArea.setText("Select a logged SELECT statement and choose EXPLAIN.");
            }
        });
    }

    private void explainSelectedQuery() {
        int selectedRow = queryTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a logged query first.",
                    "EXPLAIN query", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = queryTable.convertRowIndexToModel(selectedRow);
        if (modelRow >= queryEntries.size()) {
            JOptionPane.showMessageDialog(this, "Refresh the query history and select a row.",
                    "EXPLAIN query", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final QueryLog.QueryEntry selected = queryEntries.get(modelRow);
        planArea.setText("Running EXPLAIN...");
        planArea.setCaretPosition(0);
        explainTask.execute(this,
                new BackgroundTask.Work<ArrayList<HashMap<String, Object>>>() {
                    @Override
                    public ArrayList<HashMap<String, Object>> run() throws LfrlmsException {
                        return insightsService.explain(selected);
                    }
                }, new BackgroundTask.Completion<ArrayList<HashMap<String, Object>>>() {
                    @Override
                    public void completed(ArrayList<HashMap<String, Object>> rows,
                            Exception failure) {
                        if (failure != null) {
                            planArea.setText(planFailureMessage(failure));
                            planArea.setCaretPosition(0);
                            if (failure instanceof ValidationException) {
                                return;
                            }
                            GuiSupport.showFailure(DbmsInsightsPanel.this,
                                    "Explain selected query", failure, null);
                            return;
                        }
                        planArea.setText(formatPlan(rows));
                        planArea.setCaretPosition(0);
                    }
                });
    }

    private void loadCatalog() {
        final String selectedTable = (String) tableSelector.getSelectedItem();
        if (selectedTable == null) {
            return;
        }
        catalogMessage.setText("Loading schema objects...");
        catalogTask.execute(this, new BackgroundTask.Work<CatalogSnapshot>() {
            @Override
            public CatalogSnapshot run() throws LfrlmsException {
                return new CatalogSnapshot(selectedTable,
                        insightsService.showCreateTable(selectedTable),
                        insightsService.listViews(), insightsService.listFunctions(),
                        insightsService.listProcedures(), insightsService.listTriggers());
            }
        }, new BackgroundTask.Completion<CatalogSnapshot>() {
            @Override
            public void completed(CatalogSnapshot result, Exception failure) {
                if (failure != null) {
                    catalogMessage.setText("Schema definitions are unavailable.");
                    GuiSupport.showFailure(DbmsInsightsPanel.this,
                            "Load schema definitions", failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadCatalog();
                                }
                            });
                    return;
                }
                createTableArea.setText(result.createTable);
                createTableArea.setCaretPosition(0);
                replaceDefinitionRows(viewsModel, result.views);
                replaceDefinitionRows(functionsModel, result.functions);
                replaceDefinitionRows(proceduresModel, result.procedures);
                replaceDefinitionRows(triggersModel, result.triggers);
                catalogMessage.setText("Definitions loaded for " + result.tableName + ".");
            }
        });
    }

    private void loadTableNames() {
        tableListTask.execute(this, new BackgroundTask.Work<ArrayList<String>>() {
            @Override
            public ArrayList<String> run() {
                return insightsService.listTableNames();
            }
        }, new BackgroundTask.Completion<ArrayList<String>>() {
            @Override
            public void completed(ArrayList<String> names, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(DbmsInsightsPanel.this,
                            "Load table list", failure, new Runnable() {
                                @Override
                                public void run() {
                                    loadTableNames();
                                }
                            });
                    return;
                }
                for (String tableName : names) {
                    tableSelector.addItem(tableName);
                }
                tableSelector.addActionListener(event -> loadCatalog());
                loadCatalog();
            }
        });
    }

    private void loadChecklist() {
        checklistTask.execute(this, new BackgroundTask.Work<ArrayList<FeatureExample>>() {
            @Override
            public ArrayList<FeatureExample> run() {
                return insightsService.featureChecklist();
            }
        }, new BackgroundTask.Completion<ArrayList<FeatureExample>>() {
            @Override
            public void completed(ArrayList<FeatureExample> result, Exception failure) {
                if (failure != null) {
                    GuiSupport.showFailure(DbmsInsightsPanel.this,
                            "Load SQL checklist", failure, null);
                    return;
                }
                Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
                for (FeatureExample feature : result) {
                    Vector<Object> row = new Vector<Object>();
                    row.add(feature.getFeature());
                    row.add(feature.getExample());
                    rows.add(row);
                }
                checklistModel.replaceRows(rows);
            }
        });
    }

    private void replaceDefinitionRows(SharedTableModel model,
            ArrayList<HashMap<String, Object>> definitions) {
        Vector<Vector<Object>> rows = new Vector<Vector<Object>>();
        for (HashMap<String, Object> definition : definitions) {
            Vector<Object> row = new Vector<Object>();
            row.add(definition.get("object_name"));
            row.add(definition.get("definition"));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            Vector<Object> empty = new Vector<Object>();
            empty.add("No objects found");
            empty.add("");
            rows.add(empty);
        }
        model.replaceRows(rows);
    }

    private String formatPlan(ArrayList<HashMap<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "EXPLAIN returned no plan text.";
        }
        StringBuffer text = new StringBuffer();
        boolean hasPlanText = false;
        for (HashMap<String, Object> row : rows) {
            Object value = planValue(row, "explain");
            if (value == null && row != null && row.size() == 1) {
                value = row.values().iterator().next();
            }
            if (value != null) {
                if (hasPlanText) {
                    text.append('\n');
                }
                text.append(value);
                hasPlanText = true;
            }
        }
        String plan = text.toString();
        return plan.trim().isEmpty() ? "EXPLAIN returned no plan text." : plan;
    }

    private Object planValue(HashMap<String, Object> row, String column) {
        if (row == null) {
            return null;
        }
        String expected = column.toLowerCase(java.util.Locale.ROOT);
        for (String key : row.keySet()) {
            if (key != null && expected.equals(key.toLowerCase(java.util.Locale.ROOT))) {
                return row.get(key);
            }
        }
        return null;
    }

    private String planFailureMessage(Exception failure) {
        if (failure instanceof LfrlmsException) {
            String message = failure.getMessage();
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
        }
        return "The query plan could not be loaded. Check the selected query and try again.";
    }

    private void applyQueryLogColumnWidths() {
        queryTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        queryTable.getColumnModel().getColumn(1).setPreferredWidth(620);
        queryTable.getColumnModel().getColumn(2).setPreferredWidth(380);
        queryTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        queryTable.getColumnModel().getColumn(4).setPreferredWidth(70);
    }

    private JPanel definitionTable(SharedTableModel model) {
        JPanel panel = new JPanel(new BorderLayout());
        JTable table = SharedTableModel.createTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getColumnModel().getColumn(0).setPreferredWidth(210);
        table.getColumnModel().getColumn(1).setPreferredWidth(1100);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private SharedTableModel definitionModel() {
        return new SharedTableModel(new String[] {"Object", "Definition"});
    }

    private JTextArea readOnlyArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(Theme.BASE_FONT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    /** Carries one complete metadata read back to the EDT for display. */
    private static class CatalogSnapshot {
        private final String tableName;
        private final String createTable;
        private final ArrayList<HashMap<String, Object>> views;
        private final ArrayList<HashMap<String, Object>> functions;
        private final ArrayList<HashMap<String, Object>> procedures;
        private final ArrayList<HashMap<String, Object>> triggers;

        private CatalogSnapshot(String tableName, String createTable,
                ArrayList<HashMap<String, Object>> views,
                ArrayList<HashMap<String, Object>> functions,
                ArrayList<HashMap<String, Object>> procedures,
                ArrayList<HashMap<String, Object>> triggers) {
            this.tableName = tableName;
            this.createTable = createTable;
            this.views = views;
            this.functions = functions;
            this.procedures = procedures;
            this.triggers = triggers;
        }
    }
}
