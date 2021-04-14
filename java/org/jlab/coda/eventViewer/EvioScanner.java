package org.jlab.coda.eventViewer;


import org.jlab.coda.jevio.*;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * This class is used to scan a file for block info, event info, and evio errors
 * in order to catalog them for later viewing.
 *
 * @author timmer (1/9/15)
 */
public class EvioScanner {

    /** Stores info of all block headers in an evio format file with an error. */
    private final ArrayList<BlockHeader> blockErrorNodes = new ArrayList<BlockHeader>(10);

    /** Object for accessing file data. */
    private final MyTableModel dataModel;

    /** Object for rendering file data in table. */
    private final MyRenderer dataRenderer;

    /** Reference to GUI component that creates this object. */
    private FileFrameBig parentComponent;

    /** Reference needed to update progress bar when searching file for evio errors. */
    private FileFrameBig.ErrorScanTask errorScanTask;



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
     * @param dataModel     object with file memory maps.
     * @param errorTask     object doing file scan in background,
     *                      use to update its progress.
     * @throws EvioException if endianness is wrong, version is wrong,
     *                       or too little data to read block header
     */
    public EvioScanner(FileFrameBig component,
                       MyTableModel dataModel,
                       MyRenderer dataRenderer,
                       FileFrameBig.ErrorScanTask errorTask) throws EvioException {
        this.parentComponent = component;
        this.dataModel       = dataModel;
        this.dataRenderer    = dataRenderer;
        this.errorScanTask   = errorTask;

        checkFirstHeader();
    }


    /**
     * Get the list of BlockHeader objects containing evio errors.
     * @return list of BlockHeader objects containing evio errors.
     */
    public ArrayList<BlockHeader> getBlockErrorNodes() { return blockErrorNodes; }


    /**
     * Did the scan of the file show any evio errors?
     * @return {@code true} if there were errors, else {@code false}.
     */
    public boolean hasError() {return (blockErrorNodes.size() > 0); }


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
        node.pos = position;
        node.type = DataType.BANK.getValue();
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

        // If type or pad is a bad value, return error condition
        DataType dataTypeObj = DataType.getDataType(node.dataType);
        if (dataTypeObj == null) {
            node.error = "Bad data type (" + node.dataType + ")";
        }
        else if (node.pad != 0) {
            if (node.pad == 2) {
                if (!dataTypeHasPadding(dataTypeObj)) {
                    node.error = "Padding (" + node.pad + ") does not match data type ("
                                 + dataTypeObj + ")";
                }
            }
            else if ((dataTypeObj != DataType.CHAR8 &&
                      dataTypeObj != DataType.UCHAR8 &&
                      dataTypeObj != DataType.COMPOSITE)) {
                node.error = "Padding (" + node.pad + ") does not match data type ("
                             + dataTypeObj + ")";
            }
        }

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
        eventNode.pos = position;
        eventNode.type = DataType.BANK.getValue();
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


        // Test value of dataType
        DataType dataTypeObj = DataType.getDataType(eventNode.dataType);

        // If type or pad is a bad value, error
        if (dataTypeObj == null) {
            eventNode.error = "Bad data type (" + eventNode.dataType + ")";
            if (debug) System.out.println("searchForErrorInEvent: place = " + place + ", " + eventNode.error);
            return eventNode;
        }
        else if (eventNode.pad != 0) {
            if (eventNode.pad == 2) {
                if (!dataTypeHasPadding(dataTypeObj)) {
                    eventNode.error = "Padding (" + eventNode.pad + ") does not match data type (= "
                            + dataTypeObj + ")";
                    if (debug) System.out.println("searchForErrorInEvent: place = " + place + ", " + eventNode.error);
                    return eventNode;
                }
            }
            else if ((dataTypeObj != DataType.CHAR8 &&
                      dataTypeObj != DataType.UCHAR8 &&
                      dataTypeObj != DataType.COMPOSITE)) {
                eventNode.error = "Padding (" + eventNode.pad + ") does not match data type ("
                        + dataTypeObj + ")";
                if (debug) System.out.println("searchForErrorInEvent: place = " + place + ", " + eventNode.error);
                return eventNode;
            }
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

                    // Test value of dataType
                    dataTypeObj = DataType.getDataType(dataType);
                    // If type or pad is a bad value , error
                    if (dataTypeObj == null) {
                        kidNode.error = "Bad data type (" + dataType + ")";
if (debug) System.out.println("Error 1: " + kidNode.error);
                        return kidNode;
                    }
                    else if (kidNode.pad != 0) {
                        if (kidNode.pad == 2) {
                            if (!dataTypeHasPadding(dataTypeObj)) {
                                kidNode.error = "Padding (" + kidNode.pad + ") does not match data type (= "
                                        + dataTypeObj + ")";
                                if (debug) System.out.println("Error 1: " + kidNode.error);
                                return kidNode;
                            }
                        }
                        else if ((dataTypeObj != DataType.CHAR8 &&
                                  dataTypeObj != DataType.UCHAR8 &&
                                  dataTypeObj != DataType.COMPOSITE)) {
                            kidNode.error = "Padding (" + kidNode.pad + ") does not match data type ("
                                    + dataTypeObj + ")";
                            if (debug) System.out.println("Error 1: " + kidNode.error);
                            return kidNode;
                        }
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

                    dataTypeObj = DataType.getDataType(dataType);

                    if (dataTypeObj == null) {
                        kidNode.error = "Bad data type (" + dataType + ")";
if (debug) System.out.println("Error 2: " + kidNode.error);
                        return kidNode;
                    }
                    else if (kidNode.pad != 0) {
                        if (kidNode.pad == 2) {
                            if (!dataTypeHasPadding(dataTypeObj)) {
                                kidNode.error = "Padding (" + kidNode.pad + ") does not match data type (= "
                                        + dataTypeObj + ")";
                                if (debug) System.out.println("Error 2: " + kidNode.error);
                                return kidNode;
                            }
                        }
                        else if ((dataTypeObj != DataType.CHAR8 &&
                                  dataTypeObj != DataType.UCHAR8 &&
                                  dataTypeObj != DataType.COMPOSITE)) {
                            kidNode.error = "Padding (" + kidNode.pad + ") does not match data type ("
                                    + dataTypeObj + ")";
                            if (debug) System.out.println("Error 2: " + kidNode.error);
                            return kidNode;
                        }
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
     * @returns {@code true if error occurred}, else {@code false}
     * @throws EvioException if file cannot even be attempted to be parsed
     */
    public boolean scanFileForErrors() throws EvioException {

        int      byteInfo, magicNum;
        long     blockEventLengthsSum, blockDataBytes, blockWordSize, byteLen;
        int      blockNum, blockHdrWordSize, blockEventCount;
        boolean  firstBlock=true, foundError=false, foundErrorInBlock, debug=false;
        BlockHeader blockNode;
        EvioHeader node;

        blockErrorNodes.clear();

        // Keep track of the # of events in file
        int eventCount = 0;

        // Bytes from file beginning to map beginning
        // which allows translation of positions in EvioNode
        // to absolute file positions.
        long bufPosInBlock;

        // Start at the beginning and use FileFrameBig's JTable model to get data.
        long bufPos = 0L;

        // Keep track of position in file
        long fileByteSize  = dataModel.getFileSize();
        long fileBytesLeft = fileByteSize;

        // Need enough data to at least read 1 block header (32 bytes)
        if (fileBytesLeft < 32) {
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
            blockWordSize    = dataModel.getInt(bufPos) & 0xffffffffL;
            blockNum         = dataModel.getInt(bufPos + 4*BlockHeaderV4.EV_BLOCKNUM);
            byteInfo         = dataModel.getInt(bufPos + 4*BlockHeaderV4.EV_VERSION);
            blockHdrWordSize = dataModel.getInt(bufPos + 4*BlockHeaderV4.EV_HEADERSIZE);
            blockEventCount  = dataModel.getInt(bufPos + 4*BlockHeaderV4.EV_COUNT);
            magicNum         = dataModel.getInt(bufPos + 4*BlockHeaderV4.EV_MAGIC);

//            System.out.println("Magic # = 0x" + Integer.toHexString(magicNum));
            // Store block header info in object
            blockNode           = new BlockHeader();
            blockNode.filePos   = bufPos;
            blockNode.len       = blockWordSize;
            blockNode.headerLen = blockHdrWordSize;
            blockNode.count     = blockEventCount;
            blockNode.place     = blockNum;
            blockNode.setInfoWord(byteInfo);

            // Init variables
            foundErrorInBlock = false;
            blockEventLengthsSum = 0L;
            blockDataBytes = 4L*(blockWordSize - blockHdrWordSize);

            // Block lengths are too small
            if (blockWordSize < 8 || blockHdrWordSize < 8 ||
                blockNum < 0 || blockEventCount < 0) {

                blockNode.error = "Block: len, header len, event cnt or block # is out of range";
                blockErrorNodes.add(blockNode);
                if(debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, bufPos, true);
                return true;
            }

            // If magic # is not right, file is not in proper format
            if (magicNum != BlockHeaderV4.MAGIC_NUMBER) {
                // If attempting to scan file in wrong endian, get usr eto switch
                if (Integer.reverseBytes(magicNum) == BlockHeaderV4.MAGIC_NUMBER) {
                    blockErrorNodes.clear();
                    JOptionPane.showMessageDialog(parentComponent,
                                                  "Try switching data endian under \"File\" menu",
                                                  "Return", JOptionPane.INFORMATION_MESSAGE);

                    throw new EvioException("Switch endianness & try again");
                }

                blockNode.error = "Block header, magic # incorrect";
                blockErrorNodes.add(blockNode);
                if(debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, bufPos, true);
                return true;
            }

            // Block header length not = 8
            if (blockHdrWordSize != 8) {
                if(debug) System.out.println("Warning, suspicious block header size, " + blockHdrWordSize);
            }

            // Hop over block header to events
            bufPos += 4*blockHdrWordSize;
            bufPosInBlock = bufPos;
            fileBytesLeft -= 4*blockHdrWordSize;

            // Check for a dictionary - the first event in the first block.
            // It's not included in the header block count, but we must take
            // it into account by skipping over it.
            if (firstBlock && BlockHeaderV4.hasDictionary(byteInfo)) {
                firstBlock = false;

                // Get its length - bank's len does not include itself
                byteLen = 4L*((dataModel.getInt(bufPosInBlock) & 0xffffffffL) + 1L);

                // Skip over dictionary
                bufPosInBlock += byteLen;
                blockEventLengthsSum += byteLen;
//System.out.println("    hopped dict, pos = " + bufPosInBlock);
            }

            // For each event in block ...
            for (int i=0; i < blockEventCount; i++) {

                // Sanity check - must have at least 1 header's amount to read
                if (fileBytesLeft - blockEventLengthsSum < 8) {
                    blockNode.error = "Not enough data to read event (bad bank len?)";
                    blockErrorNodes.add(blockNode);
                    // We're done with this block
                    foundError = true;
                    foundErrorInBlock = true;
                    if(debug) System.out.println("scanFile: fatal error = " + blockNode.error);
                    break;
                }

                // Catch event count that's too large.
                // Used up precisely all data in block, but looking for more events.
                if (blockDataBytes - blockEventLengthsSum == 0) {
                    blockNode.error = "Block event count = " + blockEventCount + ", but should = " + i;
                    blockErrorNodes.add(blockNode);
                    // We're done with this block
                    foundError = true;
                    foundErrorInBlock = true;
                    if(debug) System.out.println("scanFile: block event count is " + blockEventCount + " but should be " + i);
                    break;
                }

                // There's a possibility that the event lengths are fine but the block
                // length and/or event count are wrong. So check to see if we've landed
                // at the beginning of the next block header.
                long word = dataModel.getInt(bufPosInBlock + 28);
                if (word == BlockHeader.MAGIC_INT) {
                    // We've gone too far - to beginning of next block
                    blockNode.error = "block len too large and event count = " +
                                       blockEventCount + " but should = " + i;
                    blockErrorNodes.add(blockNode);
                    foundError = true;
                    foundErrorInBlock = true;
                    break;
                }

                try {
                    // Returns event node which may contain a sub node with an evio error
                    node = searchForErrorInEvent(dataModel, bufPosInBlock,
                                                 eventCount + i,
                                                 fileBytesLeft - blockEventLengthsSum);
                }
                catch (Exception e) {
                    // Any error is here is probably due to a bad bank length
                    // causing an IndexOutOfBoundException.

                    // Create a node even though we know there's an error so we have
                    // something to highlight for the user that's looking for errors.
                    node = extractEventNode(dataModel, bufPosInBlock, eventCount + i,
                                            fileBytesLeft - blockEventLengthsSum);

                    blockNode.error = e.getMessage() + " (bad bank len?)";
                    blockNode.events.add(node);
                    blockErrorNodes.add(blockNode);
                    foundError = true;
                    foundErrorInBlock = true;
                    dataModel.highLightEventHeader(parentComponent.highlightEvntHdrErr, bufPosInBlock, true);
                    break;
                }

                // If there's been an error detected inside this event ...
                // or an error at the top level ...
                if (node.errorHeader != null || node.error != null) {
                    // Try to salvage things by skipping this block and going to next
                    blockNode.error = "contained event #" +  node.place + " has error";
                    blockNode.events.add(node);
                    blockErrorNodes.add(blockNode);
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
                byteLen = 4L*((dataModel.getInt(bufPosInBlock) & 0xffffffffL) + 1L);

                bufPosInBlock += byteLen;
                blockEventLengthsSum += byteLen;
//System.out.println("    Hopped event " + (i+eventCount) + ", file offset = " + fileOffset + "\n");
            }

            if (foundErrorInBlock) {
                 dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            }
            else {
                // If the length of events taken from the block header is not the same
                // as the length of all the events in the block added up, there's a problem.
                if (blockEventLengthsSum != blockDataBytes) {
                    blockNode.error = "Byte len of events in block (" + blockEventLengthsSum +
                            ") doesn't match block header (" + blockDataBytes + ")";
                    blockErrorNodes.add(blockNode);
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
            blockNode.error = "Block len too large (not enough data)";
            blockErrorNodes.add(blockNode);
            if(debug) System.out.println("scanFile: not enough data for block len");
            dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            return true;
        }
        // or not enough data to read next block header (32 bytes)
        else if (fileBytesLeft < 32) {
            blockNode.error = "Extra " + fileBytesLeft + " bytes at file end";
            blockErrorNodes.add(blockNode);
            if(debug) System.out.println("scanFile: data left at file end");
            dataModel.highLightBlockHeader(parentComponent.highlightBlkHdrErr, blockNode.filePos, true);
            return true;
        }

        return false;
    }


    /**
     * Reads the first block (physical record) header in order to determine
     * characteristics of the file or buffer in question. These things
     * do <b>not</b> need to be examined in subsequent block headers.
     * ByteBuffer endianness needs to be set so visual data can
     * be viewed properly.
     * File or buffer must be evio version 4 or greater.
     *
     * @throws EvioException if endianness or version is bad or too little data
     */
    private void checkFirstHeader() throws EvioException {

        // Get first block header
        ByteBuffer byteBuffer = dataModel.getMemoryHandler().getFirstMap();

        // Have enough remaining bytes to read header?
        if (byteBuffer.limit() < 32) {
            throw new EvioException("Too little data");
        }

        // Check the magic number for endianness (buffer defaults to big endian)
        int magicNumber = byteBuffer.getInt(4*BlockHeaderV4.EV_MAGIC);

        if (magicNumber != IBlockHeader.MAGIC_NUMBER) {
            throw new EvioException("Try switching data endian under \"File\" menu");
        }

        // Check the version number
        int bitInfo = byteBuffer.getInt(4*BlockHeaderV4.EV_VERSION);
        int evioVersion = bitInfo & 0xff;
        if (evioVersion < 4)  {
            throw new EvioException("unsupported evio version (" + evioVersion + ")");
        }
    }


}
