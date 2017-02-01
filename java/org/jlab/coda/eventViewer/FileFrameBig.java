package org.jlab.coda.eventViewer;


import org.jlab.coda.jevio.BlockHeaderV4;
import org.jlab.coda.jevio.DataType;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;

/**
 * This class implements a window that displays a file's bytes as hex, 32 bit integers.
 * @author timmer
 */
public class FileFrameBig extends JFrame implements PropertyChangeListener {

    /** Table containing event data. */
    private JTable dataTable;

    /** Table's data model. */
    private MyTableModel dataTableModel;

    /** Table's custom renderer. */
    private MyRenderer dataTableRenderer;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Remember comments placed into 7th column of table. */
    private HashMap<Integer,String> comments = new HashMap<Integer,String>();

    private TreeSet<Long> eventSet = new TreeSet<Long>();
    private TreeMap<Long,EvioHeader> eventMap = new TreeMap<Long,EvioHeader>();

    /** Look at file in big endian order by default. */
    private ByteOrder order = ByteOrder.BIG_ENDIAN;

    /** Menu item for switching data endianness. */
    private JMenuItem switchMenuItem;

    /** Buffer of memory mapped file. */
    SimpleMappedMemoryHandler mappedMemoryHandler;

    /** Keep track of the block header currently being viewed so
     *  back and forward arrows know which events to look for.
     */
    private volatile BlockHeader currentBlockHeader;

    /** Was the data scanned for errors? */
    private volatile boolean isScanned;

    /** Data endian when scanned for errors. */
    private ByteOrder scannedDataOrder;


    private JPanel controlPanel;
    private JPanel errorPanel;
    private static int controlPanelWidth = 220;

    // Colors
    private static Color darkGreen = new Color(0,120,0);

    private static Color highlightRed    = new Color(255,220,220);
    private static Color highlightBlue   = new Color(200,230,255);
    private static Color highlightYellow = new Color(240,240,170);
    private static Color highlightPurple = new Color(230,210,255);
    private static Color highlightCyan   = new Color(190,255,255);
    private static Color highlightGreen  = new Color(210,250,210);
    private static Color highlightOrange = new Color(255,200,130);

    // Normal colors
    static Color highlightBlkHdr     = highlightGreen;
    static Color highlightEvntHdr    = highlightCyan;
    static Color highlightValue      = highlightYellow;
    // Error colors
    static Color highlightBlkHdrErr  = highlightRed;
    static Color highlightEvntHdrErr = highlightPurple;
    static Color highlightNodeErr    = highlightOrange;


    // Info back to user
    private JLabel messageLabel;
    private JLabel fileNameLabel;
    private String fileName;
    private JSlider viewPosition;

    // Widgets for choosing search type
    private JRadioButton wordValueButton;
    private JRadioButton wordIndexButton;
    private JRadioButton evioBlockButton;
    private JRadioButton evioEventButton;
    private JRadioButton evioFaultButton;
    private JRadioButton pageScrollButton;
    private ButtonGroup  radioGroup;

    // Widgets & members for fault searching
    private JRadioButton[] faultButtons;
    private ButtonGroup faultRadioGroup;
    private boolean evioFaultsFound;
    private EvioScanner evioFaultScanner;

    // Panels to hold event & block header info
    JPanel eventInfoPanel;
    JPanel blockInfoPanel;

    // Widgets for controlling search & setting value
    private JProgressBar progressBar;
    private JButton searchButtonStart;
    private JButton searchButtonStop;
    private JButton searchButtonNext;
    private JButton searchButtonPrev;
    private JComboBox<String> searchStringBox;
    private String searchString;

    /** Last row to be searched (rows start at 0). */
    private int lastSearchedRow = -1;
    /** Last col to be searched (cols start at 1). */
    private int lastSearchedCol = 0;

    /** Last row to be found in a search (rows start at 0). */
    private int lastFoundRow = -1;
    /** Last col to be found in a search (cols start at 1). */
    private int lastFoundCol = 0;
    /** Memory map (index) containing last item to be found in a search. */
    private int lastFoundMap;

    /** Kill search that's taking too long. */
    private volatile boolean stopSearch;
    /** Tell us when search is done. */
    private volatile boolean searchDone = true;
    /** Thread to handle search in background. */
    private SearchTask task;



    /**
	 * Constructor for a simple viewer for a file's bytes.
	 */
	public FileFrameBig(File file) {
		super(file.getName() + " bytes");
		initializeLookAndFeel();

		// Set the close to call System.exit
		WindowAdapter wa = new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
                FileFrameBig.this.dispose();
			}
		};
		addWindowListener(wa);

		// Set layout
        setLayout(new BorderLayout());

        // Track file's name
        fileName = file.getPath();

        // Add menus
        addMenus();

        // Add buttons
        addControlPanel();

        // Add color key
        addKeyPanel();

        // Add JPanel to view file
        addFileViewPanel(file);

		// Size to the screen
		sizeToScreen(this, .85);

        setVisible(true);
	}



    /** Add the menus to the frame. */
    private void addMenus() {
        JMenuBar menuBar = new JMenuBar();

        JMenu menu = new JMenu(" File ");

        // Endian switching menu item
        ActionListener al_switch = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMessage(" ", null, null);
                comments.clear();
                dataTableModel.clearHighLights();
                switchEndian();

                // Reinstate selection of index (not search find)
                if (wordIndexButton.isSelected() || pageScrollButton.isSelected()) {
                    dataTable.setRowSelectionInterval(lastSearchedRow, lastSearchedRow);
                    dataTable.setColumnSelectionInterval(lastSearchedCol, lastSearchedCol);
                }
            }
        };
        switchMenuItem = new JMenuItem("To little endian");
        switchMenuItem.addActionListener(al_switch);
        menu.add(switchMenuItem);

        // Clear error
        ActionListener al_clearError = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMessage(" ", null, null);
            }
        };
        JMenuItem clearErrorMenuItem = new JMenuItem("Clear error");
        clearErrorMenuItem.addActionListener(al_clearError);
        menu.add(clearErrorMenuItem);

        // Clear comments
        ActionListener al_clearComments = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMessage(" ", null, null);
                comments.clear();
                dataTableModel.fireTableDataChanged();
           }
        };
        JMenuItem clearCommentsMenuItem = new JMenuItem("Clear comments");
        clearCommentsMenuItem.addActionListener(al_clearComments);
        menu.add(clearCommentsMenuItem);

        // Clear highlights
        ActionListener al_clearHighlights = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMessage(" ", null, null);
                eventMap.clear();
                dataTableModel.clearHighLights();
                dataTableModel.fireTableDataChanged();
           }
        };
        JMenuItem clearHighlightsMenuItem = new JMenuItem("Clear highlights");
        clearHighlightsMenuItem.addActionListener(al_clearHighlights);
        menu.add(clearHighlightsMenuItem);

        // Quit menu item
        ActionListener al_exit = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                FileFrameBig.this.dispose();
            }
        };
        JMenuItem exit_item = new JMenuItem("Quit");
        exit_item.addActionListener(al_exit);
        menu.add(exit_item);

        menuBar.add(menu);
        setJMenuBar(menuBar);
    }


    /**
     * Get the endianness of viewed data.
     * @return endianness of viewed data.
     */
    public ByteOrder getOrder() {
        return order;
    }


    /** Switch endianness of viewed data. */
    private void switchEndian() {
        if (order == ByteOrder.BIG_ENDIAN) {
            order  = ByteOrder.LITTLE_ENDIAN;
            switchMenuItem.setText("To big endian");
        }
        else {
            order  = ByteOrder.BIG_ENDIAN;
            switchMenuItem.setText("To little endian");
        }

        mappedMemoryHandler.setByteOrder(order);

        // Write into table
        setTableData();
    }


    /**
     *  Set text of widget providing feedback info.
     * @param msg   text to display
     * @param foreColor color of foreground (text)
     * @param backColor color of background
     */
    private void setMessage(String msg, Color foreColor, Color backColor) {
        messageLabel.setForeground(foreColor);
        messageLabel.setBackground(backColor);
        messageLabel.setText(msg);
    }


    /**
     * Find the index of the top visible row.
     * It may change if the user resized the application window.
     * @param viewport scrollPane's viewport
     * @return index of the top visible row (first row = 0).
     */
    private int findTopVisibleRow(JViewport viewport) {
        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // If the window was resized, the top visible row may have changed
        return (pt.y + dataTable.getRowHeight()/2)/dataTable.getRowHeight();
    }


    /**
     * Find whether the last row of the table is fully visible.
     * @param viewport scrollPane's viewport
     * @return {@code true} if last table row fully visible, else {@code false}.
     */
    private boolean atEnd(JViewport viewport) {
        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // If the window was resized, the top visible row may have changed
        return ( (pt.y + dim.height) >= dataTable.getHeight() );
    }


    /**
     * Jump up or down to the next section of table rows
     * currently outside of the view. It moves so that a full
     * row is exactly at the top.
     * @param down {@code true} if going to end of file,
     *             {@code false} if going to top of file
     * @param pagesPerClick how many visible pages to scroll per button click
     */
    private void scrollToVisible(boolean down, int pagesPerClick) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // Size of view
        Dimension dim = viewport.getExtentSize();

        // Number of rows fully viewed at once
        int numRowsViewed = dim.height / dataTable.getRowHeight();

        // How many pixels beyond an exact # of rows is the view?
        int extraPixels = dim.height % dataTable.getRowHeight();

        // How far we jump at each button press
        int deltaY = pagesPerClick*numRowsViewed*dataTable.getRowHeight();

        Rectangle rec;

        if (down) {
            // If viewport is already at the bottom ...
            if (atEnd(viewport)) {
                // See if there are more data (maps) to follow.
                // If not, do nothing.
                if (!dataTableModel.nextMap()) {
                    return;
                }
                // If so, go to the top of the next map.
//System.out.println("Jump to TOP of next map");
                rec = new Rectangle(pt.x, 0, dim.width, dim.height);
            }
            else {
                rec = new Rectangle(pt.x, pt.y + deltaY, dim.width, dim.height);
            }
        }
        else {
            // First backward jump after hitting end needs to account
            // for the viewing window not fitting an exact integral
            // number or rows. This ensures a row is exactly at the top.
            if (atEnd(viewport)) {
                deltaY -= extraPixels;
            }

            rec = new Rectangle(pt.x,  pt.y - deltaY, dim.width, dim.height);

            if (pt.y < 1) {
                // See if there previous data (maps).
                // If not, do nothing.
                if (!dataTableModel.previousMap()) {
//System.out.println("Table at top, stop scrolling");
                    return;
                }

                // If so, go to the bottom of the previous map
//System.out.println("Jump to BOTTOM of prev map");
                rec = new Rectangle(pt.x, viewport.getViewSize().height - extraPixels,
                                    dim.width, dim.height);
            }
        }

        dataTable.scrollRectToVisible(rec);
        dataTableModel.dataChanged();
    }


    /**
     * Jump up or down to the specified word position in file.
     * It moves so that a full row is exactly at the top.
     * @param position index of data word to view
     * @param color    highlight color is any
     * @param isEvent  are we highlighting an event (and therefore 2 words)?
     */
    private void scrollToIndex(long position, Color color, boolean isEvent) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Switch to correct map
        int[] mapRowCol = dataTableModel.getMapRowCol(position);
        if (mapRowCol == null) {
            JOptionPane.showMessageDialog(this, "Reached end of file", "Return",
                    JOptionPane.INFORMATION_MESSAGE);

            return;
        }
        dataTableModel.setMapIndex(mapRowCol[0]);

        // Where we jump to at each button press
        int finalY = (mapRowCol[1] - 5)*dataTable.getRowHeight();
        Rectangle rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
        lastSearchedRow = mapRowCol[1];
        lastSearchedCol = mapRowCol[2];

        // Set the color
        if (color != null) {
            if (isEvent) {
                dataTableModel.highLightEventHeader(color, lastSearchedRow, lastSearchedCol, false);
            }
            else {
                dataTableRenderer.setHighlightCell(color, lastSearchedRow, lastSearchedCol, false);
            }
        }

        dataTable.scrollRectToVisible(rec);
        dataTableModel.dataChanged();

        // Select cell
        dataTable.setRowSelectionInterval(lastSearchedRow, lastSearchedRow);
        dataTable.setColumnSelectionInterval(lastSearchedCol, lastSearchedCol);
    }



    /**
     * Jump up or down to the row containing a cell with value = findValue.
     * It moves so that a full row is exactly at the top. Provides feedback
     * on progress of search.
     *
     * @param down {@code true}  if going to end of file,
     *             {@code false} if going to top of file
     * @param findValue value of word to find
     * @param getBlock  return the 7 words prior to & also the found value (8 words total).
     *                  Used when finding block headers.
     * @param comments  comments to add to found value's row
     * @param task      object used to update progress of search
     *
     * @return previous 7 ints of found value if getPrevData arg is {@code true}
     */
    private int[] scrollToAndHighlight(boolean down, long findValue, boolean getBlock,
                                      String comments, SearchTask task) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point viewPoint = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Current view's height
        int viewHeight = dim.height;

        // Current view's width
        int viewWidth  = dim.width;

        // Height of each row
        int dataTableRowHeight = dataTable.getRowHeight();

        // Place to store block header data
        int[] blockData = null;

        // Map index starts at 0
        int maxMapIndex = dataTableModel.getMapCount() - 1;
        int startingMapIndex = dataTableModel.getMapIndex();

        long val;
        Rectangle rec = null;
        int row, startingCol=1, finalY, rowY, viewY;

        boolean foundValue = false;
        stopSearch = false;
        searchDone = false;

        out:
        while (true) {
            // Value which depends on the map being used.
            // The last map will most likely be smaller.
            int rowCount = dataTableModel.getRowCount();

            if (down) {
                // Where do we start the search?

                // If we're just beginning ...
                if (lastSearchedRow < 0) {
                    row = 0;
                    startingCol = 1;
                }
                else {
                    // If last val found is in last col
                    if (lastSearchedCol == 5) {
                        // If there is no next row ...
                        if (lastSearchedRow == rowCount - 1) {
                            // This will skip over next while() and go to next map
                            row = rowCount;
                        }
                        else {
                            // Start at end of next row
                            row = lastSearchedRow + 1;
                            startingCol = 1;
                        }
                    }
                    else {
                        // Start at the row of the last value we found
                        row = lastSearchedRow;
                        // Start after the column of the last value we found
                        startingCol = lastSearchedCol + 1;
                    }
                }
//System.out.println("\nStart looking at row = " + row + ", col = " + startingCol);

                while (row < rowCount) {

                    for (int col = startingCol; col < 6; col++) {
                        // Stop search now
                        if (stopSearch) {
                            foundValue = false;
                            break out;
                        }

                        // Check value of column containing file data at given row
                        val = dataTableModel.getLongValueAt(row, col);

                        // Set row & col being searched right now
                        lastSearchedRow = row;
                        lastSearchedCol = col;

                        // If we found a match in a table's element ...
                        if (val == findValue) {

                            // If we're looking for a block header ...
                            if (getBlock) {
                                blockData = dataTableModel.highLightBlockHeader(highlightBlkHdr,
                                                                                row, col, false);

                                // We just found the magic #, but is it part of a block header?
                                // Check other values to see if they make sense as a header,
                                // (7th word is 0, lowest 8 bytes of 6th word is version (4).
                                if (blockData[6] != 0 || (blockData[5] & 0xf) != 4) {
                                    // This is most likely NOT a header, so continue search
                                    foundValue = false;
                                    continue;
                                }
                            }
                            else {
                                dataTableModel.highLightCell(highlightValue, row, col, false);
                            }

                            // Mark it in comments
                            if (comments != null) {
                                dataTableModel.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTableRowHeight;
                            // Current view's top Y position
                            viewY = viewPoint.y;

                            // If found value's row is currently visible ...
                            if (rowY >= viewY && rowY <= viewY + viewHeight) {
//System.out.println("Do NOT change view");
                                dataTableModel.dataChanged();
                                // Select cell of found value
                                dataTable.setRowSelectionInterval(row,row);
                                dataTable.setColumnSelectionInterval(col, col);
                            }
                            else {
                                // Place found row 5 rows below view's top
                                finalY = (row - 5)*dataTableRowHeight;
                                rec = new Rectangle(viewPoint.x, finalY, viewWidth, viewHeight);
                                // Selection will be made AFTER jump to view (at very end)
                            }

                            // Remember where we found value
                            lastFoundRow = row;
                            lastFoundCol = col;
                            lastFoundMap = dataTableModel.getMapIndex();
                            foundValue = true;
                            break out;
                        }
                    }

                    // Go to next row
                    row++;
                    startingCol = 1;

                    // Cut way back on the number of times we do this
                    if (row % 4194304 == 0) {
                        task.setTaskProgress(dataTableModel.getRowProgress(row));
                    }
                }
            }
            else {
                // Where do we start the search?

                // If we're just beginning ...
                if (lastSearchedRow < 0) {
                    // We can't go backwards, so search is done
                    row = -1;
                }
                else {
                    // If last val found is in first col
                    if (lastSearchedCol == 1) {
                        // If there is no previous row ...
                        if (lastSearchedRow == 0) {
                            // We can't go backwards, so search is done
                            row = -1;
                        }
                        else {
                            // Start at end of previous row
                            row = lastSearchedRow - 1;
                            startingCol = 5;
                        }
                    }
                    else {                                                                                  // Page Scrolling

                        // Start at the row of the last value we found
                        row = lastSearchedRow;
                        // Start before the column of the last value we found
                        startingCol = lastSearchedCol - 1;
                    }
                }
//System.out.println("\nStart looking at row = " + row + ", col = " + startingCol);

                while (row >= 0) {

                    // In general, start with right-most col and go left
                    for (int col = startingCol; col > 0; col--) {
                        // Stop search now
                        if (stopSearch) {
                            foundValue = false;
                            break out;
                        }

                        val = dataTableModel.getLongValueAt(row, col);

                        // Set row & col being searched right now
                        lastSearchedRow = row;
                        lastSearchedCol = col;

                        // If we found a match in a table's element ...
                        if (val == findValue)  {

                            // If we're looking for a block header ...
                            if (getBlock) {
                                blockData = dataTableModel.highLightBlockHeader(highlightBlkHdr,
                                                                                row, col, false);

                                // We just found the magic #, but is it part of a block header?
                                // Check other values to see if they make sense as a header,
                                // (7th word is 0, lowest 8 bytes of 6th word is version (4).
                                if (blockData[6] != 0 || (blockData[5] & 0xf) != 4) {
                                    // This is most likely NOT a header, so continue search
                                    foundValue = false;
                                    continue;
                                }
                            }
                            else {
                                dataTableModel.highLightCell(highlightValue, row, col, false);
                            }


                            // Mark it in comments
                            if (comments != null) {
                                dataTableModel.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTableRowHeight;
                            // Current view's top Y position
                            viewY = viewPoint.y;

                            // If found value's row is currently visible ...
                            if (rowY >= viewY && rowY <= viewY + viewHeight) {
//System.out.println("Do NOT change view");
                                dataTableModel.dataChanged();
                                // Select cell of found value
                                dataTable.setRowSelectionInterval(row,row);
                                dataTable.setColumnSelectionInterval(col, col);
                            }
                            else {
                                // Place found row 5 rows above view's bottom
                                int numRowsViewed = viewHeight / dataTableRowHeight;
//System.out.println("rows viewed = " + numRowsViewed + ", final row = " + (row + numRowsViewed - 5));
                                finalY = (row - numRowsViewed + 6)*dataTableRowHeight;
                                rec = new Rectangle(viewPoint.x, finalY, viewWidth, viewHeight);
                                // Selection will be made AFTER jump to view (at very end)
                            }

                            // Remember where we found value
                            lastFoundRow = row;
                            lastFoundCol = col;
                            lastFoundMap = dataTableModel.getMapIndex();
                            foundValue = true;
                            break out;
                        }
                    }

                    // Go to previous row
                    row--;
                    startingCol = 5;

                    if (row % 4194304 == 0) {
                        task.setTaskProgress(dataTableModel.getRowProgress(row));
                    }
                }
            }

            // If we're here, we did NOT find any value

            if (down) {
//System.out.println("Did NOT find val, at bottom = " + atBottom +
//                   ", stopSearch = " + stopSearch);
                // See if there are more data (maps) to follow.
                // If so, go to the next map and search there.
                if (!dataTableModel.nextMap()) {
                    dataTable.clearSelection();
                    break;
                }

                // Start at beginning of next map
                lastSearchedRow = -1;
                lastSearchedCol = 0;
            }
            else {
//System.out.println("Did NOT find val, at top = " + atTop);
                // See if there are more data (maps) before.
                // If so, go to the previous map and search there.
                if (!dataTableModel.previousMap()) {
                    dataTable.clearSelection();
                    break;
                }

                // Start at end of previous map
                lastSearchedRow = dataTableModel.getRowCount() - 1;
                lastSearchedCol = 6;
            }
        }

        // If we did NOT find the value
        if (!foundValue) {
            // Feedback to user in msg
            if (stopSearch) {
                setMessage("Search Stopped", darkGreen, null);
            }
            else {
                setMessage("No value found", darkGreen, null);
            }

            // GO back to previous settings
            lastSearchedCol = lastFoundCol;
            lastSearchedRow = lastFoundRow;

            // Switch mem maps if necessary
            if (dataTableModel.getMapIndex() != lastFoundMap) {
                dataTableModel.setMapIndex(lastFoundMap);
            }

            // Select what we last found
            dataTable.setRowSelectionInterval(lastFoundRow, lastFoundRow);
            dataTable.setColumnSelectionInterval(lastFoundCol, lastFoundCol);

            searchDone = true;
            return blockData;
        }

        // If we found something, make it visible
        if (rec != null) {
            dataTable.scrollRectToVisible(rec);
            dataTableModel.dataChanged();

            // Select cell of found value (after jump so it's visible)
            dataTable.setRowSelectionInterval(lastSearchedRow,lastSearchedRow);
            dataTable.setColumnSelectionInterval(lastSearchedCol, lastSearchedCol);
        }

        searchDone = true;
        return blockData;
    }



    /** A SwingWorker thread to handle a possibly lengthy search for a value. */
    class SearchTask extends SwingWorker<Void, Void> {

        private final boolean down;
        private final boolean findBlock;
        private final long value;
        private String label;
        private int[] blockData;

        public SearchTask(boolean down, boolean findBlock, long value, String label) {
            this.down = down;
            this.findBlock = findBlock;
            this.value = value;
            this.label = label;
        }

        // If looking for a block header, here's the data in it. May be null.
        public int[] getBlockData() {return blockData;}

        // Main search task executed in background thread
        @Override
        public Void doInBackground() {
            enableControlsDuringSearch();
            blockData = scrollToAndHighlight(down, value, findBlock, label, this);
            return null;
        }

        public void setTaskProgress(int p) {
            setProgress(p);
        }

        // Executed in event dispatching thread after doInBackground()
        @Override
        public void done() {
            searchDone = true;
            setProgress(0);
            progressBar.setString("Done");
            progressBar.setValue(0);
            setSliderPosition();

            // If looking for a block, display its info
            if (findBlock) {
                if (blockInfoPanel == null) {
                    addBlockInfoPanel();
                }
                updateBlockInfoPanel(blockData);
            }

            Toolkit.getDefaultToolkit().beep();
            enableSearchControls();
        }
    }


    /** A SwingWorker thread to handle a possibly lengthy search for errors. */
    class ErrorScanTask extends SwingWorker<Void, Void> {

        public ErrorScanTask() {}

        // Main search task executed in background thread
        @Override
        public Void doInBackground() {
            enableControlsDuringSearch();
            addEvioFaultPanel(this);
            return null;
        }

        public void setTaskProgress(int p) { setProgress(p); }

        public boolean stopSearch() {
            return stopSearch;
        }

        // Executed in event dispatching thread
        @Override
        public void done() {
            if (stopSearch()) {
                setMessage("Search stopped", Color.red, null);
                enableControls();
            }
            searchDone = true;
            stopSearch = false;
            setProgress(0);
            progressBar.setString("Done");
            progressBar.setValue(0);
            setSliderPosition();

            Toolkit.getDefaultToolkit().beep();
            enableControls();
        }
    }


    /**
     * Method to search for a value in the background.
     * @param down       going up or down?
     * @param findBlock  are we looking for a block header?
     */
    private void handleWordValueSearch(final boolean down, boolean findBlock) {
        setMessage(" ", null, null);
        long l = 0xc0da0100L;

        // If NOT searching for a block ...
        if (!findBlock) {
            String txt = (String) searchStringBox.getSelectedItem();
//System.out.println("String = \"" + txt + "\"");
            try {
                if (txt.length() > 1 && txt.substring(0, 2).equalsIgnoreCase("0x")) {
                    txt = txt.substring(2);
//System.out.println("new String = \"" + txt + "\"");
                    l = Long.parseLong(txt, 16);
//System.out.println("Search for l = " + l);
                }
                else {
                    l = Long.parseLong(txt, 10);
                }
            }
            catch (NumberFormatException e) {
                setMessage("Search input not a number: " + txt, Color.red, null);
                return;
            }
        }

        String label = null;
        final long ll = l;

        if (l == 0xc0da0100L) {
            label = "Block Header";
        }

        // Use swing worker thread to do time-consuming search in the background
        task = new SearchTask(down, findBlock, ll, label);
        task.addPropertyChangeListener(this);
        task.execute();
    }


    /** Search for evio errors from beginning of file in background. */
    private void handleErrorSearch() {
        stopSearch = false;
        searchDone = false;

        // Use swing worker thread to do time-consuming error scan in background
        ErrorScanTask errorTask = new ErrorScanTask();
        errorTask.addPropertyChangeListener(this);
        errorTask.execute();
    }


    /**
     * Search for first word of next evio event (bank).
     * Will search from selected row & col. If no selection,
     * search from beginning row & col (this may not be good
     * if not in first memory map).
     *
     * @return event header info of next event found.
     */
    private EvioHeader handleEventSearchForward() {
        setMessage(" ", null, null);

        int[] mapRowCol;
        EvioHeader node = null;
        long val, wordIndex;

        // Where are we now?
        int row = dataTable.getSelectedRow();
        int col = dataTable.getSelectedColumn();

        // If in first(index)/last(comment) column ...
        if (!dataTableModel.isDataColumn(col)) {

            // If starting past the first row & col, error.
            // Must start at an event.
            if (row > 0 || col >= dataTableModel.getColumnCount() - 1) {
                JOptionPane.showMessageDialog(this, "Start at 0 or beginning of known event", "Return",
                                              JOptionPane.INFORMATION_MESSAGE);

                return null;
            }
            // If no selection made yet (before all rows)
            else {
                row = 0;
                col = 1;

                // Transform row & col into absolute index
                wordIndex = dataTableModel.getWordIndexOf(row, col);

                //----------------------------------------------------------------------
                // Are we right at the beginning of a block header? If so, move past it.
                //----------------------------------------------------------------------
                mapRowCol = dataTableModel.getMapRowCol(wordIndex + 7);

                // First make sure we getting our data from the
                // correct (perhaps next) memory mapped buffer.
                dataTableModel.setMapIndex(mapRowCol[0]);

                // Look forward 7 words to possible magic #
                val = dataTableModel.getLongValueAt(mapRowCol[1], mapRowCol[2]);

                // If it is indeed a magic number, there is a block header at very beginning
                if (val == 0xc0da0100L) {
                    searchDone = true;
                    // Hop over block header to next int which should be start of an event
                    wordIndex += 8;
                    // Put new cell in view & select
                    scrollToIndex(wordIndex, highlightEvntHdr, true);
                    node = new EvioHeader((int) (dataTableModel.getLongValueAt(wordIndex)),
                                          (int) (dataTableModel.getLongValueAt(wordIndex + 1)),
                                          wordIndex);
                    return node;
                }
                //---------------------------------------------------------------
            }
        }

        //-----------------------------------------------
        // Deal with the event where we at are right now
        //-----------------------------------------------

        // Highlight selected event header len & next word
        dataTableModel.highLightEventHeader(highlightEvntHdr, row, col, false);

        // Transform row & col into absolute index
        wordIndex = dataTableModel.getWordIndexOf(row,col);

        node = new EvioHeader((int) (dataTableModel.getLongValueAt(wordIndex)),
                              (int) (dataTableModel.getLongValueAt(wordIndex + 1)),
                              wordIndex);

        // Warn user if the selected word most likely is NOT the first word of a bank
        if (!node.probablyIsBank()) {
            int n = JOptionPane.showOptionDialog(this,
                "\"Probably not a bank, continue?", null,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, null, null);

            if (n != JOptionPane.OK_OPTION) {
                return null;
            }
        }

        eventMap.put(wordIndex, node);
        eventSet.add(wordIndex);

        //-----------------------------------------------
        // Move on to the next event
        //-----------------------------------------------

        // Take value of selected cell and treat that as the
        // beginning of an evio event - its bank length.
        // Hop this many entries in the table
        long eventWordLen = dataTableModel.getLongValueAt(row,col) + 1;

        // Destination cell's index
        wordIndex += eventWordLen;

        //---------------------------------------------------------------
        // Are we at the beginning of a block header? If so, move past it.
        //---------------------------------------------------------------
        mapRowCol = dataTableModel.getMapRowCol(wordIndex + 7);
        if (mapRowCol == null) {
            searchDone = true;
            JOptionPane.showMessageDialog(this, "No more events", "Return",
                                          JOptionPane.INFORMATION_MESSAGE);

            return null;
        }

        // First make sure we getting our data from the
        // correct (perhaps next) memory mapped buffer.
        dataTableModel.setMapIndex(mapRowCol[0]);

        val = dataTableModel.getLongValueAt(mapRowCol[1], mapRowCol[2]);
//System.out.println("  ***  at possible block index, map = " + mapRowCol[0] + ", r = " + mapRowCol[1] +
//        ", c = " + mapRowCol[2] + ", val = " + val + ", in hex 0x" + Long.toHexString(val));

        // If we're at the front of block header, hop past it
        if (val == 0xc0da0100L) {
            wordIndex += 8;
        }
        //---------------------------------------------------------------

        // Put new cell in view & select
        scrollToIndex(wordIndex, highlightEvntHdr, true);
        searchDone = true;
        node = new EvioHeader((int)(dataTableModel.getLongValueAt(wordIndex)),
                              (int)(dataTableModel.getLongValueAt(wordIndex + 1)),
                              wordIndex);
        eventMap.put(wordIndex, node);
        eventSet.add(wordIndex);
        return node;
    }


    /**
     * Go back to the last event previously found.
     *
     * @return event header info of last event found.
     */
    private EvioHeader handleEventSearchBack() {
        setMessage(" ", null, null);

        EvioHeader node;
        long wordIndex;

        if (eventMap.size() == 0) return null;

        // Where are we now?
        int row = dataTable.getSelectedRow();
        int col = dataTable.getSelectedColumn();

        // If in first(index)/last(comment) column, pick nearest data col ...
        if (!dataTableModel.isDataColumn(col)) {
            if      (col == 0) col = 1;
            else if (col == 6) col = 5;
        }

        // Transform row & col into absolute index
        wordIndex = dataTableModel.getWordIndexOf(row,col);

        // Get map with keys all less than ours
        SortedMap<Long,EvioHeader> map = eventMap.headMap(wordIndex);
        if (map.size() < 1) {
            return null;
        }

        // Go to the event immediately prior
        Long key = map.lastKey();
        if (key == null) {
            return null;
        }
        node = map.get(key);
        scrollToIndex(key, highlightEvntHdr, true);
        return node;
    }


    /**
     * Go to a given word index in file and put into view.
     */
    private void handleWordIndexSearch() {
        setMessage(" ", null,null);

        // Interpret search coordinate box entry
        long l = 1;
        String txt = (String) searchStringBox.getSelectedItem();
        try {
            if (txt.length() > 1 && txt.substring(0, 2).equalsIgnoreCase("0x")) {
                txt = txt.substring(2);
                l = Long.parseLong(txt, 16);
            }
            else {
                l = Long.parseLong(txt, 10);
            }
        }
        catch (NumberFormatException e) {
            setMessage("Search input not a number: " + txt, Color.red, null);
            return;
        }

        if (l < 1) l = 1L;

        // Go to it
        scrollToIndex(l-1, null, false);
    }


    /** Handler invoked when task's progress property changes. */
    public void propertyChange(PropertyChangeEvent evt) {
        if (searchDone) {
            return;
        }

        if ("progress".equalsIgnoreCase(evt.getPropertyName())) {
            progressBar.setValue((Integer) evt.getNewValue());
        }
    }

    /** Enable/disable control buttons in preparation for doing an error scan. */
    private void setControlsForErrorScan() {
        searchButtonStart.setText("Start Scan");
        searchButtonStop.setText("Stop");

        enableControls();
        searchStringBox.setEnabled(false);
    }

    /** Enable control buttons to start a word value search. */
    private void enableSearchControls() {
        searchButtonStop.setText("Stop");

        enableControls();
        searchButtonStart.setEnabled(false);
    }

    /** Enable control buttons DURING a word value search. */
    private void enableControlsDuringSearch() {
        searchButtonStart.setEnabled(false);
        searchButtonStop. setEnabled(true);
        searchStringBox . setEnabled(false);
        searchButtonNext. setEnabled(false);
        searchButtonPrev. setEnabled(false);

        wordValueButton.  setEnabled(false);
        wordIndexButton.  setEnabled(false);
        pageScrollButton. setEnabled(false);
        evioBlockButton.  setEnabled(false);
        evioEventButton.  setEnabled(false);
        evioFaultButton.  setEnabled(false);
    }

    /** Enable control buttons in preparation for jumping to file positions. */
    private void enableControlsForPositionJump() {
        enableControls();
        searchButtonStart.setEnabled(false);
        searchButtonStop. setEnabled(false);
        searchButtonPrev. setEnabled(false);
    }

    /** Enable control buttons in preparation for jumping to file positions. */
    private void enableControlsForEventJump() {
        enableControls();
        searchButtonStart.setEnabled(false);
        searchButtonStop. setEnabled(false);
    }

    /** Enable control buttons in preparation for scrolling. */
    private void enableControlsForScrolling() {
        searchButtonStart.setText("<<");
        searchButtonStop.setText(">>");

        enableControls();
    }

    /** Enable control buttons for block jumping. */
    private void enableControlsForBlock() {
        searchButtonStop.setText("Stop");

        enableControls();
        searchButtonStart.setEnabled(false);
        searchStringBox.setSelectedIndex(0);
        searchStringBox.setEditable(false);
    }

    /** Enable all control buttons. */
    private void enableControls() {
        // Search Box Buttons
        searchButtonStart.setEnabled(true);
        searchButtonStop. setEnabled(true);
        searchStringBox . setEnabled(true);
        searchButtonNext. setEnabled(true);
        searchButtonPrev. setEnabled(true);

        // Radio Buttons
        wordValueButton.  setEnabled(true);
        wordIndexButton.  setEnabled(true);
        pageScrollButton. setEnabled(true);
        evioBlockButton.  setEnabled(true);
        evioEventButton.  setEnabled(true);
        evioFaultButton.  setEnabled(true);
    }


    /**
     * Set the position of the slider which represents the placement
     * of the currently visible data in relation to its position in
     * the file being viewed.
     */
    private void setSliderPosition() {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point viewPoint = viewport.getViewPosition();

        // Current view's height
        int viewHeight = viewport.getExtentSize().height;

        // # of row in middle of current view in current map
        long midRow = (2*(viewPoint.y) + viewHeight)/(2*dataTable.getRowHeight());

        // Add all rows of previous maps (if any).
        int currentMapIndex = dataTableModel.getMapIndex();
        if (currentMapIndex > 0) {
            midRow += currentMapIndex*dataTableModel.getMaxRowsPerMap();
        }

        viewPosition.setValue((int)(1000L*midRow/dataTableModel.getTotalRows()));
    }


    /**
     * Update the panel showing an event's header info.
     * @param node object containing header info.
     */
    private void updateEventInfoPanel(EvioHeader node) {
        if (node == null || eventInfoPanel == null) return;

        // Length
        ((JLabel)(eventInfoPanel.getComponent(1))).setText("" + ((long)node.len & 0xffffffffL));
        // Tag
        ((JLabel)(eventInfoPanel.getComponent(3))).setText("0x" + Integer.toHexString(node.tag));
        // Num
        ((JLabel)(eventInfoPanel.getComponent(5))).setText("" + node.num);
        // Padding
        ((JLabel)(eventInfoPanel.getComponent(11))).setText("" + node.pad);
        // Bank type
        ((JLabel)(eventInfoPanel.getComponent(13))).setText("" + node.bankType);

        // Catch bad Type
        String type;
        DataType nodeType = node.getTypeObj();
        if (nodeType == null) {
            type = "bad (" + node.type + ")";
        }
        else {
            type = "" + nodeType;
        }

        ((JLabel)(eventInfoPanel.getComponent(7))).setText("" + type);

        // Catch bad Data Type
        String dtype;
        DataType dataType = node.getDataTypeObj();
        if (dataType == null) {
            dtype = "bad (" + node.dataType + ")";
        }
        else {
            dtype = "" + dataType;
        }

        ((JLabel)(eventInfoPanel.getComponent(9))).setText("" + dtype);

    }


    /** Remove the event information panel from gui. */
    private void removeEventInfoPanel() {
        if (eventInfoPanel == null) {
            return;
        }

        Component[] comps = controlPanel.getComponents();
        for (int i=0; i < comps.length; i++) {
            if (comps[i] == eventInfoPanel) {
                 // Need to remove both the event info panel
                controlPanel.remove(i);
                // and the vertical strut before it
                controlPanel.remove(i-1);
            }
        }

        controlPanel.revalidate();
        controlPanel.repaint();
        eventInfoPanel = null;
    }


    /** Add the event information panel to gui. */
    private void addEventInfoPanel() {
        if (eventInfoPanel != null) {
            return;
        }

        Border blkLineBorder = BorderFactory.createLineBorder(Color.black);
        Border lineBorder = BorderFactory.createLineBorder(Color.blue);
        Border compound = BorderFactory.createCompoundBorder(lineBorder, null);
        compound = BorderFactory.createTitledBorder(
                          compound, "Event Info",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);


        eventInfoPanel = new JPanel();
        eventInfoPanel.setLayout(new GridLayout(7,2,5,2));
        eventInfoPanel.setBorder(compound);

        JLabel[] labels = new JLabel[14];
        labels[0]  = new JLabel("Length  ");
        labels[2]  = new JLabel("Tag  ");
        labels[4]  = new JLabel("Num  ");
        labels[6]  = new JLabel("Type  ");
        labels[8]  = new JLabel("Data type  ");
        labels[10] = new JLabel("Padding  ");
        labels[12] = new JLabel("Bank Type  ");

        labels[1]  = new JLabel("");
        labels[3]  = new JLabel("");
        labels[5]  = new JLabel("");
        labels[7]  = new JLabel("");
        labels[9]  = new JLabel("");
        labels[11] = new JLabel("");
        labels[13] = new JLabel("");

        for (int i=0; i < 14; i++) {
            labels[i].setOpaque(true);
            if (i%2 == 1) {
                labels[i].setBackground(Color.white);
                labels[i].setForeground(darkGreen);
                labels[i].setBorder(blkLineBorder);
            }
            else {
                labels[i].setHorizontalAlignment(SwingConstants.RIGHT);
            }

            eventInfoPanel.add(labels[i]);
        }

        // Add to control panel
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(eventInfoPanel);
        controlPanel.revalidate();
        controlPanel.repaint();
    }


    /**
     * Method to update panel containing block header info.
     * @param blockData array containing block header info.
     */
    private void updateBlockInfoPanel(int[] blockData) {
        if (blockData == null ||blockInfoPanel == null ) return;

        ((JLabel)(blockInfoPanel.getComponent(1))).setText("" + blockData[0]);
        ((JLabel)(blockInfoPanel.getComponent(3))).setText("" + blockData[2]);
        ((JLabel)(blockInfoPanel.getComponent(5))).setText("" + blockData[1]);
        ((JLabel)(blockInfoPanel.getComponent(7))).setText("" + blockData[3]);
        ((JLabel)(blockInfoPanel.getComponent(9))).setText("" + (blockData[5] & 0xff));
        ((JLabel)(blockInfoPanel.getComponent(11))).setText("" + BlockHeaderV4.hasDictionary(blockData[5]));
        ((JLabel)(blockInfoPanel.getComponent(13))).setText(""+ BlockHeaderV4.isLastBlock(blockData[5]));
    }


    /**
     * Method to update panel containing block header info.
     * @param header object containing block header info.
     */
    private void updateBlockInfoPanel(BlockHeader header) {
        if (header == null || blockInfoPanel == null) return;

        ((JLabel)(blockInfoPanel.getComponent(1))).setText(""  + header.len);
        ((JLabel)(blockInfoPanel.getComponent(3))).setText(""  + header.headerLen);
        ((JLabel)(blockInfoPanel.getComponent(5))).setText(""  + header.place);
        ((JLabel)(blockInfoPanel.getComponent(7))).setText(""  + header.count);
        ((JLabel)(blockInfoPanel.getComponent(9))).setText(""  + header.version);
        ((JLabel)(blockInfoPanel.getComponent(11))).setText("" + header.hasDictionary);
        ((JLabel)(blockInfoPanel.getComponent(13))).setText("" + header.isLast);
    }


    /**  Method to remove panel containing block header info from gui. */
    private void removeBlockInfoPanel() {
        if (blockInfoPanel == null) {
            return;
        }

        Component[] comps = controlPanel.getComponents();
        for (int i=0; i < comps.length; i++) {
            if (comps[i] == blockInfoPanel) {
                 // Need to remove both the block info panel
                controlPanel.remove(i);
                // and the vertical strut before it
                controlPanel.remove(i-1);
            }
        }

        controlPanel.revalidate();
        controlPanel.repaint();
        blockInfoPanel = null;
    }


    /**  Method to add panel containing block header info to gui. */
    private void addBlockInfoPanel() {
        if (blockInfoPanel != null) {
            return;
        }

        Border blkLineBorder = BorderFactory.createLineBorder(Color.black);
        Border lineBorder = BorderFactory.createLineBorder(Color.blue);
        Border compound = BorderFactory.createCompoundBorder(lineBorder, null);
        compound = BorderFactory.createTitledBorder(
                          compound, "Block Info",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);


        blockInfoPanel = new JPanel();
        blockInfoPanel.setLayout(new GridLayout(7,2,5,2));
        blockInfoPanel.setBorder(compound);

        JLabel[] labels = new JLabel[14];
        labels[0]  = new JLabel("Total words  ");
        labels[2]  = new JLabel("Header words  ");
        labels[4]  = new JLabel("Id number  ");
        labels[6]  = new JLabel("Event count  ");
        labels[8]  = new JLabel("Version  ");
        labels[10] = new JLabel("Has dictionary  ");
        labels[12] = new JLabel("Is last  ");

        labels[1]  = new JLabel("");
        labels[3]  = new JLabel("");
        labels[5]  = new JLabel("");
        labels[7]  = new JLabel("");
        labels[9]  = new JLabel("");
        labels[11] = new JLabel("");
        labels[13] = new JLabel("");

        for (int i=0; i < 14; i++) {
            labels[i].setOpaque(true);
            if (i%2 == 1) {
                labels[i].setBackground(Color.white);
                labels[i].setForeground(darkGreen);
                labels[i].setBorder(blkLineBorder);
            }
            else {
                labels[i].setHorizontalAlignment(SwingConstants.RIGHT);
            }

            blockInfoPanel.add(labels[i]);
        }

        // Add to control panel
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(blockInfoPanel);
        controlPanel.revalidate();
        controlPanel.repaint();
    }


    /**  Method to remove panel containing error info from gui. */
    private void removeEvioFaultPanel() {
        if (errorPanel == null) {
            return;
        }

        Component[] comps = controlPanel.getComponents();
        for (int i=0; i < comps.length; i++) {
            if (comps[i] == errorPanel) {
                 // Need to remove both the block info panel
                controlPanel.remove(i);
                // and the vertical strut before it
                controlPanel.remove(i-1);
            }
        }

        controlPanel.revalidate();
        controlPanel.repaint();
        errorPanel = null;
    }


    /** Add a panel showing evio faults in the data to gui. */
    private void addEvioFaultPanel(ErrorScanTask errorTask) {

        // If no scan for faults has been done, do it now
        if (evioFaultScanner == null) {
            evioFaultScanner = new EvioScanner(this,
                                               dataTableModel,
                                               dataTableRenderer,
                                               errorTask);
        }

        if (isScanned) {
            removeEvioFaultPanel();
        }

        scannedDataOrder = order;
        isScanned = true;

        try {
            evioFaultScanner.scanFileForErrors();
        }
        catch (Exception e) {
            return;
        }

        if (!evioFaultScanner.hasError()) {
            setMessage("No errors found", darkGreen,null);
            return;
        }
        setMessage("Errors found", Color.red, null);

        faultRadioGroup = new ButtonGroup();

        Border lineBorder = BorderFactory.createLineBorder(Color.blue);
        Border compound = BorderFactory.createCompoundBorder(lineBorder, null);
        compound = BorderFactory.createTitledBorder(
                          compound, "Evio Errors",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);

        errorPanel = new JPanel();
        errorPanel.setBorder(compound);
        errorPanel.setLayout(new BorderLayout(0, 10));
        errorPanel.setMinimumSize(new Dimension(controlPanelWidth, 180));
        errorPanel.setPreferredSize(new Dimension(controlPanelWidth, 180));


        // Blocks & events containing evio errors are placed in a list.
        // Clicking on an item of that list will display either the info
        // of that block or event.
        MouseListener ml = new MouseListener() {
            public void mousePressed(MouseEvent e)  {}
            public void mouseReleased(MouseEvent e) {}
            public void mouseEntered(MouseEvent e)  {}
            public void mouseExited(MouseEvent e)   {}

            public void mouseClicked(MouseEvent e) {
                // Parse the action command
                ButtonModel sel = faultRadioGroup.getSelection();
                if (sel == null) return;
                String actionCmd = sel.getActionCommand();
                String[] strings = actionCmd.split(":");
                int index = Integer.parseInt(strings[1]);
                boolean isBlock = strings[0].equals("B");

                currentBlockHeader = evioFaultScanner.getBlockErrorNodes().get(index);
                // Want forward button to be 0 when index incremented
                currentBlockHeader.currentEventIndex = -1;
                setMessage(currentBlockHeader.error, Color.red, null);
                scrollToIndex(currentBlockHeader.filePos / 4, null, false);
                setSliderPosition();
                removeEventInfoPanel();
                addBlockInfoPanel();
                updateBlockInfoPanel(currentBlockHeader);
            }

        };

        // Lists of blocks & events containing evio errors
        ArrayList<BlockHeader> blocks = evioFaultScanner.getBlockErrorNodes();

        int blockCount = blocks.size();

        // Create a radio button for each fault/error
        faultButtons = new JRadioButton[blockCount];

        // Model for JList below
        DefaultListModel<JRadioButton> model = new DefaultListModel<JRadioButton>();

        for (int i=0; i < blockCount; i++) {
            BlockHeader blockHeader = blocks.get(i);
            // Reported number is word position which starts at 1
            faultButtons[i] = new JRadioButton("Block " + blockHeader.place);
            faultButtons[i].setActionCommand("B:" + i);
            faultRadioGroup.add(faultButtons[i]);
            // Put button in list
            model.addElement(faultButtons[i]);
        }

        class PanelRenderer implements ListCellRenderer {
            public Component getListCellRendererComponent(JList list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JRadioButton renderer = (JRadioButton) value;
                renderer.setSelected(isSelected);
                if (isSelected) {
                    renderer.doClick();
                }
                return renderer;
            }
        }

        // List of JRadioButtons
        JList list = new JList<JRadioButton>(model);
        list.setCellRenderer(new PanelRenderer());
        list.addMouseListener(ml);

        // Put list in scroll pane
        JScrollPane jsp = new JScrollPane(list);

        // Put scroll pane in panel
        errorPanel.add(jsp);

        // Add to control panel
        controlPanel.add(Box.createVerticalStrut(10), 6);
        controlPanel.add(errorPanel, 7);
        controlPanel.revalidate();
        controlPanel.repaint();
    }


    private void scanBlockErrorEventsBack() {
        // Get BlockHeader set in mouseClick routine above
        if (currentBlockHeader == null) return;
        if (--currentBlockHeader.currentEventIndex < 0)  currentBlockHeader.currentEventIndex = 0;
        EvioHeader header = currentBlockHeader.events.get(currentBlockHeader.currentEventIndex);

        if (header == null) {
            //System.out.println("No event going backward");
            return;
        }

        if (header.error != null) {
            setMessage(header.error, Color.red, null);
            scrollToIndex(header.getFilePosition() / 4, highlightEvntHdrErr, true);
        }
        else {
            setMessage("", null, null);
            scrollToIndex(header.getFilePosition() / 4, highlightEvntHdr, true);
        }
        setSliderPosition();
        addEventInfoPanel();
        updateEventInfoPanel(header);
    }


    private void scanBlockErrorEventsForward() {
        // Get BlockHeader set in mouseClick routine above
        if (currentBlockHeader == null) return;
        int maxIndex = currentBlockHeader.events.size() - 1;
        if (maxIndex < 0) {
            return;
        }

        if (++currentBlockHeader.currentEventIndex > maxIndex) {
            currentBlockHeader.currentEventIndex = maxIndex;
        }
        EvioHeader header = currentBlockHeader.events.get(currentBlockHeader.currentEventIndex);

        if (header == null) {
            //System.out.println("No event going forward");
            return;
        }

        if (header.error != null) {
            setMessage(header.error, Color.red, null);
            scrollToIndex(header.getFilePosition() / 4, highlightEvntHdrErr, true);
        }
        else {
            setMessage("", null, null);
            scrollToIndex(header.getFilePosition() / 4, highlightEvntHdr, true);
        }
        setSliderPosition();
        addEventInfoPanel();
        updateEventInfoPanel(header);
    }


    /** Add a panel showing highlight color key. */
    private void addKeyPanel() {

        // Put browsing buttons into panel
        JPanel keyPanel = new JPanel();
        keyPanel.setLayout(new BorderLayout());
        Border border = new CompoundBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                new EmptyBorder(5, 5, 5, 5));
        keyPanel.setBorder(border);

        Border blkLineBorder = BorderFactory.createLineBorder(Color.gray);
        Border lineBorder = BorderFactory.createLineBorder(Color.blue);
        Border compound = BorderFactory.createCompoundBorder(lineBorder, null);
        compound = BorderFactory.createTitledBorder(
                          compound, "Color Key",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);


        JPanel keyInfoPanel = new JPanel();
        keyInfoPanel.setLayout(new GridLayout(7,1,5,2));
        keyInfoPanel.setBorder(compound);


        JLabel[] labels = new JLabel[7];
        labels[0]  = new JLabel("Block normal");
        labels[1]  = new JLabel("Event normal");
        labels[2]  = new JLabel("Block with error");
        labels[3]  = new JLabel("Event with error");
        labels[4]  = new JLabel("Evio struct error");
        labels[5]  = new JLabel("Word value");
        labels[6]  = new JLabel("Current selection");

        labels[0].setBackground(highlightBlkHdr);
        labels[1].setBackground(highlightEvntHdr);
        labels[2].setBackground(highlightBlkHdrErr);
        labels[3].setBackground(highlightEvntHdrErr);
        labels[4].setBackground(highlightNodeErr);
        labels[5].setBackground(highlightValue);
        labels[6].setBackground(Color.yellow);

        for (int i=0; i < 7; i++) {
            labels[i].setOpaque(true);
            labels[i].setBorder(blkLineBorder);
            keyInfoPanel.add(labels[i]);
        }

        // Add to control panel
        keyPanel.add(keyInfoPanel, BorderLayout.NORTH);

        this.add(keyPanel, BorderLayout.EAST);
    }


    /** Add a panel controlling viewed data to this frame. */
    private void addControlPanel() {
        JPanel containControlPanel = new JPanel(new BorderLayout());

        // Put browsing buttons into panel
        controlPanel = new JPanel();
        BoxLayout boxLayout = new BoxLayout(controlPanel, BoxLayout.Y_AXIS);
        controlPanel.setLayout(boxLayout);
        Border border = new CompoundBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                new EmptyBorder(5,5,5,5));
        controlPanel.setBorder(border);

        //-------------------------------
        // Put radio boxes in this panel
        //-------------------------------

        Border lineBorder = BorderFactory.createLineBorder(Color.blue);
        // Add to compound border
        Border compound = BorderFactory.createCompoundBorder(lineBorder, null);

        compound = BorderFactory.createTitledBorder(
                          compound, "Search By",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);

        JPanel radioButtonPanel = new JPanel();
        radioButtonPanel.setLayout(new GridLayout(6, 1, 0, 2));
        // The next 2 call determine width of containControlPanel
        radioButtonPanel.setMinimumSize(new Dimension(controlPanelWidth, 170));
        radioButtonPanel.setPreferredSize(new Dimension(controlPanelWidth, 170));
        radioButtonPanel.setBorder(compound);

        // Create the radio buttons
        wordValueButton = new JRadioButton("Word Value");
        wordValueButton.setMnemonic(KeyEvent.VK_V);
        wordValueButton.setActionCommand("1");
        wordValueButton.setSelected(true);

        wordIndexButton = new JRadioButton("Word Position");
        wordIndexButton.setMnemonic(KeyEvent.VK_I);
        wordIndexButton.setActionCommand("2");

        pageScrollButton = new JRadioButton("Page Scrolling");
        pageScrollButton.setMnemonic(KeyEvent.VK_F);
        pageScrollButton.setActionCommand("3");

        evioBlockButton = new JRadioButton("Evio Block");
        evioBlockButton.setMnemonic(KeyEvent.VK_B);
        evioBlockButton.setActionCommand("4");

        evioEventButton = new JRadioButton("Evio Event");
        evioEventButton.setMnemonic(KeyEvent.VK_E);
        evioEventButton.setActionCommand("5");

        evioFaultButton = new JRadioButton("Evio Fault");
        evioFaultButton.setMnemonic(KeyEvent.VK_F);
        evioFaultButton.setActionCommand("6");

        // Group the radio buttons
        radioGroup = new ButtonGroup();
        radioGroup.add(wordValueButton);
        radioGroup.add(wordIndexButton);
        radioGroup.add(pageScrollButton);
        radioGroup.add(evioBlockButton);
        radioGroup.add(evioEventButton);
        radioGroup.add(evioFaultButton);

        // Add radio buttons to panel
        radioButtonPanel.add(wordValueButton);
        radioButtonPanel.add(wordIndexButton);
        radioButtonPanel.add(pageScrollButton);
        radioButtonPanel.add(evioBlockButton);
        radioButtonPanel.add(evioEventButton);
        radioButtonPanel.add(evioFaultButton);

        controlPanel.add(Box.createVerticalStrut(5));
        controlPanel.add(radioButtonPanel);

        // Register a listener for the radio buttons
        ActionListener al_radio = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int cmd = Integer.parseInt(radioGroup.getSelection().getActionCommand());

                switch (cmd) {
                    case 1:
                        // Word Value
                        enableSearchControls();
                        searchButtonStart.setEnabled(false);
                        searchStringBox.setEditable(true);
                        removeEventInfoPanel();
                        removeBlockInfoPanel();
                        break;
                    case 2:
                        // Word Index
                        enableControlsForPositionJump();
                        searchStringBox.setEditable(true);
                        removeEventInfoPanel();
                        removeBlockInfoPanel();
                        break;
                    case 3:
                        // Page Scrolling
                        enableControlsForScrolling();
                        searchStringBox.setEditable(false);
                        removeEventInfoPanel();
                        removeBlockInfoPanel();
                        break;
                    case 4:
                        // Evio Block
                        enableControlsForBlock();
                        // Only search for 0xc0da0100
                        removeEventInfoPanel();
                        break;
                    case 5:
                        // Evio Event
                        enableControlsForEventJump();
                        searchStringBox.setSelectedIndex(0);
                        searchStringBox.setEditable(false);
                        removeBlockInfoPanel();
                        break;
                    case 6:
                        // Evio Fault
                        setControlsForErrorScan();
                        break;
                    default:
                }
            }
        };

        wordValueButton.addActionListener(al_radio);
        wordIndexButton.addActionListener(al_radio);
        evioBlockButton.addActionListener(al_radio);
        evioEventButton.addActionListener(al_radio);
        evioFaultButton.addActionListener(al_radio);
        pageScrollButton.addActionListener(al_radio);

        //----------------------------------
        // Input box
        //----------------------------------

        // Use this to filter input for combo box
        ActionListener al_box = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox jcb = (JComboBox) e.getSource();
                String listItem;
                String selectedItem = (String) jcb.getSelectedItem();
                int numItems = jcb.getItemCount();
                boolean addNewItem = true;

                // If nothing has changed, do nothing
                if (selectedItem == null ||
                    selectedItem.equals("") ||
                    selectedItem.equals(searchString)) {
                    return;
                }

                searchString = selectedItem;
//                dataTableModel.clearHighLights();

                if (numItems == 0) {
                    addNewItem = true;
                }
                else {
                    for (int i = 0; i < numItems; i++) {
                        listItem = (String) jcb.getItemAt(i);
                        if (listItem.equals(selectedItem)) {
                            addNewItem = false;
                            break;
                        }
                    }
                }

                if (addNewItem) {
                    jcb.addItem(selectedItem);
                }
            }
        };

        searchStringBox = new JComboBox<String>(new String[] {"0xc0da0100"});
        searchStringBox.setEditable(true);
        searchStringBox.addActionListener(al_box);

        Border compound2 = BorderFactory.createCompoundBorder(lineBorder, null);

        compound2 = BorderFactory.createTitledBorder(
                          compound2, "Search For",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);

        searchStringBox.setBorder(compound2);

        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(searchStringBox);

        //----------------------------------
        // Put search buttons in this panel
        //----------------------------------

        Border compound3 = BorderFactory.createCompoundBorder(lineBorder, null);

        compound3 = BorderFactory.createTitledBorder(
                          compound3, "Search Controls",
                          TitledBorder.CENTER,
                          TitledBorder.TOP, null, Color.blue);

        JPanel searchPanel = new JPanel();
        searchPanel.setBorder(compound3);
        BoxLayout boxLayout2 = new BoxLayout(searchPanel, BoxLayout.Y_AXIS);
        searchPanel.setLayout(boxLayout2);

        JPanel searchButtonPanel = new JPanel();
        searchButtonPanel.setLayout(new GridLayout(1, 2, 3, 0));

        searchButtonPrev = new JButton(" < ");
        ActionListener al_search = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopSearch = false;
                progressBar.setValue(0);
                progressBar.setString(null);

                int cmd = Integer.parseInt(radioGroup.getSelection().getActionCommand());

                switch (cmd) {
                    case 1:
                        // Word Value
                        handleWordValueSearch(false, false);
                        break;
                    case 2:
                        // Word Index
                        handleWordIndexSearch();
                        setSliderPosition();
                        break;
                    case 3:
                        // Page Scrolling
                        setMessage(" ", null, null);
                        scrollToVisible(false, 1);
                        setSliderPosition();
                        break;
                    case 4:
                        // Evio Block
                        handleWordValueSearch(false, true);
                        break;
                    case 5:
                        // Evio Event
                        EvioHeader node = handleEventSearchBack();
                        if (node == null) {
                            // Error
                            break;
                        }
                        addEventInfoPanel();
                        updateEventInfoPanel(node);
                        setSliderPosition();
                        break;
                    case 6:
                        // Evio Fault
                        scanBlockErrorEventsBack();
                        break;
                    default:
                }
            }
        };
        searchButtonPrev.addActionListener(al_search);
        searchButtonPanel.add(searchButtonPrev);

        searchButtonNext = new JButton(" > ");
        ActionListener al_searchNext = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopSearch = false;
                progressBar.setValue(0);
                progressBar.setString(null);

                int cmd = Integer.parseInt(radioGroup.getSelection().getActionCommand());

                switch (cmd) {
                    case 1:
                        // Word Value
                        handleWordValueSearch(true, false);
                        break;
                    case 2:
                        // Word Index
                        handleWordIndexSearch();
                        setSliderPosition();
                        break;
                    case 3:
                        // Page Scrolling
                        setMessage(" ", null, null);
                        scrollToVisible(true, 1);
                        setSliderPosition();
                        break;
                    case 4:
                        // Evio Block
                        handleWordValueSearch(true, true);
                        break;
                    case 5:
                        // Evio Event
                        EvioHeader node = handleEventSearchForward();
                        if (node == null) {
                            // Error
                            break;
                        }

                        addEventInfoPanel();
                        updateEventInfoPanel(node);
                        setSliderPosition();
                        break;
                    case 6:
                        // Evio Fault
                        scanBlockErrorEventsForward();
                        break;
                    default:
                }
            }
        };
        searchButtonNext.addActionListener(al_searchNext);
        searchButtonPanel.add(searchButtonNext);

        searchPanel.add(searchButtonPanel);

        JPanel progressButtonPanel = new JPanel();
        progressButtonPanel.setLayout(new GridLayout(1, 2, 3, 0));

        // Progress bar defined
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        searchButtonStop = new JButton("Stop");
        ActionListener al_searchStop = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopSearch = true;

                int cmd = Integer.parseInt(radioGroup.getSelection().getActionCommand());

                switch (cmd) {
                    case 1:
                        // Word Value
                    case 2:
                        // Word Index
                    case 3:
                        // Page Scrolling
                        setMessage(" ", null, null);
                        scrollToVisible(true, 40);
                        setSliderPosition();
                        break;
                    case 4:
                    case 5:
                        // Evio Event
                        break;
                    case 6:
                        // Evio Fault
                        //removeEvioFaultPanel();
                        break;
                    default:
                }
            }
        };
        searchButtonStop.addActionListener(al_searchStop);

        searchButtonStart = new JButton("Start Scan");
        ActionListener al_searchStart = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopSearch = false;
                progressBar.setValue(0);
                progressBar.setString(null);

                int cmd = Integer.parseInt(radioGroup.getSelection().getActionCommand());

                switch (cmd) {
                    case 1:
                        // Word Value
                    case 2:
                        // Word Index
                    case 3:
                        // Page Scrolling
                        setMessage(" ", null, null);
                        scrollToVisible(false, 40);
                        setSliderPosition();
                        break;
                    case 4:
                    case 5:
                        // Evio Event
                        break;
                    case 6:
                        // Evio Fault
                        handleErrorSearch();
                        break;
                    default:
                }
            }
        };
        searchButtonStart.addActionListener(al_searchStart);
        searchButtonStart.setEnabled(false);

        progressButtonPanel.add(searchButtonStart);
        progressButtonPanel.add(searchButtonStop);

        searchPanel.add(Box.createVerticalStrut(3));
        searchPanel.add(progressButtonPanel);
        searchPanel.add(Box.createVerticalStrut(3));
        searchPanel.add(progressBar);

        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(searchPanel);

        // Add slide to show view position relative to entire file
        viewPosition = new JSlider(JSlider.VERTICAL, 0, 1000, 0);
        viewPosition.setInverted(true);
        viewPosition.setEnabled(false);
        this.add(viewPosition, BorderLayout.EAST);

        // Add filename & error message widgets
        JPanel msgPanel = new JPanel();
        msgPanel.setLayout(new GridLayout(2,1,0,0));

        fileNameLabel = new JLabel(fileName);
        fileNameLabel.setBorder(border);
        fileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        messageLabel = new JLabel(" ");
        messageLabel.setOpaque(true);
        messageLabel.setBorder(border);
        messageLabel.setForeground(Color.red);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        msgPanel.add(fileNameLabel);
        msgPanel.add(messageLabel);

        this.add(msgPanel, BorderLayout.NORTH);

        containControlPanel.add(controlPanel, BorderLayout.NORTH);
        this.add(containControlPanel, BorderLayout.WEST);
    }


    /** Add a panel of viewed data to this frame. */
    private void addFileViewPanel(File file) {

        if (file == null) return;

        // Create object to memory map the whole file (perhaps in chunks)
        try {
            mappedMemoryHandler = new SimpleMappedMemoryHandler(file, order);
        }
        catch (IOException e) {/* should not happen */}

        // Set up the table widget for displaying data
        dataTableModel = new MyTableModel(mappedMemoryHandler.getFileSize());
        dataTable = new JTable(dataTableModel);
        dataTableRenderer = new MyRenderer(8);
        dataTableRenderer.setHorizontalAlignment(JLabel.RIGHT);
        dataTable.setDefaultRenderer(String.class, dataTableRenderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setCellSelectionEnabled(true);
        dataTable.setSelectionBackground(Color.yellow);
        // Cannot allow re-ordering of data !!!
        dataTable.getTableHeader().setReorderingAllowed(false);

        // Start searching from mouse-clicked cell
        dataTable.addMouseListener(
                new MouseListener() {
                    public void mouseClicked(MouseEvent e) {
                        // Ignore mouse click during on-going search
                        if (!searchDone ) {
//System.out.println("select listener: search is NOT done");
                            return;
                        }

                        lastSearchedRow = dataTable.getSelectedRow();
                        lastSearchedCol = dataTable.getSelectedColumn();
//System.out.println("select listener: row = " + lastSearchedRow +
//                   ", col = " + lastSearchedCol);

                        // If we're looking for events ...
                        if (dataTableModel.isDataColumn(lastSearchedCol) &&
                            evioEventButton.isSelected()) {
                            // Display current selection as if it is start of bank

                            // Transform row & col into absolute index
                            long wordIndex = dataTableModel.getWordIndexOf(lastSearchedRow,
                                                                           lastSearchedCol);

                            // Parse this & next word as bank header
                            EvioHeader node =
                                    new EvioHeader((int)(dataTableModel.getLongValueAt(wordIndex)),
                                                                (int)(dataTableModel.getLongValueAt(wordIndex + 1)));
                            // Display
                            addEventInfoPanel();
                            updateEventInfoPanel(node);
                        }
                    }

                    public void mouseReleased(MouseEvent e) { }
                    public void mousePressed(MouseEvent e)  { }
                    public void mouseEntered(MouseEvent e)  { }
                    public void mouseExited(MouseEvent e)   { }
                }
        );

        // Set the font to one that's fixed-width
        Font newFont = new Font(Font.MONOSPACED, Font.PLAIN, dataTable.getFont().getSize());
        dataTable.setFont(newFont);
        tablePane = new JScrollPane(dataTable);

        // Write into table
        setTableData();

        // Put table into panel and panel into frame
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(tablePane, BorderLayout.CENTER);

        this.add(panel, BorderLayout.CENTER);
    }


    //-------------------------------------------
    // Classes and methods to handle data table
    //-------------------------------------------


    /** This class describes the data table's data including column names.
     *  Each file is broken up into 200MB (maxMapByteSize) memory maps
     *  (last one is probably smaller than that since file size is probably
     *  not an exact multiple of 200MB).
     *  A single map is loaded into the table at any one time. */
    final class MyTableModel extends AbstractTableModel {

        /** Offset in bytes from beginning of file to
         * beginning of map currently being viewed. */
        private long wordOffset;

        /** Index to map we are using. */
        private int mapIndex;

        /** Index to last word in file. */
        private final long maxWordIndex;

        /** Size of file in bytes. */
        private final long fileSize;

        /** Number of memory maps used for this file. */
        private final int mapCount;

        /** Number of words in each table row. */
        private final int wordsPerRow = 5;

        /** Number of bytes in each table row. */
        private final int bytesPerRow = 20;

        /** Max number of bytes per memory map. */
        private final long maxMapByteSize;

        /** Max number of rows per memory map. */
        private final int  maxRowsPerMap;

        /** Max number of words (32 bit ints) per memory map. */
        private final long maxWordsPerMap;

        /** Total number of rows currently in table. */
        private long totalRows;

        /** Column names. */
        private final String[] names = {"Word Position", "+1", "+2", "+3", "+4", "+5", "Comments"};
        private final String[] columnNames = {names[0], names[1], names[2],
                                              names[3], names[4], names[5],
                                              names[6]};


        /** Constructor. */
        public MyTableModel(long fileSize) {
            this.fileSize = fileSize;
            // Min file size = 4 bytes
            maxWordIndex = (fileSize-4L)/4L;
            mapCount = mappedMemoryHandler.getMapCount();
            maxMapByteSize = mappedMemoryHandler.getMaxMapSize();
            maxWordsPerMap = maxMapByteSize/4;
            maxRowsPerMap  = (int) (maxWordsPerMap/wordsPerRow);
        }

        /**
         * Get the size of the file in bytes.
         * @return size of the file in bytes.
         */
        public long getFileSize() { return fileSize; }

        /**
         * Get the mapped memory handler object.
         * @return mapped memory handler object.
         */
        public SimpleMappedMemoryHandler getMemoryHandler() { return mappedMemoryHandler; }

        /**
         * Get the size of the file in bytes.
         * @return size of the file in bytes.
         */
        public ByteOrder order() { return mappedMemoryHandler.getOrder(); }

        /**
         * Get the max # of rows per map.
         * @return max # of rows per map.
         */
        public int getMaxRowsPerMap() { return maxRowsPerMap; }

        /**
         * Get the # of memory maps.
         * @return # of memory maps.
         */
        public int getMapCount() {
            return mapCount;
        }

        /**
         * Get the total # of rows in file.
         * @return total # of rows in file.
         */
        public long getTotalRows() {
             // Only calculate this once
            if (totalRows < 1) {
                totalRows = fileSize/bytesPerRow;
                // May need to round up if the last row is not full
                int addOne = fileSize % bytesPerRow > 0 ? 1 : 0;
                totalRows += addOne;
            }

            return totalRows;
        }

        /**
         * Get the percentage (or progress) of the way through the file that the given row is.
         * @param currentRow the current row in current map (starting at 0)
         * @return percentage of the way through the file that the given row is.
         */
        public int getRowProgress(int currentRow) {
            long rowFromBeginning = mapIndex*maxRowsPerMap + currentRow;
            int percent = (int) (100 * rowFromBeginning/getTotalRows());
            //if (rowFromBeginning % 1000000 == 0)
            //    System.out.println(rowFromBeginning + "," + getTotalRows() + ", " + percent);
            if (percent > 100) return 100;
            return percent;
        }

        /**
         * Set data to next map if there is one, but
         * do not refresh table view.
         * @return {@code true} if next map loaded,
         *         else {@code false} if already using last map.
         */
        public boolean nextMap() {
            if (mapIndex == mapCount -1) {
                return false;
            }

            mapIndex++;
            wordOffset = mapIndex*maxWordsPerMap;
            return true;
        }

        /**
         * Set data to previous map if there is one, but
         * do not refresh table view.
         * @return {@code true} if previous map loaded,
         *         else {@code false} if already using first map.
         */
        public boolean previousMap() {
            if (mapIndex == 0) {
                return false;
            }

            mapIndex--;
            wordOffset = mapIndex*maxWordsPerMap;
            return true;
        }

        /**
         * Get the index of the currently used memory map.
         * @return index of the currently used memory map.
         */
        public int getMapIndex() {
            return mapIndex;
        }

        /**
         * Set the index of the desired memory map and
         * refresh table view.
         * @param mi index of the desired memory map.
         */
        public void setMapIndex(int mi) {
            if (mi == mapIndex) return;
            mapIndex = mi;
            wordOffset = mapIndex*maxWordsPerMap;
            fireTableDataChanged();
        }

        /** Refresh view of table. */
        public void dataChanged() {fireTableDataChanged();}

        /**
         * Is the given column one which contains data or not?
         * @param col column index
         * @return true if col contains data, else false
         */
        public boolean isDataColumn(int col) {
            return  (col > 0 && col < getColumnCount() - 1);
        }

        /**
         * Set the table's data to the map containing the given word
         * and refresh view.
         * @param wordIndex index to the word to view.
         */
        public void setWindowData(long wordIndex) {
            long oldMapIndex = mapIndex;

            mapIndex = mappedMemoryHandler.getMapIndex(wordIndex);

            if (oldMapIndex == mapIndex) {
                return;
            }
            fireTableDataChanged();
        }

        /**
         * Get the map index, row, and column of the given word
         * and refresh the view.
         * @param wordIndex index to the word.
         * @return array of 3 ints, 1st is map index, 2nd is row, 3rd is column.
         *         Return null if index out of bounds
         */
        public int[] getMapRowCol(long wordIndex) {
            if (wordIndex > maxWordIndex || wordIndex < 0) return null;

            int[] dat = new int[3];

            // map index
            dat[0] = (int) (wordIndex*4/maxMapByteSize);

            int  byteIndex = (int) (wordIndex*4 - (dat[0] * maxMapByteSize));
            // row
            dat[1] =  byteIndex / bytesPerRow;
            // column
            dat[2] = (byteIndex % bytesPerRow)/4 + 1;

            return dat;
        }

        /**
         * Highlight the given row & col entry, then refresh view.
         * @param color   color of highlight
         * @param row     row
         * @param col     column
         * @param isError true if highlighting an error
         */
        public void highLightCell(Color color, int row, int col, boolean isError) {
            dataTableRenderer.setHighlightCell(color, row, col, isError);
            fireTableCellUpdated(row, col);
        }

        /**
         * Highlight an event header. The given row & col are for
         * the first word of the header. Refresh view.
         * @param color   color of highlight
         * @param row     row
         * @param col     column
         * @param isError true if highlighting an error
         */
        public void highLightEventHeader(Color color, int row, int col, boolean isError) {
            // Highlight starting point
            dataTableRenderer.setHighlightCell(color, row, col, isError);
            fireTableCellUpdated(row, col);
            // Also highlight the next (tag/num) header word, but
            // not if we're starting at pos = 0, col 0
            if (col != 0) {
                long pos  = dataTableModel.getWordIndexOf(row, col);
                int[] mrc = dataTableModel.getMapRowCol(pos + 1);
                if (mrc == null) return;
                setMapIndex(mrc[0]);
                dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
                fireTableCellUpdated(mrc[1], mrc[2]);
            }
        }

        /**
         * Highlight a block header. The given row & col are for
         * the last (magic #) word of the header. Refresh view.
         * @param color   color of highlight
         * @param row     row
         * @param col     column
         * @param isError true if highlighting an error
         * @return int array containing all the block header values; null if error
         */
        public int[] highLightBlockHeader(Color color, int row, int col, boolean isError) {

            int[] blockData = new int[8];
            long index = dataTableModel.getWordIndexOf(row,col);

            for (int i=0; i<8; i++) {
                blockData[7-i] = (int)dataTableModel.getLongValueAt(index - i);
                int[] mrc = dataTableModel.getMapRowCol(index - i);
                if (mrc == null) return null;
                setMapIndex(mrc[0]);
                dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
                fireTableCellUpdated(mrc[1], mrc[2]);
            }
            return  blockData;
        }

        /**
         * Enter block header position into highlight hashmap for future highlighting.
         * The given position is for the first word of the header.
         * @param color   color of highlight
         * @param pos     byte index of beginning of block header
         * @param isError true if highlighting an error
         */
        public void highLightBlockHeader(Color color, long pos, boolean isError) {
            for (int i=0; i<8; i++) {
                dataTableRenderer.setHighlightCell(color, pos + 4*i, isError);
            }
        }

        /**
         * Enter event header position into highlight hashmap for future highlighting.
         * The given position is for the first word of the header.
         * @param color   color of highlight
         * @param pos     byte index of beginning of event header
         * @param isError true if highlighting an error
         */
        public void highLightEventHeader(Color color, long pos, boolean isError) {
            for (int i=0; i<2; i++) {
                dataTableRenderer.setHighlightCell(color, pos + 4*i, isError);
            }
        }

        /** Clear all highlights and refresh view. */
        public void clearHighLights() {dataTableRenderer.clearHighlights();}

        /** {@inheritDoc} */
        public int getColumnCount() {return columnNames.length;}

        /** {@inheritDoc} */
        public int getRowCount() {
            // All maps are maxMapByteSize bytes in size except
            // the last one which may be any non-zero size.
            int mapCount = mappedMemoryHandler.getMapCount();

            if (mapIndex == mapCount - 1) {
                // Need to round up since the last row may not be "full"
                int mapSize = mappedMemoryHandler.getMapSize(mapIndex);
                int addOne = mapSize % bytesPerRow > 0 ? 1 : 0;
                return mapSize/bytesPerRow + addOne;
            }
            return maxRowsPerMap;
        }

        /** {@inheritDoc} */
        public String getColumnName(int col) {
            return columnNames[col];
        }

        /** {@inheritDoc} */
        public Object getValueAt(int row, int col) {
            if (row < 0) {
                return "";
            }

            // 1st column is row or integer #
            if (col == 0) {
                return String.format("%,d", (wordOffset + row*5));
            }

            // Remember comments placed into 7th column
            if (col == 6) {
                return comments.get(row);
            }

            long index = wordOffset + (row * 5) + col - 1;

            if (index > maxWordIndex) {
                return "";
            }
            // Value of cell is in memory map handler
            return String.format("0x%08x", mappedMemoryHandler.getInt(index));
        }

        /**
         * Get the word value of the given row and column.
         * @param row   row
         * @param col   column
         * @return word value
         */
        public long getLongValueAt(int row, int col) {
            // 1st column is row or integer #
            if (row < 0 || col == 0 || col == 6) {
                return 0;
            }

            long index = wordOffset + (row * 5) + col - 1;

            if (index > maxWordIndex) {
                return 0;
            }

            return (((long)mappedMemoryHandler.getInt(index)) & 0xffffffffL);
        }

        /**
         * Get the long value at the given file word index.
         * @param wordIndex file word index.
         * @return long value
         */
        public long getLongValueAt(long wordIndex) {
            if (wordIndex < 0 || wordIndex > maxWordIndex) {
                return 0;
            }

            return (((long)mappedMemoryHandler.getInt(wordIndex)) & 0xffffffffL);
        }

        /**
         * Get the int value at the given file byte index.
         * @param byteIndex file byte index.
         * @return int value
         */
        public int getInt(long byteIndex) {
            if (byteIndex < 0 || byteIndex > fileSize - 1) {
                return 0;
            }

            return (mappedMemoryHandler.getIntAtBytePos(byteIndex));
        }

        /**
         * Get the short value at the given file byte index.
         * @param byteIndex file byte index.
         * @return short value
         */
        public int getShort(long byteIndex) {
            if (byteIndex < 0 || byteIndex > fileSize - 1) {
                return 0;
            }

            return (mappedMemoryHandler.getShortAtBytePos(byteIndex));
        }

        /**
         * Get the byte value at the given file byte index.
         * @param byteIndex file byte index.
         * @return byte value
         */
        public int get(long byteIndex) {
            if (byteIndex < 0 || byteIndex > fileSize - 1) {
                return 0;
            }

            return (mappedMemoryHandler.getByteAtBytePos(byteIndex));
        }

        /**
         * Get the file word index of the entry at the given row and column.
         * @param row   row
         * @param col   column
         * @return file word index
         */
        public long getWordIndexOf(int row, int col) {
            // 1st column is row or integer #
            if (col == 0 || col == 6) {
                return 0;
            }

            return wordOffset + (row * 5) + col - 1;
        }

        /** {@inheritDoc} */
        public Class getColumnClass(int c) {
            if (c == 0) return Long.class;
            return String.class;
        }

        /** {@inheritDoc} */
        public boolean isCellEditable(int row, int col) {
            return col > 1;
        }

        /** {@inheritDoc} */
        public void setValueAt(Object value, int row, int col) {
            if (col == 6) {
                comments.put(row, (String)value);
            }
            fireTableCellUpdated(row, col);
        }
    }



    /** Refresh the view of the table's data. */
    void setTableData() {
        dataTableModel.fireTableDataChanged();
    }


    /** Renderer used to change background color every Nth row and to highlight cells. */
    final class MyRenderer extends DefaultTableCellRenderer {
        private int   nthRow;
        private Color alternateRowColor = new Color(235, 245, 255);

        /**
         * Keep track of which cells have been highlighted.
         * Key is a long in which highest 16 bits are map index,
         * next 32 bits are the row, and lowest 16 bits are the column.
         * Thus a map/row/col input can be quickly computed to the map's key
         * and the color can be quickly found.
         * Value object is the Color object for the given long (map/row/col).
         */
        private final HashMap<Long,Color> highlightCells  = new HashMap<Long,Color>(100);

        /**
         * Keep track of which cells have been highlighted as containing errors.
         * These have priority over the regular highlighting.
         */
        private final HashMap<Long,Color> highlightErrors = new HashMap<Long,Color>(100);


        /** Constructor. */
        public MyRenderer(int nthRow) {
            super();
            this.nthRow = nthRow;
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
            if (color == null) color = highlightBlkHdr;
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
            if (color == null) color = highlightBlkHdr;
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
         * Normal rendering means highlighting every nth row with a darker background.
         * {@inheritDoc} */
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            if (!isSelected) {
                super.setForeground(table.getForeground());

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
                super.setForeground(Color.black);
                super.setBackground(color);
            }

            // Selection has priority over highlighting
            if (isSelected) {
                super.setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            }

            setFont(table.getFont());
            setValue(value);

            return this;
        }
    }


    //-------------------------------------------


	/**
	 * Size and center a JFrame relative to the screen.
	 *
	 * @param frame the frame to size.
	 * @param fractionalSize the fraction desired of the screen--e.g., 0.85 for 85%.
	 */
	private void sizeToScreen(JFrame frame, double fractionalSize) {
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		d.width = (int) (fractionalSize * .7 * d.width);
		d.height = (int) (fractionalSize * d.height);
		frame.setSize(d);
		centerComponent(frame);
	}

	/**
	 * Center a component.
	 *
	 * @param component the Component to center.
	 */
	private void centerComponent(Component component) {

		if (component == null)
			return;

		try {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension componentSize = component.getSize();
			if (componentSize.height > screenSize.height) {
				componentSize.height = screenSize.height;
			}
			if (componentSize.width > screenSize.width) {
				componentSize.width = screenSize.width;
			}

			int x = ((screenSize.width - componentSize.width) / 2);
			int y = ((screenSize.height - componentSize.height) / 2);

			component.setLocation(x, y);

		}
		catch (Exception e) {
			component.setLocation(200, 200);
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the look and feel.
	 */
	private void initializeLookAndFeel() {

		LookAndFeelInfo[] lnfinfo = UIManager.getInstalledLookAndFeels();

		if (lnfinfo.length < 1) {
			return;
		}

		String desiredLookAndFeel = "Windows";

        for (LookAndFeelInfo aLnfinfo : lnfinfo) {
            if (aLnfinfo.getName().equals(desiredLookAndFeel)) {
                try {
                    UIManager.setLookAndFeel(aLnfinfo.getClassName());
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
        }
	}


}