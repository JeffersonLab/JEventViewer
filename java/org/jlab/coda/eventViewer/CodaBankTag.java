package org.jlab.coda.eventViewer;

import java.util.HashMap;

/**
 * This an enum listing the evio tag values reserved for and used by the
 * CODA group for online designations.
 * Used to convert numerical tag values to a meaningful name.
 * For example, the tag with value 0xff10 corresponds to the enum TRIGGER_RAW_NOTS
 * which refers to a raw trigger bank with no timestamps.
 *
 * @author timmer
 */
public enum CodaBankTag {
    /** Raw trigger bank, no timestamps */
    TRIGGER_RAW_NOTS                 (0xff10, "Raw trigger bank"),
    /** Raw trigger bank, with timestamps */
    TRIGGER_RAW_TS                   (0xff11, "Raw trigger bank"),
   	/** Built trigger bank, no timestamps, run: no #/type, with specific */
    TRIGGER_BUILT_NOTS_NORUN         (0xff20, "Built trigger bank"),
    /** Built trigger bank, with timestamps, run: no #/type, with specific */
    TRIGGER_BUILT_TS_NORUN           (0xff21, "Built trigger bank"),
    /** Built trigger bank, no timestamps, run: with #, type and specific */
    TRIGGER_BUILT_NOTS_RUN           (0xff22, "Built trigger bank"),
    /** Built trigger bank, with timestamps, run: with #, type and specific */
    TRIGGER_BUILT_TS_RUN             (0xff23, "Built trigger bank"),
    /** Built trigger bank, no timestamps, run: no #, type or specific */
    TRIGGER_BUILT_NOTS_NORUN_NOSPEC  (0xff24, "Built trigger bank"),
    /** Built trigger bank, with timestamps, run: no #, type or specific */
    TRIGGER_BUILT_TS_NORUN_NOSPEC    (0xff25, "Built trigger bank"),
    /** Built trigger bank, no timestamps, run: #/type, no specific */
    TRIGGER_BUILT_NOTS_RUN_NOSPEC    (0xff26, "Built trigger bank"),
    /** Built trigger bank, with timestamps, run: no #/type, no specific */
    TRIGGER_BUILT_TS_RUN_NOSPEC      (0xff27, "Built trigger bank"),
    /** Primary event builder */
   	PEB                              (0xff50, "PEB built"),
    /** Primary event builder with sync set */
   	PEB_SYNC                         (0xff58, "PEB built & sync"),
    /** Secondary event builder */
   	SEB                              (0xff70, "SEB built"),
    /** Secondary event builder with sync set */
   	SEB_SYNC                         (0xff78, "SEB built & sync"),
    /** Sync event */
   	SYNC                             (0xffd0, "Sync event"),
    /** Prestart event */
    PRESTART                         (0xffd1, "Prestart event"),
    /** Go event */
    GO                               (0xffd2, "Go event"),
    /** Pause event */
   	PAUSE                            (0xffd3, "Pause event"),
    /** End event */
   	END                              (0xffd4, "End event");


    /** Each name is associated with a specific evio integer tag value. */
    private int value;

    /** Each name is associated with a description. */
    private String description;

    /** Way to convert integer tag values into CodaBankTags objects. */
    private static HashMap<Integer, CodaBankTag> tagToString;


    // Fill map after all enum objects created
    static {
        tagToString = new HashMap<Integer, CodaBankTag>(32);
        for (CodaBankTag type : values()) {
            tagToString.put(type.value, type);
        }
    }


    /**
     * Obtain the enum from the tag value or null if none.
     *
     * @param val the tag value to match.
     * @return the matching enum, or <code>null</code>.
     */
    public static CodaBankTag getBankType(int val) {
        return tagToString.get(val);
    }


    /**
     * Obtain the name from the tag value.
     *
     * @param val the tag value to match.
     * @return the name, or "Unknown".
     */
    public static String getName(int val) {
        CodaBankTag tag = getBankType(val);
        if (tag == null) return "Unknown";
        return tag.name();
    }


    /**
     * Obtain the description from the tag value.
     *
     * @param val the tag value to match.
     * @return the description, or "Unknown".
     */
    public static String getDescription(int val) {
        CodaBankTag tag = getBankType(val);
        if (tag == null) return "Unknown";
        return tag.description;
    }


    /**
     * Constructor.
     * @param value CODA tag value
     * @param description tag description
     */
    private CodaBankTag(int value, String description) {
        this.value = value;
        this.description = description;
    }


    /**
     * Get the enum's tag value.
     * @return tag value
     */
    public int getValue() {
        return value;
    }


    /**
     * Get the enum's description.
     * @return description
     */
    public String getDescription() {
        return description;
    }

}
