package lfrlms.gui;

import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Purpose: Moves through server-paged result sets without loading every row.
 * Concept: Swing controls and event listeners.
 * Layer: GUI (presentation).
 * Rubric: GUI, interfaces/abstract classes.
 */
public class PaginationBar extends JPanel {
    public interface PageChangeListener {
        void pageChanged(int pageNumber, int pageSize);
    }

    private final JButton previousButton;
    private final JButton nextButton;
    private final JLabel pageLabel;
    private final JComboBox<Integer> pageSizeBox;
    private PageChangeListener listener;
    private int pageNumber;
    private long totalRows;

    public PaginationBar() {
        super(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        pageNumber = 1;
        totalRows = 0;
        previousButton = new JButton("Previous");
        nextButton = new JButton("Next");
        pageLabel = new JLabel("Page 1 of 1");
        pageSizeBox = new JComboBox<Integer>(new Integer[] {10, 25, 50});
        pageSizeBox.setPrototypeDisplayValue(Integer.valueOf(50));
        pageSizeBox.setPreferredSize(new Dimension(70,
                pageSizeBox.getPreferredSize().height));
        add(new JLabel("Rows:"));
        add(pageSizeBox);
        add(previousButton);
        add(pageLabel);
        add(nextButton);
        previousButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (pageNumber > 1) {
                    pageNumber--;
                    notifyListener();
                }
            }
        });
        nextButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (pageNumber < getPageCount()) {
                    pageNumber++;
                    notifyListener();
                }
            }
        });
        pageSizeBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                pageNumber = 1;
                notifyListener();
            }
        });
        refreshControls();
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return ((Integer) pageSizeBox.getSelectedItem()).intValue();
    }

    public void setPageNumber(int value) {
        pageNumber = Math.max(1, value);
        refreshControls();
    }

    public void setTotalRows(long rows) {
        totalRows = Math.max(0, rows);
        pageNumber = Math.min(pageNumber, getPageCount());
        refreshControls();
    }

    public void setPageChangeListener(PageChangeListener value) {
        listener = value;
    }

    private int getPageCount() {
        return (int) Math.max(1, (totalRows + getPageSize() - 1) / getPageSize());
    }

    private void notifyListener() {
        refreshControls();
        if (listener != null) {
            listener.pageChanged(pageNumber, getPageSize());
        }
    }

    private void refreshControls() {
        int pages = getPageCount();
        previousButton.setEnabled(pageNumber > 1);
        nextButton.setEnabled(pageNumber < pages);
        pageLabel.setText("Page " + pageNumber + " of " + pages
                + "  (" + totalRows + " rows)");
    }
}
