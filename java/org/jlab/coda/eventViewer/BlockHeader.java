package org.jlab.coda.eventViewer;


import org.jlab.coda.jevio.BlockHeaderV4;

import java.util.ArrayList;

/**
 * Modified copy of the org.jlab.code.jevio.BlockNode class.
 * Used to represent an evio structure such as a Bank, Segment,
 * or Tagsegment.
 *
 * @author timmer
 * (1/20/17)
 */
public final class BlockHeader {

    /** Block's length value (32-bit words). */
    long len;
    /** Block's header length value (32-bit words). */
    int headerLen;
    /** Number of events in block. */
    int count;
    /** Position of block in file.  */
    long filePos;
    /** Place of this block in file/buffer or block number.
     *  First block = 1, second = 2, etc. */
    int place;
    /** Word containing version, hasDictionary, and is Last info. */
    int infoWord;
    /** Evio version. */
    int version=4;
    /** Block has dictionary event. */
    boolean hasDictionary;
    /** Is last block in file. */
    boolean isLast;
    /** Contains description of any error in block's data. */
    String error;

    /** If error somewhere in this block, store reference to each contained event. */
    final ArrayList<EvioHeader> events = new ArrayList<EvioHeader>();


    /**
     * Given the info word, set version, isLast, and hasDictionary values.
     * @param infoWord info word of block header
     */
    void setInfoWord(int infoWord) {
        this.infoWord = infoWord;
        version       = infoWord & 0xff;
        isLast        = BlockHeaderV4.isLastBlock(infoWord);
        hasDictionary = BlockHeaderV4.hasDictionary(infoWord);
    }


    /**
     * Set members given an array containing the block header values.
     * @param blockInts array containing the block header values in proper order.
     */
    void setData(int[] blockInts) {
        len           = blockInts[0];
        place         = blockInts[1];
        headerLen     = blockInts[2];
        count         = blockInts[3];
        setInfoWord(blockInts[5]);
    }
}

