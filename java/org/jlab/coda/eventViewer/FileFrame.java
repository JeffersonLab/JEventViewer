package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;

/**
 * This class implements a window that displays a file's bytes as hex, 32 bit integers.
 * @author timmer
 */
public class FileFrame extends JFrame  {

    /** The table containing event data. */
    private JTable dataTable;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Remember comments placed into 3rd column of table. */
    private HashMap<Integer,String> comments = new HashMap<Integer,String>();

    /** Look at file in big endian order by default. */
    private ByteOrder order = ByteOrder.BIG_ENDIAN;

    /** Menu item for switching data endianness. */
    private JMenuItem switchMenuItem;

    /** Buffer of memory mapped file. */
    private MappedByteBuffer mappedByteBuffer;
    private IntBuffer mappedIntBuffer;

    /** A button for selecting the next set of rows/file-data. */
    private JButton nextButton;
    /** A button for selecting previous set of rows/file-data. */
    private JButton prevButton;
    /** A button for jumping to the next block header occurrence. */
    private JButton nextBlockButton;
    /** A button for jumping to the previous block header occurrence. */
    private JButton prevBlockButton;

    private JLabel messageLabel;

    /** When hopping evio header blocks, the row of the current block's magic #.
     *  Don't start looking before the 8th row (index starts at 0). */
    private int currentMagicRow = 6;

    /**
	 * Constructor for a simple viewer for a file's bytes.
	 */
	public FileFrame(File file) {
		super(file.getName() + " bytes");
		initializeLookAndFeel();

		// set the close to call System.exit
		WindowAdapter wa = new WindowAdapter() {
			public void windowClosing(WindowEvent event) {
                FileFrame.this.dispose();
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
		sizeToScreen(this, 0.85);

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
                FileFrame.this.dispose();
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

        mappedByteBuffer.order(order);
        mappedIntBuffer = mappedByteBuffer.asIntBuffer();

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
            rec = new Rectangle(pt.x,  pt.y + deltaY, dim.width, dim.height);
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
//System.out.println("Table at top, stop scrolling");
                return;
            }
        }

        dataTable.scrollRectToVisible(rec);
    }


    /**
     * Jump up or down to the next evio block header rows.
     * It moves so that a full row is exactly at the top.
     * @param down {@code true} if going to end of file,
     *             {@code false} if going to top of file
     */
    private void scrollToBlock(boolean down) {
        JViewport viewport = tablePane.getViewport();

        // The location of the viewport relative to the table
        Point pt = viewport.getViewPosition();

        // View dimension
        Dimension dim = viewport.getExtentSize();

        // Find the top visible row
        int currentTopRow = findTopVisibleRow(viewport);

        String val;
        Rectangle rec = null;
        int row, finalY;
        boolean atTop = pt.y == 0;
        boolean atBottom = atEnd(viewport);

        if (down) {
            // Start the new search past the last magic # we found
            row = currentMagicRow + 1;

            while (row < dataTable.getRowCount()) {
                // Check value of column containing file data at given row
                val = (String) dataTable.getValueAt(row, 1);

                if (val.equalsIgnoreCase("0xc0da0100"))  {
                    // Found last row of the header (magic #). View whole thing.

                    // Mark it in comments
                    MyTableModel model = (MyTableModel)dataTable.getModel();
                    model.setValueAt("EVIO Header >", row-7, 2);
                    model.setValueAt("EVIO Header <", row, 2);

                    // Where we jump to at each button press
                    finalY = (row - 7)*dataTable.getRowHeight();

                    rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
//System.out.println("Found magic # at row = " + row);

                    // Select the header as there may be more than one for
                    // a given view and may not be at the top of the view.
                    dataTable.setRowSelectionInterval(row-7,row);
                    currentMagicRow = row;

                    break;
                }
                row++;
            }
        }
        else {
            // Start the new search before the last magic # we found
            row = currentMagicRow - 1;

            while (row > 6) {
                val = (String) dataTable.getValueAt(row, 1);

                if (val.equalsIgnoreCase("0xc0da0100"))  {
                    // Mark it in comments
                    MyTableModel model = (MyTableModel)dataTable.getModel();
                    model.setValueAt("EVIO Header >", row-7, 2);
                    model.setValueAt("EVIO Header <", row, 2);

                    finalY = (row - 7)*dataTable.getRowHeight();

                    if (atBottom) {
//System.out.println("We're at the end");
                        // If we're at the end, there still may be a header
                        // fully contained above the current one but still in view.
                        // In this case, current top stays the same, just new
                        // selection.
                        if (row - 7 >= currentTopRow) {
                            finalY = pt.y;
                        }
                    }

                    rec = new Rectangle(pt.x, finalY, dim.width, dim.height);
//System.out.println("Found magic # at row = " + row);

                    dataTable.setRowSelectionInterval(row-7,row);
                    currentMagicRow = row;

                    break;
                }
                row--;
            }
        }


        // If next evio block header not found ...
        if (rec == null) {
            if (atTop) {
                boolean topRowsSelected = false;
                int[] sRows = dataTable.getSelectedRows();
                if (sRows.length == 8 && sRows[0] == 0) {
                    topRowsSelected = true;
                }
                // Error: if at top AND
                //          trying to go down OR
                //          trying to go up with no top rows selected
                if (down || (!down && !topRowsSelected))  {
                    messageLabel.setText("No evio block header found");
                }
            }
            else if (atBottom) {
                boolean botRowsSelected = false;
                int[] sRows = dataTable.getSelectedRows();
                if (sRows.length == 8 && (sRows[sRows.length-1] == dataTable.getRowCount()-1)) {
                    botRowsSelected = true;
                }
                // Error: if at bottom AND
                //          trying to up OR
                //          trying to go down with no bottom rows selected
                if (!down || (down && !botRowsSelected))  {
                    messageLabel.setText("No evio block header found");
                }
            }
            // Error: if not at top or bottom
            else {
                messageLabel.setText("No evio block header found");
            }
        }

        if (rec != null) dataTable.scrollRectToVisible(rec);
    }


    /** Add a panel controlling viewed data to this frame. */
    private void addControlPanel() {

        // Put browsing buttons into panel
        JPanel buttonPanel = new JPanel();
        Border border = new CompoundBorder(
                BorderFactory.createEtchedBorder(EtchedBorder.LOWERED),
                new EmptyBorder(5,5,5,5));
        buttonPanel.setBorder(border);

        // Hop to next batch of rows
        nextButton = new JButton(">");
        ActionListener al_next = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
                scrollToVisible(true);
            }
        };
        nextButton.addActionListener(al_next);
        buttonPanel.add(nextButton);

        // Hop to previous batch of rows
        prevButton = new JButton("<");
        ActionListener al_prev = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
                scrollToVisible(false);
            }
        };
        prevButton.addActionListener(al_prev);
        buttonPanel.add(prevButton);

        // Hop to next evio block header
        nextBlockButton = new JButton("Block >");
        ActionListener al_nextBlk = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
                scrollToBlock(true);
            }
        };
        nextBlockButton.addActionListener(al_nextBlk);
        buttonPanel.add(nextBlockButton);

        // Hop to previous evio block header
        prevBlockButton = new JButton("< Block");
        ActionListener al_prevBlk = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                messageLabel.setText(" ");
                scrollToBlock(false);
            }
        };
        prevBlockButton.addActionListener(al_prevBlk);
        buttonPanel.add(prevBlockButton);

        // Add error message widget
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BorderLayout());
        messageLabel = new JLabel(" ");
        messageLabel.setBorder(border);
        messageLabel.setForeground(Color.red);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        controlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(messageLabel, BorderLayout.SOUTH);

        this.add(controlPanel, BorderLayout.NORTH);
    }


    /** Add a panel of viewed data to this frame. */
    private void addFileViewPanel(File file) {
        if (file == null) return;

        try {
            // Map the file to get access to its data
            // with having to read the whole thing.
            FileInputStream fileInputStream = new FileInputStream(file);
            String path = file.getAbsolutePath();
            FileChannel fileChannel = fileInputStream.getChannel();
            long fileSize = fileChannel.size();

            mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileSize);
            mappedIntBuffer = mappedByteBuffer.asIntBuffer();
            // This object is no longer needed since we have the map, so close it
            fileChannel.close();
        }
        catch (IOException e) {/* should not happen */}


        // Set up the table widget for displaying data
        dataTable = new JTable(new MyTableModel());
        MyRenderer renderer = new MyRenderer(8);
        renderer.setHorizontalAlignment(JLabel.RIGHT);
        dataTable.setDefaultRenderer(String.class, renderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

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
    private class MyTableModel extends AbstractTableModel {
        /** Column names won't change. */
        String[] names = {"Row", "Data", "Comments"};
        String[] columnNames = {names[0], names[1], names[2]};

        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
            return mappedByteBuffer.limit()/4;
        }

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
            if (mappedByteBuffer == null) return null;

            // 1st column is row or integer #
            if (col == 0) {
                return row+1;
            }

            // Remember comments placed into 3rd column
            if (col == 2) {
                return comments.get(row);
            }

            // Read data from mapped memory buffer
            return String.format("0x%08x", mappedIntBuffer.get(row));
        }

        public Class getColumnClass(int c) {
            if (c == 0) return Integer.class;
            if (c == 1) return String.class;
            return String.class;
        }

        public boolean isCellEditable(int row, int col) {
            if (col > 1) return true;
            return false;
        }

        public void setValueAt(Object value, int row, int col) {
            if (col == 1) {
                mappedByteBuffer.putInt(row, (Integer) value);
            }
            else if (col == 2) {
                comments.put(row, (String)value);
            }
            fireTableCellUpdated(row, col);
        }
    }

    /** Set table's data. */
    void setTableData() {
        MyTableModel model = (MyTableModel)dataTable.getModel();
        model.fireTableDataChanged();
//System.out.println("Done adding data to table model");
    }

    /** Render used to change background color every Nth row. */
    static private class MyRenderer extends DefaultTableCellRenderer {
        int nthRow;
        Color alternateRowColor = new Color(225, 235, 245);

        public MyRenderer(int nthRow) {
            super();
            this.nthRow = nthRow;
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
		d.width = (int) (fractionalSize * .2 * d.width);
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

}