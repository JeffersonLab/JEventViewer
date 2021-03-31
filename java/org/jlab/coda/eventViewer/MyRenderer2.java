package org.jlab.coda.eventViewer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;

/**
 * Renderer used in displaying data to change background color every
 * Nth row and to highlight cells. Used in FileFrameBig and FileFrameV6.
 */
final class MyRenderer2 extends DefaultTableCellRenderer {

    private final int   nthRow;
    private static final Color alternateRowColor = new Color(240, 240, 240);
    private static final Color highlightGreen = new Color(210,250,210);
    private MyTableModel2 dataTableModel;
    private Color defaultHighlight = highlightGreen;

    /**
     * Keep track of which cells have been highlighted.
     * Key is a long in which highest 16 bits are map index,
     * next 32 bits are the row, and lowest 16 bits are the column.
     * Thus a map/row/col input can be quickly computed to the map's key
     * and the color can be quickly found.
     * Value object is the Color object for the given long (map/row/col).
     */
    private final HashMap<Long,Color> highlightCells  = new HashMap<>(100);

    /**
     * Keep track of which cells have been highlighted as containing errors.
     * These have priority over the regular highlighting.
     */
    private final HashMap<Long,Color> highlightErrors = new HashMap<>(100);


    /** Constructor. */
    public MyRenderer2(int nthRow) {
        this(nthRow, null);
    }

    public MyRenderer2(int nthRow, Color defaultHighlight) {
        super();
        this.nthRow = nthRow;
        if (defaultHighlight != null) {
            this.defaultHighlight = defaultHighlight;
        }
    }


    /**
     * Set the table's data model.
     * @param dataTableModel data model to use.
     */
    public void setTableModel(MyTableModel2 dataTableModel) {
        this.dataTableModel = dataTableModel;
    }


    /** Clear the highlights from the list of highlighted items. */
    public void clearHighlights() {
        highlightCells.clear();
        highlightErrors.clear();
    }


    /**
     * Given a map index, row, and column, get the corresponding
     * highlight hashmap key. This key is a long in which the highest
     * 16 bits are the map index, the next 32 bits are the row,
     * and the lowest 16 bits are the column.
     * Thus a map/row/col input can be quickly computed to the map's key
     * and the color can be quickly found.
     *
     * @param map  memory map index
     * @param row  row
     * @param col  column
     * @return highlight hashmap index
     */
    private long getHighlightKey(int map, int row, int col) {
        return (((long)map << 48) | ((long)row << 16) | col) & 0x0fff7fffffff00ffL;
    }


    /**
     * Is the cell at the given row, column, and memory map highlighted?
     * If so, return which color, else return null.
     *
     * @param map  memory map index
     * @param row  row
     * @param col  column
     * @return Color if highlighted, else null
     */
    private Color isHighlightCell(int map, int row, int col) {
        Color err = highlightErrors.get(getHighlightKey(map, row, col));
        if (err != null) {
            return err;
        }
        return highlightCells.get(getHighlightKey(map, row, col));
    }


    /**
     * Highlight the cell at the given row and column to the given color.
     * @param color   color to highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     */
    public void setHighlightCell(Color color, int row, int col, boolean isError) {
        if (color == null) color = defaultHighlight;
        if (isError) {
            highlightErrors.put(getHighlightKey(dataTableModel.getMapIndex(), row, col), color);
        }
        else {
            highlightCells.put(getHighlightKey(dataTableModel.getMapIndex(), row, col), color);
        }
    }


    /**
     * Highlight the cell at the given file byte position to the given color.
     * @param color   color to highlight
     * @param pos     file byte position
     * @param isError true if highlighting an error
     */
    public void setHighlightCell(Color color, long pos, boolean isError) {
        if (color == null) color = defaultHighlight;
        int[] mrc = dataTableModel.getMapRowCol(pos/4);
        if (mrc == null) return;
        if (isError) {
            highlightErrors.put(getHighlightKey(mrc[0], mrc[1], mrc[2]), color);
        }
        else {
            highlightCells.put(getHighlightKey(mrc[0], mrc[1], mrc[2]), color);
        }
    }


    /**
     * Remove the highlight of the cell at the given row and column.
     * @param row     row
     * @param col     column
     * @param isError true if removing the highlighting of an error
     */
    public void removeHighlightCell(int row, int col, boolean isError) {
        if (isError) {
            highlightErrors.remove(getHighlightKey(dataTableModel.getMapIndex(), row, col));
        }
        else {
            highlightCells.remove(getHighlightKey(dataTableModel.getMapIndex(), row, col));
        }
    }


    /**
     * Normal rendering means highlighting every nth row with a darker background.
     * {@inheritDoc} */
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {

        if (!isSelected) {
            if ((row+1)%nthRow == 0) {
                super.setBackground(alternateRowColor);
            }
            else {
                super.setBackground(table.getBackground());
            }
        }

        // Highlighting has priority over regular background
        Color color = isHighlightCell(dataTableModel.getMapIndex(), row, column);
        if (color != null) {
            super.setBackground(color);
        }

        // Selection has priority over highlighting
        if (isSelected) {
            super.setBackground(table.getSelectionBackground());
        }

        setFont(table.getFont());
        setValue(value);

        return this;
    }
}