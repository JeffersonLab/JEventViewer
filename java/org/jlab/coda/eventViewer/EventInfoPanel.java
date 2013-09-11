package org.jlab.coda.eventViewer;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EtchedBorder;
import java.awt.*;

/**
 * This is a panel that displays evio event info -
 * event source, dictionary source - at the top of the GUI.
 */
public class EventInfoPanel extends JPanel {

    /**A label for displaying the current event source name. */
    private NamedLabel eventSourceLabel;

    /** A label for displaying the dictionary source. */
    private NamedLabel dictionaryLabel;


    /**
     * Create a panel that goes in the north - top of the GUI. This will hold 2 labels.
     * One showing the current event source. The second showing the current dictionary source.
     *
     * @return the panel.
     */
    public EventInfoPanel() {
        Border lowerEtched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        Border empty = BorderFactory.createEmptyBorder(5,5,5,5);  // top, left, bot, right
        // This creates a nice frame
        CompoundBorder compound2 = BorderFactory.createCompoundBorder(lowerEtched, empty);

        setLayout(new BorderLayout());
        setBorder(compound2);

        eventSourceLabel = new NamedLabel("event source", "event_source", 430);
        dictionaryLabel  = new NamedLabel("dictionary", "event_source", 430);

        // limit size of labels
        Dimension d1 = eventSourceLabel.getPreferredSize();

        eventSourceLabel.setMaximumSize(d1);
        dictionaryLabel.setMaximumSize(d1);

        // panels

        JPanel p0 = new JPanel(new GridLayout(2, 1, 0, 3));
        p0.add(eventSourceLabel);
        p0.add(dictionaryLabel);

        add(p0, BorderLayout.CENTER);
    }

    /**
     * Set this panel's displayed values.
     *
     * @param source source of viewed event.
     * @param dictionary dictionary source: name of dictionary file or,
     *                   "in message" if dictionary came from cMsg message.
     */
    public void setDisplay(String source, String dictionary) {
        if (source != null) {
            eventSourceLabel.setText(source);
        }
        if (dictionary != null) {
            dictionaryLabel.setText(dictionary);
        }
    }

    /**
     * Set the displayed dictionary source value.
     * @param dictionary dictionary source.
     */
    public void setDictionary(String dictionary) {
        if (dictionary != null) {
            dictionaryLabel.setText(dictionary);
        }
    }

    /**
     * Get the displayed dictionary source value.
     * @return the displayed dictionary source value.
     */
    public String getDictionary() {
        return dictionaryLabel.getText();
    }

    /**
     * Set the displayed event source value.
     * @param source event source.
     */
    public void setSource(String source) {
        if (source != null) {
            eventSourceLabel.setText(source);
        }
    }

    /**
     * Get the displayed event source value.
     * @return the displayed event source value.
     */
    public String getSource() {
        return eventSourceLabel.getText();
    }

}
