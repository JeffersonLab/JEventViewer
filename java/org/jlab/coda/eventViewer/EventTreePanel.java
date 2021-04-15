package org.jlab.coda.eventViewer;

import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.jevio.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This is a simple GUI that displays an evio event in a tree.
 * It allows the user to open event files and dictionaries,
 * and go event-by-event showing the event in a JTree.<p>
 *
 * The text in a tree associated with a particular evio structure is
 * determined by evio's BaseStructure.toString() method. So if you want
 * to change it you must modify this evio code. Note it is the
 * BaseStructure.getDescription() method that returns the dictionary
 * contents for that structure.
 *
 * @author Heddle
 * @author Timmer
 */
public class EventTreePanel extends JPanel implements TreeSelectionListener {

    /** Panel for displaying header information. */
    private HeaderPanel headerPanel = new HeaderPanel();

    /** The tree containing all evio structure information. */
	private JTree tree;

    /** The table containing event data. */
    private JTable dataTable;

    /** The widget containing Composite text data. */
    private JTextArea dataText;

    /** Widget allowing scrolling of tree widget. */
    private JScrollPane treePane;

    /** Widget allowing scrolling of table widget. */
    private JScrollPane tablePane;

    /** Widget allowing scrolling of text widget.
     * Switch with tablePane when displaying Composite data. */
    private JScrollPane textPane;

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

    /** View data or view dictionary? */
    boolean viewData = true;

    /** Are we currently viewing numerical data or Composite data in XML (text) ? */
    boolean viewText = false;

    // A couple other things that must be viewed but passed thru EventTreeMenu

    /** Evio version of data being viewed. */
    private int evioVersion;

    /** What type of data compression? */
    private CompressionType dataCompressionType;




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
     * Set the evio version of data being viewed.
     * @param version evio version of data being viewed.
     */
    public void setEvioVersion(int version) {evioVersion = version;}

    /**
     * Set the compression type of data being viewed.
     * @param type compression type of data being viewed.
     */
    public void setDataCompressionType(CompressionType type) {dataCompressionType = type;}

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
     * Switch the gui's data view between numerical data and text data.
     * @param toText switch to viewing Composite data text
     */
    void switchDataAndText(boolean toText) {
        if (toText) {
            if (viewText) return;
            splitPane.remove(tablePane);
            splitPane.add(textPane);
            viewText = true;
            // set the divider location to correct place
            if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                splitPane.setDividerLocation(horizontalDividerPosition);
            }
            else {
                splitPane.setDividerLocation(verticalDividerPosition);
            }
        }
        else if (viewText) {
            splitPane.remove(textPane);
            splitPane.add(tablePane);
            viewText = false;
            if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                splitPane.setDividerLocation(horizontalDividerPosition);
            }
            else {
                splitPane.setDividerLocation(verticalDividerPosition);
            }
        }
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

    /** Set table's data. */
    void setTableData(String[] data) {
        MyTableModel model = (MyTableModel)dataTable.getModel();
        model.setTableData(data);
    }


    //--------------------------------------------------
    // End of table stuff
    //--------------------------------------------------

    /** Set text display's data. */
    void setTextData(String[] data) {

        dataText.setText("");

        if (data == null || data.length < 1) {
            return;
        }

        for (String s : data) {
            dataText.append(s);
            dataText.append("\n");
        }

        // Make top of text visible
        dataText.moveCaretPosition(0);
    }

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


        // Set up the text widget for displaying Composite format data
        // which is presented to us as a single XML string.
        dataText = new JTextArea(50,50); // hints to rows & cols
        dataText.setEditable(false);
        textPane = new JScrollPane(dataText);


        // Set up the table widget for displaying data
        MyRenderer renderer = new MyRenderer(5);
        renderer.setHorizontalAlignment(SwingConstants.CENTER);

        MyTableModel tableModel = new MyTableModel();
        tableModel.setFirstColLabel("Position");

        dataTable = new JTable(tableModel);
        dataTable.setDefaultRenderer(String.class, renderer);
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataTable.setSelectionBackground(Color.yellow);
        dataTable.setSelectionForeground(Color.black);
        dataTable.setModel(tableModel);
        renderer.setTableModel(tableModel);

        // Define class to get the text alignment of table header correct
        class MyRender extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table,Object value,boolean isSelected,boolean hasFocus,int row,int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (column == 0) {
                    setHorizontalAlignment(SwingConstants.RIGHT);
                } else if (column == 6) {
                    setHorizontalAlignment(SwingConstants.LEFT);
                } else {
                    setHorizontalAlignment(SwingConstants.CENTER);
                }
                return this;
            }
        }

        MyRender tableHeaderRenderer = new MyRender();
        dataTable.getTableHeader().setDefaultRenderer(tableHeaderRenderer);

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
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
        renderer.setTextSelectionColor(Color.black);
        renderer.setBackgroundSelectionColor(Color.yellow);

        return new JScrollPane(tree);
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
		headerPanel.setHeader(structure, evioVersion, dataCompressionType);

        // Old selection is not remembered
        structureSelection.clear();

        if (!structure.isLeaf()) {
            setTableData(null);
            setTextData(null);
        }
        else {
            int pos;

            // Store information about the current selection, if any
            TreePath selectionPath = tree.getSelectionPath();

            if (selectionPath != null) {
                // Root is the first element, etc.
                Object[] pathItems = selectionPath.getPath();

                // Pull info out of tree
                if (pathItems.length > 0) {
                    for (int i=0; i < pathItems.length; i++) {
                        BaseStructure bs = (BaseStructure) pathItems[i];
                        // Find what # child we are by looking at all kids of our parent
                        pos = 0;
                        BaseStructure parent = bs.getParent();
                        // If null parent, pos = 0
                        if (parent != null) {
                            Iterator<BaseStructure> iter = parent.getChildrenList().iterator();
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

            int counter=0;
			BaseStructureHeader header = structure.getHeader();

            switch (header.getDataType()) {
			case DOUBLE64:
                switchDataAndText(false);
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
                switchDataAndText(false);
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
                switchDataAndText(false);
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
                switchDataAndText(false);
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
                switchDataAndText(false);
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
                switchDataAndText(false);
				byte bytedata[] = structure.getByteData();
				if (bytedata != null) {
                    String[] stringData = new String[bytedata.length];
					for (byte i : bytedata) {
                        if (intsInHex) {
                            stringData[counter++] = String.format("%#04x", i);
                        }
                        else {
                            stringData[counter++] = String.format("%4d", i);
                        }
					}
                    setTableData(stringData);
				}
				break;

			case CHARSTAR8:
                String[] stringData = structure.getStringData();
                // How are we going to display the string data?
                // If this is an array of short strings, the table is fine.
                // If the strings are long or contain new lines, then the
                // dataText widget is much better.
                boolean inTable = true;
                if (stringData != null && stringData.length > 0) {
                    int len = stringData[0].length();
                    int max = len > 100 ? 100 : len-1;
                    // If # chars > 50 or first 100 chars contain newline, use dataText
                    if (len > 50 || stringData[0].substring(0,max).contains("\n")) {
                        inTable = false;
                    }
                }

                if (inTable) {
                    switchDataAndText(false);
                    setTableData(structure.getStringData());
                }
                else {
                    switchDataAndText(true);
                    setTextData(stringData);
                }
				break;

            case COMPOSITE:
                try {
                    switchDataAndText(true);
                    CompositeData[] cData = structure.getCompositeData();
                    if (cData != null) {
                        stringData = new String[cData.length];
                        for (CompositeData cd : cData) {
                            stringData[counter++] = cd.toXML(intsInHex);
                        }
                        setTextData(stringData);
                    }
                }
                catch (EvioException e) {
                    // internal format error
                }
                break;

            default:

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

            kid = parent.getChildrenList().get(info.pos);

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
			headerPanel.setHeader(event, evioVersion, dataCompressionType);
		    expandAll();

            // Get the current TreePath, if any,
            // that will duplicate the previous selection.
            TreePath newSelection = getNewSelectionPath();
            if (newSelection == null) {
                // If none, show no data
                setTableData(null);
                setTextData(null);
            }
            else {
                tree.setSelectionPath(newSelection);
            }
        }
		else {
			tree.setModel(null);
			headerPanel.setHeader(null, evioVersion, dataCompressionType);
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