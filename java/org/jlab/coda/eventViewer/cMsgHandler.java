package org.jlab.coda.eventViewer;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.jevio.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class handles all cMsg communications using a singleton pattern.
 * It is expecting each message to contain bytes corresponding to an evio
 * event in the byteArray field. It also looks for an xml format dictionary
 * in a String payload item called "dictionary" (case sensitive).
 * The endianness of the byte array is set in cMsg by the setByteArrayEndian
 * method and, of course, must be set by the sender.
 *
 * @author timmer
 * @date Oct 19, 2009
 */
public class cMsgHandler {

    /** Panel to control connection to the ET system. */
    private JPanel panel;



    /** Handle all cMsg communications with this object. */
    private cMsg cmsg;

    /** Uniform Domain Locator (UDL) used to specify cMsg server to connect to. */
    private String udl;

    /** Subject to subscribe to for receiving evio event filled messages. */
    private String subject;

    /** Type to subscribe to for receiving evio event filled messages. */
    private String type;

    /** Handle cMsg subscription with this object. */
    private cMsgSubscriptionHandle handle;

    /** Callback to run when receiving a message. */
    private myCallback callback = new myCallback();


    /** Keep track of event numbering across messages. */
    private int eventNum = 1;

    /** Keep track of that last event the user asked for (0 is first event). */
    private int currentIndex = -1;

    /** How many events we currently allow into the eventList. */
    private int listLimit = 100;

    /** Maximum number of events allowed in list. */
    private final int maxListSize = 1000;

    /** List of received EvioEvent objects (parsed ET buffers). */
    private ArrayList<EvioEvent> eventList = new ArrayList<EvioEvent>(maxListSize);

    /** Filter allowing only certain events into eventList. */
    private Filter eventFilter = Filter.EVERY;



    /**
     * This class defines the callback to be run when a message matching
     * our subscription arrives.
     */
    private class myCallback extends cMsgCallbackAdapter {

        /**
         * Callback method definition.
         * @param msg        message received from cMsg server
         * @param userObject object passed as an argument which was set when the client
         *                   originally subscribed to a subject and type of message.
         */
        public void callback(cMsgMessage msg, Object userObject) {
            // If list is full, return
            if (eventList.size() >= listLimit)  return;

            // Check to see if message may contain evio event (there is a byte array)
            byte[] data = msg.getByteArray();
            if (data == null) return;

            // Decode messages into events & store on the list if there is room,
            // else it disappears.
            extractEvents(msg);
        }
    }

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

    /**
     * Get current subscription's subject.
     * @return current subscription's subject.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Get current subscription's type.
     * @return current subscription's type.
     */
    public String getType() {
        return type;
    }

    /** Disconnect from the cMsg server if any connection exists.  */
    public void disconnect() {
        if (cmsg == null || !cmsg.isConnected()) {
            return;
        }

        try {
            cmsg.disconnect();
        }
        catch (cMsgException e) {
            // if this fails it's disconnected anyway
        }

        // each new connection means resubscribing
        handle  = null;
        subject = null;
        type    = null;

        return;
    }

    /**
     * Connect to the specified cmsg server.
     *
     * @param udl UDL used to specify a cmsg server to connect to
     * @throws cMsgException if connection cannot be made
     */
    public void connect(String udl) throws cMsgException {
        if (cmsg == null) {
            // must create a unique cmsg client name
            String name = "evioViewer_" + System.currentTimeMillis();
            String descr = "evio event viewer";
            cmsg = new cMsg(udl, name, descr);
            // store udl
            this.udl = udl;
        }

        // if we're already connected ...
        if (cmsg.isConnected()) {
            // if to same server, just return
            if (udl.equals(this.udl)) {
                return;
            }
            // otherwise disconnect from old server, before reconnecting to new one
            else {
                cmsg.disconnect();
            }
        }

        // if using new udl, recreate cmsg object
        if (!udl.equals(this.udl)) {
            String name = "evioViewer_" + System.currentTimeMillis();
            String descr = "evio event viewer";
            cmsg = new cMsg(udl, name, descr);
            // store udl
            this.udl = udl;
        }

        // connect to cmsg server
        cmsg.connect();

        // allow receipt of messages
        cmsg.start();

        // each new connection means resubscribing
        subject = null;
        type = null;

        return;
    }

    /**
     * Subscribe to the given subject and type.
     * If no connection to a cMsg server exists, nothing is done.
     * If an identical subscription already exists nothing is done.
     * If an older subscription exists, it is replaced by the new one.
     *
     * @param subject subject to subscribe to.
     * @param type type to subscribe to.
     * @return <code>true</code> if the subscription exists or was made, else <code>false</code>.
     * @throws cMsgException if subscription fails
     */
    public boolean subscribe(String subject, String type) throws cMsgException {
        // can't subscribe without connection or with null args
        if (cmsg == null || !cmsg.isConnected() ||
            subject == null || type == null ||
            subject.length() < 1 || type.length() < 1) {
            handle = null;
            return false;
        }
        // already subscribed to this subject & type
        else if (subject.equals(this.subject) && type.equals(this.type)) {
            return true;
        }

        // only want 1 subscription at a time for receiving evio messages
        if (handle != null) {
            try {
                cmsg.unsubscribe(handle);
            }
            catch (cMsgException e) { }
        }

        handle = cmsg.subscribe(subject, type, callback, null);

        this.subject = subject;
        this.type = type;

        return true;
    }

    /**
     * Take a cMsg message's byte array and extract evio events from it
     * and place them on the event list until there is no room left.
     *
     * @param msg cMsg from which to extract evio events
     * @return next event number to use
     */
    private int extractEvents(cMsgMessage msg) {
        try {
            EvioEvent ev;
            ByteBuffer buf = ByteBuffer.wrap(msg.getByteArray());
            EvioReader reader = new EvioReader(buf);
            String dictionary = reader.getDictionaryXML();

            // If no dictionary defined in buffer, look for it in message payload
            if (dictionary == null) {
                cMsgPayloadItem payloadItem = msg.getPayloadItem("dictionary");
                if (payloadItem != null) {
                    try { dictionary = payloadItem.getString(); }
                    catch (cMsgException e) { }
                }
            }

            while ( (ev = reader.parseNextEvent()) != null) {
                ev.setDictionaryXML(dictionary);
                addEvent(ev);
                if (eventList.size() >= listLimit)  break;
            }
        }
        catch (IOException e) {
            // data in wrong format so try next msg
        }
        catch (EvioException e) {
            // data in wrong format so try next msg
        }

        return eventNum;
    }

    /** Reset the event number back to 1. */
    synchronized private void resetEventNumber() {
        eventNum = 1;
    }

    /**
     * Add the given event to the event list.
     * If the list is full, nothing is added.
     * If the event does not make it past the filter,
     * nothing is added.
     *
     * @param event Evio event to add to event list.
     */
    synchronized public void addEvent(EvioEvent event) {
        if (event == null || eventList.size() >= listLimit) return;

        if (!Filter.allow(event)) {
            System.out.println("CMSG FILTER REJECTS event -> " + event);
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


    /**
     *  Create the panel/menuitem used to handle communications with a cmsg server.
     * @return the panel/menuitem used to handle communications with a cmsg server.
     */
    public JPanel createCmsgPanel() {
        if (panel != null) return panel;

        // custom colors
        final Color darkGreen = new Color(0, 160, 0);
        final Color darkRed   = new Color(160, 0, 0);

        // put in a default UDL for connection to cMsg server
        final JTextField UDL = new JTextField("cMsg://localhost/cMsg/myNameSpace");
        // put in a default subscription subject
        final JTextField Subject = new JTextField("evio");
        // put in a default subscription type (* means everything)
        final JTextField Type = new JTextField("*");

        /** Button to connect-to / disconnect-from cMsg server. */
        final JButton connectButton = new JButton("Connect");

        UDL.setEditable(true);
        UDL.setMargin(new Insets(2, 5, 2, 5));

        // update subscription when hit enter
        ActionListener al_sub = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    subscribe(Subject.getText(), Type.getText());
                }
                catch (cMsgException e1) {
                    e1.printStackTrace();
                    Subject.setText("evio");
                }
            }
        };
        Subject.addActionListener(al_sub);

        // update subscription when move mouse out of widget
        MouseListener ml_sub = new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                try {
                    subscribe(Subject.getText(), Type.getText());
                }
                catch (cMsgException e1) {
                    e1.printStackTrace();
                    Subject.setText("evio");
                }
            }
        };
        Subject.addMouseListener(ml_sub);
        Subject.setEditable(true);
        Subject.setMargin(new Insets(2, 5, 2, 5));

        // update subscription when hit enter
        ActionListener al_typ = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    subscribe(Subject.getText(), Type.getText());
                }
                catch (cMsgException e1) {
                    e1.printStackTrace();
                    Subject.setText("*");
                }
            }
        };
        Type.addActionListener(al_typ);

        // update subscription when move mouse out of widget
        MouseListener ml_typ = new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                try {
                    subscribe(Subject.getText(), Type.getText());
                }
                catch (cMsgException e1) {
                    e1.printStackTrace();
                    Subject.setText("*");
                }
            }
        };
        Type.addMouseListener(ml_typ);
        Type.setEditable(true);
        Type.setMargin(new Insets(2, 5, 2, 5));

        final JLabel status = new JLabel("  Press button to connect to cMsg server  ");
        status.setVerticalTextPosition(SwingConstants.CENTER);
        status.setBorder(new LineBorder(Color.black));

        // button panel
        final JPanel p1 = new JPanel();

        // connect/disconnect button
        ActionListener al_con = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JButton button = (JButton) e.getSource();

                if (button.getText().equals("Connect")) {
                    // connect to cMsg server, and subscribe to receive messages
                    try {
                        connect(UDL.getText());
                        subscribe(Subject.getText(), Type.getText());
                    }
                    catch (cMsgException e1) {
                        // handle failure
                        status.setForeground(Color.red);
                        status.setText(" Failed to connect to cMsg server");
                        return;
                    }

                    // success connecting to cmsg server
                    UDL.setEnabled(false);
                    status.setForeground(darkGreen);
                    status.setText(" Connected to cMsg server");
                    connectButton.setText("Disconnect");
                }
                else {
                    // disconnect from cMsg server
                    disconnect();

                    // reset sources
                    UDL.setEnabled(true);
                    status.setForeground(darkRed);
                    status.setText(" Disconnected from cMsg server");
                    connectButton.setText("Connect");
                }
            }
        };
        connectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        connectButton.addActionListener(al_con);
        connectButton.setEnabled(true);

        p1.setBorder(new EmptyBorder(2, 5, 2, 5));
        p1.setLayout(new GridLayout(0, 1));
        p1.add(connectButton);

        // label panel
        JPanel p3 = new JPanel();
        p3.setLayout(new GridLayout(4, 0));
        JLabel label1 = new JLabel("UDL ");
        label1.setHorizontalAlignment(SwingConstants.RIGHT);
        p3.add(label1);
        JLabel label2 = new JLabel("Subject ");
        label2.setHorizontalAlignment(SwingConstants.RIGHT);
        p3.add(label2);
        JLabel label3 = new JLabel("Type ");
        label3.setHorizontalAlignment(SwingConstants.RIGHT);
        p3.add(label3);
        JLabel label4 = new JLabel("Status ");
        label4.setHorizontalAlignment(SwingConstants.RIGHT);
        p3.add(label4);

        // textfield panel
        JPanel p4 = new JPanel();
        p4.setLayout(new GridLayout(4, 0));
        p4.add(UDL);
        p4.add(Subject);
        p4.add(Type);
        p4.add(status);

        // keep left hand labels from growing & shrinking in X-axis
        Dimension d = p3.getPreferredSize();
        d.height = p4.getPreferredSize().height;
        p3.setMaximumSize(d);

        // panel containing label & textfield panels
        JPanel p2 = new JPanel();
        p2.setLayout(new BoxLayout(p2, BoxLayout.X_AXIS));
        p2.add(Box.createRigidArea(new Dimension(5, 0)));
        p2.add(p3);
        p2.add(p4);
        p2.add(Box.createRigidArea(new Dimension(3, 0)));

        // top-level panel
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(Box.createRigidArea(new Dimension(0, 3)));
        panel.add(p2);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(p1);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));

        return panel;
    }
}
