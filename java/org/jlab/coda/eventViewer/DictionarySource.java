package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.EvioXMLDictionary;

/**
 * This enum contains the choices for a source of dictionary.
 * @author timmer
 * Feb 21, 2013
 */
public enum DictionarySource {
    EVIOFILE ,   // embedded in evio file
    XML      ,   // from external xml file
    CMSG     ,   // in cMsg message either embedded in evio byte array
                 // (1st priority) or as "dictionary" payload item
    ET       ,   // in et event's data embedded in evio byte array
    NONE     ;   // no dictionary in use


    /** Each dictionary source may be associated with a single dictionary - in xml form. */
    private String xml;

    /** Each dictionary source may be associated with a single dictionary - in object form. */
    private EvioXMLDictionary dictionary;


    /** Constructor. */
    private DictionarySource() {}

    /**
     * Set both the string and object forms of a dictionary to be associated
     * with a particular dictionary source.
     *
     * @param dictionary dictionary object
     */
    public void setDictionary (EvioXMLDictionary dictionary) {
        this.dictionary = dictionary;

        if (dictionary == null) {
            xml = null;
        }
        else {
            xml = dictionary.toXML();
        }
    }

    /**
     * Get the associated xml string dictionary.
     * @return associated xml string dictionary.
     */
    public String getXML() {
        return xml;
    }

    /**
     * Get the associated dictionary object.
     * @return associated dictionary object.
     */
    public EvioXMLDictionary getDictionary() {
        return dictionary;
    }
 }
