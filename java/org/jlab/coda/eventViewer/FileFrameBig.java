package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
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
public class FileFrameBig extends JFrame  {

    /** The table containing event data. */
    private JTable dataTable;

    private MyTableModel dataTableModel;

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

    /** A button for selecting the next set of rows/file-data. */
    private JButton nextButton;
    /** A button for selecting previous set of rows/file-data. */
    private JButton prevButton;
    /** A button for jumping to the next block header occurrence. */
    private JButton nextBlockButton;
    /** A button for jumping to the previous block header occurrence. */
    private JButton prevBlockButton;

    private JButton searchButtonNext;
    private JButton searchButtonPrev;
    private JButton gotoButton;
    private JComboBox<String> searchStringBox;
    private JSpinner currentEvent;
    private JLabel messageLabel;

    private JRadioButton wordValueButton;
    private JRadioButton wordIndexButton;
    private JRadioButton evioBlockButton;
    private JRadioButton evioFaultButton;
    private JRadioButton pageScrollButton;
    private ButtonGroup  radioGroup;

    /** Row to start searching (starts at 0). */
    private int searchStartRow = 0;
    /** Col to start searching (starts at 1). */
    private int searchStartCol = 0;

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
                switchEndian();
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
                // remember which rows are selected
                int[] sRows = dataTable.getSelectedRows();
                MyTableModel tm = (MyTableModel)dataTable.getModel();
                tm.fireTableDataChanged();
                // re-select the rows
                if (sRows.length > 0) {
                    dataTable.setRowSelectionInterval(sRows[0], sRows[sRows.length-1]);
                }
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
    }


    /**
     * Jump up or down to the specified word in file.
     * It moves so that a full row is exactly at the top.
     * @param word byte index of data
     */
    private void scrollToIndex(long word) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Switch to correct map
        int[] mapRowCol = dataTableModel.getMapRowCol(word);
        if (mapRowCol == null) {
            JOptionPane.showMessageDialog(this, "Entry exceeded file size", "Return",
                    JOptionPane.INFORMATION_MESSAGE);

            return;
        }
        System.out.println("map = " + mapRowCol[0] + ", row = " + mapRowCol[1] +
                           ", col " + mapRowCol[2]);
        dataTableModel.setMapIndex(mapRowCol[0]);

        // Where we jump to at each button press
        int finalY = (mapRowCol[1] - 5)*dataTable.getRowHeight();
        Rectangle rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
        searchStartRow = mapRowCol[1];

        // Select row of word
        dataTable.setRowSelectionInterval(searchStartRow,searchStartRow);

        dataTable.scrollRectToVisible(rec);
    }



    /**
     * Jump up or down to the next evio block header rows.
     * It moves so that a full row is exactly at the top.
     * @param down {@code true}  if going to end of file,
     *             {@code false} if going to top of file
     * @param findValue value of word to find
     * @param comments comments to add to found value's row
     */
    private void scrollToAndHighlight(boolean down, long findValue, String comments) {
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
        int row, finalY, rowY, viewY;
        boolean atTop = pt.y == 0;
        boolean atBottom = atEnd(viewport);
        boolean foundValue = false;

        out:
        while (true) {
            // Value which depends on the map being used.
            // The last map will most likely be smaller.
            int rowCount = dataTableModel.getRowCount();

            if (down) {
                // Where do we start the search?

                // If last val found is in last col
                if (searchStartCol == 5) {
                    // If there is no next row ...
System.out.println("searchStartCol = 5 so, searchStartRow = " + searchStartRow +
                    ", rowCount -1  = " + (rowCount -1));
                    if (searchStartRow == rowCount-1) {
                        row = rowCount-1;
                        // This will skip over while() and go to next map
                        searchStartCol = 6;
                    }
                    else {
                        // Start at end of next row
                        row = ++searchStartRow;
                        searchStartCol = 1;
System.out.println("searchStartCol = 5 so, searchStartRow = " + searchStartRow +
                                            ", searchStartCOl  = " + searchStartCol);
                    }
                }
                else {
                    // Start at the row of the last value we found
                    row = searchStartRow;
                    // Start after the column of the last value we found
                    searchStartCol++;
                }
System.out.println("\nStart looking at row = " + row + ", col = " + searchStartCol);

                while (row < rowCount) {

                    for (int col=searchStartCol; col < 6; col++) {
                        // Check value of column containing file data at given row
                        val = dataTableModel.getLongValueAt(row, col);

                        // If we found a match in a table's element ...
                        if (val == findValue)  {
System.out.println("Found val # at row = " + row + ", col = " + col);
                            MyTableModel model = (MyTableModel)dataTable.getModel();
                            model.highListCell(row, col);

                            // Mark it in comments
                            if (comments != null) {
                                model.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTable.getRowHeight();
                            // Current view's top Y position
                            viewY = viewport.getViewPosition().y;

                            // If found value's row is currently visible ...
                            if (rowY >= viewY && rowY <= viewY + viewHeight) {
                                System.out.println("Do NOT change view");
                            }
                            else {
                                // Place found row 5 rows below view's top
                                finalY = (row - 5)*dataTableRowHeight;
                                rec = new Rectangle(pt.x, finalY, viewWidth, viewHeight);
                            }

                            // Select row of found value
                            dataTable.setRowSelectionInterval(row,row);

                            // Set row & col to start next search
                            searchStartRow = row;
                            searchStartCol = col;

                            foundValue = true;
                            break out;
                        }
                        else {
                            searchStartCol = 1;
                        }
                    }

                    // Go to next row
                    row++;
                }
            }
            else {
                // Where do we start the search?

                // If last val found is in first col
                if (searchStartCol == 1) {
                    // If there is no previous row ...
                    if (searchStartRow == 0) {
                        row = 0;
                    }
                    else {
                        // Start at end of previous row
                        row = --searchStartRow;
                        searchStartCol = 5;
                    }
                }
                else {
                    // Start at the row of the last value we found
                    row = searchStartRow;
                    // Start before the column of the last value we found
                    searchStartCol--;
                }
System.out.println("\nStart looking at row = " + row + ", col = " + searchStartCol);

                while (row >= 0) {
                    // In general, start with right-most col and go left
                    for (int col=searchStartCol; col > 0; col--) {
                        val = dataTableModel.getLongValueAt(row, col);

                        // If we found a match in a table's element ...
                        if (val == findValue)  {
System.out.println("Found val # at row = " + row + ", col = " + col);
                            MyTableModel model = (MyTableModel)dataTable.getModel();
                            model.highListCell(row, col);

                            // Mark it in comments
                            if (comments != null) {
                                model.setValueAt(comments, row, 6);
                            }

                            // Y position of row with found value
                            rowY = row*dataTableRowHeight;
                            // Current view's top Y position
                            viewY = viewport.getViewPosition().y;

                            // If found value's row is currently visible ...
                            if (rowY >= viewY && rowY <= viewY + viewHeight) {
System.out.println("Do NOT change view");
                            }
                            else {
                                // Place found row 5 rows above view's bottom
                                int numRowsViewed = viewHeight / dataTableRowHeight;
//System.out.println("rows viewed = " + numRowsViewed + ", final row = " + (row + numRowsViewed - 5));
                                finalY = (row - numRowsViewed + 6)*dataTableRowHeight;
                                rec = new Rectangle(pt.x, finalY, viewWidth, viewHeight);
                            }

                            // Select row of found value
                            dataTable.setRowSelectionInterval(row,row);

                            // Set row & col to start next search
                            searchStartRow = row;
                            searchStartCol = col;

                            foundValue = true;
                            break out;
                        }
                        else {
                            searchStartCol = 5;
                        }
                    }

                    // Go to previous row
                    row--;
                }
            }

            if (!foundValue) {
                if (down) {
                    System.out.println("Did NOT find val, at bottom = " + atBottom);
                    // See if there are more data (maps) to follow.
                    // If so, go to the next map and search there.
                    if (!dataTableModel.nextMap()) {
                        break;
                    }
                    searchStartRow = 0;
                    searchStartCol = 1;
                }
                else {
                    System.out.println("Did NOT find val, at top = " + atTop);
                    // See if there are more data (maps) before.
                    // If so, go to the previous map and search there.
                    if (!dataTableModel.previousMap()) {
                        break;
                    }
                    searchStartRow = dataTableModel.getRowCount() - 1;
                    searchStartCol = 5;
                }
                continue;
            }

            break;
        }

//        // Where we jump to at each button press
//        finalY = (searchStartRow - 5)*dataTable.getRowHeight();
//        int viewY = viewport.getViewPosition().y;
//        System.out.println("finalY = " + finalY + ", viewY = " + viewY + ", h = " +
//                                   viewport.getExtentSize().height);
//
//        if (finalY >= viewY && finalY <= viewY + viewport.getExtentSize().height) {
//            System.out.println("Do NOT change view");
//            return;
//        }
//
//        rec = new Rectangle(pt.x, finalY, dim.width, dim.height);

        if (!foundValue) {
            messageLabel.setText("No value found");
            return;
        }


//        // If next evio block header not found ...
//        if (rec == null) {
//            if (atTop) {
//                boolean topRowsSelected = false;
//                int[] sRows = dataTable.getSelectedRows();
//                if (sRows.length == 1 && sRows[0] == 0) {
//                    topRowsSelected = true;
//                }
//                // Error: if at top AND
//                //          trying to go down OR
//                //          trying to go up with no top rows selected
//                if (down || !topRowsSelected)  {
//                    messageLabel.setText("No evio block header found");
//                }
//            }
//            else if (atBottom) {
//                boolean botRowsSelected = false;
//                int[] sRows = dataTable.getSelectedRows();
//                if (sRows.length == 1 && (sRows[sRows.length-1] == dataTable.getRowCount()-1)) {
//                    botRowsSelected = true;
//                }
//                // Error: if at bottom AND
//                //          trying to up OR
//                //          trying to go down with no bottom rows selected
//                if (!down || !botRowsSelected)  {
//                    messageLabel.setText("No evio block header found");
//                }
//            }
//            // Error: if not at top or bottom
//            else {
//                messageLabel.setText("No evio block header found");
//            }
//
//            // Where we jump to at each button press
//            finalY = (searchStartRow - 5)*dataTable.getRowHeight();
//            rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
//        }

        if (rec != null) dataTable.scrollRectToVisible(rec);
    }


    private void handleWordValueSearch(boolean down, boolean findBlock) {
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

        if (l == 0xc0da0100L) {
            scrollToAndHighlight(down, l, "Block Header");
        }
        else {
            scrollToAndHighlight(down, l, null);
        }
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

        wordIndexButton = new JRadioButton("Word Index");
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

//        // Register a listener for the radio buttons
//        ActionListener al_radio = new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//            }
//        };
//        wordValueButton.addActionListener(al_radio);
//        wordIndexButton.addActionListener(al_radio);
//        evioBlockButton.addActionListener(al_radio);
//        evioFaultButton.addActionListener(al_radio);

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

                if (selectedItem == null || selectedItem.equals("")) {
                    addNewItem = false;
                }
                else if (numItems == 0) {
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
        JPanel searchButtonPanel = new JPanel();
        searchButtonPanel.setLayout(new GridLayout(1, 2, 10, 0));

        searchButtonPrev = new JButton(" < ");
        ActionListener al_search = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
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
        searchButtonPanel.add(searchButtonPrev);

        searchButtonNext = new JButton(" > ");
        ActionListener al_searchNext = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
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
        searchButtonPanel.add(searchButtonNext);
        searchButtonPanel.setPreferredSize(new Dimension(100, 25));

        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(searchButtonPanel);

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
        MyRenderer renderer = new MyRenderer(8);
        renderer.setHorizontalAlignment(JLabel.RIGHT);
        dataTable.setDefaultRenderer(String.class, renderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Start searching from last selected row
        dataTable.getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent e) {
                        if (e.getValueIsAdjusting()) return;
                        int lastRowToChange  = e.getLastIndex();
                        int firstRowToChange = e.getFirstIndex();
                        if (searchStartRow == firstRowToChange) {
                            searchStartRow = lastRowToChange;
                            searchStartCol = 5;
                        }
                        else {
                            searchStartRow = firstRowToChange;
                            searchStartCol = 1;
                        }
                        System.out.println("change row: first = " + firstRowToChange +
                                                   ", last = " + lastRowToChange);
                        System.out.println("Start search at row " + searchStartRow);
                    }
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
        private final String[] names = {"Word Index", "+1", "+2", "+3", "+4", "+5", "Comments"};
        private final String[] columnNames = {names[0], names[1], names[2],
                                              names[3], names[4], names[5],
                                              names[6]};


        public MyTableModel(long fileSize) {
            // Min file size = 4 bytes
            maxWordIndex = (fileSize-4L)/4L;
            mapCount = mappedMemoryHandler.getMapCount();
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
            fireTableDataChanged();
            System.out.println("Jumped to NEXT map " + mapIndex);
            return true;
        }

        public boolean previousMap() {
            if (mapIndex == 0) {
                return false;
            }

            mapIndex--;
            wordOffset = mapIndex*maxWordsPerMap;
            fireTableDataChanged();
            System.out.println("Jumped to PREV map " + mapIndex);
            return true;
        }

        public void setMapIndex(int mi) {
            mapIndex = mi;
            wordOffset = mapIndex*maxWordsPerMap;
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
            MyRenderer renderer = (MyRenderer)dataTable.getCellRenderer(row, col);
            renderer.setHighlightCell(null, row, col);
            fireTableCellUpdated(row, col);
        }

        public void clearHighLights() {
            MyRenderer renderer = (MyRenderer)dataTable.getCellRenderer(0,0);
            renderer.clearHighlights();
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