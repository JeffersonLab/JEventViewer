package org.jlab.coda.eventViewer;


import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.hipo.RecordHeader;
import org.jlab.coda.jevio.Utilities;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Modified copy of the org.jlab.code.jevio.BlockNode class but for evio version 6 records.
 *
 * @author timmer
 * (3/25/21)
 */
public final class BlockHeaderV6 implements Cloneable {

    static int MAGIC_INT = 0xc0da0100;

    /** Record's length value (32-bit words), inclusive. */
    long len;
    /** Record's header length value (32-bit words). */
    int headerLen;
    /** Number of events in record. */
    int count;

    /** Position of record in file.  */
    long filePos;

    /** Place of this record in file/buffer or record number.
     *  First record = 1, second = 2, etc. */
    int place;
    /** Word containing version, hasDictionary, and is Last info. */
    int infoWord;
    /** Evio version. */
    int version=6;
    /** Record has dictionary event. */
    boolean hasDictionary;
    /** Is last record in file. */
    boolean isLast;

    // Following are fields unique to evio 6's record header
    /** Index array length in bytes. Index array contains lengths of each event in bytes. */
    int indexArrayBytes;
    /** Length of user defined data in bytes. */
    int userHeaderBytes;
    /** Following data, uncompressed, in bytes. */
    int uncompressedDataBytes;
    /** Type of data compression, 0 = none, 1 = lz4 , 2 = lz4_best, 3 = gzip. */
    int compressionType;
    String compressionTypeStr;
    /** Data length of compressed, following data in words. */
    int compressedDataWords;
    /** User defined long register 1. */
    long register1;
    /** User defined long register 2. */
    long register2;
    //------------------------------------------------------
    /** Total bytes of header + index + user header. */
    int totalBytes;
    //------------------------------------------------------

    /** Contains description of any error in record's data. */
    String error;
    /** The index into the "events" list of the element that
      * is currently being looked at (starting at 0). */
    int currentEventIndex;

    /** If error somewhere in this record, store reference to each contained event. */
    final ArrayList<EvioHeader> events = new ArrayList<EvioHeader>();


    public Object clone() {
        try {
            return super.clone();
        }
        catch (CloneNotSupportedException ex) {
            return null;    // never invoked
        }
    }

    /**
     * Given the info word, set version, isLast, and hasDictionary values.
     * @param infoWord info word of record header
     */
    void setInfoWord(int infoWord) {
        this.infoWord = infoWord;
        version       = infoWord & 0xff;
        isLast        = RecordHeader.isLastRecord(infoWord);
        hasDictionary = RecordHeader.hasDictionary(infoWord);
    }


    /**
     * Set members given an array containing the record header values.
     * @param recordInts array containing the record header values in proper order.
     * @param order byte order of data in recordInts so longs can be parsed properly.
     */
    void setData(int[] recordInts, ByteOrder order) {
        if (recordInts.length < 14) {
            System.out.println("BlockHeaderV6.setData: input array is too small");
            return;
        }

        len             = recordInts[0];
        place           = recordInts[1];
        headerLen       = recordInts[2];
        count           = recordInts[3];
        indexArrayBytes = recordInts[4];

        setInfoWord(recordInts[5]);

        userHeaderBytes        = recordInts[6];
        uncompressedDataBytes  = recordInts[8];
        compressionType        = recordInts[9] >>> 28;
        CompressionType cType  = CompressionType.getCompressionType(compressionType);
        if (cType != null) {
            compressionTypeStr = cType.getDescription();
        }
        compressedDataWords    = recordInts[9] & 0xffffff;

        if (order == ByteOrder.BIG_ENDIAN) {
            register1 = ((long) recordInts[10] << 32) | recordInts[11];
            register2 = ((long) recordInts[12] << 32) | recordInts[13];
        }
        else {
            register1 = ((long) recordInts[11] << 32) | recordInts[10];
            register2 = ((long) recordInts[13] << 32) | recordInts[12];
        }

        totalBytes = 4*headerLen + indexArrayBytes + 4*Utilities.getWords(userHeaderBytes);
    }

    /**
     * Set members given an array containing the record header values.
     * @param recordInts array containing the record header values in proper order.
     */
    void setData(ByteBuffer recordInts) {
        if (recordInts.remaining() < 4*14) {
            System.out.println("BlockHeaderV6.setData: input ByteBuffer is too small");
            return;
        }

        int pos = recordInts.position();

        // Do absolute reads so we don't mess with the buffer's position

        len             = recordInts.getInt(pos); pos+=4;
        place           = recordInts.getInt(pos); pos+=4;
        headerLen       = recordInts.getInt(pos); pos+=4;
        count           = recordInts.getInt(pos); pos+=4;
        indexArrayBytes = recordInts.getInt(pos); pos+=4;

        setInfoWord(recordInts.getInt(pos)); pos+=4;

        userHeaderBytes        = recordInts.getInt(pos); pos+=8;
        uncompressedDataBytes  = recordInts.getInt(pos); pos+=4;
        int compStuff          = recordInts.getInt(pos); pos+=4;
        compressionType        = compStuff >>> 28;
        compressionTypeStr     = CompressionType.getCompressionType(compressionType).getDescription();
        compressedDataWords    = compStuff & 0xffffff;

        register1 = recordInts.getLong(pos); pos += 8;
        register1 = recordInts.getLong(pos);

        totalBytes = 4*headerLen + indexArrayBytes + 4*Utilities.getWords(userHeaderBytes);
    }


    public String toString () {

        StringBuilder sb = new StringBuilder(400);

        sb.append("len = ");
        sb.append(len);
        sb.append("\nplace =");
        sb.append(place);
        sb.append("\nhdr len = ");
        sb.append(headerLen);
        sb.append("\nrec count = ");
        sb.append(count);
        sb.append("\nindex bytes = ");
        sb.append(indexArrayBytes);

        sb.append("\nversion = ");
        sb.append(version);
        sb.append("\nhas dict = ");
        sb.append(hasDictionary);
        sb.append("\nis last = ");
        sb.append(isLast);
        sb.append("\nversion = ");
        sb.append(version);

        sb.append("\nuser hdr bytes = ");
        sb.append(userHeaderBytes);
        sb.append("\nuncomp data bytes = ");
        sb.append(uncompressedDataBytes);
        sb.append("\ncompression type = ");
        sb.append(uncompressedDataBytes);
        sb.append("\ncomp data words = ");
        sb.append(compressedDataWords);
        sb.append("\nindex bytes = ");
        sb.append(indexArrayBytes);

        sb.append("\nregister 1 = ");
        sb.append(register1);
        sb.append("\nregister 2 = ");
        sb.append(register2);
        sb.append("\ntotalBytes = ");
        sb.append(totalBytes);

        return sb.toString();
    }
}

