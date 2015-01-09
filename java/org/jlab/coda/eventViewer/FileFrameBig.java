package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class implements a window that displays a file's bytes as hex, 32 bit integers.
 * @author timmer
 */
public class FileFrameBig extends JFrame implements PropertyChangeListener {

    /** The table containing event data. */
    private JTable dataTable;

    private MyTableModel dataTableModel;

    private MyRenderer dataTableRenderer;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Remember comments placed into 7th column of table. */
    private HashMap<Integer,String> comments = new HashMap<Integer,String>();

    /** Look at file in big endian order by default. */
    private ByteOrder order = ByteOrder.BIG_ENDIAN;

    /** Menu item for switching data endianness. */
    private JMenuItem switchMenuItem;

    /** Buffer of memory mapped file. */
    private SimpleMappedMemoryHandler mappedMemoryHandler;

    private JButton searchButtonStop;
    private JProgressBar progressBar;

    private JButton searchButtonNext;
    private JButton searchButtonPrev;
    private JButton gotoButton;
    private JComboBox<String> searchStringBox;
    private String searchString;
    private JSpinner currentEvent;
    private JLabel messageLabel;

    private JRadioButton wordValueButton;
    private JRadioButton wordIndexButton;
    private JRadioButton evioBlockButton;
    private JRadioButton evioFaultButton;
    private JRadioButton pageScrollButton;
    private ButtonGroup  radioGroup;

    /** Last row to be searched (rows start at 0). */
    private int lastSearchedRow = -1;
    /** Last col to be searched (cols start at 1). */
    private int lastSearchedCol = 0;

    /** Kill search that's taking too long. */
    private volatile boolean stopSearch;
    private volatile boolean searchDone = true;

    /**
	 * Constructor for a simple viewer for a file's bytes.
	 */
	public FileFrameBig(File file) {
		super(file.getName() + " bytes");
		initializeLookAndFeel();

		// set the close to call System.exit
		WindowAdapter wa = new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
                FileFrameBig.this.dispose();
			}
		};
		addWindowListener(wa);

		// set layout
        setLayout(new BorderLayout());

        // add menus
        addMenus();

        // add buttons
        addControlPanel();

        // add JPanel to view file
        addFileViewPanel(file);

		// size to the screen
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
                messageLabel.setText(" ");
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

        // Clear comments
        ActionListener al_clearComments = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
                comments.clear();
                dataTableModel.fireTableDataChanged();
           }
        };
        JMenuItem clearMenuItem = new JMenuItem("Clear comments");
        clearMenuItem.addActionListener(al_clearComments);
        menu.add(clearMenuItem);

        // Clear comments
        ActionListener al_clearError = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
            }
        };
        JMenuItem clearErrorMenuItem = new JMenuItem("Clear error");
        clearErrorMenuItem.addActionListener(al_clearError);
        menu.add(clearErrorMenuItem);

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
     */
    private void scrollToVisible(boolean down) {
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
        int deltaY = numRowsViewed*dataTable.getRowHeight();

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
System.out.println("Jump to TOP of next map");
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
System.out.println("Jump to BOTTOM of prev map");
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
     */
    private void scrollToIndex(long position) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Switch to correct map
        int[] mapRowCol = dataTableModel.getMapRowCol(position);
        if (mapRowCol == null) {
            JOptionPane.showMessageDialog(this, "Entry exceeded file size", "Return",
                    JOptionPane.INFORMATION_MESSAGE);

            return;
        }
//System.out.println("map = " + mapRowCol[0] + ", row = " + mapRowCol[1] +
//                   ", col " + mapRowCol[2]);
        dataTableModel.setMapIndex(mapRowCol[0]);

        // Where we jump to at each button press
        int finalY = (mapRowCol[1] - 5)*dataTable.getRowHeight();
        Rectangle rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
        lastSearchedRow = mapRowCol[1];
        lastSearchedCol = mapRowCol[2];

        dataTable.scrollRectToVisible(rec);
        dataTableModel.dataChanged();

        // Select cell
        dataTable.setRowSelectionInterval(lastSearchedRow, lastSearchedRow);
        dataTable.setColumnSelectionInterval(lastSearchedCol, lastSearchedCol);
    }



    /**
     * Jump up or down to the row containing a cell with value = findValue.
     * It moves so that a full row is exactly at the top.
     * @param down {@code true}  if going to end of file,
     *             {@code false} if going to top of file
     * @param findValue value of word to find
     * @param comments comments to add to found value's row
     */
    private void scrollToAndHighlight(boolean down, long findValue,
                                      String comments, SearchTask task) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Current view's height
        int viewHeight = dim.height;

        // Current view's width
        int viewWidth  = dim.width;

        // Height of each row
        int dataTableRowHeight = dataTable.getRowHeight();

        long val;
        Rectangle rec = null;
        int row, startingCol=1, finalY, rowY, viewY;
        boolean atTop = pt.y == 0;
        boolean atBottom = atEnd(viewport);
        boolean foundValue = false;

        // Map index starts at 0
        int maxMapIndex = dataTableModel.getMapCount() - 1;
        int startingMapIndex = dataTableModel.getMapIndex();

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
System.out.println("\nStart looking at row = " + row + ", col = " + startingCol);

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
                        if (val == findValue)  {
System.out.println("    Found at row = " + row + ", col = " + col);
                            dataTableModel.highListCell(row, col);

                            // Mark it in comments
                            if (comments != null) {
                                dataTableModel.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTable.getRowHeight();
                            // Current view's top Y position
                            viewY = viewport.getViewPosition().y;

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
                                rec = new Rectangle(pt.x, finalY, viewWidth, viewHeight);
                                // Selection will be made AFTER jump to view (at very end)
                            }

                            foundValue = true;
                            break out;
                        }
                    }

                    // Go to next row
                    row++;
                    startingCol = 1;
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
                    else {
                        // Start at the row of the last value we found
                        row = lastSearchedRow;
                        // Start before the column of the last value we found
                        startingCol = lastSearchedCol - 1;
                    }
                }
System.out.println("\nStart looking at row = " + row + ", col = " + startingCol);

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
System.out.println("    Found at row = " + row + ", col = " + col);
                            dataTableModel.highListCell(row, col);

                            // Mark it in comments
                            if (comments != null) {
                                dataTableModel.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTableRowHeight;
                            // Current view's top Y position
                            viewY = viewport.getViewPosition().y;

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
                                rec = new Rectangle(pt.x, finalY, viewWidth, viewHeight);
                                // Selection will be made AFTER jump to view (at very end)
                            }

                            foundValue = true;
                            break out;
                        }
                    }

                    // Go to previous row
                    row--;
                    startingCol = 5;
                }
            }

            // If we're here, we did NOT find any value

            if (down) {
System.out.println("Did NOT find val, at bottom = " + atBottom +
                   ", stopSearch = " + stopSearch);
                // See if there are more data (maps) to follow.
                // If so, go to the next map and search there.
                if (!dataTableModel.nextMap()) {
                    dataTable.clearSelection();
                    break;
                }

                // Update progress
                if (task != null) {
                    int progressPercent;
                    int currentMapIndex = dataTableModel.getMapIndex();
                    if (startingMapIndex == maxMapIndex || currentMapIndex == maxMapIndex) {
                        progressPercent = 100;
                    }
                    else {
                        progressPercent = 100*(currentMapIndex - startingMapIndex)/(maxMapIndex - startingMapIndex);
                    }
System.out.println("start = " + startingMapIndex + ", cur = " + currentMapIndex +
                   ", max = " + maxMapIndex + ", prog = " + progressPercent);
                    task.setTaskProgress(progressPercent);
                }

                // Start at beginning of next map
                lastSearchedRow = -1;
                lastSearchedCol = 0;
            }
            else {
System.out.println("Did NOT find val, at top = " + atTop);
                // See if there are more data (maps) before.
                // If so, go to the previous map and search there.
                if (!dataTableModel.previousMap()) {
                    dataTable.clearSelection();
                    break;
                }

                // Update progress
                if (task != null) {
                    int progressPercent;
                    int currentMapIndex = dataTableModel.getMapIndex();
                    if (startingMapIndex == 0 || currentMapIndex == 0) {
                        progressPercent = 100;
                    }
                    else {
                        progressPercent = 100*(startingMapIndex - currentMapIndex)/startingMapIndex;
                    }
System.out.println("start = " + startingMapIndex + ", cur = " + currentMapIndex +
                   ", max = " + maxMapIndex + ", prog = " + progressPercent);
                    task.setTaskProgress(progressPercent);
                }

                // Start at end of previous map
                lastSearchedRow = dataTableModel.getRowCount() - 1;
                lastSearchedCol = 6;
            }
        }

        if (!foundValue) {
            if (stopSearch) {
                messageLabel.setText("Search Stopped");
            }
            else {
                messageLabel.setText("No value found");
            }

            // Set view at top if going up, bottom if going down
            if (down) {
                finalY = (dataTableModel.getRowCount() - 1 - 5)*dataTableRowHeight;
                rec = new Rectangle(pt.x, finalY, viewWidth, viewHeight);
                dataTable.scrollRectToVisible(rec);
            }
            else {
                rec = new Rectangle(pt.x, 0, viewWidth, viewHeight);
                dataTable.scrollRectToVisible(rec);
            }

            dataTableModel.dataChanged();
            searchDone = true;
            return;
        }

        if (rec != null) {
            dataTable.scrollRectToVisible(rec);
            dataTableModel.dataChanged();

            // Select cell of found value (after jump so it's visible)
            dataTable.setRowSelectionInterval(lastSearchedRow,lastSearchedRow);
            dataTable.setColumnSelectionInterval(lastSearchedCol, lastSearchedCol);
        }

        searchDone = true;
    }



    /**
     * A SwingWorker thread to handle a possibly lengthy search.
     */
    class SearchTask extends SwingWorker<Void, Void> {
        private final boolean down;
        private final long value;
        private String label;

        public SearchTask(boolean down, long value, String label) {
            this.down = down;
            this.value = value;
            this.label = label;
        }

        // Main search task executed in background thread
        @Override
        public Void doInBackground() {
            disableControlsForSearch();
            scrollToAndHighlight(down, value, label, this);
            return null;
        }

        public void setTaskProgress(int p) {
            setProgress(p);
        }

        // Executed in event dispatching thread
        @Override
        public void done() {
            searchDone = true;
            setProgress(0);
            progressBar.setString("Done");
            progressBar.setValue(0);

            Toolkit.getDefaultToolkit().beep();
            enableControls();
        }
    }



    private void handleWordValueSearch(final boolean down, boolean findBlock) {
        messageLabel.setText(" ");
        long l = 0xc0da0100L;

        if (!findBlock) {
            String txt = (String) searchStringBox.getSelectedItem();
            System.out.println("String = \"" + txt + "\"");
            if (txt.length() > 1 && txt.substring(0, 2).equalsIgnoreCase("0x")) {
                txt = txt.substring(2);
                System.out.println("new String = \"" + txt + "\"");
                try {
                    l = Long.parseLong(txt, 16);
                }
                catch (NumberFormatException e1) {
                    System.out.println("Number format ex: " + e1.getMessage());
                }
                System.out.println("Search for l = " + l);
            }
            else {
                l = Long.parseLong(txt, 10);
            }
        }

        String label = null;
        final long ll = l;

        if (l == 0xc0da0100L) {
            label = "Block Header";
        }

        // Use swing worker thread to do time-consuming search in the background
        SearchTask task = new SearchTask(down, ll, label);
        task.addPropertyChangeListener(this);
        task.execute();
    }


    private void handleWordIndexSearch() {
        messageLabel.setText(" ");
        long l = 1;
        String txt = (String) searchStringBox.getSelectedItem();
        if (txt.length() > 1 && txt.substring(0, 2).equalsIgnoreCase("0x")) {
            txt = txt.substring(2);
            try {
                l = Long.parseLong(txt, 16);
            }
            catch (NumberFormatException e1) {
                System.out.println("Number format ex: " + e1.getMessage());
            }
        }
        else {
            l = Long.parseLong(txt, 10);
        }

        if (l < 1) l = 1L;

        scrollToIndex(l-1);
    }


    /** Invoked when task's progress property changes. */
    public void propertyChange(PropertyChangeEvent evt) {
        if (searchDone) {
//            System.out.println("propChange: search is done");
            return;
        }

        if ("progress".equalsIgnoreCase(evt.getPropertyName())) {
//System.out.println("propChange: set bar to " + ((Integer) evt.getNewValue()));
            progressBar.setValue((Integer) evt.getNewValue());
        }
    }


    private void disableControlsForSearch() {
        searchStringBox .setEnabled(false);
        searchButtonNext.setEnabled(false);
        searchButtonPrev.setEnabled(false);
        wordValueButton. setEnabled(false);
        wordIndexButton. setEnabled(false);
        pageScrollButton.setEnabled(false);
        evioBlockButton. setEnabled(false);
        evioFaultButton. setEnabled(false);
    }


    private void enableControls() {
        searchStringBox .setEnabled(true);
        searchButtonNext.setEnabled(true);
        searchButtonPrev.setEnabled(true);
        wordValueButton. setEnabled(true);
        wordIndexButton. setEnabled(true);
        pageScrollButton.setEnabled(true);
        evioBlockButton. setEnabled(true);
        evioFaultButton. setEnabled(false);
    }


    /** Add a panel controlling viewed data to this frame. */
    private void addControlPanel() {
        JPanel containControlPanel = new JPanel(new BorderLayout());

        // Put browsing buttons into panel
        JPanel controlPanel = new JPanel();
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
        radioButtonPanel.setLayout(new GridLayout(5, 1, 0, 4));
        radioButtonPanel.setMinimumSize(new Dimension(160, 200));
        radioButtonPanel.setPreferredSize(new Dimension(160, 200));
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

        evioFaultButton = new JRadioButton("Evio Fault");
        evioFaultButton.setMnemonic(KeyEvent.VK_F);
        evioFaultButton.setActionCommand("5");
        evioFaultButton.setEnabled(false);

        // Group the radio buttons
        radioGroup = new ButtonGroup();
        radioGroup.add(wordValueButton);
        radioGroup.add(wordIndexButton);
        radioGroup.add(pageScrollButton);
        radioGroup.add(evioBlockButton);
        radioGroup.add(evioFaultButton);

        // Add radio buttons to panel
        radioButtonPanel.add(wordValueButton);
        radioButtonPanel.add(wordIndexButton);
        radioButtonPanel.add(pageScrollButton);
        radioButtonPanel.add(evioBlockButton);
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
                        searchButtonPrev.setEnabled(true);
                        searchButtonNext.setText(" > ");
                        searchStringBox.setEditable(true);
                        break;
                    case 2:
                        // Word Index
                        searchButtonPrev.setEnabled(false);
                        searchButtonNext.setText("Go");
                        searchStringBox.setEditable(true);
                        break;
                    case 3:
                        // Page Scrolling
                        searchButtonPrev.setEnabled(true);
                        searchButtonNext.setText(" > ");
                        searchStringBox.setEditable(false);
                        break;
                    case 4:
                        // Evio Block
                        searchButtonPrev.setEnabled(true);
                        searchButtonNext.setText(" > ");
                        // Only search for 0xc0da0100
                        searchStringBox.setSelectedIndex(0);
                        searchStringBox.setEditable(false);
                        break;
                    case 5:
                        // Evio Fault
                        break;
                    default:
                }
            }
        };

        wordValueButton.addActionListener(al_radio);
        wordIndexButton.addActionListener(al_radio);
        evioBlockButton.addActionListener(al_radio);
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
                dataTableModel.clearHighLights();

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
                        break;
                    case 3:
                        // Page Scrolling
                        messageLabel.setText(" ");
                        scrollToVisible(false);
                        break;
                    case 4:
                        // Evio Block
                        handleWordValueSearch(false, true);
                        break;
                    case 5:
                        // Evio Fault
                        break;
                    default:
                }
            }
        };
        searchButtonPrev.addActionListener(al_search);
        searchButtonPrev.setPreferredSize(new Dimension(80, 25));
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
                        break;
                    case 3:
                        // Page Scrolling
                        messageLabel.setText(" ");
                        scrollToVisible(true);
                        break;
                    case 4:
                        // Evio Block
                        handleWordValueSearch(true, true);
                        break;
                    case 5:
                        // Evio Fault
                        break;
                    default:
                }
            }
        };
        searchButtonNext.addActionListener(al_searchNext);
        searchButtonNext.setPreferredSize(new Dimension(80, 25));
        searchButtonPanel.add(searchButtonNext);

        searchPanel.add(searchButtonPanel);


        JPanel progressButtonPanel = new JPanel();
        progressButtonPanel.setLayout(new GridLayout(2, 1, 0, 3));

        // Progress bar defined
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);

        searchButtonStop = new JButton("Stop");
        ActionListener al_searchStop = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopSearch = true;
            }
        };
        searchButtonStop.addActionListener(al_searchStop);

        progressButtonPanel.add(searchButtonStop);
        progressButtonPanel.add(progressBar);

        searchPanel.add(Box.createVerticalStrut(3));
        searchPanel.add(progressButtonPanel);

        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(searchPanel);


        // Add error message widget
        messageLabel = new JLabel(" ");
        messageLabel.setBorder(border);
        messageLabel.setForeground(Color.red);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        this.add(messageLabel, BorderLayout.NORTH);

        containControlPanel.add(controlPanel, BorderLayout.NORTH);


        this.add(containControlPanel, BorderLayout.WEST);
    }


    /** Add a panel of viewed data to this frame. */
    private void addFileViewPanel(File file) {
        if (file == null) return;

        long fileSize = 0L;

        try {
            // Map the file to get access to its data
            // with having to read the whole thing.
            FileInputStream fileInputStream = new FileInputStream(file);
            //String path = file.getAbsolutePath();
            FileChannel fileChannel = fileInputStream.getChannel();
            fileSize = fileChannel.size();

System.out.println("FILE SIZE = " + fileSize);

            mappedMemoryHandler = new SimpleMappedMemoryHandler(fileChannel, order);

            if (currentEvent != null) {
                SpinnerNumberModel model = (SpinnerNumberModel) (currentEvent.getModel());
                model.setMaximum(fileSize / 4);
            }

            // This object is no longer needed since we have the map, so close it
            fileChannel.close();
        }
        catch (IOException e) {/* should not happen */}


        // Set up the table widget for displaying data
        dataTableModel = new MyTableModel(fileSize);
        dataTable = new JTable(dataTableModel);
        dataTableRenderer = new MyRenderer(8);
        dataTableRenderer.setHorizontalAlignment(JLabel.RIGHT);
        dataTable.setDefaultRenderer(String.class, dataTableRenderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setCellSelectionEnabled(true);
        dataTable.setSelectionBackground(Color.yellow);
        // Start searching from mouse-clicked cell
        dataTable.addMouseListener(
                new MouseListener() {
                    public void mouseClicked(MouseEvent e) {
                        // Ignore mouse click during on-going search
                        if (!searchDone ) {
System.out.println("select listener: search is NOT done");
                            return;
                        }

                        lastSearchedRow = dataTable.getSelectedRow();
                        lastSearchedCol = dataTable.getSelectedColumn();
System.out.println("select listener: row = " + lastSearchedRow +
                   ", col = " + lastSearchedCol);
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


    /** This class describes the data table's data including column names. */
    private final class MyTableModel extends AbstractTableModel {
        private long wordOffset;

        // Which map are we using? */
        private int mapIndex;

        private final long maxWordIndex;

        private final int mapCount;

        // 5 words/row, 4 bytes/word, 20 bytes/row
        private final int wordsPerRow = 5;
        private final int bytesPerRow = 20;
        // # of rows/map (1 map = 1 window) (200 MB/map)
        private final int  maxRowsPerMap  = 10000000;
        private final long maxMapByteSize = 200000000L;
        private final long maxWordsPerMap = maxRowsPerMap * wordsPerRow;

        // Column names won't change
        private final String[] names = {"Word Position", "+1", "+2", "+3", "+4", "+5", "Comments"};
        private final String[] columnNames = {names[0], names[1], names[2],
                                              names[3], names[4], names[5],
                                              names[6]};


        public MyTableModel(long fileSize) {
            // Min file size = 4 bytes
            maxWordIndex = (fileSize-4L)/4L;
            mapCount = mappedMemoryHandler.getMapCount();
        }

        public int getMapCount() {
            return mapCount;
        }

        /**
         * Set data to next map if there is one.
         * @return {@code true} if next map loaded,
         *         else {@code false} if already using last map.
         */
        public boolean nextMap() {
            if (mapIndex == mapCount -1) {
                return false;
            }

            mapIndex++;
            wordOffset = mapIndex*maxWordsPerMap;
//            fireTableDataChanged();
            System.out.println("Jumped to NEXT map " + mapIndex);
            return true;
        }

        public boolean previousMap() {
            if (mapIndex == 0) {
                return false;
            }

            mapIndex--;
            wordOffset = mapIndex*maxWordsPerMap;
//            fireTableDataChanged();
            System.out.println("Jumped to PREV map " + mapIndex);
            return true;
        }

        public int getMapIndex() {
            return mapIndex;
        }

        public void setMapIndex(int mi) {
            mapIndex = mi;
            wordOffset = mapIndex*maxWordsPerMap;
            fireTableDataChanged();
        }

        public void dataChanged() {
            fireTableDataChanged();
        }

        public void setWindowData(long wordIndex) {
//            if (mappedMemoryHandler == null || (wordIndex > maxWordIndex)) return;

            long oldMapIndex = mapIndex;

            mapIndex = mappedMemoryHandler.getMapIndex(wordIndex);

            if (oldMapIndex == mapIndex) {
System.out.println("setWindowData: map index not changed = " + mapIndex );
                return;
            }
System.out.println("setWindowData: map index = " + mapIndex);
            fireTableDataChanged();
        }

        public int[] getMapRowCol(long wordIndex) {
            if (wordIndex > maxWordIndex) return null;

            int[] dat = new int[3];
            // map index
            dat[0] = (int) (wordIndex*4/maxMapByteSize);

            int  byteIndex = (int) (wordIndex*4 - (dat[0] * maxMapByteSize));
//            long byteIndexLong = (int) (wordIndex*4 - (dat[0] * maxMapByteSize));
//System.out.println("getMapRowCol: in byteIndex = " + byteIndex + ", long bi = " + byteIndexLong);
//System.out.println("getMapRowCol: wordIndex*4 = " +(wordIndex*4) + ", offset = " + (dat[0] * maxMapByteSize));
            // row
            dat[1] =  byteIndex / bytesPerRow;
            // column
            dat[2] = (byteIndex % bytesPerRow)/4 + 1;
//System.out.println("getMapRowCol: row = " + dat[1] + ", col = " + dat[2]);

            return dat;
        }


        public void highListCell(int row, int col) {
            dataTableRenderer.setHighlightCell(null, row, col);
            fireTableCellUpdated(row, col);
        }

        public void clearHighLights() {
            dataTableRenderer.clearHighlights();
        }

        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
//            if (mappedMemoryHandler == null) return 0;

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

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
//            if (mappedMemoryHandler == null) return null;

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
//            System.out.println("getVal: row = " + row + ", col = " + col + ", index = " + index);
            return String.format("0x%08x", mappedMemoryHandler.getInt(index));
        }

        public long getLongValueAt(int row, int col) {
//            if (mappedMemoryHandler == null) return 0;

            // 1st column is row or integer #
            if (col == 0 || col == 6) {
                return 0;
            }

            long index = wordOffset + (row * 5) + col - 1;

            if (index > maxWordIndex) {
                return 0;
            }

            return (((long)mappedMemoryHandler.getInt(index)) & 0xffffffffL);
        }

        public Class getColumnClass(int c) {
            if (c == 0) return Long.class;
            return String.class;
        }

        public boolean isCellEditable(int row, int col) {
            return col > 1;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 6) {
                comments.put(row, (String)value);
            }
            fireTableCellUpdated(row, col);
        }
    }

    /** Set table's data. */
    void setTableData() {
        //MyTableModel model = (MyTableModel)dataTable.getModel();
        dataTableModel.fireTableDataChanged();
//System.out.println("Done adding data to table model");
    }

    /** Render used to change background color every Nth row. */
    static private class MyRenderer extends DefaultTableCellRenderer {
        int nthRow;
        Color alternateRowColor  = new Color(225, 235, 245);
        Color newForegroundColor = Color.red;
        ArrayList<int[]> highlightCells = new ArrayList<int[]>(20);

        public MyRenderer(int nthRow) {
            super();
            this.nthRow = nthRow;
        }

        private boolean isHighlightCell(int row, int col) {
            for (int[] i : highlightCells) {
                if (i[0] == row && i[1] == col) {
                    return true;
                }
            }
            return false;
        }

        public void clearHighlights() {
            highlightCells.clear();
        }

        public void setHighlightCell(Color color, int row, int col) {
            if (color != null) newForegroundColor = color;
            highlightCells.add(new int[] {row,col});
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {

            if (isSelected) {
                super.setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                super.setForeground(table.getForeground());

                if ((row+1)%nthRow == 0) {
                    super.setBackground(alternateRowColor);
                }
                else {
                    super.setBackground(table.getBackground());
                }
            }

            if (isHighlightCell(row, column)) {
                super.setForeground(newForegroundColor);
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
		d.width = (int) (fractionalSize * .6 * d.width);
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

		if ((lnfinfo == null) || (lnfinfo.length < 1)) {
			return;
		}

		String desiredLookAndFeel = "Windows";

		for (int i = 0; i < lnfinfo.length; i++) {
			if (lnfinfo[i].getName().equals(desiredLookAndFeel)) {
				try {
					UIManager.setLookAndFeel(lnfinfo[i].getClassName());
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				return;
			}
		}
	}



    /**
     * This is a class designed to handle access files with size greater than 2.1 GBytes.
     * Currently the largest size memory map that Java can handle
     * is Integer.MAX_VALUE which limits the use of memory mapping to looking at
     * files that size or smaller. This class circumvents this limit by viewing
     * large files as a collection of multiple memory maps.<p>
     *
     * Just a note about synchronization. This object is <b>NOT</b> threadsafe.
     */
    public class SimpleMappedMemoryHandler {

        private long fileSize;

        /** Max map size in bytes (200MB) */
        private final long maxMapSize = 200000000L;

        /** Byte order of data in buffer. */
        private ByteOrder order;

        /** Number of memory maps needed to fully map file. */
        private int mapCount;

        /** List containing each event's memory map. */
        private ArrayList<ByteBuffer> maps = new ArrayList<ByteBuffer>(20);


        /**
         * Constructor.
         *
         * @param channel   file's file channel object
         * @param order byte order of the data
         * @throws IOException   if could not map file
         */
        public SimpleMappedMemoryHandler(FileChannel channel, ByteOrder order)
                throws IOException {

            this.order = order;

            long remainingSize = fileSize = channel.size();
            if (fileSize < 4) {
                throw new IOException("file too small at " + fileSize + " byes");
            }
            long sz, offset = 0L;
            ByteBuffer memoryMapBuf = null;

            if (remainingSize < 1) return;

            // Divide the memory into chunks or regions
            while (remainingSize > 0) {
                // Break into chunks of 2^30
                sz = Math.min(remainingSize, maxMapSize);
                System.out.println("mmapHandler: remaining size = " + remainingSize +
                                   ", map size = " + sz + ", mapCount = " + mapCount);

                memoryMapBuf = channel.map(FileChannel.MapMode.READ_ONLY, offset, sz);
                memoryMapBuf.order(order);

                // Store the map
                maps.add(memoryMapBuf);

                offset += sz;
                remainingSize -= sz;
                mapCount++;
            }
        }


        /**
         * Constructor.
         * @param buf buffer to analyze
         */
        public SimpleMappedMemoryHandler(ByteBuffer buf) {
            if (buf == null) {
                return;
            }

            mapCount = 1;
            maps.add(buf);
        }


        public long getFileSize() {
            return fileSize;
        }

        public int getMapSize(int mapIndex) {
            if (mapIndex < 0 || mapIndex > mapCount - 1) {
                return 0;
            }
            return maps.get(mapIndex).limit();
        }


        /**
         * Get the number of memory maps used to fully map file.
         *
         * @return number of memory maps used to fully map file.
         */
        public int getMapCount() {return mapCount;}


        /**
         * Get the first memory map - used to map the beginning of the file.
         *
         * @return first memory map - used to map the beginning of the file.
         */
        public ByteBuffer getFirstMap() {return maps.get(0);}


        /**
         * Set the byte order of the data.
         * @param order byte order of the data.
         */
        public void setByteOrder(ByteOrder order) {
            if (this.order == order) return;
            this.order = order;
            for (ByteBuffer map : maps) {
                map.order(order);
            }
        }


        /**
         * Get the indicated memory map.
         * @param mapIndex index into map holding memory mapped ByteBuffers
         * @return indicated memory map.
         */
        public ByteBuffer getMap(int mapIndex) {
            if (mapIndex < 0 || mapIndex > mapCount - 1) {
                return null;
            }
            return maps.get(mapIndex);
        }


        public int getInt(int byteIndex, int mapIndex) {
            ByteBuffer buf = getMap(mapIndex);
            if (buf == null) return 0;
            return buf.getInt(byteIndex);
        }



        public int getInt(long wordIndex) {
            int mapIndex  = (int) (wordIndex*4/maxMapSize);
            int byteIndex = (int) (wordIndex*4 - (mapIndex * maxMapSize));
            ByteBuffer buf = getMap(mapIndex);
            if (buf == null) return 0;
//            System.out.println("getInt: index = " + index + ", region = " + region + ", mapIndex = " + mapIndex);
            return buf.getInt(byteIndex);
        }


        public int getMapIndex(long wordIndex) {
            return (int) (wordIndex*4/maxMapSize);
        }


    }

}