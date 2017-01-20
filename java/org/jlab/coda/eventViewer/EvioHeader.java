package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.DataType;

/**
 * Abbreviated copy of the org.jlab.code.jevio.EvioNode class.
 * Used to represent an evio structure such as a Bank, Segment,
 * or Tagsegment.
 *
 * @author timmer
 * (1/20/17)
 */
public final class EvioHeader implements Cloneable {

    /** Header's length value (32-bit words). */
    long len;
    /** Position of header in file in bytes.  */
    long pos;
    /** This node's (evio container's) type. Must be bank, segment, or tag segment. */
    int type;
    /** Tag. */
    int tag;
    /** Num, only meaningful for Bank. */
    int num;
    /** Padding, only meaningful if type is 8 or 16 bit int. */
    int pad;

    /** Length of node's data in 32-bit words. */
    long dataLen;
    /** Position of node's data in file in bytes. */
    long dataPos;
    /** Type of data stored in node. */
    int dataType;

    /** Type of bank based on tag's value. */
    String bankType;

    /** Contains description of any error in event's header. */
    String error;

    /** Does this node represent an event (top-level bank)? */
    boolean isEvent;

    /** Data source that this node is associated with. */
    FileFrameBig.MyTableModel model;

    //-------------------------------
    // For event-level node
    //-------------------------------

    /** Place of containing event in file/buffer. First event = 0, second = 1, etc. */
    int place;

    //----------------------------------
    // Constructors (package accessible)
    //----------------------------------

    /**
     * Constructor which creates an EvioNode associated with
     * an event (top level).
     *
     * @param word1  event's first header word
     * @param word2  event's second header word
     */
    EvioHeader(int word1, int word2) {
        len  = word1;
        tag  = word2 >> 16 & 0xffff;
        num  = word2 & 0xff;
        pad  = word2 >> 14 & 0x3;
        dataType = word2 >> 8 & 0x3f;
        // Assume we're hopping from bank to bank
        type = DataType.BANK.getValue();
        bankType = CodaBankTag.getDescription(tag);
    }

    /**
     * Constructor which creates an EvioNode associated with
     * an event (top level) evio container when parsing buffers
     * for evio data.
     *
     * @param pos        position of event in buffer (number of bytes)
     * @param place      containing event's place in buffer (starting at 1)
     * @param model      data source containing this event
     */
    EvioHeader(long pos, int place, FileFrameBig.MyTableModel model) {
        this.pos   = pos;
        this.place = place;
        this.model = model;
        // This is an event by definition
        this.isEvent = true;
        // Event is a Bank by definition
        this.type = DataType.BANK.getValue();
    }


    //-------------------------------
    // Methods
    //-------------------------------

    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException ex) {
            return null;    // never invoked
        }
    }

    //-------------------------------
    // Getters  &  Setters
    //-------------------------------

    /**
     * Set all relevant parameters.
     *
     * @param word1    event's first header word
     * @param word2    event's second header word
     * @param pos      evio structure's byte position in file
     * @param dataPos  evio structure's data position in file
     * @param dataLen  length of node data in 32-bit words
     * @param dataType type of data contained in node
     * @param isEvent  is this structure an event (top-level bank)?
     */
    public void setAll (int word1, int word2, long pos, long dataPos,
                        int dataLen, int dataType, boolean isEvent) {
        len  = word1;
        tag  = word2 >> 16 & 0xffff;
        num  = word2 & 0xff;
        pad  = word2 >> 14 & 0x3;
        dataType = word2 >> 8 & 0x3f;
        // Assume we're hopping from bank to bank
        type = DataType.BANK.getValue();
        bankType = CodaBankTag.getDescription(tag);

        this.pos      = pos;
        this.dataPos  = dataPos;
        this.dataLen  = dataLen;
        this.dataType = dataType;
        this.isEvent  = isEvent;
    }

    /**
     * Get the position of this event in file.
     * @return position of this event in file.
     */
    public long getFilePosition() { return pos; }


    /**
     * Get the evio type of the data this evio structure contains as an object.
     * @return evio type of the data this evio structure contains as an object.
     */
    public DataType getDataTypeObj() {
        return DataType.getDataType(dataType);
    }

    /**
     * Get the evio type of this evio structure as an object.
     * @return evio type of this evio structure as an object.
     */
    public DataType getTypeObj() {
        return DataType.getDataType(type);
    }
}

