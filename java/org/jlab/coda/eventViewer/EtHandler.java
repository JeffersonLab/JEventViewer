package org.jlab.coda.eventViewer;

import org.jlab.coda.et.*;
import org.jlab.coda.et.enums.Mode;
import org.jlab.coda.et.enums.Modify;
import org.jlab.coda.et.exception.*;
import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.jevio.EvioEvent;
import org.jlab.coda.jevio.EvioException;
import org.jlab.coda.jevio.EvioReader;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class handles communications with an ET system
 * - including creating a panel to do so through the GUI -
 * in order to get ET events which are parsed into evio events.
 *
 * @author Timmer
 * Feb 25, 2013
 */
public class EtHandler {

    /** Panel to control connection to the ET system. */
    private JPanel panel;

    // widgets' names
    private JTextField    etName, hostName, stationName;
    private JSpinner      mPort, tPort, statPos;
    private JRadioButton  directButton, multicastButton;
    private JRadioButton  createStationButton;
    private JRadioButton  firstButton, lastButton, posButton;
    private JRadioButton  hostButton;


    // ET handling
    /** Created station's configuration. */
    private EtStationConfig stationConfig;
    /** Access to ET system. */
    private EtSystem etSystem;
    /** Station to attach to. */
    private EtStation station;
    /** Attachment to used to get events. */
    private EtAttachment att;
    /** May we create the station? */
    private boolean stationCreated;
    /** Last host entered into "ET Host" input widget. */
    private String lastHost = "localhost";


    /** Keep track of event numbering across ET events. */
    private int eventNum = 1;

    /** Thread that gets ET events, parses their data into Evio events,
     *  and places them into the eventList. */
    private ProcessEvents getEventThread;

    /** Used to tell the getEventThread to terminate. */
    private volatile boolean die;

    /** Keep track of that last event the user asked for. */
    private int currentIndex = -1;

    /** How many events we currently allow into the eventList. */
    private int listLimit = 100;

    /** Maximum number of events allowed in list. */
    private final int maxListSize = 1000;

    /** List of received EvioEvent objects (parsed ET buffers). */
    private ArrayList<EvioEvent> eventList = new ArrayList<EvioEvent>(maxListSize);

    /** Filter allowing only certain events into eventList. */
    private Filter eventFilter = Filter.EVERY;

    /** Evio version of data from ET. Default to v6. */
    private int evioVersion = 6;

    /** What type of data compression? */
    private CompressionType dataCompressionType = CompressionType.RECORD_UNCOMPRESSED;





    /**
     * Get the evio version of data being viewed.
     * @return evio version of data being viewed.
     */
    public int getEvioVersion() {return evioVersion;}

    /**
     * Get the compression type of data being viewed.
     * @return compression type of data being viewed.
     */
    public CompressionType getDataCompressionType() {return dataCompressionType;}

    /**
     * Get the event filter to use on each event before adding to list.
     * @return the event filter to use on each event before adding to list.
     */
    public Filter getEventFilter() {
        return eventFilter;
    }

    /**
     * Set the event filter to use on each event before adding to list.
     * @param eventFilter filter to use on each event before adding to list.
     */
    public void setEventFilter(Filter eventFilter) {
        this.eventFilter = eventFilter;
    }

    /** Reset the event number back to 1. */
    synchronized private void resetEventNumber() {
        eventNum = 1;
    }

    /**
     * Add the given event to the event list.
     * If the list is full, nothing is added.
     *
     * @param event Evio event to add to event list.
     */
    synchronized public void addEvent(EvioEvent event) {
        if (event == null || eventList.size() >= listLimit) return;

        if (!Filter.allow(event)) {
            System.out.println("ET FILTER REJECTS event -> " + event);
            return;
        }

        event.setEventNumber(eventNum++);
        eventList.add(event);
        return;
    }

    /**
     * Get the specified event, index beginning at 1.
     * @param index index into event list beginning at 1.
     * @return EvioEvent object at given index in event list;
     *         or null if no object at that index
     */
    synchronized public EvioEvent getEvent(int index) {
        if (index < 1 || index > listLimit || index > eventList.size()) return null;
        currentIndex = index - 1;
        return eventList.get(currentIndex);
    }

    /**
     * Get the next evio event from the list or null if none.
     * @return next evio event from the list or null if none.
     */
    synchronized public EvioEvent getNextEvent() {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= listLimit || nextIndex >= eventList.size()) return null;
        return eventList.get(++currentIndex);
    }

    /**
     * Is there another evio event, after the one previously obtained, in the list?
     * @return <code>true</code> if there is an event, after the one previously obtained,
     *         in the list, else <code>false</code>.
     */
    synchronized public boolean hasNextEvent() {
        int nextIndex = currentIndex + 1;
        return !(nextIndex >= listLimit || nextIndex >= eventList.size());
    }

    /**
     * Get the previous evio event from the list or null if none.
     * @return previous evio event from the list or null if none.
     */
    synchronized public EvioEvent getPrevEvent() {
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) return null;
        return eventList.get(--currentIndex);
    }

    /**
     * Get the size of the event list.
     * @return size of the event list.
     */
    synchronized public int getListSize() {
        return eventList.size();
    }

    /**
     * Get the index of the current event (starting at 0).
     * Used to determine what is next and what is previous.
     * @return index of the current event (starting at 0).
     */
    synchronized public int getCurrentEventIndex() {
        return currentIndex;
    }

    /**
      * Reset the index of the current event to -1.
      * Used to determine what is next and what is previous.
      */
     synchronized public void resetCurrentEventIndex() {
         currentIndex = -1;
     }

    /**
     * Set the event number. Newly added events
     * have numbers starting with the given value.
     * @param eventNum newly added events have numbers starting with this value
     */
    public void setEventNum(int eventNum) {
        this.eventNum = eventNum;
    }

    /** Clear the entire event list - all events. */
    synchronized public void clearList() {
        eventList.clear();
        currentIndex = -1;
        resetEventNumber();
    }

    /**
     * Clear the specified number of latest events from the list.
     * @param numberToDelete number of latest events to delete from the list.
     */
    synchronized public void clearList(int numberToDelete) {
        // Use an extra variable here to avoid a changing upper limit as
        // we're removing one item from eventList each iteration.
        int listSize = eventList.size();
        if (numberToDelete < listSize) {
            // delete latest additions
            for (int i=listSize; i > listSize - numberToDelete; i--) {
                eventList.remove(i-1);
            }
            // if we removed what we are looking at, look at nothing
            if (currentIndex >= eventList.size()) {
                currentIndex = -1;
            }
            return;
        }

        clearList();
    }

    /**
     * Get the maximum number of events that the list will hold.
     * @return maximum number of events that the list will hold.
     */
    synchronized public int getListLimit() {
        return listLimit;
    }

    /**
     * Set the maximum number of events that the list will hold.
     * Does nothing if limit &lt; 1. Will set it to a max of 1000.
     * If reducing the size of the current limit, it will clear
     * any events beyond the new limit.
     * @param limit maximum number of events that the list will hold.
     */
    synchronized public void setListLimit(int limit) {
        if (limit < 1 || limit == listLimit) return;

        if (limit > maxListSize) limit = maxListSize;

        if (limit < listLimit) {
            // If we're here, we must reduce the current limit.
            // If necessary, get rid of newest events.
            if (limit < eventList.size()) {
                clearList(eventList.size() - limit);
            }
        }

        listLimit = limit;
    }

    /** Create and start a thread to process ET events into Evio events. */
    synchronized public void startFillingEventList() {
        if (getEventThread == null || !getEventThread.isAlive()) {
            die = false;
            getEventThread = new ProcessEvents();
            getEventThread.start();
        }
    }

    /** Stop the thread processing ET events into Evio events, if any. */
    synchronized public void stopFillingEventList() {
        if (getEventThread == null || !getEventThread.isAlive()) return;

        die = true;
        try {
            etSystem.wakeUpAttachment(att);
            getEventThread.join(200);
        }
        catch (EtClosedException e) { }
        catch (EtException e) { }
        catch (InterruptedException e) { }
        catch (IOException e) { }

        getEventThread = null;
    }

    /**
     * This class is a thread which gets ET events and parses them
     * to extract evio events from the ET event data.
     */
    private class ProcessEvents extends Thread {

        public void run() {
            if (etSystem == null) {
                return;
            }

            int chunk = 1;
            EvioEvent evioEv;
            String dictionary;
            EtEvent[] events = null;
            EvioReader reader = null;

            // Start event number at 1
            resetEventNumber();

            do {
                if (die) {
                    return;
                }

                // Get some events
                try {
                    events = etSystem.getEvents(att, Mode.TIMED,
                                                Modify.NOTHING, 1000000, chunk);
                }
                catch (EtTimeoutException e) {
                    continue;
                }
                catch (EtWakeUpException e) {
                    continue;
                }
                catch (Exception e) {
                    //e.printStackTrace();
                    return;
                }

                loop:
                for (EtEvent ev : events) {
                    byte[] data = ev.getData();

                    // Data must contain at least one block header and a single bank ...
                    if (data.length < 40) {
                        continue;
                    }

                    ByteBuffer buf = ByteBuffer.wrap(data);

                    // Pick event apart
                    try {
                        if (reader == null) {
                            reader = new EvioReader(buf);
                        }
                        else {
                            reader.setBuffer(buf);
                        }
                        dictionary = reader.getDictionaryXML();
                        evioVersion = reader.getEvioVersion();
                        dataCompressionType = reader.getFirstBlockHeader().getCompressionType();

                        while ((evioEv = reader.parseNextEvent()) != null) {
                            evioEv.setDictionaryXML(dictionary);
                            addEvent(evioEv);
                            if (eventList.size() >= listLimit)  break loop;
                        }
                    }
                    // Error, try next ET buffer
                    catch (EvioException e) { }
                    catch (IOException e)   { }
                }

                // Put events back into ET
                try {
                    etSystem.putEvents(att, events);
                }
                catch (Exception e) {
                    //e.printStackTrace();
                    return;
                }

              // keep going until the list is full ...
            } while (eventList.size() < listLimit);

        }
    }


    /**
     *  Create the panel/menu-item used to handle communications with an ET system.
     * @return the panel/menu-item used to handle communications with an ET system.
     */
    public JPanel createEtPanel() {
        if (panel != null) return panel;

        // custom colors
        final Color darkGreen = new Color(0, 160, 0);
        final Color darkRed   = new Color(160, 0, 0);

        /** Button to connect-to / disconnect-from cMsg server. */
        final JButton connectButton = new JButton("Connect");

        Border blackLine = BorderFactory.createLineBorder(Color.black);
        Border lowerEtched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        Border empty = BorderFactory.createEmptyBorder(4,4,4,4);

        // This creates a nice frame
        CompoundBorder compound = BorderFactory.createCompoundBorder(lowerEtched, empty);

//-------------------------------------------------------------------------

        final JLabel status = new JLabel("  Press button to connect to ET system  ");
        status.setForeground(Color.BLUE);
        status.setVerticalTextPosition(SwingConstants.CENTER);
        status.setHorizontalTextPosition(SwingConstants.CENTER);
        status.setBorder(empty);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(status, BorderLayout.CENTER);

        // connect/disconnect button
        ActionListener al_con = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();

                // If connecting ...
                if (button.getText().equals("Connect")) {

                    // Open ET system, attach to station,
                    // and start thread to get ET events.
                    try {
                        EtSystemOpenConfig sysConfig = getEtSystemConfig();
                        if (sysConfig == null) return;

//System.out.println("Config = \n" + sysConfig.toString());

                        etSystem = connect(sysConfig);
                        if (etSystem == null) return;

//System.out.println("CONNECTED TO ET " + sysConfig.getEtName());
                        att  = attach(etSystem);
                        if (att == null) return;

//System.out.println("ATTACHED TO station " + att.getStation().getName() + ", start filling list");
                        startFillingEventList();
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        status.setForeground(Color.red);
                        status.setText(" Failed to connect to ET system");
                        return;
                    }

                    // success connecting to ET
                    status.setForeground(darkGreen);
                    status.setText(" Connected to ET system");
                    connectButton.setText("Disconnect");
                }
                // If disconnecting ...
                else {
                    stopFillingEventList();

                    // Detach, get rid of station if created, then close
                    try {
                        etSystem.detach(att);
                    }
                    catch (Exception ex) { ex.printStackTrace(); }

                    try {
                        if (stationCreated) {
                            etSystem.removeStation(station);
                        }
                    }
                    catch (Exception ex) { ex.printStackTrace(); }

                    etSystem.close();

                    // reset button
                    status.setForeground(darkRed);
                    status.setText(" Disconnected from ET system");
                    connectButton.setText("Connect");
                }
            }
        };

        connectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectButton.addActionListener(al_con);
        connectButton.setEnabled(true);

        //-------------------------------------------------------------------------

        // Several combo boxes use this to filter input.

        JPanel pTopLabels = new JPanel();
        pTopLabels.setLayout(new GridLayout(2, 1, 0, 2));
        JLabel l1 = new JLabel("ET name");
        JLabel l2 = new JLabel("ET host");
        pTopLabels.add(l1);
        pTopLabels.add(l2);

        etName = new JTextField();
        etName.setEditable(true);

        hostName = new JTextField(lastHost);
        hostName.setEditable(true);
        hostName.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        JTextField textField = (JTextField) e.getSource();
                        String text = textField.getText();
                        if (hostButton.isSelected()) {
                            lastHost = text;
                        }
                    }
                }
        );
        // update lastHost when move mouse out of widget
        hostName.addMouseListener(
                new MouseAdapter() {
                    public void mouseExited(MouseEvent e) {
                        JTextField textField = (JTextField) e.getSource();
                        String text = textField.getText();
                        if (hostButton.isSelected()) {
                            lastHost = text;
                        }
                    }
                }
        );

        JPanel pTopBoxes = new JPanel();
        pTopBoxes.setLayout(new GridLayout(2, 1, 0, 2));
        pTopBoxes.add(etName);
        pTopBoxes.add(hostName);

        hostButton = new JRadioButton("Host");
        JRadioButton localButton = new JRadioButton("Local");
        JRadioButton remoteButton = new JRadioButton("Remote");
        JRadioButton anywhereButton = new JRadioButton("Anywhere");

        hostButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        hostName.setText(lastHost);
                        hostName.setEditable(true);
                    }
                }
        );

        localButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        hostName.setText("local");
                        hostName.setEditable(false);
                    }
                }
        );

        remoteButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        hostName.setText("remote");
                        hostName.setEditable(false);
                    }
                }
        );

        anywhereButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        hostName.setText("anywhere");
                        hostName.setEditable(false);
                    }
                }
        );


        ButtonGroup group1 = new ButtonGroup();
        group1.add(hostButton);
        group1.add(localButton);
        group1.add(remoteButton);
        group1.add(anywhereButton);
        hostButton.setSelected(true);

        JPanel pTopButtons = new JPanel();
        pTopButtons.setLayout(new GridLayout(1, 4, 0, 2));
        pTopButtons.add(hostButton);
        pTopButtons.add(localButton);
        pTopButtons.add(remoteButton);
        pTopButtons.add(anywhereButton);

        JPanel topPanel = new JPanel();
        BorderLayout bl = new BorderLayout(10, 0);

        // Puts label in border
        TitledBorder border1 = new TitledBorder(compound,
                                                "ET System",
                                                TitledBorder.LEFT,
                                                TitledBorder.CENTER,
                                                null, Color.BLUE);

        topPanel.setBorder(border1);
        topPanel.setLayout(bl);
        topPanel.add(pTopLabels,  BorderLayout.WEST);
        topPanel.add(pTopBoxes,   BorderLayout.CENTER);
        topPanel.add(pTopButtons, BorderLayout.SOUTH);

        //-------------------------------------------------------------------------
        // text input for udp multicast port number
        mPort = new JSpinner(new SpinnerNumberModel(EtConstants.serverPort, 1024, 65535, 1));

        // text input for tcp server port number
        tPort = new JSpinner(new SpinnerNumberModel(EtConstants.serverPort, 1024, 65535, 1));

        directButton = new JRadioButton("Direct");
        multicastButton = new JRadioButton("Multicast");
        multicastButton.setSelected(true);
        tPort.setEnabled(false);

        directButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        mPort.setEnabled(false);
                        tPort.setEnabled(true);
                    }
                }
        );

        multicastButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        mPort.setEnabled(true);
                        tPort.setEnabled(false);
                    }
                }
        );

        ButtonGroup group2 = new ButtonGroup();
        group2.add(directButton);
        group2.add(multicastButton);

        JPanel methodPanel = new JPanel();
        methodPanel.setLayout(new GridLayout(2, 1));
        methodPanel.add(directButton);
        methodPanel.add(multicastButton);

        JPanel tcpPanel = new JPanel();
        tcpPanel.setLayout(new BorderLayout());
        tcpPanel.add(new JLabel("TCP Port  "), BorderLayout.WEST);
        tcpPanel.add(tPort, BorderLayout.CENTER);

        JPanel udpPanel = new JPanel();
        udpPanel.setLayout(new BorderLayout());
        udpPanel.add(new JLabel("UDP Port  "), BorderLayout.WEST);
        udpPanel.add(mPort, BorderLayout.CENTER);

        JPanel ipPanel = new JPanel();
        ipPanel.setLayout(new GridLayout(2,1));
        ipPanel.add(tcpPanel);
        ipPanel.add(udpPanel);

        // Puts label in border
        TitledBorder border2 = new TitledBorder(compound,
                                                "Connection",
                                                TitledBorder.LEFT,
                                                TitledBorder.CENTER,
                                                null, Color.BLUE);
        JPanel midPanel = new JPanel();
        midPanel.setBorder(border2);
        midPanel.setLayout(new GridLayout(1, 2));
        midPanel.add(methodPanel);
        midPanel.add(ipPanel);

        stationName = new JTextField();
        stationName.setEditable(true);

        createStationButton = new JRadioButton("Create");
        createStationButton.setSelected(true);

        JPanel statPanel = new JPanel();
        statPanel.setLayout(new BorderLayout(10, 0));
        statPanel.add(new JLabel("Station Name"), BorderLayout.WEST);
        statPanel.add(stationName, BorderLayout.CENTER);
        statPanel.add(createStationButton, BorderLayout.EAST);

        // input for station position
        statPos = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));

        // buttons for station position
        posButton   = new JRadioButton("Position");
        lastButton  = new JRadioButton("Last");
        firstButton = new JRadioButton("First");

        ButtonGroup group3 = new ButtonGroup();
        group3.add(lastButton);
        group3.add(firstButton);
        group3.add(posButton);

        posButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        statPos.setEnabled(true);
                    }
                }
        );

        lastButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        statPos.setEnabled(false);
                    }
                }
        );

        firstButton.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        statPos.setEnabled(false);
                    }
                }
        );

        lastButton.doClick();

        JPanel posButtonPanel = new JPanel();
        posButtonPanel.setLayout(new BoxLayout(posButtonPanel, BoxLayout.X_AXIS));
        posButtonPanel.setBorder(new EmptyBorder(5,0,0,0));
        posButtonPanel.add(lastButton);
        posButtonPanel.add(firstButton);
        posButtonPanel.add(posButton);
        posButtonPanel.add(statPos);

        // Puts label in border
        TitledBorder border3 = new TitledBorder(compound,
                                                "Station",
                                                TitledBorder.LEFT,
                                                TitledBorder.CENTER,
                                                null, Color.BLUE);

        JPanel lowPanel = new JPanel();
        lowPanel.setBorder(border3);
        lowPanel.setLayout(new BorderLayout());
        lowPanel.add(statPanel, BorderLayout.NORTH);
        lowPanel.add(posButtonPanel, BorderLayout.CENTER);

        JPanel centerPanel = new JPanel();
        GroupLayout layout1 = new GroupLayout(centerPanel);
        //layout.setAutoCreateGaps(true);
        layout1.setHorizontalGroup(
                layout1.createParallelGroup()
                        .addComponent(topPanel)
                        .addComponent(midPanel)
                        .addComponent(lowPanel)
                        .addComponent(statusPanel)
        );
        layout1.setVerticalGroup(
                layout1.createSequentialGroup()
                        .addComponent(topPanel)
                        .addComponent(midPanel)
                        .addComponent(lowPanel)
                        .addComponent(statusPanel)
        );
        centerPanel.setLayout(layout1);


        // top-level panel
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(connectButton, BorderLayout.SOUTH);

        return panel;
    }


    /**
     * Gather data about which ET system and how to connect to it.
     * @return ET system configuration
     */
    private EtSystemOpenConfig getEtSystemConfig() {

        try {
            boolean specifingHostname = false;
            EtSystemOpenConfig config;

            // Get ET system name.
            String etSystem = etName.getText();

            // Get host name.
            String host = hostName.getText();

            if (etSystem == null || etSystem.length() < 1 ||
                    host == null || host.length() < 1) {
                JOptionPane.showMessageDialog(new JFrame(), "Enter et and host names",
                                              "Error", JOptionPane.ERROR_MESSAGE);
                return  null;
            }

            // Find out how we're connecting with the ET system.
            boolean directConnection = directButton.isSelected();

            if (host.equals("local")) {
                host = EtConstants.hostLocal;
                specifingHostname = true;
            }
            else if (host.equals("remote")) {
                host = EtConstants.hostRemote;
            }
            else if (host.equals("anywhere")) {
                host = EtConstants.hostAnywhere;
            }
            else {
                specifingHostname = true;
            }

            if (directConnection) {
                 // Since we've making a direct connection, a host name
                 // (not remote, or anywhere) must be specified. The selection
                 // of "local" can be easily resolved into an actual host name.
                 if (!specifingHostname) {
                     throw new EtException("Specify a host's name (not remote, or anywhere) to make a direct connection.");
                 }
                 int port = ((SpinnerNumberModel)tPort.getModel()).getNumber().intValue();
                 config = new EtSystemOpenConfig(etSystem, host, port);
            }
            else {
                // use default multicast address
                ArrayList<String> mAddrs = new ArrayList<String>(1);
                mAddrs.add(EtConstants.multicastAddr);
                // get multicast port
                int mcastPort = ((SpinnerNumberModel)mPort.getModel()).getNumber().intValue();
                // Use the multicast specific constructor
                config = new EtSystemOpenConfig(etSystem, host, mAddrs, mcastPort, 32);
            }

            return config;
        }

        catch (EtException ex) {
            JOptionPane.showMessageDialog(new JFrame(), ex.getMessage(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }

        return null;
    }


    /**
     * Make an attachment to an ET station.
     *
     * @param system ET system to use
     * @return attachment to station (parameters given by gui), null if not possible
     */
    private EtAttachment attach(EtSystem system) {
        // Get station name
        String statName = stationName.getText();

        if (statName == null || statName.length() < 1) {
            JOptionPane.showMessageDialog(new JFrame(),
                                          "Enter station name",
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return  null;
        }

        // Find out if we're allowed to create the station
        boolean create = createStationButton.isSelected();

        // Find out position for created station
        int position = EtConstants.end;
        if (firstButton.isSelected()) {
            position = 1;
        }
        else if (posButton.isSelected()) {
            position = ((SpinnerNumberModel)statPos.getModel()).getNumber().intValue();
        }

        // Find out from the ET system if the station exists
        boolean exists;
        try {
            exists = system.stationExists(statName);
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(new JFrame(), "Cannot communicate with ET",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        catch (EtClosedException e) {
            JOptionPane.showMessageDialog(new JFrame(), "ET connection is closed",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        catch (EtDeadException e) {
            JOptionPane.showMessageDialog(new JFrame(), "ET system is dead, Jim",
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        catch (EtException e) {
            JOptionPane.showMessageDialog(new JFrame(), e.getMessage(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // If it exists, attach to it
        if (exists) {
            try {
                station = system.stationNameToObject(statName);
                stationCreated = false;
                return system.attach(station);
            }
            catch (IOException e) {
                JOptionPane.showMessageDialog(new JFrame(), "cannot communicate with ET",
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
            catch (EtClosedException e) {
                JOptionPane.showMessageDialog(new JFrame(), "ET connection is closed",
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
            catch (EtDeadException e) {
                JOptionPane.showMessageDialog(new JFrame(), "ET system is dead, Jim",
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
            catch (EtException e) {
                JOptionPane.showMessageDialog(new JFrame(), e.getMessage(),
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }
            catch (EtTooManyException e) {
                JOptionPane.showMessageDialog(new JFrame(), "too many attachments to station",
                                              "Error", JOptionPane.ERROR_MESSAGE);
            }

            return null;
        }

        // If we're here, we need to create the ET station first
        if (!create) {
            JOptionPane.showMessageDialog(new JFrame(),
                                          "select create button to allow creating station",
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            if (stationConfig == null) {
                stationConfig = new EtStationConfig();
                try {
                    stationConfig.setBlockMode(EtConstants.stationNonBlocking);
                    stationConfig.setCue(1);
                }
                catch (EtException e) {/* never happen*/}
            }
            station = system.createStation(stationConfig, statName, position);
            stationCreated = true;
            return system.attach(station);
        }
        catch (IOException e) {
            JOptionPane.showMessageDialog(new JFrame(), "cannot communicate with ET",
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch (EtClosedException e) {
            JOptionPane.showMessageDialog(new JFrame(), "ET connection is closed",
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch (EtDeadException e) {
            JOptionPane.showMessageDialog(new JFrame(), "ET system is dead, Jim",
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch (EtException e) {
            JOptionPane.showMessageDialog(new JFrame(), e.getMessage(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch (EtTooManyException e) {
            JOptionPane.showMessageDialog(new JFrame(), "too many stations already exist",
                                          "Error", JOptionPane.ERROR_MESSAGE);
        }
        catch (EtExistsException e) {/* never happen */}

        return null;
    }


    /**
     * Make a connection to (open) an ET system.
     * @param config configuration used to open the Et system
     * @return object handling the connection or null if connection is not possible
     */
    private EtSystem connect(EtSystemOpenConfig config) {
        if (config == null) {
            return null;
        }

        // Make a connection. Use EtSystemOpen object directly here
        // instead of EtSystem object so we can see exactly who
        // responded to a broad/multicast if there were multiple
        // responders.
        config.setConnectRemotely(true);  // forget loading JNI lib
        config.setWaitTime(3000); // wait up to 3 sec for connection

        EtSystemOpen open = new EtSystemOpen(config);

        try {
            open.connect();

        }
        catch (UnknownHostException ex) {
            JOptionPane.showMessageDialog(new JFrame(),
                                          config.getHost() + " is an unknown host",
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }
        catch (IOException ex) {
            JOptionPane.showMessageDialog(new JFrame(),
                                          "Communication problems with " +
                                                  config.getEtName() + " on " +
                                                  config.getHost() + ":\n" + ex.getMessage(),
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
            return null;
        }
        catch (EtTooManyException ex) {
            // This can only happen if specifying "anywhere"
            // or "remote" for the host name.

            int port = 0;
            String host;

            String[] hosts = open.getAllHosts();
            int[]    ports = open.getAllPorts();

            if (hosts.length > 1) {
                host = (String) JOptionPane.showInputDialog(
                        new JFrame(),
                        "Choose the ET system responding from host:",
                        "ET System Choice",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        hosts,
                        hosts[0]
                );

                if (host == null) {
                    return null;
                }

                for (int i = 0; i < hosts.length; i++) {
                    if (host.equals(hosts[i])) {
                        port = ports[i];
                    }
                }

                // now connect to specified host & port
                try {
                    config.setHost(host);
                    config.setTcpPort(port);
                    config.setNetworkContactMethod(EtConstants.direct);
                    open.connect();
                }
                catch (Exception except) {
                    JOptionPane.showMessageDialog(new JFrame(),
                                                  "Communication problems with " +
                                                          config.getEtName() + " on " +
                                                          config.getHost() + ":\n" + ex.getMessage(),
                                                  "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        }
        catch (EtException ex) {
            JOptionPane.showMessageDialog(new JFrame(),
                                          "Cannot find or connect to " + config.getEtName(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // Return a EtSystem object - create from EtSystemOpen object
        EtSystem use = null;
        try {
            use = new EtSystem(open, EtConstants.debugNone);
        }
        catch (Exception ex) {
            open.disconnect();
            JOptionPane.showMessageDialog(new JFrame(),
                                          "Communication problems with " +
                                                  config.getEtName() + " on " +
                                                  config.getHost() + ":\n" + ex.getMessage(),
                                          "Error",  JOptionPane.ERROR_MESSAGE);
        }

        return use;
    }


}