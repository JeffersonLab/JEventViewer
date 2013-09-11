package org.jlab.coda.eventViewer;

/**
 * This enum contains the choices for a source of events to view.
 * @author timmer
 * @date Oct 20, 2009
 */
public enum EventSource {
    FILE ,
    CMSG ,
    ET   ;

    /** Each event source may be associated with a single dictionary source
      * and thereby a single dictionary. */
    private DictionarySource source = DictionarySource.NONE;


    /** Constructor. */
    private EventSource() {}

    /**
     * Set the dictionary source to be associated
     * with a particular event source.
     *
     * @param source source of associated dictionary
     */
    public void setDictionarySource(DictionarySource source) {
        if (source == null) {
            this.source = DictionarySource.NONE;
        }
        else {
            this.source = source;
        }
    }

    /**
     * Get the associated dictionary source.
     * @return associated dictionary source.
     */
    public DictionarySource getDictionarySource() {
        return source;
    }

}
