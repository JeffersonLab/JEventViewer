package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.EventListener;
import java.util.HashMap;

/**
 * This class creates the menus used in the GUI.
 * @author Heddle
 * @author Timmer
 */
public class EventTreeMenu {

    //----------------------
    // gui stuff
    //----------------------

    /** A button for selecting "next" event. */
    JButton nextButton;

    /** A button for selecting "previous" event. */
    JButton prevButton;

	/** Menu item for exporting file to XML. */
	private JMenuItem xmlExportItem;

    /** Menu item for opening event file. */
    private JMenuItem openEventFile;

    /** Menu item allowing configuration of event sources.  */
    private JMenu eventSourceConfig;

    /** Menu item allowing filtering of events.  */
    private JMenu filterMenu;

    /** Menu item setting the number of the event to be displayed. */
    private JSpinner currentEvent;

    /**
     * Store values associated with currentEvent to help with
     * identifying the change that triggered the calling of its
     * ChangeListener.
     */
    private int currentEventNum, currentEventMax, currentEventMin;

    /** The panel that holds the tree and all associated widgets. */
	private EventTreePanel eventTreePanel;

    /**
     * Source of the evio events being displayed.
     * By default this GUI is setup to look at files.
     */
    private EventSource eventSource = EventSource.FILE;

    /** Number of event currently being displayed. */
    private int eventIndex;

    // Create new background colors
//    private Color bg1Color = new Color(245, 250, 255);
//    private Color bg2Color = new Color(225, 230, 255);
    private Color bg1Color = null;
    private Color bg2Color = null;

    //----------------------
    // File stuff
    //----------------------

     /** Last selected data file. */
    private String dataFilePath;

    /** Last selected dictionary file. */
    private String dictionaryFilePath = "";

    /** Last selected xml file to export event file into. */
    private String xmlFilePath;

    /** Filter so only files with specified extensions are seen in file viewer. */
    private FileNameExtensionFilter evioFileFilter;

    /** Remember if evio file extension filter was used last time. */
    boolean useEvioFileFilter = false;

    /** The reader object for the currently viewed evio file. */
    private EvioReader evioFileReader;

    //----------------------
    // Dictionary stuff
    //----------------------

    private JRadioButtonMenuItem fileItem = new JRadioButtonMenuItem("EvioFile");
    private JRadioButtonMenuItem  xmlItem = new JRadioButtonMenuItem("Xml");
    private JRadioButtonMenuItem cmsgItem = new JRadioButtonMenuItem("cMsg");
    private JRadioButtonMenuItem   etItem = new JRadioButtonMenuItem("Et");
    private JRadioButtonMenuItem   noItem = new JRadioButtonMenuItem("None");

    /**
     * Source of the dictionary used for displayed.
     * By default this uses the dictionary embedded
     * in the evio file containing events.
     */
    private DictionarySource dictionarySource = DictionarySource.NONE;

    /**
     * To avoid parsing of identical XML strings into dictionary objects
     * over and over again, just keep a hash of created dictionaries.
     */
    private HashMap<String, EvioXMLDictionary> dictionaryMap =
            new HashMap<String, EvioXMLDictionary>(32);

    //----------------------
    // cMsg stuff
    //----------------------

    /** Panel to handle cMsg communications object. */
    private JPanel cmsgPanel;

    /** Object to handle cMsg communications. */
    private cMsgHandler cmsgHandler;

    //----------------------
    // ET stuff
    //----------------------

    /** Panel to handle ET communications object. */
    private JPanel etPanel;

    /** Object to handle ET communications. */
    private EtHandler etHandler;

    /** Thread to update cMsg or ET list size in GUI. */
    private UpdateThread listSizeUpdateThread;

    //----------------------
    // ET & cMsg stuff
    //----------------------
    private JSpinner triggerType, qLimit;
    private JButton clearQ;
    private boolean   isListSizeOne;
    // custom colors
    private final Color darkGreen = new Color(0, 140, 0);
    private final Color darkRed   = new Color(160, 0, 0);
    private JLabel qSize;

    //----------------------------
    // General function
    //----------------------------
    EventInfoPanel eventInfoPanel;
	/**
	 * Listener list for structures (banks, segments, tagsegments) encountered while processing an event.
	 */
	private EventListenerList evioListenerList;


    /**
	 * Constructor. Holds the menus for a frame or internal frame that wants to manage a tree panel.
	 * @param eventTreePanel holds the tree and all associated the widgets.
	 */
	public EventTreeMenu(final EventTreePanel eventTreePanel, EventInfoPanel eventInfoPanel) {
        this.eventTreePanel = eventTreePanel;
        this.eventInfoPanel = eventInfoPanel;
	}

    /**
     * Get the main event display panel.
     * @return main event display panel.
     */
    public EventTreePanel getEventTreePanel() {
        return eventTreePanel;
    }

    public JMenu getEventSourceConfig() {
        return eventSourceConfig;
    }

    public cMsgHandler getCmsgHandler() {
        return cmsgHandler;
    }

    public void setCmsgHandler(cMsgHandler cmsgHandler) {
        this.cmsgHandler = cmsgHandler;
    }


    private void setNumberOfEventsColor(Color color) {
        qSize.setForeground(color);
    }



    private void displayCmsgEvent(EvioEvent event) {
        if (event != null) {
            eventTreePanel.setEvent(event);
            // If there's a dictionary in the this message, make it available.
            String xml = event.getDictionaryXML();
            if (xml != null) {
                EvioXMLDictionary dict = dictionaryMap.get(xml);
                if (dict == null) {
                    dict = (EvioXMLDictionary)NameProviderFactory.
                            createNameProvider(xml);
                    dictionaryMap.put(xml, dict);
                }

                // Store this dictionary as the cMsg dictionary
                DictionarySource.CMSG.setDictionary(dict);

                // Allow the new dictionary to be used
                cmsgItem.setEnabled(true);
            }
            else {
                // No dictionary associated with this cMsg message
                DictionarySource.CMSG.setDictionary(null);

                // If cMsg was chose as dictionary source, switch to NONE
                validateDictionarySource();

                // Don't allow cMsg dictionary (non-existent) to be used
                cmsgItem.setSelected(false);
                cmsgItem.setEnabled(false);
            }
            currentEvent.setValue(event.getEventNumber());
        }
    }


    private void displayEtEvent(EvioEvent event) {
        if (event != null) {
            eventTreePanel.setEvent(event);
            String xml = event.getDictionaryXML();
            if (xml != null) {
                EvioXMLDictionary dict = dictionaryMap.get(xml);
                if (dict == null) {
                    dict = (EvioXMLDictionary)NameProviderFactory.
                            createNameProvider(xml);
                    dictionaryMap.put(xml, dict);
                }

                DictionarySource.ET.setDictionary(dict);
                etItem.setEnabled(true);
            }
            else {
                DictionarySource.ET.setDictionary(null);
                validateDictionarySource();
                etItem.setSelected(false);
                etItem.setEnabled(false);
            }
            currentEvent.setValue(event.getEventNumber());
        }
    }


    private void setCmsgButtons() {
        if (isListSizeOne) {
            prevButton.setEnabled(false);
            nextButton.setEnabled(true);
        }
        else {
            if (cmsgHandler.getCurrentEventIndex() > 0) {
                prevButton.setEnabled(true);
            }
            else {
                prevButton.setEnabled(false);
            }

            if (cmsgHandler.getCurrentEventIndex() >= cmsgHandler.getListSize() - 1 ) {
                nextButton.setEnabled(false);
            }
            else {
                nextButton.setEnabled(true);
            }
        }
    }

    private void setEtButtons() {
        if (isListSizeOne) {
            prevButton.setEnabled(false);
            nextButton.setEnabled(true);
        }
        else {
            if (etHandler.getCurrentEventIndex() > 0) {
                prevButton.setEnabled(true);
            }
            else {
                prevButton.setEnabled(false);
            }

            if (etHandler.getCurrentEventIndex() >= etHandler.getListSize() - 1 ) {
                nextButton.setEnabled(false);
            }
            else {
                nextButton.setEnabled(true);
            }
        }
    }

    /**
     * This class is a thread which updates the number of events
     * existing in the queue and displays it every 1/2 second.
     */
    private class UpdateThread extends Thread {

        // Update queue size in GUI
        Runnable r = new Runnable() {

            public void run() {
                switch (eventSource) {
                    case CMSG:
                        if (cmsgHandler != null) {
                            int listSize = cmsgHandler.getListSize();

                            // Set the limits of the event # spinner (valid event #s to select)
                            SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                            model.setMaximum(listSize);
                            if (listSize > 0) {
                                currentEvent.setEnabled(true);
                                // Don't leave the widget value outside of its range or trouble!
                                if ((Integer)model.getValue() > 0) {
                                    model.setMinimum(1);
                                }
                            }

                            // Enable "next" button if more events come along
                            if (cmsgHandler.getCurrentEventIndex() < listSize - 1) {
                                nextButton.setEnabled(true);
                            }

                            // Update Q size widget
                            if (listSize >= cmsgHandler.getListLimit()) {
                                setNumberOfEventsColor(darkRed);
                                qSize.setText("Full  " + listSize);
                            }
                            else {
                                setNumberOfEventsColor(darkGreen);
                                qSize.setText("" + listSize);
                            }

                            // If we've redefined the "next" button when list limit = 1,
                            // then we load a new event every time next is hit.
                            if (isListSizeOne && listSize > 0) {
                                displayCmsgEvent(cmsgHandler.getNextEvent());
                            }
                        }
                        break;

                    case ET:
                        if (etHandler != null) {
                            int listSize = etHandler.getListSize();

                            SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                            model.setMaximum(listSize);
                            if (listSize > 0) {
                                currentEvent.setEnabled(true);
                                if ((Integer)model.getValue() > 0) {
                                    model.setMinimum(1);
                                }
                            }

                            if (etHandler.getCurrentEventIndex() <  listSize - 1) {
                                nextButton.setEnabled(true);
                            }

                            if (listSize >= etHandler.getListLimit()) {
                                setNumberOfEventsColor(darkRed);
                                qSize.setText("Full  " + listSize);
                            }
                            else {
                                setNumberOfEventsColor(darkGreen);
                                qSize.setText("" + listSize);
                            }

                            if (isListSizeOne && listSize > 0) {
                                displayEtEvent(etHandler.getNextEvent());
                            }
                        }
                        break;

                    default:

                }
            }
        };

        // update queue size in GUI every 1/2 second
        public void run() {
            while (true) {
                if (isInterrupted()) { break; }
                SwingUtilities.invokeLater(r);
                try { Thread.sleep(1000); }
                catch (InterruptedException e) { break; }
            }
        }
    }



    /**
     * Switch between different event sources (file, cmsg, et).
     */
    private void setEventSource(EventSource source) {

        // do nothing if same source already selected
        if (source == eventSource) {
            return;
        }

        // remember the current source
        eventSource = source;

        // clear display of any event
        eventTreePanel.setEvent(null);

        // Switch dictionary back to last one used by
        // this particular event source, else use none.
        setDictionarySource(eventSource.getDictionarySource());

        switch (source) {

            case CMSG:
                filterMenu.setEnabled(true);

                // show "cMsg config" menu item
                eventSourceConfig.setEnabled(true);
                eventSourceConfig.removeAll();
                eventSourceConfig.add(cmsgPanel);
                eventSourceConfig.setText("cMsg config");

                // turn menu items off/on
                openEventFile.setEnabled(false);
                if (xmlExportItem.isEnabled()) {
                    xmlExportItem.setEnabled(false);
                }


                // cmsgHandler should never be null ...
                ((qLimit.getModel())).setValue(cmsgHandler.getListLimit());
                // show event we we're looking at before
                int evIndex = cmsgHandler.getCurrentEventIndex() + 1;
                EvioEvent ev = cmsgHandler.getEvent(evIndex);
                if (ev != null) {
                    eventTreePanel.setEvent(ev);
                }

                eventInfoPanel.setSource("cMsg messages");
                int listSize = cmsgHandler.getListSize();

                // Update Q size widget
                if (listSize >= cmsgHandler.getListLimit()) {
                    setNumberOfEventsColor(darkRed);
                    qSize.setText("Full  " + listSize);
                }
                else {
                    setNumberOfEventsColor(darkGreen);
                    qSize.setText("" + listSize);
                }


                SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                if (listSize > 0) {
                    currentEvent.setEnabled(true);
                    if (evIndex > 0)  model.setMinimum(1);
                    else              model.setMinimum(0);
                }
                else {
                    currentEvent.setEnabled(false);
                    model.setMinimum(0);
                }
                model.setMaximum(listSize);
                model.setValue(evIndex);

                setCmsgButtons();

                qLimit.setEnabled(true);
                clearQ.setEnabled(true);

                // start thread that tells how many messages are in list
                if (listSizeUpdateThread == null) {
                    listSizeUpdateThread = new UpdateThread();
                    listSizeUpdateThread.start();
                }

                break;

            case ET:
                filterMenu.setEnabled(true);

                // Show "ET config" menu item
                eventSourceConfig.setEnabled(true);
                eventSourceConfig.removeAll();
                eventSourceConfig.add(etPanel);
                eventSourceConfig.setText("ET config");

                // Turn menu items off/on
                openEventFile.setEnabled(false);
                if (xmlExportItem.isEnabled()) {
                    xmlExportItem.setEnabled(false);
                }


                // etHandler should never be null ...
                ((qLimit.getModel())).setValue(etHandler.getListLimit());
                // Show event we we're looking at before
                evIndex = etHandler.getCurrentEventIndex() + 1;
                ev = etHandler.getEvent(evIndex);
                if (ev != null) {
                    eventTreePanel.setEvent(ev);
                }

                eventInfoPanel.setSource("ET buffers");
                listSize = etHandler.getListSize();

                // Update Q size widget
                if (listSize >= etHandler.getListLimit()) {
                    setNumberOfEventsColor(darkRed);
                    qSize.setText("Full  " + listSize);
                }
                else {
                    setNumberOfEventsColor(darkGreen);
                    qSize.setText("" + listSize);
                }

                model = (SpinnerNumberModel) currentEvent.getModel();
                if (listSize > 0) {
                    currentEvent.setEnabled(true);
                    if (evIndex > 0)  model.setMinimum(1);
                    else              model.setMinimum(0);
                }
                else {
                    currentEvent.setEnabled(false);
                    model.setMinimum(0);
                }
                model.setMaximum(listSize);
                model.setValue(evIndex);

                setEtButtons();

                qLimit.setEnabled(true);
                clearQ.setEnabled(true);

                // Start thread that tells how many messages are in list
                if (listSizeUpdateThread == null) {
                    listSizeUpdateThread = new UpdateThread();
                    listSizeUpdateThread.start();
                }

                break;

            case FILE:
                filterMenu.setEnabled(false);

                // interrupt list size update thread
                if (listSizeUpdateThread != null) {
                    listSizeUpdateThread.interrupt();
                    listSizeUpdateThread = null;
                }

                // turn menu items off/on
                eventSourceConfig.setText(" ");
                eventSourceConfig.setEnabled(false);
                openEventFile.setEnabled(true);

                // values for display
                String fileName = "";
                int eventCount = 0;

                if (evioFileReader == null) {
                    prevButton.setEnabled(false);
                    nextButton.setEnabled(false);
                }
                else {
                    if (eventIndex > 1) {
                        prevButton.setEnabled(true);
                    }
                    else {
                        prevButton.setEnabled(false);
                    }

                    try {
                        if (eventIndex >= evioFileReader.getEventCount()) {
                            nextButton.setEnabled(false);
                        }
                        else {
                            nextButton.setEnabled(true);
                        }
                    }
                    catch (IOException   e) {}
                    catch (EvioException e) {}

                    xmlExportItem.setEnabled(true);

                    // Switch data back to last file (which is still loaded)
                    fileName = dataFilePath;

                    // Get event count
                    try {
                        eventCount = evioFileReader.getEventCount();
                    }
                    catch (IOException   e) {e.printStackTrace();}
                    catch (EvioException e) { /* should never happen */ }

                    // switch back to last event viewed
                    try {
                        EvioEvent event = evioFileReader.parseEvent(eventIndex);
                        if (event != null) {
                            eventTreePanel.setEvent(event);
                        }
                    }
                    catch (IOException   e) {e.printStackTrace();}
                    catch (EvioException e) { /* should never happen */ }
                }

                // update display
                eventInfoPanel.setSource(fileName);

                model = (SpinnerNumberModel) currentEvent.getModel();
                model.setValue(eventIndex);
                model.setMaximum(eventCount);
                if (eventCount > 0) {
                    currentEvent.setEnabled(true);
                    if (eventIndex > 0) {
                        model.setMinimum(1);
                    }
                }
                else {
                    currentEvent.setEnabled(false);
                    model.setMinimum(0);
                }

                qSize.setText(""+eventCount);
                qLimit.setValue(eventCount);

                setNumberOfEventsColor(Color.black);
                qLimit.setEnabled(false);
                clearQ.setEnabled(false);

                break;

            default:
        }

    }

// TODO: synchronize this with setting event source ?
    /**
     * Switch between different dictionary sources (evioFile, xml, cMsg, et, none).
     * Set the current event source to use the given dictionary source.
     * If there is no associated dictionary with that source,
     * then use DictionarySource.NONE.
     *
     * @param dictSource source of dictionary to switch to.
     */
    private void setDictionarySource(DictionarySource dictSource) {

        if (dictSource == null) return;

        String dictText = "";
        String description = "";

        // Dictionary to begin using
        EvioXMLDictionary dictionary = dictSource.getDictionary();

        // If there is no actual dictionary associated
        // with this source, then switch to source = NONE.
        if (dictionary == null) {
            dictSource = DictionarySource.NONE;
        }
        else {
            dictText = dictionary.toXML();
        }
        dictionarySource = dictSource;
        eventSource.setDictionarySource(dictSource);

        switch (dictSource) {

            case EVIOFILE:
                fileItem.setEnabled(true);
                fileItem.setSelected(true);
                description = "from evio file";
                break;

            case XML:
                xmlItem.setEnabled(true);
                xmlItem.setSelected(true);
                description = dictionaryFilePath;
                break;

            case CMSG:
                cmsgItem.setEnabled(true);
                cmsgItem.setSelected(true);
                description = "from cMsg message";
                break;

            case ET:
                etItem.setEnabled(true);
                etItem.setSelected(true);
                description = "from ET event";
                break;

            case NONE:
                noItem.setSelected(true);
                description = "none";
                break;

            default:
        }

        NameProvider.setProvider(dictionary);
        eventInfoPanel.setDictionary(description);
        eventTreePanel.setDictionaryText(dictText);
        eventTreePanel.refreshDescription();
        eventTreePanel.repaintTreeAfterNewDictionary();
    }


    /**
     * The currently selected dictionary source may not be valid
     * if cMsg or ET was selected and the evio event being viewed has
     * no dictionary associated with it. In this case, set the
     * selected dictionary source to NONE.
     */
    private void validateDictionarySource() {

        if (dictionarySource == null) {
            return;
        }

        // Only worry if cMsg or ET dictionary source was chosen
        if (dictionarySource != DictionarySource.ET &&
            dictionarySource != DictionarySource.CMSG) {
            return;
        }

        // Dictionary to begin using
        EvioXMLDictionary dictionary = dictionarySource.getDictionary();

        // If there is a dictionary associated with this
        // source, then we don't need to worry about it.
        if (dictionary != null) {
            return;
        }

        // If no dictionary, switch source to NONE
        dictionarySource = DictionarySource.NONE;
        noItem.setSelected(true);

        NameProvider.setProvider(dictionary);
        eventInfoPanel.setDictionary("none");
        eventTreePanel.setDictionaryText("");
        eventTreePanel.refreshDescription();
        eventTreePanel.repaintTreeAfterNewDictionary();
    }


    /**
     * Create a panel to change events in viewer.
     */
    JPanel addEventControlPanel() {

        nextButton = new JButton("next >");
        nextButton.setEnabled(false);

        // next event menu item
        ActionListener al_next = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (eventSource) {
                    // If we're looking at a file, there are multiple events contained in it
                    case FILE:
                        if (evioFileReader != null) {
                            try {
                                if (eventIndex >= evioFileReader.getEventCount()) break;

                                EvioEvent event = evioFileReader.parseEvent(++eventIndex);
                                if (event != null)  {
                                    eventTreePanel.setEvent(event);
                                    SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                                    model.setValue(event.getEventNumber());
                                    // Do this here since "next" button is automatically
                                    // selected upon loading a file.
                                    model.setMinimum(1);
                                }

                                if (eventIndex > 1) prevButton.setEnabled(true);
                                if (eventIndex >= evioFileReader.getEventCount()) {
                                    nextButton.setEnabled(false);
                                }
                            }
                            catch (IOException e1) {
                                eventIndex--;
                                e1.printStackTrace();
                            }
                            catch (EvioException e1) {
                                eventIndex--;
                                e1.printStackTrace();
                            }
                            catch (Exception e1) {
                                e1.printStackTrace();
                            }
                        }
                        break;

                    // If we're looking at cMsg messages, there is a queue of evio events
                    // extracted from the messages.
                    case CMSG:
                        if (cmsgHandler != null) {
                            if (isListSizeOne) {
                                cmsgHandler.clearList();
                                eventTreePanel.setEvent(null);
                                currentEvent.setValue(0);
                                // next event will be automatically loaded into view by update thread
                            }
                            else {
                                displayCmsgEvent(cmsgHandler.getNextEvent());
                                setCmsgButtons();
                            }
                        }

                        break;

                    // If we're looking at ET buffers(events), there is a queue of evio events
                    // extracted from the buffers.
                    case ET:
                        if (etHandler != null) {
                            if (isListSizeOne) {
                                // stop old filling thread, if any
                                etHandler.stopFillingEventList();

                                etHandler.clearList();
                                eventTreePanel.setEvent(null);

                                // start a new one
                                etHandler.startFillingEventList();
                                currentEvent.setValue(0);

                                // next event will be automatically loaded into view by update thread
                            }
                            else {
                                if (!etHandler.hasNextEvent()) break;

                                displayEtEvent(etHandler.getNextEvent());
                                setEtButtons();
                            }
                        }
                        break;

                    default:

                }
            }
        };
        nextButton.addActionListener(al_next);

        prevButton = new JButton("< prev");
        prevButton.setEnabled(false);
        // previous event menu item
        ActionListener al_prev = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                switch (eventSource) {
                    // If we're looking at a file, there are multiple events contained in it
                    case FILE:
                        if (evioFileReader != null) {
                            try {
                                if (eventIndex < 2) break;

                                EvioEvent event = evioFileReader.parseEvent(--eventIndex);
                                if (event != null)  {
                                    eventTreePanel.setEvent(event);
                                    currentEvent.setValue(event.getEventNumber());
                                }

                                if (eventIndex < 2) prevButton.setEnabled(false);
                                if (eventIndex < evioFileReader.getEventCount()) {
                                    nextButton.setEnabled(true);
                                }
                            }
                            catch (IOException e1) {
                                eventIndex++;
                                e1.printStackTrace();
                            }
                            catch (EvioException e1) {
                                eventIndex++;
                                e1.printStackTrace();
                            }
                        }
                        break;

                    case CMSG:
                        if (cmsgHandler != null) {
                            if (cmsgHandler.getCurrentEventIndex() < 1) break;

                            displayCmsgEvent(cmsgHandler.getPrevEvent());
                            setCmsgButtons();
                        }
                        break;

                    case ET:
                        if (etHandler != null) {
                            if (etHandler.getCurrentEventIndex() < 1) break;

                            displayEtEvent(etHandler.getPrevEvent());
                            setEtButtons();
                        }
                        break;

                    default:

                }
            }
        };
        prevButton.addActionListener(al_prev);


        // Event # spinner listener
        ChangeListener currentEventListener = new ChangeListener() {

            public void stateChanged(ChangeEvent e) {

                try {
                    SpinnerNumberModel model = (SpinnerNumberModel) ((JSpinner) e.getSource()).getModel();
                    int eventNum = model.getNumber().intValue();
                    int max = (Integer) model.getMaximum();
                    int min = (Integer) model.getMinimum();

                    if (currentEventMax != max) {
                        currentEventMax = max;
                        return;
                    }
                    if (currentEventMin != min) {
                        currentEventMin = min;
                        return;
                    }
                    if (currentEventNum != eventNum) {
                        currentEventNum = eventNum;
                    }


                    switch (eventSource) {
                        // If we're looking at a file, go to the specified event number
                        case FILE:
                            if ((eventNum > 0) && (eventNum <= evioFileReader.getEventCount())) {
                                eventIndex = eventNum;
                                EvioEvent event = evioFileReader.gotoEventNumber(eventIndex);
                                if (event != null) {
                                    eventTreePanel.setEvent(event);
                                    currentEvent.setValue(event.getEventNumber());
                                }

                                if (eventIndex > 1) prevButton.setEnabled(true);
                                else                prevButton.setEnabled(false);

                                if (eventIndex >= evioFileReader.getEventCount()) {
                                    nextButton.setEnabled(false);
                                }
                                else {
                                    nextButton.setEnabled(true);
                                }
                            }
                            break;

                        // If we're looking at cMsg messages, there is a list of events.
                        // Can go forwards and backwards to various list elements.
                        case CMSG:
                            if (cmsgHandler != null) {
                                // Look at event 0 (nothing)
                                if (eventNum < 1) {
                                    eventTreePanel.setEvent(null);
                                    currentEvent.setValue(0);
                                    return;
                                }

                                if ((eventNum > 0) && (eventNum <= cmsgHandler.getListSize())) {
                                    displayCmsgEvent(cmsgHandler.getEvent(eventNum));
                                    setCmsgButtons();
                                }
                            }

                            break;

                        // If we're looking at ET events, there is a list of events.
                        // Can go forwards and backwards to various list elements.
                        case ET:
                            if (etHandler != null) {
                                // Look at event 0 (nothing)
                                if (eventNum < 1) {
                                    eventTreePanel.setEvent(null);
                                    currentEvent.setValue(0);
                                    return;
                                }

                                if ((eventNum > 0) && (eventNum <= etHandler.getListSize())) {
                                    displayEtEvent(etHandler.getEvent(eventNum));
                                    setEtButtons();
                                }
                            }
                            break;


                        default:
                    }
                }
                catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        };

        // Input for Q size limit (only adjustable for cMsg & ET, not File)
        JLabel evLabel = new JLabel("Event #");
        evLabel.setOpaque(true);
        evLabel.setBackground(bg1Color);
        evLabel.setForeground(Color.blue);
        evLabel.setHorizontalAlignment(JLabel.CENTER);

        currentEvent = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
        currentEvent.addChangeListener(currentEventListener);
        currentEvent.setEnabled(false);
        currentEvent.setMaximumSize(new Dimension(10, 20));

        clearQ = new JButton("clear");
        clearQ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                // switch limit color to green
                setNumberOfEventsColor(darkGreen);

                switch (eventSource) {
                    case CMSG:
                        cmsgHandler.clearList();
                        prevButton.setEnabled(false);
                        nextButton.setEnabled(false);
                        eventTreePanel.setEvent(null);
                        SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                        model.setMinimum(0);
                        model.setValue(0);
                        // Update Q size widget
                        setNumberOfEventsColor(darkGreen);
                        qSize.setText("0");

                        break;

                    case ET:
                        // stop old filling thread, if any
                        etHandler.stopFillingEventList();

                        etHandler.clearList();
                        prevButton.setEnabled(false);
                        nextButton.setEnabled(false);
                        eventTreePanel.setEvent(null);
                        model = (SpinnerNumberModel) currentEvent.getModel();
                        model.setMinimum(0);
                        model.setValue(0);
                        setNumberOfEventsColor(darkGreen);
                        qSize.setText("0");

                        // start a new one
                        etHandler.startFillingEventList();

                    default:
                }
            }
        });
        clearQ.setEnabled(false);
        clearQ.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Input for Q size limit (only adjustable for cMsg & ET, not File)
        JLabel qLabel = new JLabel(" Limit ");
        qLabel.setOpaque(true);
        qLabel.setBackground(bg2Color);
        qLabel.setHorizontalAlignment(JLabel.CENTER);

        // Set Q size limit
        qLimit = new JSpinner(new SpinnerNumberModel(100, 1, 1000, 1));
        qLimit.setEnabled(false);
        qLimit.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                SpinnerNumberModel model = (SpinnerNumberModel) ((JSpinner) e.getSource()).getModel();
                int limit = model.getNumber().intValue();

                switch (eventSource) {
                    case CMSG:
                        // Do we need to delete any events from the list?
                        int num2delete = cmsgHandler.getListSize() - limit;

                        // If so, newly added events (if limit is increased later)
                        // should start at a new #.
                        if (num2delete > 0) cmsgHandler.setEventNum(limit + 1);

                        int evIndex = cmsgHandler.getCurrentEventIndex();
                        cmsgHandler.setListLimit(limit);

                        // If we've removed the event we were
                        // looking at by reducing the list size ...
                        if (evIndex > limit - 1) {
                            cmsgHandler.resetCurrentEventIndex();
                            eventTreePanel.setEvent(null);
                            prevButton.setEnabled(false);
                            nextButton.setEnabled(true);
                        }
                        // If we've removed all > the event we are
                        // looking at by, there is no next event ...
                        else if (evIndex == limit - 1) {
                            nextButton.setEnabled(false);
                        }

                        // With only 1 in list, "prev" is meaningless and
                        // "next" is redefined to mean clear list and get (1) more.
                        if (limit < 2) {
                            isListSizeOne = true;
                            // If none left ...
                            if (evIndex < 0) {
                                cmsgHandler.clearList();
                            }
                            prevButton.setEnabled(false);
                            nextButton.setEnabled(true);
                        }
                        else {
                            isListSizeOne = false;
                        }

                        break;

                    case ET:
                        num2delete = etHandler.getListSize() - limit;
                        if (num2delete > 0) etHandler.setEventNum(limit + 1);

                        evIndex = etHandler.getCurrentEventIndex();
                        etHandler.setListLimit(limit);
                        if (evIndex > limit - 1) {
                            etHandler.resetCurrentEventIndex();
                            eventTreePanel.setEvent(null);
                            prevButton.setEnabled(false);
                            nextButton.setEnabled(true);
                        }
                        else if (evIndex == limit - 1) {
                            nextButton.setEnabled(false);
                        }

                        if (limit < 2) {
                            isListSizeOne = true;
                            if (evIndex < 0) {
                                etHandler.clearList();
                            }
                            prevButton.setEnabled(false);
                            nextButton.setEnabled(true);
                        }
                        else {
                            isListSizeOne = false;
                        }
                        break;

                    default:
                }
            }
        });

        JPanel qPanel = new JPanel(new BorderLayout());
        qPanel.add(qLabel, BorderLayout.WEST);
        qPanel.add(qLimit, BorderLayout.CENTER);

        // Borders
        Border blackLine = BorderFactory.createLineBorder(Color.black);
        Border lowerEtched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        Border empty = BorderFactory.createEmptyBorder(5,5,5,5);

        // These create nice frames
        CompoundBorder compound1 = BorderFactory.createCompoundBorder(blackLine, empty);
        CompoundBorder compound2 = BorderFactory.createCompoundBorder(lowerEtched, empty);

        // Input for Q size
        JLabel qsLabel = new JLabel(" Size   ");
        qsLabel.setOpaque(true);
        qsLabel.setBackground(bg2Color);
        qsLabel.setHorizontalAlignment(JLabel.CENTER);

        // Set Q size
        qSize = new JLabel("0");
        qSize.setOpaque(true);
        qSize.setBackground(Color.white);
        qSize.setHorizontalAlignment(JLabel.RIGHT);
        qSize.setBorder(compound1);

        JPanel qsPanel = new JPanel(new BorderLayout());
        qsPanel.add(qsLabel, BorderLayout.WEST);
        qsPanel.add(qSize, BorderLayout.CENTER);

        JPanel p1 = new JPanel(new GridLayout(2,1,0,5));
        p1.setBackground(bg2Color);
        p1.add(qPanel);
        p1.add(qsPanel);

        JPanel pq = new JPanel(new GridLayout(2,1,0,5));
        pq.setBackground(bg2Color);
        JLabel evqLabel = new JLabel("Event Q");
        evqLabel.setOpaque(true);
        evqLabel.setBackground(bg2Color);
        evqLabel.setHorizontalAlignment(JLabel.CENTER);
        evqLabel.setForeground(Color.blue);
        pq.add(evqLabel);
        pq.add(clearQ);

        JPanel p2 = new JPanel();
        BoxLayout bl = new BoxLayout(p2, BoxLayout.X_AXIS);
        p2.setLayout(bl);
        p2.setBackground(bg2Color);
        p2.setBorder(compound2);
        p2.add(pq);
        p2.add(Box.createHorizontalStrut(10));
        p2.add(p1);

        JPanel p3 = new JPanel();
        p3.setBackground(bg1Color);
        p3.setBorder(compound2);
        p3.setLayout(new GridLayout(2,3,5,5));   // rows, cols, hgap, vgap
        p3.add(evLabel);
        p3.add(currentEvent);
        p3.add(prevButton);
        p3.add(nextButton);

        JPanel p4 = new JPanel(new BorderLayout());
        p4.add(p3, BorderLayout.CENTER);
        p4.add(p2, BorderLayout.EAST);

        return p4;
    }


    /**
	 * Create the event menu.
	 *
	 * @return the event menu.
	 */
	public JMenu createEventMenu() {
		final JMenu menu = new JMenu(" Event ");
        menu.getPopupMenu().setLayout(new BoxLayout(menu.getPopupMenu(), BoxLayout.Y_AXIS));

        // Select between different evio event sources
        JLabel jl = new JLabel("Event Sources");
        jl.setHorizontalTextPosition(JLabel.CENTER);
        jl.setBorder(new EmptyBorder(3, 10, 3, 10));
        jl.setAlignmentX(Component.CENTER_ALIGNMENT);
        menu.add(jl);

        JRadioButtonMenuItem fileItem = new JRadioButtonMenuItem("File");
        JRadioButtonMenuItem cmsgItem = new JRadioButtonMenuItem("cMsg");
        JRadioButtonMenuItem   etItem = new JRadioButtonMenuItem("ET");

        EmptyBorder eBorder = new EmptyBorder(3,20,3,0);
        fileItem.setBorder(eBorder);
        cmsgItem.setBorder(eBorder);
        etItem.setBorder(eBorder);

        // action listener for selecting cmsg source
        ActionListener cmsgListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (cmsgPanel == null) {
                    cmsgHandler = new cMsgHandler();
                    cmsgPanel = cmsgHandler.createCmsgPanel();
                }
                setEventSource(EventSource.CMSG);
                // keep this menu up (displayed) so user can go to config item
                menu.doClick();
            }
        };
        cmsgItem.addActionListener(cmsgListener);

        // action listener for selecting ET source
        ActionListener etListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (etPanel == null) {
                    etHandler = new EtHandler();
                    etPanel = etHandler.createEtPanel();
                }
                setEventSource(EventSource.ET);
                menu.doClick();
            }
        };
        etItem.addActionListener(etListener);

        // action listener for selecting file source
        ActionListener fileListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setEventSource(EventSource.FILE);
                menu.doClick();
            }
        };
        fileItem.addActionListener(fileListener);

        ButtonGroup group = new ButtonGroup();
        group.add(fileItem);
        group.add(cmsgItem);
        group.add(  etItem);
        // file source selected by default
        group.setSelected(fileItem.getModel(), true);

        fileItem.setAlignmentX(Component.CENTER_ALIGNMENT);
        cmsgItem.setAlignmentX(Component.CENTER_ALIGNMENT);
        etItem.setAlignmentX(Component.CENTER_ALIGNMENT);

        menu.add(fileItem);
        menu.add(cmsgItem);
        menu.add(  etItem);

        menu.addSeparator();

        // Configure cMsg and/or ET connections
        eventSourceConfig = new JMenu("");
        eventSourceConfig.setText(" ");
        eventSourceConfig.setEnabled(false);
        menu.add(eventSourceConfig);

		return menu;
	}

    /**
     * Create the view menu.
     *
     * @return the view menu.
     */
    public JMenu createViewMenu() {
        final JMenu menu = new JMenu(" View ");

        // ints-viewed-as-hex menu item
        ActionListener al_hex = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JMenuItem item = (JMenuItem) e.getSource();
                String txt = item.getText();
                if (txt.equals("Hexidecimal")) {
                    eventTreePanel.setIntsInHex(true);
                    item.setText("Decimal");
                }
                else {
                    eventTreePanel.setIntsInHex(false);
                    item.setText("Hexidecimal");
                }
                eventTreePanel.refreshDisplay();
            }
        };

        JMenuItem hexItem = new JMenuItem("Hexidecimal");
        hexItem.addActionListener(al_hex);
        hexItem.setEnabled(true);
        menu.add(hexItem);


        // ints-viewed-as-hex menu item
        ActionListener al_dict = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JMenuItem item = (JMenuItem) e.getSource();
                String txt = item.getText();
                if (txt.equals("View Data")) {
                    eventTreePanel.switchDataAndDictionary();
                    item.setText("View Dictionary");
                }
                else {
                    eventTreePanel.switchDataAndDictionary();
                    item.setText("View Data");
                }
                eventTreePanel.refreshDisplay();
            }
        };

        JMenuItem dictItem = new JMenuItem("View Dictionary");
        dictItem.addActionListener(al_dict);
        dictItem.setEnabled(true);
        menu.add(dictItem);


        // menuitem to switch JSplitPane orientation
        ActionListener al_orient = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int orient = eventTreePanel.getOrientation();
                if (orient == JSplitPane.HORIZONTAL_SPLIT) {
                    eventTreePanel.setOrientation(JSplitPane.VERTICAL_SPLIT);
                }
                else {
                    eventTreePanel.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
                }
            }
        };
        JMenuItem orientItem = new JMenuItem("Change Orientation");
        orientItem.addActionListener(al_orient);
        orientItem.setEnabled(true);
        menu.add(orientItem);


        menu.addSeparator();


        // menuitem to add another column to data view
        ActionListener al_addCol = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                eventTreePanel.addTableColumn();
            }
        };
        JMenuItem addColItem = new JMenuItem("Add Column");
        addColItem.addActionListener(al_addCol);
        addColItem.setEnabled(true);
        menu.add(addColItem);


        // menuitem to remove another column from data view
        ActionListener al_subCol = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                eventTreePanel.removeTableColumn();
            }
        };
        JMenuItem subColItem = new JMenuItem("Remove Column");
        subColItem.addActionListener(al_subCol);
        subColItem.setEnabled(true);
        menu.add(subColItem);

        return menu;
    }


    /**
     * Create the view menu.
     *
     * @return the view menu.
     */
    public JMenu createDictionaryMenu() {
        final JMenu menu = new JMenu(" Dict ");

        // Select between different dictionary sources
        JLabel jl = new JLabel("Sources");
        jl.setBorder(new EmptyBorder(3,20,3,0));
        jl.setHorizontalTextPosition(JLabel.CENTER);
        menu.add(jl);

        EmptyBorder eBorder = new EmptyBorder(3,30,3,0);
        fileItem.setBorder(eBorder);
        xmlItem.setBorder(eBorder);
        cmsgItem.setBorder(eBorder);
        etItem.setBorder(eBorder);
        noItem.setBorder(eBorder);

        // action listener for selecting file source
        ActionListener embListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDictionarySource(DictionarySource.EVIOFILE);
                menu.doClick();
            }
        };
        fileItem.addActionListener(embListener);
        fileItem.setEnabled(false);

        // action listener for selecting ET source
        ActionListener xmlListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDictionarySource(DictionarySource.XML);
                menu.doClick();
            }
        };
        xmlItem.addActionListener(xmlListener);
        xmlItem.setEnabled(false);

        // action listener for selecting cmsg source
        ActionListener cmsgListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDictionarySource(DictionarySource.CMSG);
                // keep this menu up (displayed) so user can go to config item
                menu.doClick();
            }
        };
        cmsgItem.addActionListener(cmsgListener);
        cmsgItem.setEnabled(false);

        // action listener for selecting ET source
        ActionListener etListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDictionarySource(DictionarySource.ET);
                // keep this menu up (displayed) so user can go to config item
                menu.doClick();
            }
        };
        etItem.addActionListener(etListener);
        etItem.setEnabled(false);

        // action listener for selecting ET source
        ActionListener noListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setDictionarySource(DictionarySource.NONE);
                menu.doClick();
            }
        };
        noItem.addActionListener(noListener);

        ButtonGroup group = new ButtonGroup();
        group.add(fileItem);
        group.add( xmlItem);
        group.add(cmsgItem);
        group.add(  etItem);
        group.add(  noItem);
        // no dictionary selected by default
        group.setSelected(noItem.getModel(), true);

        menu.add(fileItem);
        menu.add( xmlItem);
        menu.add(cmsgItem);
        menu.add( etItem);
        menu.add( noItem);

        return menu;
    }

    /**
	 * Create the filter menu.
	 * @return the filter menu.
	 */
	public JMenu createFilterMenu() {
		filterMenu = new JMenu(" Filter ");
        filterMenu.getPopupMenu().setLayout(new BoxLayout(filterMenu.getPopupMenu(), BoxLayout.Y_AXIS));

        // Select between different types of evio events to allow into viewer
        JLabel jl = new JLabel("Allow");
        jl.setHorizontalTextPosition(JLabel.CENTER);
        jl.setBorder(new EmptyBorder(3, 3, 3, 3));
        jl.setAlignmentX(Component.RIGHT_ALIGNMENT);
        filterMenu.add(jl);

        final JRadioButtonMenuItem   everyItem = new JRadioButtonMenuItem("Everything", true);
        final JRadioButtonMenuItem controlItem = new JRadioButtonMenuItem("Control");
        final JRadioButtonMenuItem physicsItem = new JRadioButtonMenuItem("Physics");
        final JRadioButtonMenuItem partialItem = new JRadioButtonMenuItem("Partial Physics");

        EmptyBorder eBorder = new EmptyBorder(3,20,3,0);
        everyItem.setBorder(eBorder);
        controlItem.setBorder(eBorder);
        physicsItem.setBorder(eBorder);
        partialItem.setBorder(eBorder);

        // action listener for selecting "Everything"
        ActionListener everyListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Do NOT allow unselection, selecting other buttons does this
                if (!everyItem.isSelected()) {
                    everyItem.setSelected(true);
                    filterMenu.doClick();
                    return;
                }

                triggerType.setEnabled(false);
                controlItem.setSelected(false);
                partialItem.setSelected(false);
                physicsItem.setSelected(false);

                Filter.PHYSICS.setActive(false);
                Filter.PARTIAL.setActive(false);
                Filter.CONTROL.setActive(false);
                Filter.EVERY.setActive(true);

                // keep this menu up (displayed) so user can go to config item
                filterMenu.doClick();
            }
        };
        everyItem.addActionListener(everyListener);

        // action listener for selecting "Physics"
        ActionListener physicsListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                everyItem.setSelected(false);
                Filter.EVERY.setActive(false);

                if (physicsItem.isSelected() || partialItem.isSelected()) {
                    triggerType.setEnabled(true);
                }
                else {
                    triggerType.setEnabled(false);
                }

                if (!physicsItem.isSelected()) {
                    Filter.PHYSICS.setActive(false);
                }
                else {
                    Filter.PHYSICS.setActive(true);
                }

                if (!physicsItem.isSelected() &&
                    !partialItem.isSelected() &&
                    !controlItem.isSelected())   {
                    everyItem.doClick();
                    return;
                }

                filterMenu.doClick();
            }
        };
        physicsItem.addActionListener(physicsListener);

        // action listener for selecting "Partial Physics"
        ActionListener partialListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                everyItem.setSelected(false);
                Filter.EVERY.setActive(false);

                if (physicsItem.isSelected() || partialItem.isSelected()) {
                    triggerType.setEnabled(true);
                }
                else {
                    triggerType.setEnabled(false);
                }

                if (!partialItem.isSelected()) {
                    Filter.PARTIAL.setActive(false);
                }
                else {
                    Filter.PARTIAL.setActive(true);
                }

                if (!physicsItem.isSelected() &&
                    !partialItem.isSelected() &&
                    !controlItem.isSelected())   {
                    everyItem.doClick();
                }

                filterMenu.doClick();
            }
        };
        partialItem.addActionListener(partialListener);

        // action listener for selecting "Control"
        ActionListener controlListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                everyItem.setSelected(false);
                Filter.EVERY.setActive(false);

                if (!physicsItem.isSelected() &&
                    !partialItem.isSelected() &&
                    !controlItem.isSelected())   {
                    everyItem.doClick();
                }

                if (!controlItem.isSelected()) {
                    Filter.CONTROL.setActive(false);
                }
                else {
                    Filter.CONTROL.setActive(true);
                }

                filterMenu.doClick();
            }
        };
        controlItem.addActionListener(controlListener);

        everyItem.setAlignmentX(Component.CENTER_ALIGNMENT);
        physicsItem.setAlignmentX(Component.CENTER_ALIGNMENT);
        partialItem.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlItem.setAlignmentX(Component.CENTER_ALIGNMENT);

        filterMenu.add(everyItem);
        filterMenu.add(controlItem);
        filterMenu.add(partialItem);
        filterMenu.add(physicsItem);

        filterMenu.addSeparator();

        // Set trigger type to select if looking at Roc raw events
        JLabel qLabel = new JLabel("Trigger Type");
        qLabel.setHorizontalTextPosition(JLabel.CENTER);
        triggerType = new JSpinner(new SpinnerNumberModel(-1, -1, 255, 1));
        triggerType.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                SpinnerNumberModel model = (SpinnerNumberModel) ((JSpinner) e.getSource()).getModel();
                int type = model.getNumber().intValue();
                Filter.PHYSICS.setTriggerType(type);
            }
        });
        triggerType.setEnabled(false);


        JPanel qPanel = new JPanel();
        qPanel.add(qLabel);
        qPanel.add(triggerType);
        qPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        filterMenu.add(qPanel);

        filterMenu.setEnabled(false);

		return filterMenu;
	}

	/**
	 * Create the file menu.
	 *
	 * @return the file menu.
	 */
	public JMenu createFileMenu() {
		JMenu menu = new JMenu(" File ");

        // open event file menu item
        ActionListener al_oef = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doOpenEventFile();
            }
        };
        openEventFile = new JMenuItem("Open Event File");
        openEventFile.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        openEventFile.addActionListener(al_oef);
        menu.add(openEventFile);

        // open dictionary menu item
		ActionListener al_odf = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doOpenDictionary();
			}
		};
		JMenuItem df_item = new JMenuItem("Open Dictionary");
		df_item.addActionListener(al_odf);
		menu.add(df_item);

        // open any file menu item
        ActionListener al_vf = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doViewFileBytes();
            }
        };
        JMenuItem viewFile = new JMenuItem("View File Bytes");
        viewFile.addActionListener(al_vf);
        menu.add(viewFile);

        // separator
		menu.addSeparator();

		// export file to xml menu item
		ActionListener al_xml = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exportToXML();
			}
		};
		xmlExportItem = new JMenuItem("Export File to XML");
		xmlExportItem.addActionListener(al_xml);
		xmlExportItem.setEnabled(false);
		menu.add(xmlExportItem);
		menu.addSeparator();

		// Quit menu item
		ActionListener al_exit = new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		};
		JMenuItem exit_item = new JMenuItem("Quit");
		exit_item.addActionListener(al_exit);
		menu.add(exit_item);

		return menu;
	}


    /**
     * Select and open an event file.
     */
    private void doOpenEventFile() {
        EvioReader eFile    = evioFileReader;
        EvioReader evioFile = openEventFile();
        // handle cancel button properly
        if (eFile == evioFile) {
            return;
        }
        nextButton.setEnabled(evioFile != null);
        prevButton.setEnabled(false);
        xmlExportItem.setEnabled(evioFile != null);
        eventTreePanel.setEvent(null);
        // automatically go to the first event
        nextButton.doClick();
    }

    /**
     * Select and view the contents of a file.
     */
    private void doViewFileBytes() {
        File file = viewFileBytes();
        if (file == null) {
            return;
        }
        new FileFrame(file);
    }

    /**
     * Convenience method to open a file programmatically.
     * @param file the file to open
     */
    public void manualOpenEventFile(File file) {
        EvioReader eFile    = evioFileReader;
        EvioReader evioFile = openEventFile(file);
        // handle cancel button properly
        if (eFile == evioFile) {
            return;
        }
        nextButton.setEnabled(evioFile != null);
        prevButton.setEnabled(false);
        xmlExportItem.setEnabled(evioFile != null);
        eventTreePanel.setEvent(null);
    }

	/**
	 * Select and open a dictionary.
	 */
	private void doOpenDictionary() {
        openDictionary();
	}


    /**
     * Select a file and then write into it the current event file in xml format.
     */
    public void exportToXML() {
        JFileChooser chooser = new JFileChooser(xmlFilePath);
        chooser.setSelectedFile(null);
        FileNameExtensionFilter filter = new FileNameExtensionFilter("XML Evio Files", "xml");
        chooser.setFileFilter(filter);

        int returnVal = chooser.showSaveDialog(eventTreePanel);
        if (returnVal == JFileChooser.APPROVE_OPTION) {

            // remember which file was chosen
            File selectedFile = chooser.getSelectedFile();
            xmlFilePath = selectedFile.getAbsolutePath();

            if (selectedFile.exists()) {
                int answer = JOptionPane.showConfirmDialog(null, selectedFile.getPath()
                        + "  already exists. Do you want to overwrite it?", "Overwite Existing File?",
                        JOptionPane.YES_NO_OPTION);

                if (answer != JFileChooser.APPROVE_OPTION) {
                    return;
                }
            }

            // do the xml processing in a separate thread.
            Runnable runner = new Runnable() {
                public void run() {
                    try {
                        evioFileReader.toXMLFile(xmlFilePath, null);
                    }
                    catch (EvioException e) {e.printStackTrace();}
                    catch (IOException   e) {e.printStackTrace();}
                    JOptionPane.showMessageDialog(eventTreePanel, "XML Writing has completed.", "Done",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            };
            (new Thread(runner)).start();

        }
    }


    /**
     * Add a file extension for viewing evio format files in file chooser.
     * @param extension file extension to add
     */
    public void addEventFileExtension(String extension) {
        if (evioFileFilter == null) {
            evioFileFilter = new FileNameExtensionFilter("EVIO Event Files", "ev", "evt", "evio", extension);
        }
        else {
            String[] exts = evioFileFilter.getExtensions();
            String[] newExts = Arrays.copyOf(exts, exts.length + 1);
            newExts[exts.length] = extension;
            evioFileFilter = new FileNameExtensionFilter("EVIO Event Files", newExts);
        }
    }


    /**
     * Set all file extensions for viewing evio format files in file chooser.
     * @param extensions all file extensions
     */
    public void setEventFileExtensions(String[] extensions) {
        evioFileFilter = new FileNameExtensionFilter("EVIO Event Files", extensions);
    }


    /**
     * Select and open an event file.
     *
     * @return the opened file reader, or <code>null</code>
     */
    public EvioReader openEventFile() {

        if (dataFilePath == null || dataFilePath.length() < 1) {
            // Instead of going to the user's home directory, go cmd line given path
            dataFilePath = System.getProperty("filePath");
            // If that is null, go to current path
            if (dataFilePath == null) {
                dataFilePath = System.getProperty("user.dir");
            }
        }

        JFileChooser chooser = new JFileChooser(dataFilePath);
        chooser.setSelectedFile(null);
        if (evioFileFilter == null) {
            evioFileFilter = new FileNameExtensionFilter("EVIO Event Files", "ev", "evt", "evio");
        }

        chooser.addChoosableFileFilter(evioFileFilter);
        if (!useEvioFileFilter) {
            chooser.setFileFilter(chooser.getAcceptAllFileFilter());
        }

        int returnVal = chooser.showOpenDialog(eventTreePanel);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            eventTreePanel.getHeaderPanel().setHeader(null);

            // remember which file was chosen
            File selectedFile = chooser.getSelectedFile();
            dataFilePath = selectedFile.getAbsolutePath();

            // remember the file filter used
            if (chooser.getFileFilter().getDescription().equals("EVIO Event Files")) {
                useEvioFileFilter = true;
            }
            else {
                useEvioFileFilter = false;
            }

            if (selectedFile.length() < 1) {
                JOptionPane.showMessageDialog(new Frame(),
                    "File is empty",
                    "Error reading file",
                    JOptionPane.ERROR_MESSAGE);

                return evioFileReader;
            }

            // set the text field
            eventInfoPanel.setSource(dataFilePath);

            try {
                // Try creating a new reader, if it fails the old is retained
                EvioReader reader = new EvioReader(selectedFile);
                int evCount = reader.getEventCount();

                // Close current reader if any
                if (evioFileReader != null) {
                    evioFileReader.close();
                    qLimit.setValue(0);
                }
                // Use new one
                evioFileReader = reader;

                qLimit.setValue(evCount);
                qSize.setText("" + evCount);
                // Enable & set limits of event # spinner
                currentEvent.setEnabled(true);
                SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
                model.setMaximum(evCount);
                model.setMinimum(0);
                model.setValue(0);

                String xml = evioFileReader.getDictionaryXML();
                if (xml != null) {
                    EvioXMLDictionary dict = dictionaryMap.get(xml);
                    if (dict == null) {
                        dict = (EvioXMLDictionary)NameProviderFactory.
                                createNameProvider(xml);
                        dictionaryMap.put(xml, dict);
                    }

                    // Store this dictionary as the loaded-evio-file dictionary
                    DictionarySource.EVIOFILE.setDictionary(dict);

                    // Allow the new dictionary to be used
                    fileItem.setEnabled(true);

                    // Start using loaded evio file as dictionary for current(file) event source
                    setDictionarySource(DictionarySource.EVIOFILE);
                }

                eventIndex = 0;
            }
            catch (Exception e) {
e.printStackTrace();
                // We're here if there's trouble with evioFileReader
                JOptionPane.showMessageDialog(new Frame(),
                        e.getMessage(),
                        "Error reading file",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        connectEvioListeners();     // Connect Listeners to the parser.

        return evioFileReader;
    }


    /** Select a file in order to view its bytes. */
    public File viewFileBytes() {

        File selectedFile = null;

        if (dataFilePath == null || dataFilePath.length() < 1) {
            // Instead of going to the user's home directory, go cmd line given path
            dataFilePath = System.getProperty("filePath");
            // If that is null, go to current path
            if (dataFilePath == null) {
                dataFilePath = System.getProperty("user.dir");
            }
        }

        JFileChooser chooser = new JFileChooser(dataFilePath);
        chooser.setSelectedFile(null);
        if (evioFileFilter == null) {
            evioFileFilter = new FileNameExtensionFilter("EVIO Event Files", "ev", "evt", "evio");
        }

        chooser.addChoosableFileFilter(evioFileFilter);
        if (!useEvioFileFilter) {
            chooser.setFileFilter(chooser.getAcceptAllFileFilter());
        }

        int returnVal = chooser.showOpenDialog(eventTreePanel);

        if (returnVal == JFileChooser.APPROVE_OPTION) {

            // remember which file was chosen
            selectedFile = chooser.getSelectedFile();
            dataFilePath = selectedFile.getAbsolutePath();

            // remember the file filter used
            if (chooser.getFileFilter().getDescription().equals("EVIO Event Files")) {
                useEvioFileFilter = true;
            }
            else {
                useEvioFileFilter = false;
            }

            if (selectedFile.length() < 1) {
                JOptionPane.showMessageDialog(new Frame(),
                        "File is empty",
                        "Error reading file",
                        JOptionPane.ERROR_MESSAGE);

                return null;
            }
        }

        return selectedFile;
    }


    /**
     * Open an event file using a given file.
     *
     * @param file the file to use, i.e., an event file
     * @return the opened file reader, or <code>null</code>
     */
    public EvioReader openEventFile(File file) {
        currentEvent.setValue(0);

        eventTreePanel.getHeaderPanel().setHeader(null);

        // remember which file was chosen
        dataFilePath = file.getAbsolutePath();

        // set the text field
        eventInfoPanel.setSource(dataFilePath);

        try {
            if (evioFileReader != null) {
                evioFileReader.close();
                qLimit.setValue(0);
            }

            evioFileReader = new EvioReader(file);
            int evCount = evioFileReader.getEventCount();
            qLimit.setValue(evCount);
            qSize.setText("" + evCount);
            // Enable & set limits of event # spinner
            currentEvent.setEnabled(true);
            SpinnerNumberModel model = (SpinnerNumberModel) currentEvent.getModel();
            model.setMaximum(evCount);
            model.setMinimum(0);
            model.setValue(0);

            String xml = evioFileReader.getDictionaryXML();
            if (xml != null) {
                EvioXMLDictionary dict = dictionaryMap.get(xml);
                if (dict == null) {
                    dict = (EvioXMLDictionary)NameProviderFactory.
                            createNameProvider(xml);
                    dictionaryMap.put(xml, dict);
                }

                DictionarySource.EVIOFILE.setDictionary(dict);
                fileItem.setEnabled(true);
                setDictionarySource(DictionarySource.EVIOFILE);
            }

            eventIndex = 0;
        }
        catch (EvioException e) {
            evioFileReader = null;
            e.printStackTrace();
        }
        catch (IOException e) {
            evioFileReader = null;
            e.printStackTrace();
        }
        connectEvioListeners();     // Connect Listeners to the parser.
        return evioFileReader;
    }


    /**
     * Get the EvioReader object so the file/buffer can be read.
     * @return  EvioReader object
     */
    public EvioReader getEvioFileReader() {
        return evioFileReader;
    }


    /**
     * Set the default directory in which to look for event files.
     * @param defaultDataDir default directory in which to look for event files
     */
    public void setDefaultDataDir(String defaultDataDir) {
        dataFilePath = defaultDataDir;
    }


    /**
     * Select and open a dictionary.
     * @return <code>true</code> if file was opened and dictionary loaded.
     */
    public boolean openDictionary() {
        if (dictionaryFilePath == null || dictionaryFilePath.length() < 1) {
            // Instead of going to the user's home directory, go cmd line given path
            dictionaryFilePath = System.getProperty("dictionaryPath");
            // If that is null, go to current path
            if (dictionaryFilePath == null) {
                dictionaryFilePath = System.getProperty("user.dir");
            }
        }
        JFileChooser chooser = new JFileChooser(dictionaryFilePath);
        chooser.setSelectedFile(null);

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Dictionary Files", "xml", "dict", "txt");
        chooser.setFileFilter(filter);

        int returnVal = chooser.showOpenDialog(eventTreePanel);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            // Create a dictionary from given file
// TODO: this can silently fail and return a useless dictionary
            EvioXMLDictionary dict = (EvioXMLDictionary)NameProviderFactory.
                                                        createNameProvider(selectedFile);
            // Store this dictionary as the loaded-xml-file dictionary
            DictionarySource.XML.setDictionary(dict);

            // Allow the new dictionary to be used
            xmlItem.setEnabled(true);

            // Start using loaded XML file as dictionary for current event source
            setDictionarySource(DictionarySource.XML);

            // Remember which file was chosen
            dictionaryFilePath = selectedFile.getAbsolutePath();

            return true;
        }
        return false;
    }


    /**
     * Select and open a dictionary.
     * @param file file to open
     */
    public void openDictionaryFile(File file) {
        if (file != null) {
            EvioXMLDictionary dict = (EvioXMLDictionary)NameProviderFactory.
                                                        createNameProvider(file);
            DictionarySource.XML.setDictionary(dict);
            xmlItem.setEnabled(true);
            setDictionarySource(DictionarySource.XML);
            dictionaryFilePath = file.getAbsolutePath();
        }
    }

	/**
	 * Add an Evio listener. Evio listeners listen for structures encountered when an event is being parsed.
	 * The listeners are passed to the EventParser once a file is opened.
	 * @param listener The Evio listener to add.
	 */
	public void addEvioListener(IEvioListener listener) {

		if (listener == null) {
			return;
		}

		if (evioListenerList == null) {
			evioListenerList = new EventListenerList();
		}

		evioListenerList.add(IEvioListener.class, listener);
	}
	/**
	 * Connect the listeners in the evioListenerList to the EventParser
	 */
	private void connectEvioListeners(){
		
		if (evioListenerList == null) {
			return;
		}

		EventParser parser = getEvioFileReader().getParser();
		
		EventListener listeners[] = evioListenerList.getListeners(IEvioListener.class);

		for (int i = 0; i < listeners.length; i++) {
			parser.addEvioListener((IEvioListener)listeners[i]);
		}		
	}

}
