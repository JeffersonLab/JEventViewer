package org.jlab.coda.eventViewer;


import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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

    /** Buffer to store file data in. */
    private ByteBuffer dataBuffer;


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
        dataBuffer.order(order);

        // Convert bytes into hex strings
        IntBuffer intBuf = dataBuffer.asIntBuffer();
        String[] data = new String[dataBuffer.capacity()/4];
        for (int i=0; i < dataBuffer.capacity()/4; i++) {
            data[i] = String.format("%#010x", intBuf.get(i));
        }

        // Write into table
        setTableData(data);
    }


    /** Add a panel of viewed data to this frame. */
    private void addFileViewPanel(File file) {
        if (file == null) return;

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

        // Add data to table
        try {
            // Get file channel
            FileInputStream fileInputStream = new FileInputStream(file);
            FileChannel fileChannel = fileInputStream.getChannel();

            // Read file into byte buffer
            int bytes = (int) (fileChannel.size());
            bytes = 4 * (bytes/4);
            dataBuffer = ByteBuffer.allocateDirect(bytes);
            fileChannel.read(dataBuffer);
            dataBuffer.flip();

            // Convert bytes into hex strings
            IntBuffer intBuf = dataBuffer.asIntBuffer();
            String[] data = new String[bytes/4];
            for (int i=0; i < bytes/4; i++) {
                data[i] = String.format("%#010x", intBuf.get(i));
            }

            // Write into table
            setTableData(data);
        }
        catch (IOException e) {/* should not happen */}

        // Put table into panel and panel into frame
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(tablePane, BorderLayout.CENTER);

        this.add(panel);
    }


    //-------------------------------------------
    // Classes and methods to handle data table
    //-------------------------------------------


    /** This class describes the data table's data including column names. */
    static private class MyTableModel extends AbstractTableModel {
        /** Column names won't change. */
        String[] names = {"Row", "Data", "Comments"};
        String[] columnNames = {names[0], names[1], names[2]};
        Object[][] data;
        // Store original data string array for convenience
        String[] stringData;

        public int getColumnCount() {
            return columnNames.length;
        }

        public int getRowCount() {
            if (data == null) return 0;
            return data.length;
        }

        public String getColumnName(int col) {
            return columnNames[col];
        }

        public Object getValueAt(int row, int col) {
            if (data == null) return null;
            return data[row][col];
        }

        public Class getColumnClass(int c) {
            //return getValueAt(0, c).getClass();
            return String.class;
        }

        public boolean isCellEditable(int row, int col) {
            if (col > 1) return true;
            return false;
        }

        public void setValueAt(Object value, int row, int col) {
            data[row][col] = value;
            fireTableCellUpdated(row, col);
        }
    }

    /** Set table's data. */
    void setTableData(String[] data) {
        MyTableModel model = (MyTableModel)dataTable.getModel();

        if (data == null || data.length < 1) {
            model.data = null;
            model.fireTableDataChanged();
            return;
        }

        int rowCount = data.length;

        // If first time thru, initialize unchanging stuff
        if (model.stringData == null) {
            model.data = new Object[rowCount][3];

            for (int row=0; row < rowCount; row++) {
                model.data[row][0] = row+1;
                model.data[row][2] = null;
            }
        }

        model.stringData = data;

        for (int row=0; row < rowCount; row++) {
            model.data[row][1] = data[row];
        }

        model.fireTableDataChanged();
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