package org.jlab.coda.eventViewer;


import org.jlab.coda.hipo.FileHeader;
import org.jlab.coda.hipo.HeaderType;


/**
 * Class but for evio version 6 file header.
 *
 * @author timmer
 * (3/25/21)
 */
public final class FileHeaderV6 {

    static int MAGIC_INT = 0xc0da0100;

    /** File's id value. */
    long id;
    /** Type of evio file. */
    String fileType;
    /** File's split number. */
    int fileNumber;
    /** NUmber of words in this header. */
    int headerLen;
    /** Number of records in file. */
    int count;

    /** Word containing version, hasDictionary, and is Last info. */
    int infoWord;
    /** Evio version. */
    int version=6;
    /** Record has dictionary event. */
    boolean hasDictionary;
    /** Has a "first event" which exists in each related split file. */
    boolean hasFirstEvent;
    /** Has a last record with no data (trailer) containing an index of all records. */
    boolean hasTrailerWithIndex;

    /** Index array length in bytes. Index array contains lengths of each record in bytes. */
    int indexArrayBytes;
    /** Length of user defined data in bytes. */
    int userHeaderBytes;
    /** User defined long register. */
    long register;
    /** Trailer position from beginning of file. */
    long trailerPos;
    /** User defined long integer 1. */
    int userInt1;
    /** User defined long integer 2. */
    int userInt2;


    /**
     * Given the info word, sets values embedded in it.
     * @param infoWord info word of file header
     */
    void setInfoWord(int infoWord) {
        this.infoWord = infoWord;
        version       = infoWord & 0xff;
        hasFirstEvent = FileHeader.hasFirstEvent(infoWord);
        hasDictionary = FileHeader.hasDictionary(infoWord);
        hasTrailerWithIndex = FileHeader.hasTrailerWithIndex(infoWord);
        fileType = (FileHeader.getFileType(infoWord)).toString();
    }


    /**
     * Set members given an array containing the file header values.
     * @param recordInts array containing the file header values in proper order.
     */
    void setData(int[] recordInts) {
        if (recordInts.length < 14) {
            System.out.println("FileHeaderV6.setData: input array is too small");
            return;
        }

        id           = recordInts[0];
        fileNumber   = recordInts[1];
        headerLen    = recordInts[2];
        count        = recordInts[3];

        setInfoWord(recordInts[5]);

        indexArrayBytes        = recordInts[4];
        userHeaderBytes        = recordInts[6];
// TODO: make sure both endians work ...
        register              = ((long)recordInts[8]  << 32) | recordInts[9];
        trailerPos            = ((long)recordInts[10] << 32) | recordInts[11];

        userInt1 = recordInts[12];
        userInt2 = recordInts[13];

    }
}

