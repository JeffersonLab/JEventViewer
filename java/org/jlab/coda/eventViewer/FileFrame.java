package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * This class implements a window that displays a file's bytes as hex, 32 bit integers.
 * @author timmer
 */
public class FileFrame extends JFrame  {

    /** The table containing event data. */
    private JTable dataTable;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Look at file in big endian order by default. */
    private ByteOrder order = ByteOrder.BIG_ENDIAN;

    /** Menu item for switching data endianness. */
    private JMenuItem switchMenuItem;

    /** Buffer of memory mapped file. */
    private MappedByteBuffer mappedByteBuffer;
    private IntBuffer mappedIntBuffer;

    /** A button for selecting the next set of rows/file-data. */
    JButton nextButton;
    /** A button for selecting previous set of rows/file-data. */
    JButton prevButton;
    /** A button for jumping to the next block header occurrence. */
    JButton nextBlockButton;
    /** A button for jumping to the previous block header occurrence. */
    JButton prevBlockButton;


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
        addButtons();

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
                switchEndian();
            }
        };
        switchMenuItem = new JMenuItem("To little endian");
        switchMenuItem.addActionListener(al_switch);
        menu.add(switchMenuItem);

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


    /** Add a panel of viewed data to this frame. */
    private void addButtons() {
        // Put table into panel and panel into frame
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 2, 2, 2));
        nextButton = new JButton("Next >");
        panel.add(nextButton);
        prevButton = new JButton("< Prev");
        panel.add(prevButton);
        nextBlockButton = new JButton("Next Block");
        panel.add(nextBlockButton);
        prevBlockButton = new JButton("Prev Block");
        panel.add(prevBlockButton);

        this.add(panel, BorderLayout.NORTH);
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
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

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

        // Store original data string array for convenience
        String[] stringData;

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

            // Don't keep track of 3rd column
            if (col != 1) return null;

            // Read data from mapped memory buffer
            return String.format("0x %08x", mappedIntBuffer.get(row));
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
            mappedByteBuffer.putInt(row, (Integer)value);
            fireTableCellUpdated(row, col);
        }
    }

    /** Set table's data. */
    void setTableData() {
        MyTableModel model = (MyTableModel)dataTable.getModel();
        model.fireTableDataChanged();
System.out.println("Done adding data to table model");
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