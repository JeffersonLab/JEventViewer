package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.*;

/**
 * This enum contains the choices for a filter which
 * allows the described evio events into the event viewer.
 *
 * @author timmer
 * Mar 19, 2013
 */
public enum Filter {

    /** Allow all. Default filtering. */
    EVERY   (true),
    /** Allow only CODA physics events. */
    PHYSICS (false),
    /** Allow only CODA partially-built physics events. */
    PARTIAL (false),
    /** Allow only CODA control events. */
    CONTROL (false),
    /** Allow only CODA ROC raw events. */
    ROC_RAW (false);


    // Is this filter active? Are we allowing this CODA event type into the viewer?
    private boolean active;


    // Stuff for Physics & Partial Physics filtering

    /** Array of all trigger types contained in a single ROC raw event. */
    private int[] triggerTypes;

    /** Value of trigger type which is allowed by this filter in Physics events. */
    private int triggerType = -1;


    /** Constructor. */
    Filter(boolean active) {
        this.active = active;
    }


    public boolean isActive() {
        return active;
    }

    synchronized public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Set the value of the trigger type which is
     * allowed by this filter in Physics events.
     * @param triggerType  value of the trigger type which is
     *                     allowed by this filter in Physics events.
     */
    synchronized public void setTriggerType(int triggerType) {
        this.triggerType = triggerType;
    }


    /**
     * Filter to apply to Evio events.
     *
     * @param event evio event to be examined
     * @return <code>true</code> if event is allowed into viewer, else <code>false</code>.
     */
    synchronized public static boolean allow(EvioEvent event) {
        // If the EVERY event filter is active,
        // all events are allowed in
        if (EVERY.active) {
            return true;
        }

        // If the PHYSICS event filter is active,
        // all physics events are allowed in
        if (PHYSICS.active && PHYSICS.isPhysics(event)) {
            // If we're not interested in filtering on trigger types, we're done
            if (PHYSICS.triggerType == -1) return true;

            // Now look at trigger types
            if (PHYSICS.triggerTypes == null) return true;

            for (int tType : PHYSICS.triggerTypes) {
                if (PHYSICS.triggerType == tType) {
//System.out.println("Matching event type found!");
                    return true;
                }
            }
            return false;
        }

        // If the PARTIAL event filter is active, all
        // partially-built physics events are allowed in
        if (PARTIAL.active && PHYSICS.isPartialPhysics(event)) {
            // If we're not interested in filtering on trigger types, we're done
            if (PARTIAL.triggerType == -1) return true;

            // Now look at trigger types
            if (PARTIAL.triggerTypes == null) return true;

            for (int tType : PARTIAL.triggerTypes) {
                if (PARTIAL.triggerType == tType) {
//System.out.println("Matching event type found!");
                    return true;
                }
            }
            return false;
        }

        // If the CONTROL event filter is active,
        // all control events are allowed in
        if (CONTROL.active && CONTROL.isControl(event)) {
            return true;
        }

//        if (ROC_RAW.active && ROC_RAW.isRocRaw(event)) {
//            return true;
//        }

        return false;
    }




    /**
     * Filter to apply to Evio events.
     *
     * @param event evio event to be examined
     * @return <code>true</code> if event is allowed into viewer, else <code>false</code>.
     */
    synchronized public boolean allow2(EvioEvent event) {
        switch (this) {
            case EVERY:
                return true;

            case ROC_RAW:
                return false;

            case CONTROL:
                return isControl(event);

            case PARTIAL:
                if (isPartialPhysics(event)) {
                    // If we're not interested in filtering on trigger types, we're done
                    if (triggerType == -1) return true;

                    // Now look at trigger types
                    if (triggerTypes == null) return true;

                    for (int tType : triggerTypes) {
                        if (triggerType == tType) {
//System.out.println("Matching event type found!");
                            return true;
                        }
                    }
                }

            case PHYSICS:
                if (isPhysics(event)) {
                    // If we're not interested in filtering on trigger types, we're done
                    if (triggerType == -1) return true;

                    // Now look at trigger types
                    if (triggerTypes == null) return true;

                    for (int tType : triggerTypes) {
                        if (triggerType == tType) {
//System.out.println("Matching event type found!");
                            return true;
                        }
                    }
                }
                break;

            default:
        }

        System.out.println("event reject 1");
        return false;
    }


    /**
     * Determine whether a bank is a physics event or not.
     *
     * @param bank input bank
     * @return <code>true</code> if arg is a physics event, else <code>false</code>
     */
    private boolean isPhysics(EvioBank bank) {
        if (bank == null)  return false;

        BaseStructureHeader header = bank.getHeader();

        // Always bank of banks
        if (header.getDataType() != DataType.BANK &&
            header.getDataType() != DataType.ALSOBANK) {
            return false;
        }

        // Tag of fully built event is in range of (0xFF50 - 0xFF8F) inclusive
        int tag = header.getTag();
        if (tag < 0xFF50 || tag > 0xFF8F) return false;

        // As long as we're here, save all the event/trigger types
        int eventCount = header.getNumber();

        if (eventCount > 0) {
            // Get first bank, which is trigger bank (of segments)
            EvioBank kid = (EvioBank)bank.getChildAt(0);
            if (kid == null) return false;

            // Event types are in second segment
            EvioSegment seg = (EvioSegment)kid.getChildAt(1);
            if (seg == null) return false;

            short[] data = seg.getShortData();
            if (data == null) return false;

            triggerTypes = new int[data.length];
            for (int i=0; i < data.length; i++) {
                // no unsigned types in Java, get rid of signed extension
                triggerTypes[i] = 0xFFFF & data[i];
//System.out.println("Found ev type " + triggerTypes[i]);
            }
//System.out.println();
        }
        else {
            triggerTypes = null;
        }

        return true;
    }


    /**
     * Determine whether a bank is a control event or not.
     *
     * @param bank input bank
     * @return <code>true</code> if arg is a control event, else <code>false</code>
     */
    private boolean isControl(EvioBank bank) {
        if (bank == null) return false;

        BaseStructureHeader header = bank.getHeader();

        // Len is always 4, 32-bit ints
        if (header.getLength() != 4) return false;

        // Data is always unsigned 32-bit ints
        if (header.getDataType() != DataType.UINT32) return false;

        // Tag of fully-built event is in range of (0xFFD0 - 0xFFD4) inclusive
        int tag = bank.getHeader().getTag();
        return tag >= 0xFFD0 && tag <= 0xFFD4;
    }


    /**
     * Determine whether a bank is a partially-built physics event or not.
     *
     * @param bank input bank
     * @return <code>true</code> if arg is a partially-built physics event,
     *         else <code>false</code>
     */
    private boolean isPartialPhysics(EvioBank bank) {
        // Partially built event is best identified by its trigger bank

        // Must be bank of banks
        BaseStructureHeader header = bank.getHeader();
        if (header.getDataType() != DataType.BANK &&
            header.getDataType() != DataType.ALSOBANK) {
            System.out.println("partial reject 0");
            return false;
        }

        // tag must NOT be same as built event
        int tag = bank.getHeader().getTag();
        if (tag >= 0xFF50 && tag <= 0xFF8F) {
            System.out.println("partial reject 1");
            return false;
        }

        // As long as we're here, save all the event/trigger types
        int eventCount = header.getNumber();

        if (eventCount > 0) {
            // Get first bank, which is trigger bank (of segments)
            EvioBank kid = (EvioBank)bank.getChildAt(0);
            if (kid == null) {
                System.out.println("partial reject 2");
                return false;
            }

            // Event types are in second segment
            EvioSegment seg = (EvioSegment)kid.getChildAt(1);
            if (seg == null) {
                System.out.println("partial reject 3");
                return false;
            }

            short[] data = seg.getShortData();
            if (data == null) {
                System.out.println("partial reject 4");
                return false;
            }

            triggerTypes = new int[data.length];
            for (int i=0; i < data.length; i++) {
                // no unsigned types in Java, get rid of signed extension
                triggerTypes[i] = 0xFFFF & data[i];
//System.out.println("Found ev type " + triggerTypes[i]);
            }
//System.out.println();
        }
        else {
            triggerTypes = null;
        }

        System.out.println("partial accept");
        return true;
    }


    /**
     * Determine whether a bank is a ROC raw event or not.
     *
     * @param bank input bank
     * @return <code>true</code> if arg is a ROC raw event, else <code>false</code>
     */
    private boolean isRocRaw(EvioBank bank) {
        // Roc raw is best identified by its trigger bank

        // Must be bank of banks
        BaseStructureHeader header = bank.getHeader();
        if (header.getDataType() != DataType.BANK ||
            header.getDataType() != DataType.ALSOBANK) {
            return false;
        }

        // Get first bank, which should be trigger bank (bank of segments).
        // (This will NOT be true if in single-event-mode).
        EvioBank kid = (EvioBank)bank.getChildAt(0);
        if (kid == null) return false;

        header = kid.getHeader();
        int tag = header.getTag();

        if (tag < 0xFF10 || tag > 0xFF1F) return false;

        // Data are always segments
        return (header.getDataType() == DataType.SEGMENT ||
                header.getDataType() == DataType.ALSOSEGMENT);
    }

}
