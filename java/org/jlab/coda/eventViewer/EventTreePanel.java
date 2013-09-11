package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This is a simple GUI that displays an evio event in a tree.
 * It allows the user to open event files and dictionaries,
 * and go event-by-event showing the event in a JTree.
 * @author Heddle
 * @author Timmer
 */
@SuppressWarnings("serial")
public class EventTreePanel extends JPanel implements TreeSelectionListener {

    /** Panel for displaying header information. */
    private HeaderPanel headerPanel = new HeaderPanel();

    /** The tree containing all evio structure information. */
	private JTree tree;

    /** The table containing event data. */
    private JTable dataTable;

    /** Widget allowing scrolling of tree widget. */
    private JScrollPane treePane;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Widget allowing scrolling of dictionary widget. */
    private JScrollPane dictionaryPane;

    /** Widget allowing side-by-side viewing of tree & data panes. */
    private JSplitPane splitPane;

    /** Text area showing dictionary xml. */
   	private JTextPane dictionaryArea;

    /** The current event. */
	private EvioEvent event;

    // View manipulating members

    /** View ints in hexadecimal or decimal? */
    private boolean intsInHex;

    /** View tree to left of or above data table? */
    private int orientation = JSplitPane.HORIZONTAL_SPLIT;

    /** Initial height of tree widget when split goes from left to right (VERTICAL_SPLIT) */
    int verticalDividerPosition = 400;

    /** Initial width of tree widget when split goes from top to bottom (HORIZONTAL_SPLIT) */
    int horizontalDividerPosition = 500;

    boolean viewData = true;



    /** Class helping to contain info about the currently selected evio structure. */
    private class SelectionInfo {
        /** Evio header tag. */
        int tag;
        /** When multiple children have the same tag, this indicates
         *  the position of the selected child (starting from 0). */
        int pos;

        SelectionInfo(int tag, int pos) {this.tag = tag; this.pos = pos;}
    }

    /** Info about the currently selected evio structure being viewed.
     *  Empty if none or if selection contains evio structures and not actual data.
     *  It's a linked list since structure is part of hierarchy. */
    LinkedList<SelectionInfo> structureSelection = new LinkedList<SelectionInfo>();



    /**
	 * Constructor for a simple tree viewer for evio files.
	 */
	public EventTreePanel() {
		setLayout(new BorderLayout());

		// add all the components
		addComponents();
	}

    /**
     * Get the selection path information list.
     * @return the selection path information list.
     */
    public LinkedList<SelectionInfo> getStructureSelection() {
        return structureSelection;
    }

    /**
     * Set wether integer data is displayed in hexidecimal or decimal.
     * @param intsInHex if <code>true</code> then display as hex, else deciaml
     */
    public void setIntsInHex(boolean intsInHex) {
        this.intsInHex = intsInHex;
    }

    /**
     * Get the panel displaying header information.
     * @return JPanel with evio header information.
     */
    public HeaderPanel getHeaderPanel() {
        return headerPanel;
    }

    /**
     * Refresh textArea display.
     */
    public void refreshDisplay() {
        valueChanged(null);
    }

    /**
     * Refresh description (dictionary) display.
     */
    public void refreshDescription() {
        headerPanel.setDescription(event);
    }


    /**
     * Set the orientation of the separating barrier between tree and data widgets.
     * @param orient orientation to set the barrier to
     */
    void setOrientation(int orient) {
        if (orient == orientation) {
            return;
        }
        splitPane.setOrientation(orient);

        if (orient == JSplitPane.HORIZONTAL_SPLIT) {
            splitPane.setDividerLocation(horizontalDividerPosition);
            orientation = JSplitPane.HORIZONTAL_SPLIT;
        }
        else {
            splitPane.setDividerLocation(verticalDividerPosition);
            orientation = JSplitPane.VERTICAL_SPLIT;
        }
        splitPane.updateUI();
    }


    /**
     * Get the orientation of the separating barrier between tree and data widgets.
     * @return orientation of the separating barrier between tree and data widgets.
     */
    int getOrientation() {return orientation;}


    /**
     * Switch the gui's view between data and any dictionary currently in use.
     */
    void switchDataAndDictionary() {
        // switch to viewing dictionary
        if (viewData) {
            splitPane.remove(tablePane);
            splitPane.add(dictionaryPane);
        }
        // switch to viewing data
        else {
            splitPane.remove(dictionaryPane);
            splitPane.add(tablePane);
        }

        // set the divider location to correct place
        if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
            splitPane.setDividerLocation(horizontalDividerPosition);
        }
        else {
            splitPane.setDividerLocation(verticalDividerPosition);
        }

        viewData = !viewData;
    }


    /**
     * Set the display to the dictionary currently in use.
     * @param xml string of xml representing the dictionary to be displayed
     */
    void setDictionaryText(String xml) {
        StyledDocument doc = dictionaryArea.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            if (xml == null || xml.length() < 1) {
                doc.insertString(0, "No dictionary in use", doc.getStyle("title"));
            }
            else {
                doc.insertString(0, "Dictionary:\n\n", doc.getStyle("title"));
                doc.insertString(13, xml, doc.getStyle("regular"));
            }
        }
        catch (BadLocationException e) {
        }
    }


    //-------------------------------------------
    // Classes and methods to handle data table
    //-------------------------------------------

    /** This class describes the data table's data including column names. */
    static private class MyTableModel extends AbstractTableModel {
        /** Column names won't change. */
        String[] names = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        String[] columnNames = {names[0], names[1], names[2], names[3], names[4]};
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

        public boolean isCellEditable(int row, int col) {return false;}

        public void setValueAt(Object value, int row, int col) {
            data[row][col] = value;
            fireTableCellUpdated(row, col);
        }
    }


    /** Increase the number of table's columns by 1. */
    void addTableColumn() {
        MyTableModel model = (MyTableModel)dataTable.getModel();

        int colCount = model.getColumnCount();
        if (colCount > 9) {
            return;
        }

        // Change column names
        model.columnNames = new String[colCount+1];
        System.arraycopy(model.names, 0, model.columnNames, 0, colCount+1);

         // Repackage data
        if (model.stringData != null && model.stringData.length > 0) {
            int dataItemCount = model.stringData.length;
            int newColCount = colCount + 1;
            int newRowCount = dataItemCount / newColCount;
            newRowCount = dataItemCount % newColCount > 0 ? newRowCount + 1 : newRowCount;
            model.data = new Object[newRowCount][newColCount];

            int counter = 0;
            out:
            for (int row=0; row < newRowCount; row++) {
                for (int col=0; col < newColCount; col++) {
                    model.data[row][col] = model.stringData[counter++];
                    if (counter >= dataItemCount) {
                        break out;
                    }
                }
            }
        }

        model.fireTableStructureChanged();
    }


    /** Reduce the number of table's columns by 1. */
    void removeTableColumn() {
        MyTableModel model = (MyTableModel)dataTable.getModel();

        int colCount = model.getColumnCount();
        if (colCount < 2) {
            return;
        }

        // Change column names
        model.columnNames = new String[colCount-1];
        System.arraycopy(model.names, 0, model.columnNames, 0, colCount-1);

        // Repackage data
        if (model.stringData != null && model.stringData.length > 0) {
            int dataItemCount = model.stringData.length;
            int newColCount = colCount - 1;
            int newRowCount = dataItemCount / newColCount;
            newRowCount = dataItemCount % newColCount > 0 ? newRowCount + 1 : newRowCount;
            model.data = new Object[newRowCount][newColCount];

            int counter = 0;
            out:
            for (int row=0; row < newRowCount; row++) {
                for (int col=0; col < newColCount; col++) {
                    model.data[row][col] = model.stringData[counter++];
                    if (counter >= dataItemCount) {
                        break out;
                    }
                }
            }
        }

        model.fireTableStructureChanged();
    }

    /** Set table's data. */
    void setTableData(String[] data) {
        MyTableModel model = (MyTableModel)dataTable.getModel();

        if (data == null || data.length < 1) {
            model.data = null;
            model.fireTableDataChanged();
            return;
        }
        model.stringData = data;

        int colCount = model.columnNames.length;
        int rowCount = data.length / colCount;
        rowCount = data.length % colCount > 0 ? rowCount + 1 : rowCount;
        model.data = new Object[rowCount][colCount];

        int counter = 0, dataCount = data.length;

        out:
        for (int row=0; row < rowCount; row++) {
            for (int col=0; col < colCount; col++) {
                model.data[row][col] = data[counter];
                if (++counter >= dataCount) {
                    break out;
                }
            }
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
    //--------------------------------------------------
    // End of table stuff
    //--------------------------------------------------


    /**
     * Add the components to this panel.
	 */
	protected void addComponents() {
        treePane  = createTree();

        // Set up dictionary widget for latter viewing.
        // Redefining the 2 methods below allow for horizontal scrollbars to appear.
        // If you don't do that, then the text just wordwraps.
        dictionaryArea = new JTextPane() {
            public boolean getScrollableTracksViewportWidth() {
                return (getSize().width < getParent().getSize().width);
            }
            public void setSize(Dimension d) {
                if (d.width < getParent().getSize().width) {
                    d.width = getParent().getSize().width;
                }
                super.setSize(d);
            }
        };

        dictionaryPane = new JScrollPane(dictionaryArea);
        dictionaryArea.setEditable(false);
        StyledDocument doc = dictionaryArea.getStyledDocument();
        // Initialize 2 styles: regular & title
        Style def = StyleContext.getDefaultStyleContext().
                                 getStyle(StyleContext.DEFAULT_STYLE);
        // "regular" for displaying dictionary
        Style regular = doc.addStyle("regular", def);
        StyleConstants.setFontFamily(def, "SansSerif");
        // "title" for displaying title
        Style s = doc.addStyle("title", regular);
        StyleConstants.setFontSize(s, 14);
        StyleConstants.setBold(s, true);
        StyleConstants.setForeground(s, Color.BLUE);


        // Set up the table widget for displaying data
        dataTable = new JTable(new MyTableModel());
        MyRenderer renderer = new MyRenderer(5);
        renderer.setHorizontalAlignment(JLabel.RIGHT);
        dataTable.setDefaultRenderer(String.class, renderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Set the font to one that's fixed-width
        Font newFont = new Font(Font.MONOSPACED, Font.PLAIN, dataTable.getFont().getSize());
        dataTable.setFont(newFont);
        tablePane = new JScrollPane(dataTable);

        splitPane = new JSplitPane(orientation);
        splitPane.setLeftComponent(treePane);
        splitPane.setRightComponent(tablePane);

        // Make sure we remember where the user put
        // the divider when we switch orientations.
        ComponentListener cl = new ComponentAdapter() {
             public void componentResized(ComponentEvent e) {
                 if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                     horizontalDividerPosition = splitPane.getDividerLocation();
                 }
                 else {
                     verticalDividerPosition = splitPane.getDividerLocation();
                 }
             }
        };
        treePane.addComponentListener(cl);

        splitPane.setDividerLocation(horizontalDividerPosition);
        splitPane.setPreferredSize(new Dimension(1100, 700));

		add(splitPane, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(headerPanel, BorderLayout.CENTER);

        add(panel, BorderLayout.SOUTH);
	}

	/**
	 * Create the tree that will display the event. What is actually
     * returned is the scroll pane that contains the tree.
	 *
	 * @return the scroll pane holding the event tree.
	 */
	private JScrollPane createTree() {
		tree = new JTree();
		tree.setModel(null);

		tree.setBorder(BorderFactory.createTitledBorder(null, "EVIO event tree",
				TitledBorder.LEADING, TitledBorder.TOP, null, Color.blue));

		tree.putClientProperty("JTree.lineStyle", "Angled");
		tree.setShowsRootHandles(true);
		tree.setEditable(false);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.addTreeSelectionListener(this);

		JScrollPane scrollPane = new JScrollPane(tree);
		return scrollPane;
	}

	/**
	 * Selection event on our tree.
	 *
	 * @param treeSelectionEvent the causal event.
	 */
	public void valueChanged(TreeSelectionEvent treeSelectionEvent) {

		BaseStructure structure = (BaseStructure) tree.getLastSelectedPathComponent();

		if (structure == null) {
			return;
		}
		headerPanel.setHeader(structure);

        // Old selection is not remembered
        structureSelection.clear();

        if (!structure.isLeaf()) {
            setTableData(null);
        }
        else {
            int pos;

            // Store information about the current selection, if any
            TreePath selectionPath = tree.getSelectionPath();

            if (selectionPath != null) {
                // Root is the first element, etc.
                Object[] pathItems = selectionPath.getPath();

                // Pull info out of tree
                if (pathItems != null && pathItems.length > 0) {
                    for (int i=0; i < pathItems.length; i++) {
                        BaseStructure bs = (BaseStructure) pathItems[i];
                        // Find what # child we are by looking at all kids of our parent
                        pos = 0;
                        BaseStructure parent = bs.getParent();
                        // If null parent, pos = 0
                        if (parent != null) {
                            Iterator<BaseStructure> iter = parent.getChildren().iterator();
                            for (int j=0; iter.hasNext(); j++) {
                                BaseStructure bsKid = iter.next();
                                if (bsKid == bs) {
                                    pos = j;
                                    break;
                                }
                            }
                        }

                        structureSelection.add(new SelectionInfo(bs.getHeader().getTag(), pos));
                    }
                }
            }

            int counter=0, lineCounter=1, index=1;
			BaseStructureHeader header = structure.getHeader();

            switch (header.getDataType()) {
			case DOUBLE64:
				double doubledata[] = structure.getDoubleData();
				if (doubledata != null) {
                    String[] stringData = new String[doubledata.length];
					for (double d : doubledata) {
						stringData[counter++] = String.format("%15.11e", d);
					}
                    setTableData(stringData);
				}
				break;

			case FLOAT32:
				float floatdata[] = structure.getFloatData();
				if (floatdata != null) {
                    String[] stringData = new String[floatdata.length];
					for (float d : floatdata) {
						stringData[counter++] = String.format("%10.6e", d);
					}
                    setTableData(stringData);
				}

			case LONG64:
			case ULONG64:
				long longdata[] = structure.getLongData();
				if (longdata != null) {
                    String[] stringData = new String[longdata.length];
					for (long i : longdata) {
                        if (intsInHex) {
                            stringData[counter++] = String.format("%#018x", i);
                        }
                        else {
                            stringData[counter++] = String.format("%4d", i);
                        }
					}
                    setTableData(stringData);
				}
				break;

			case INT32:
			case UINT32:
				int intdata[] = structure.getIntData();
				if (intdata != null) {
                    String[] stringData = new String[intdata.length];
					for (int i : intdata) {
                        if (intsInHex) {
                            stringData[counter++] = String.format("%#010x", i);
                        }
                        else {
                            stringData[counter++] = String.format("%4d", i);
                        }
                    }
                    setTableData(stringData);
				}
				break;

			case SHORT16:
			case USHORT16:
				short shortdata[] = structure.getShortData();
				if (shortdata != null) {
                    String[] stringData = new String[shortdata.length];
					for (short i : shortdata) {
                        if (intsInHex) {
                            stringData[counter++] = String.format("%#06x", i);
                        }
                        else {
                            stringData[counter++] = String.format("%4d", i);
                        }
					}
                    setTableData(stringData);
				}
				break;

			case CHAR8:
			case UCHAR8:
				byte bytedata[] = structure.getByteData();
				if (bytedata != null) {
                    String[] stringData = new String[bytedata.length];
					for (byte i : bytedata) {
						stringData[counter++] = String.format("%4d", i);
					}
                    setTableData(stringData);
				}
				break;

			case CHARSTAR8:
                setTableData(structure.getStringData());
				break;

            case COMPOSITE:
                try {
                    CompositeData[] cData = structure.getCompositeData();
                    if (cData != null) {
                        String[] stringData = new String[cData.length];
                        for (int i=0; i < cData.length; i++) {
                            stringData[counter++] = cData[i].toString(intsInHex);
                        }
                        setTableData(stringData);
                    }
                }
                catch (EvioException e) {
                    // internal format error
                }
                break;

            }

		}
		tree.repaint();
	}


    /**
     * This method repaints the tree after a new dictionary is loaded.
     */
    void repaintTreeAfterNewDictionary() {
        // Unless the tree model is changed, the selections done on a tree stick
        // around, even if no longer visible and even if dictionaries and
        // therefore string representations of a tree's nodes change.
        // Practically this means that if a
        // selection is done on a short string, and the new dictionary has a
        // longer string for that node, then not all the new text will appear.
        // The long & short of it is, you need to set the model to null first,
        // then set it to the same event in order to refresh how things are
        // rendered in the JTree widget.

        // Remember any current selection
        TreePath currentSelection = tree.getSelectionPath();

        // Get the tree to repaint itself properly
        tree.setModel(null);
        if (event != null) {
            tree.setModel(event.getTreeModel());
        }
        expandAll();

        // Make sure the selection remains
        tree.setSelectionPath(currentSelection);
    }


    /**
     * Get the current TreePath, if any,
     * that will duplicate the previous selection.
     *
     * @return current TreePath, if any,
     *         that will duplicate the previous selection.
     */
    TreePath getNewSelectionPath() {
        // Duplicate the previous selection if any
        if (structureSelection.size() < 1) return null;

        // New event's root
        BaseStructure kid, parent = (BaseStructure)event.getTreeModel().getRoot();

        // Compare this root event's tag with previous root event's tag
        SelectionInfo info = structureSelection.get(0);
        if (info.tag != parent.getHeader().getTag()) {
            return null;
        }

        Object[] objs = new Object[structureSelection.size()];
        objs[0] = parent;

        for (int i=1; i < structureSelection.size(); i++) {
            info = structureSelection.get(i);

            // skip to info.pos #th kid
            if (info.pos + 1 > parent.getChildCount()) {
                return null;
            }

            kid = parent.getChildren().get(info.pos);

            if (info.tag != kid.getHeader().getTag()) {
                return null;
            }

            objs[i] = parent = kid;
        }
        return new TreePath(objs);
    }


    /**
	 * Get the currently displayed event.
	 *
	 * @return the currently displayed event.
	 */
	public EvioEvent getEvent() {
		return event;
	}

	/**
	 * Set the currently displayed event.
	 *
	 * @param event the currently displayed event.
	 */
	public void setEvent(EvioEvent event) {
		this.event = event;

		if (event != null) {
            tree.setModel(event.getTreeModel());
			headerPanel.setHeader(event);
		    expandAll();

            // Get the current TreePath, if any,
            // that will duplicate the previous selection.
            TreePath newSelection = getNewSelectionPath();
            if (newSelection == null) {
                // If none, show no data
                setTableData(null);
            }
            else {
                tree.setSelectionPath(newSelection);
            }
        }
		else {
			tree.setModel(null);
			headerPanel.setHeader(null);
		}
	}


    /**
     * Expand all nodes.
     */
    public void expandAll() {
        if (tree != null) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        }
    }


}