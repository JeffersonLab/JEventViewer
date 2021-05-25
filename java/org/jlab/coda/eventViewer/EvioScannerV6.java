package org.jlab.coda.eventViewer;


import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.hipo.RecordHeader;
import org.jlab.coda.jevio.*;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class is used to scan an <b>EVIO VERSION 6</b> file for block info, event info, and evio errors
 * in order to catalog them for later viewing.
 *
 * @author timmer (1/9/15)
 */
public class EvioScannerV6 {

    /** Stores info of all block headers in an evio format file with an error. */
    private final ArrayList<BlockHeaderV6> blockErrorNodes = new ArrayList<BlockHeaderV6>(10);

    /** Object for accessing file data. */
    private final MyTableModel dataModel;

    /** Object for rendering file data in table. */
    private final MyRenderer dataRenderer;

    /** Reference to GUI component that creates this object. */
    private FileFrameV6 parentComponent;

    /** Reference needed to update progress bar when searching file for evio errors. */
    private FileFrameV6.ErrorScanTask errorScanTask;



    /**
     * Is the given data type one that can have non-zero padding?
     * @param type evio data type
     * @return {@code true} if the data type can have non-zero padding, else {@code false}.
     */
    static public boolean dataTypeHasPadding(DataType type) {
        return type == DataType.CHAR8    ||
               type == DataType.UCHAR8   ||
               type == DataType.SHORT16  ||
               type == DataType.USHORT16 ||
               type == DataType.COMPOSITE;
    }


    /**
     * Constructor.
     * @param component     window that displays a file's bytes as hex, 32 bit integers.
     * @param dataModel     data table model - object with file memory maps.
     * @param dataRenderer  data table render.
     * @param errorTask     object doing file scan in background,
     *                      use to update its progress.
     * @throws EvioException if endianness is wrong, version is wrong,
     *                       or too little data to read block header
     */
    public EvioScannerV6(FileFrameV6 component,
                         MyTableModel dataModel,
                         MyRenderer dataRenderer,
                         FileFrameV6.ErrorScanTask errorTask) throws EvioException {
        this.parentComponent = component;
        this.dataModel       = dataModel;
        this.dataRenderer    = dataRenderer;
        this.errorScanTask   = errorTask;

        checkFirstHeader();
    }


    /**
     * Get the list of BlockHeaderV6 objects containing evio errors.
     * @return list of BlockHeaderV6 objects containing evio errors.
     */
    public ArrayList<BlockHeaderV6> getBlockErrorNodes() { return blockErrorNodes; }


    /**
     * Did the scan of the file show any evio errors?
     * @return {@code true} if there were errors, else {@code false}.
     */
    public boolean hasError() {return (blockErrorNodes.size() > 0); }


    /**
     * Check the given data type and padding to see if they're
     * valid (or non-null) values and consistent with each other.
     * @param dataTypeVal data type as integer.
     * @param pad         padding of data.
     * @return null if no error, else string describing error.
     */
    public String checkDataTypeAndPadding(int dataTypeVal, int pad) {
        String error = null;
        DataType dataType = DataType.getDataType(dataTypeVal);

        // If type or pad is a bad value, return error condition
        if (dataType == null) {
            error = "Bad data type (" + dataType + ")";
        }
        else if (pad != 0) {
            if (pad == 2) {
                if (!dataTypeHasPadding(dataType)) {
                    error = "Padding (" + pad + ") does not match data type ("
                            + dataType + ")";
                }
            }
            else if ((dataType != DataType.CHAR8 &&
                      dataType != DataType.UCHAR8 &&
                      dataType != DataType.COMPOSITE)) {
                error = "Padding (" + pad + ") does not match data type ("
                        + dataType + ")";
            }
        }

        return error;
    }


    /**
     * This method only searches for a top-level evio structure (EvioNode) in data.
     * Data in other evio levels is not examined.
     *
     * @param model      buffer to examine
     * @param position   position in buffer
     * @param place      place of event in buffer (event # starting at 0)
     * @param bytesLeft  # of bytes left to read in file.
     *
     * @return EvioNode object containing top-level evio structure;
     *         null if less than 8 bytes left.
     */
    private EvioHeader extractEventNode(MyTableModel model,
                                        long position, int place, long bytesLeft) {

        if (bytesLeft < 8) return null;

        // Store evio event info, without de-serializing, into EvioNode object
        EvioHeader node = new EvioHeader(position, place, model);

        // Get length of current event
        node.len = model.getInt(position);
        // Position of data for a bank
        node.dataPos = position + 8;
        // Len of data for a bank
        node.dataLen = node.len - 1;

        // Hop over length word
        position += 4;

        // Read and parse second header word
        int word = model.getInt(position);
        node.tag = (word >>> 16);
        int dt = (word >> 8) & 0xff;
        node.dataType = dt & 0x3f;
        node.pad = dt >>> 6;
        // If only 7th bit set, that can only be the legacy tagsegment type
        // with no padding information - convert it properly.
        if (dt == 0x40) {
            node.dataType = DataType.TAGSEGMENT.getValue();
            node.pad = 0;
        }
        node.num = word & 0xff;

        // Check length:
        // Make sure there is enough data to read full event
        // even though it is NOT completely read at this time.
        if (bytesLeft < 4*(node.len + 1)) {
            node.error = "buffer underflow";
            return node;
        }

        // If type or pad is a bad value, return error condition, else null
        node.error = checkDataTypeAndPadding(node.dataType, node.pad);

        return node;
    }



    /**
     * This method searches for an evio structure (EvioHeader) in which a
     * format error exists. It returns the top level node which contains
     * any sub level node in which an error was found.
     *
     * @param model      buffer to examine
     * @param position   position in buffer
     * @param place      place of event in buffer (event # starting at 0)
     * @param bytesLeft  # of bytes left to read in file.
     *
     * @return EvioHeader object containing top-level or event evio structure
     */
    private EvioHeader searchForErrorInEvent(MyTableModel model,
                                             long position, int place, long bytesLeft) {
        boolean debug=false;

        // Store evio event info, without de-serializing, into EvioNode object
        EvioHeader eventNode = new EvioHeader(position, place, model);

        // Get length of current event
        eventNode.len = model.getInt(position);
        // Position of data for a bank
        eventNode.dataPos = position + 8;
        // Len of data for a bank
        eventNode.dataLen = eventNode.len - 1;

        // Check length:
        // Make sure there is enough data to read full event
        // even though it is NOT completely read at this time.
        if (bytesLeft < 4*(eventNode.len + 1)) {
            eventNode.error = "buffer underflow";
if (debug) System.out.println("searchForErrorInEvent: place = " + place + ", buffer underflow");
            return eventNode;
        }

        // Hop over length word
        position += 4;

        // Read and parse second header word
        int word = model.getInt(position);
        eventNode.tag = (word >>> 16);
        int dt = (word >> 8) & 0xff;
        eventNode.dataType = dt & 0x3f;
        eventNode.pad = dt >>> 6;
        // If only 7th bit set, that can only be the legacy tagsegment type
        // with no padding information - convert it properly.
        if (dt == 0x40) {
            eventNode.dataType = DataType.TAGSEGMENT.getValue();
            eventNode.pad = 0;
        }
        eventNode.num = word & 0xff;
        eventNode.bankType = CodaBankTag.getDescription(eventNode.tag);


        // If type or pad is a bad value, return error condition, else null
        eventNode.error = checkDataTypeAndPadding(eventNode.dataType, eventNode.pad);
        if (debug && (eventNode.error != null) ) {
            System.out.println("searchForErrorInEvent: place = " + place + ", " + eventNode.error);
        }

        // Scan through all evio structures looking for bad format.
        // Returns null if no error, else structure in which error was found.
        // It is possible that subNode is the same as eventNode.
        EvioHeader subNode = scanStructureForError(eventNode);
        if (subNode != null) {
            if (debug) System.out.println("searchForErrorInEvent: sub node error = " + subNode.error);
            // Move the error up the chain to (possibly) parent
            if (eventNode.error == null) {
                eventNode.error = subNode.error;
            }
        }
        eventNode.errorHeader = subNode;
        return eventNode;
    }


    /**
     * This method examines recursively all the information
     * about an evio structure's children. If there are any evio errors,
     * it stops the recursion and returns an EvioNode object corresponding
     * to the lowest level structure containing the error.
     *
     * @param node node being scanned
     * @return EvioHeader object (or node) containing the lowest level structure
     *         containing the error, or null if no error.
     */
    private EvioHeader scanStructureForError(EvioHeader node) {

        // Type of evio structure being scanned
        DataType type = node.getDataTypeObj();

        // If node does not contain containers, return since we can't drill any further down
        if (!type.isStructure()) {
            return null;
        }

        // Start at beginning position of evio structure being scanned
        long position = node.dataPos;
        // Don't go past the data's end which is (position + length)
        // of evio structure being scanned in bytes.
        long endingPos = position + 4*node.dataLen;
        // Data source we're using
        MyTableModel model = node.model;

        // How much memory do child structures take up?
        long thisStructureDataWords = node.len;
        DataType nodeType = node.getTypeObj();
        if (nodeType == DataType.BANK || nodeType == DataType.ALSOBANK) {
            // Account for extra word in bank header
            thisStructureDataWords--;
        }

        EvioHeader returnedNode;
        DataType dataTypeObj;
        long len, dataLen, totalKidWords = 0L;
        int dt, dataType, word;
        boolean debug = false;

        // Do something different depending on what node contains
        switch (type) {
            case BANK:
            case ALSOBANK:

                // Extract all the banks from this bank of banks.
                // Make allowance for reading header (2 ints).
                while (position <= endingPos - 8) {
                    // Cloning is a fast copy that eliminates the need
                    // for setting stuff that's the same as the parent.
                    EvioHeader kidNode = (EvioHeader)node.clone();

                    // Read first header word
                    len = model.getInt(position) & 0xffffffffL;
                    kidNode.pos = position;

                    // Len of data (no header) for a bank
                    dataLen = len - 1;
                    position += 4;

                    // Read & parse second header word
                    word = model.getInt(position);
                    position += 4;
                    kidNode.tag = (word >>> 16);
                    dt = (word >> 8) & 0xff;
                    dataType = dt & 0x3f;
                    kidNode.pad = dt >>> 6;
                    // If only 7th bit set, that can only be the legacy tagsegment type
                    // with no padding information - convert it properly.
                    if (dt == 0x40) {
                        dataType = DataType.TAGSEGMENT.getValue();
                        kidNode.pad = 0;
                    }
                    kidNode.num = word & 0xff;

                    kidNode.len = len;
                    kidNode.type = DataType.BANK.getValue();
                    kidNode.dataLen = dataLen;
                    kidNode.dataPos = position;
                    kidNode.dataType = dataType;
                    kidNode.isEvent = false;
                    kidNode.bankType = CodaBankTag.getDescription(kidNode.tag);

                    // Total length in words of this bank (including header)
                    totalKidWords += len + 1;

                    // If type or pad is a bad value, return error condition
                    kidNode.error = checkDataTypeAndPadding(dataType, kidNode.pad);
                    if (kidNode.error != null) {
                        if (debug) System.out.println("Error 1: " + kidNode.error);
                        return kidNode;
                    }

                    // Only scan through this child if it's a container
                    if (DataType.isStructure(dataType)) {
                        // Return any node with an error condition (non-null)
                        returnedNode = scanStructureForError(kidNode);
                        if (returnedNode != null) {
                            // Pass the error condition on up the chain
                            return returnedNode;
                        }
                    }

                    // Set position to start of next header (hop over kid's data)
                    position += 4*dataLen;
                }

                break; // structure contains banks

            case SEGMENT:
            case ALSOSEGMENT:

                // Extract all the segments from this bank of segments.
                // Make allowance for reading header (1 int).
                while (position <= endingPos - 4) {
                    EvioHeader kidNode = (EvioHeader) node.clone();

                    kidNode.pos = position;

                    word = model.getInt(position);
                    position += 4;
                    kidNode.tag = word >>> 24;
                    dt = (word >>> 16) & 0xff;
                    dataType = dt & 0x3f;
                    kidNode.pad = dt >>> 6;
                    // If only 7th bit set, that can only be the legacy tagsegment type
                    // with no padding information - convert it properly.
                    if (dt == 0x40) {
                        dataType = DataType.TAGSEGMENT.getValue();
                        kidNode.pad = 0;
                    }
                    len = word & 0xffff;

                    kidNode.num      = 0;
                    kidNode.len      = len;
                    kidNode.type     = DataType.SEGMENT.getValue();
                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;
                    kidNode.isEvent  = false;
                    kidNode.bankType = CodaBankTag.getDescription(kidNode.tag);

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    // If type or pad is a bad value, return error condition
                    kidNode.error = checkDataTypeAndPadding(dataType, kidNode.pad);
                    if (kidNode.error != null) {
                        if (debug) System.out.println("Error 2: " + kidNode.error);
                        return kidNode;
                    }

                    if (DataType.isStructure(dataType)) {
                        returnedNode = scanStructureForError(kidNode);
                        if (returnedNode != null) {
                            return returnedNode;
                        }
                    }

                    position += 4*len;
                }

                break; // structure contains segments

            case TAGSEGMENT:

                // Extract all the tag segments from this bank of tag segments.
                // Make allowance for reading header (1 int).
                while (position <= endingPos - 4) {
                    EvioHeader kidNode = (EvioHeader) node.clone();

                    kidNode.pos = position;

                    word = model.getInt(position);
                    position += 4;
                    kidNode.tag =  word >>> 20;
                    dataType    = (word >>> 16) & 0xf;
                    len         =  word & 0xffff;

                    kidNode.pad      = 0;
                    kidNode.num      = 0;
                    kidNode.len      = len;
                    kidNode.type     = DataType.TAGSEGMENT.getValue();
                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;
                    kidNode.isEvent  = false;

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    dataTypeObj = DataType.getDataType(dataType);
                    if (dataTypeObj == null) {
                        kidNode.error = "Bad data type (" + dataType + ")";
if (debug) System.out.println("Error 3: " + kidNode.error);
                        return kidNode;
                    }

                   if (DataType.isStructure(dataType)) {
                        returnedNode = scanStructureForError(kidNode);
                        if (returnedNode != null) {
                            return returnedNode;
                        }
                    }

                    position += 4*len;
                }

                break;

            default:
                totalKidWords = thisStructureDataWords;
        }

        if (totalKidWords != thisStructureDataWords) {
            // Error in length word(s)
            node.error = "Bad word length(s): node's data = " + thisStructureDataWords +
                         ", kids' = " + totalKidWords;
if (debug) System.out.println("Error 4: " + node.error);
            return node;
        }

        return null;
    }



    /**
     * Scan the file for evio errors.
     *
     * @return {@code true if error occurred}, else {@code false}
     * @throws EvioException if file cannot even be attempted to be parsed
     */
    public boolean scanFileForErrors() throws EvioException {

        int  bitInfo, magicNum, totalHeaderBytes, indexBytes, userHeaderBytes;
        int  uncompressedDataBytes, compressionType, compressedDataWords;
        long blockEventLengthsSum, blockDataBytes, blockWordSize, byteLen;
        int  blockNum, blockHdrWordSize, blockEventCount, dataBytes;
        boolean  foundError=false, foundErrorInBlock, debug=false;
        BlockHeaderV6 blockNode;
        EvioHeader node;

        blockErrorNodes.clear();

        // Keep track of the # of events in file
        int eventCount = 0;

        // Bytes from file beginning to map beginning
        // which allows translation of positions in EvioNode
        // to absolute file positions.
        long bufPosInBlock;

        // Start at the beginning of the first record header and
        // use FileFrameV6's JTable model to get data.
        long bufPos = dataModel.getMemoryHandler().getTotalFileHeaderBytes();

        // Keep track of position in file
        long fileByteSize  = dataModel.getFileSize();
        long fileBytesLeft = fileByteSize - bufPos;

        // Need enough data to at least read 1 record header
        if (fileBytesLeft < RecordHeader.HEADER_SIZE_BYTES) {
            throw new EvioException("File too small (" + fileBytesLeft + " bytes)");
        }

        do {
            // Update progress in scanning file for errors
            if (errorScanTask != null) {
                int progressPercent = (int) (100*(fileByteSize - fileBytesLeft)/(fileByteSize));
                errorScanTask.setTaskProgress(progressPercent);
            }

            // We were told to stop by user
            if (errorScanTask != null && errorScanTask.stopSearch()) {
                return foundError;
            }

            // Read in block header info, swapping is taken care of.
            // Make sure 32 bit unsigned int (read in as a signed int)
            // is properly converted to long without sign extension.
            blockWordSize         = dataModel.getInt(bufPos) & 0xffffffffL;
            blockNum              = dataModel.getInt(bufPos + RecordHeader.RECORD_NUMBER_OFFSET);
            bitInfo               = dataModel.getInt(bufPos + RecordHeader.BIT_INFO_OFFSET);
            blockHdrWordSize      = dataModel.getInt(bufPos + RecordHeader.HEADER_LENGTH_OFFSET);
            blockEventCount       = dataModel.getInt(bufPos + RecordHeader.EVENT_COUNT_OFFSET);
            magicNum              = dataModel.getInt(bufPos + RecordHeader.MAGIC_OFFSET);
            indexBytes            = dataModel.getInt(bufPos + RecordHeader.INDEX_ARRAY_OFFSET);
            uncompressedDataBytes = dataModel.getInt(bufPos + RecordHeader.UNCOMPRESSED_LENGTH_OFFSET);
            int compWord          = dataModel.getInt(bufPos + RecordHeader.COMPRESSION_TYPE_OFFSET);
            compressionType       = compWord >>> 28;
            compressedDataWords   = compWord & 0xffffff;
            userHeaderBytes       = dataModel.getInt(bufPos + RecordHeader.USER_LENGTH_OFFSET); // No padding
            totalHeaderBytes      = 4*blockHdrWordSize + indexBytes + 4*Utilities.getWords(userHeaderBytes);

            // If no compression ...
            if (compressionType == 0) {
                dataBytes = (int) (4 * blockWordSize) - totalHeaderBytes;
            }
            else {
                // If we have compressed data, index and user header are part of that data
                dataBytes = (int) (4 * (blockWordSize - blockHdrWordSize));
            }

            boolean isTrailer = RecordHeader.isEvioTrailer(bitInfo);
//System.out.println("errorScan: magic # = 0x" + Integer.toHexString(magicNum));

            // Store block header info in object
            blockNode           = new BlockHeaderV6();
            blockNode.filePos   = bufPos;
            blockNode.len       = blockWordSize;
            blockNode.headerLen = blockHdrWordSize;
            blockNode.count     = blockEventCount;
            blockNode.place     = blockNum;
            blockNode.indexArrayBytes = indexBytes;
            blockNode.userHeaderBytes = userHeaderBytes;
            blockNode.compressionType = compressionType;
            CompressionType cType = CompressionType.getCompressionType(compressionType);
            if (cType != null) {
                blockNode.compressionTypeStr = cType.getDescription();
            }
            else {
                blockNode.compressionTypeStr = "None";
            }
            blockNode.compressedDataWords = compressedDataWords;
            blockNode.uncompressedDataBytes = uncompressedDataBytes;
            blockNode.totalBytes = totalHeaderBytes; // Acct for padding
            blockNode.setInfoWord(bitInfo);
//System.out.println("errorScan: block node = \n" + blockNode);

            // Init variables
            foundErrorInBlock = false;
            blockEventLengthsSum = 0L;
            blockDataBytes = 4L*blockWordSize - totalHeaderBytes;

            // If magic # is not right, file is not in proper format
            if (magicNum != RecordHeader.MAGIC_NUMBER) {
                // If attempting to scan file in wrong endian, get user to switch
                if (Integer.reverseBytes(magicNum) == RecordHeader.MAGIC_NUMBER) {
                    blockErrorNodes.clear();
                    JOptionPane.showMessageDialog(parentComponent,
                                                  "Try switching data endian under \"File\" menu",
                                                  "Return", JOptionPane.INFORMATION_MESSAGE);

                    throw new EvioException("Switch endianness & try again");
                }

                blockNode.error = "Record header, magic # incorrect";
                blockErrorNodes.add(blockNode);
                if(debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, bufPos, true);
                return true;
            }

            // Block and/or header length is too small
            if (blockWordSize < totalHeaderBytes/4 ||
                blockHdrWordSize < RecordHeader.HEADER_SIZE_WORDS) {

                blockNode.error = "Record: len and/or header len is out of range";
                blockErrorNodes.add(blockNode);
                if(debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, bufPos, true);
                return true;
            }

            if (compressionType == 0) {
                if (compressedDataWords != 0 || (uncompressedDataBytes != dataBytes)) {
                    // If not compressing, lengths are wrong but we can continue scanning
                    // since these specific lengths are not used for scanning file.
                    blockNode.error = "Record: no compression, but comp len != 0 or uncomp len wrong";
                    blockErrorNodes.add(blockNode);
                    if (debug) System.out.println("scanFile: error = " + blockNode.error);
                    foundError = true;
                    foundErrorInBlock = true;
                }
            }
            else {
                if ((compressedDataWords != dataBytes/4) || uncompressedDataBytes == 0) {
                    // If compressing, lengths are wrong but we can continue scanning
                    // since these specific lengths are not used for scanning file.
                    blockNode.error = "Record: compressing data, but comp len wrong or uncomp len = 0";
                    blockErrorNodes.add(blockNode);
                    if (debug) System.out.println("scanFile: error = " + blockNode.error);
                    foundError = true;
                    foundErrorInBlock = true;
                }
            }

            // Number of events conflicts with index length (test not valid for trailer)
            if (!isTrailer && (4*blockEventCount != indexBytes)) {
                if (blockNode.error != null) {
                    blockNode.error += ";   Index bytes (" + indexBytes + ") != 4*event-count (" +
                            (4*blockEventCount) + ")";
                }
                else {
                    blockNode.error = "Record: Index bytes (" + indexBytes + ") != 4*event-count (" +
                            (4*blockEventCount) + ")";
                    blockErrorNodes.add(blockNode);
                }

                if(debug) System.out.println("scanFile: Index bytes (" + indexBytes + ") != 4*event-count (" +
                        (4*blockEventCount)  + ")");
                foundError = true;
                foundErrorInBlock = true;
            }

            // Event cnt or block # may be too large
            if (blockNum < 0 || blockEventCount < 0) {
                long blkNum   = blockNum &  0xffffffffL;
                long blkEvCnt = blockEventCount &  0xffffffffL;
                if(debug) System.out.println("Warning, suspicious record number (" + blkNum +
                                             ") and/or event count (" + blkEvCnt + ")");
            }

            // Block header length not = 14
            if (blockHdrWordSize != RecordHeader.HEADER_SIZE_WORDS) {
                if(debug) System.out.println("Warning, suspicious record header size, " + blockHdrWordSize);
            }

            // Hop over block header (and index and user header) to events
            bufPos += totalHeaderBytes;
            bufPosInBlock = bufPos;
            fileBytesLeft -= totalHeaderBytes;

            // In this version any dictionary is placed in the file header's
            // user header so no need to explicitly skip over it.

            // IF not compressing data ... for each event in block ...
            if (compressionType == 0) {

                for (int i = 0; i < blockEventCount; i++) {
                    // Sanity check - must have at least 1 header's amount to read
                    if (fileBytesLeft - blockEventLengthsSum < 8) {
                        if (blockNode.error != null) {
                            blockNode.error += ";   Not enough data (bad bank len?)";
                        }
                        else {
                            blockNode.error = "Record: not enough data (bad bank len?)";
                            blockErrorNodes.add(blockNode);
                        }
                        // We're done with this block
                        foundError = true;
                        foundErrorInBlock = true;
                        if (debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                        break;
                    }

                    // Catch event count that's too large.
                    // Used up precisely all data in block, but looking for more events.
                    if (blockDataBytes - blockEventLengthsSum == 0) {
                        if (blockNode.error != null) {
                            blockNode.error += ";   Event count = " + blockEventCount + ", but should = " + i;
                        }
                        else {
                            blockNode.error = "Record: event count = " + blockEventCount + ", but should = " + i;
                            blockErrorNodes.add(blockNode);
                        }
                        // We're done with this block
                        foundError = true;
                        foundErrorInBlock = true;
                        if (debug)
                            System.out.println("scanFile: record event count is " + blockEventCount + " but should be " + i);
                        break;
                    }

                    // There's a possibility that the event lengths are fine but the block
                    // length and/or event count are wrong. So check to see if we've landed
                    // at the beginning of the next block header.
                    int word = dataModel.getInt(bufPosInBlock + RecordHeader.MAGIC_OFFSET);
                    if (word == BlockHeaderV6.MAGIC_INT) {
                        // We've gone too far - to beginning of next block
                        if (blockNode.error != null) {
                            blockNode.error += ";   Record len too large & event count = " +
                                    blockEventCount + " but should = " + i;
                        }
                        else {
                            blockNode.error = "Record: len too large & event count = " +
                                               blockEventCount + " but should = " + i;
                            blockErrorNodes.add(blockNode);
                        }
                        foundError = true;
                        foundErrorInBlock = true;
                        break;
                    }

                    try {
                        // Returns event node which may contain a sub node with an evio error
                        node = searchForErrorInEvent(dataModel, bufPosInBlock,
                                eventCount + i,
                                fileBytesLeft - blockEventLengthsSum);
                    } catch (Exception e) {
                        // Any error is here is probably due to a bad bank length
                        // causing an IndexOutOfBoundException.

                        // Create a node even though we know there's an error so we have
                        // something to highlight for the user that's looking for errors.
                        node = extractEventNode(dataModel, bufPosInBlock, eventCount + i,
                                fileBytesLeft - blockEventLengthsSum);

                        if (blockNode.error != null) {
                            blockNode.error += ";   " + e.getMessage() + " (bad bank len?)";
                        }
                        else {
                            blockNode.error = e.getMessage() + " (bad bank len?)";
                            blockErrorNodes.add(blockNode);
                        }
                        blockNode.events.add(node);
                        foundError = true;
                        foundErrorInBlock = true;
                        dataModel.highLightEventHeader(parentComponent.highlightEvntHdrErr, bufPosInBlock, true);
                        break;
                    }

                    // If there's been an error detected inside this event ...
                    // or an error at the top level ...
                    if (node.errorHeader != null || node.error != null) {
                        // Try to salvage things by skipping this block and going to next
                        if (blockNode.error != null) {
                            blockNode.error += ";   Event #" + node.place + " has error";
                        }
                        else {
                            blockNode.error = "Event #" + node.place + " has error";
                            blockErrorNodes.add(blockNode);
                        }
                        blockNode.events.add(node);
                        foundError = true;
                        foundErrorInBlock = true;
                        // Highlight event header
                        dataModel.highLightEventHeader(parentComponent.highlightEvntHdrErr, bufPosInBlock, true);
                        // If error inside this event ...
                        if ((node.errorHeader != null) && (node != node.errorHeader)) {
                            blockNode.events.add(node.errorHeader);
                            // blockNode.error = node.errorHeader.error;
                            // Also highlight structure header containing the error
                            dataModel.highLightEventHeader(parentComponent.highlightNodeErr,
                                    node.errorHeader.pos, true);
                        }
                        break;
                    }

                    // Add it in case there's an error in this block and we need it later
                    //blockNode.events.add(node);

                    // Hop over header + data to next event or block
                    byteLen = 4L * ((dataModel.getInt(bufPosInBlock) & 0xffffffffL) + 1L);

                    bufPosInBlock += byteLen;
                    blockEventLengthsSum += byteLen;
//System.out.println("    Hopped event " + (i+eventCount) + ", file offset = " + fileOffset + "\n");
                }
            }

            if (foundErrorInBlock) {
                dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            }
            else {
                // If the length of events taken from the block header is not the same
                // as the length of all the events in the block added up, there's a problem.
                if ((compressionType == 0) && (blockEventLengthsSum != blockDataBytes)) {
                    if (blockNode.error != null) {
                        blockNode.error += ";   Len of events in record (" + blockEventLengthsSum +
                                ") does NOT match record header (" + blockDataBytes + ")";
                    }
                    else {
                        blockNode.error = "Len of events in record (" + blockEventLengthsSum +
                                ") does NOT match record header (" + blockDataBytes + ")";
                        blockErrorNodes.add(blockNode);
                    }

                    dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
                    if(debug) System.out.println("scanFile: try again error = " + blockNode.error);
                }
                else {
                    // Since there's no error in the block,
                    // remove all events from the block's list
                    blockNode.events.clear();
                }
            }

            // If there is a difference, assume that the block length is good and
            // the problems is with events' lengths. Then try to continue on.
            bufPos        += blockDataBytes;
            fileBytesLeft -= blockDataBytes;
            eventCount    += blockEventCount;

            if (fileBytesLeft == 0) {
                break;
            }

            // Length of next block
            blockWordSize = dataModel.getInt(bufPos) & 0xffffffffL;

        } while (4*blockWordSize <= fileBytesLeft);

        // If we're here, not enough data in file for next block

        // Check to see if we're at the end of the file
        if (fileBytesLeft == 0) {
            return false;
        }
        // If not enough data in rest of file ...
        else if (4*blockWordSize > fileBytesLeft) {
            if (blockNode.error != null) {
                blockNode.error += ";   Len too large (not enough data)";
            }
            else {
                blockNode.error = "Record: len too large (not enough data)";
                blockErrorNodes.add(blockNode);
            }

            if(debug) System.out.println("scanFile: not enough data for record len");
            dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            return true;
        }
        // or not enough data to read next block header (32 bytes)
        else if (fileBytesLeft < 32) {
            if (blockNode.error != null) {
                blockNode.error += ";   Extra " + fileBytesLeft + " bytes at file end";
            }
            else {
                blockNode.error = "Extra " + fileBytesLeft + " bytes at file end";
                blockErrorNodes.add(blockNode);
            }
            if(debug) System.out.println("scanFile: data left at file end");
            dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            return true;
        }

        return false;
    }


    /**
     * In evio version 6, unlike earlier versions, the file header is already been parsed.
     * So only check if there's data to read in first record header.
     * @throws EvioException if too little data
     */
    private void checkFirstHeader() throws EvioException {
        // Get file header
        SimpleMappedMemoryHandler memHandler = dataModel.getMemoryHandler();
        ByteBuffer byteBuffer = memHandler.getFirstMap();

        // Have enough remaining bytes to read first record header?
        if (byteBuffer.limit() < memHandler.getFirstDataIndex() + RecordHeader.HEADER_SIZE_BYTES) {
            throw new EvioException("Too little data");
        }
    }


}
