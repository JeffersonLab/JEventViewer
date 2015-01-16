package org.jlab.coda.eventViewer;


import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Created by timmer on 1/9/15.
 */
public class EvioScanner {


    private int blockCount = -1;
    /**
     * This is the number of events in the file. It is not computed unless asked for,
     * and if asked for it is computed and cached in this variable.
     */
    private int eventCount = -1;

    /** Stores info of the first evio block header with an error. */
    private BlockNode blockErrorNode;

    /** Stores info of the first evio structure in a block with an error. */
    private final ArrayList<BlockNode> blockErrorNodes = new ArrayList<BlockNode>(10);

    /** Stores info of the first evio structure in a block with an error. */
    private final ArrayList<EvioNode> eventErrorNodes = new ArrayList<EvioNode>(1000);

    private final SimpleMappedMemoryHandler memoryHandler;

    private ByteOrder fileByteOrder;

    // Reference needed in order to update progress bar when searching for errors
    private FileFrameBig.ErrorTask errorTask;

    public ArrayList<BlockNode> getBlockErrorNodes() {
        return blockErrorNodes;
    }

    public ArrayList<EvioNode> getEventErrorNodes() {
        return eventErrorNodes;
    }

    public boolean hasError() {
        return blockErrorNodes.size() > 0;
    }

    static public boolean dataTypeHasPadding(DataType type) {
        return type == DataType.CHAR8 ||
               type == DataType.UCHAR8 ||
               type == DataType.SHORT16 ||
               type == DataType.USHORT16;
    }


    final class BlockNode {
        /** Block's length value (32-bit words). */
        int len;
        /** Number of events in block. */
        int count;
        /** Position of block in buffer.  */
        long pos;
        /** Position of block in file.  */
        long filePos;
        /**
         * Place of this block in file/buffer. First block = 0, second = 1, etc.
         * Useful for appending banks to EvioEvent object.
         */
        int place;
        
        String error;        
    }


    /**
     * Abbreviated form of org.jlab.code.jevio.EvioNode class.
     */
    final class EvioNode implements Cloneable {

        /** Header's length value (32-bit words). */
        int len;
        /** Position of header in file/buffer in bytes.  */
        int pos;
        /** This node's (evio container's) type. Must be bank, segment, or tag segment. */
        int type;

        /** Length of node's data in 32-bit words. */
        int dataLen;
        /** Position of node's data in file/buffer in bytes. */
        int dataPos;
        /** Type of data stored in node. */
        int dataType;

        /** Offset in bytes from beginning of file to beginning of map
         * used to find pos & dataPos. */
        long offset;

        String error;

        /** Does this node represent an event (top-level bank)? */
        boolean isEvent;

        /** ByteBuffer that this node is associated with. */
        ByteBuffer buffer;

        //-------------------------------
        // For event-level node
        //-------------------------------

        /**
         * Place of containing event in file/buffer. First event = 0, second = 1, etc.
         * Useful for converting node to EvioEvent object (de-serializing).
         */
        int place;

        //----------------------------------
        // Constructors (package accessible)
        //----------------------------------


        /**
         * Constructor which creates an EvioNode associated with
         * an event (top level) evio container when parsing buffers
         * for evio data.
         *
         * @param pos        position of event in buffer (number of bytes)
         * @param place      containing event's place in buffer (starting at 1)
         * @param offset     Offset in bytes from beginning of file to beginning
         *                   of map used to find pos & dataPos.
         * @param buffer     buffer containing this event
         */
        EvioNode(int pos, int place, long offset, ByteBuffer buffer) {
            this.pos = pos;
            this.place = place;
            this.offset = offset;
            this.buffer = buffer;
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
        // Getters
        //-------------------------------

        public long getFilePosition() {
            return offset + pos;
        }

        /**
         * Get the length of this evio structure including entire header in bytes.
         * @return length of this evio structure including entire header in bytes.
         */
        public int getTotalBytes() {
            return 4*dataLen + dataPos - pos;
        }


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


        /**
         * If this object represents an event (top-level, evio bank),
         * then returns its number (place in file or buffer) starting
         * with 1. If not, return -1.
         * @return event number if representing an event, else -1
         */
        public int getEventNumber() {
            return (place + 1);
        }
    }



    /** Constructor. */
    public EvioScanner(SimpleMappedMemoryHandler memoryHandler, FileFrameBig.ErrorTask errorTask) {
        this.memoryHandler = memoryHandler;
        this.errorTask = errorTask;

        try {
            readFirstHeader();
        }
        catch (EvioException e) {
            e.printStackTrace();
        }
    }



    /**
     * This method searches for an evio structure (EvioNode) in which a
     * format error exists.
     *
     * @param buffer     buffer to examine
     * @param position   position in buffer
     * @param place      place of event in buffer (event # starting at 0)
     * @param bytesLeft  # of bytes left to read in buffer. Cannot use
     *                   buffer.remaining() since we purposefully do not
     *                   change the buffer position.
     * @param fileOffset Offset in bytes from beginning of file to beginning
     *                   of map used to find pos & dataPos.
     *
     * @return EvioNode object containing evio structure with error
     */
    private EvioNode searchForErrorInEvent(ByteBuffer buffer,
                                           int position, int place, int bytesLeft,
                                           long fileOffset) {

        int pad, dataType;

        // Store evio event info, without de-serializing, into EvioNode object
        EvioNode node = new EvioNode(position, place, fileOffset, buffer);

        // Get length of current event
        node.len = buffer.getInt(position);
        // Position of data for a bank
        node.dataPos = position + 8;
        // Len of data for a bank
        node.dataLen = node.len - 1;

        // Check length:
        // Make sure there is enough data to read full event
        // even though it is NOT completely read at this time.
        if (bytesLeft < 4*(node.len + 1)) {
            node.error = "buffer underflow";
System.out.println("Error1: " + node.error);
            return node;
        }

        // Hop over length word
        position += 4;

        // Read second header word
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            // skip tag
            position += 2;

            int dt = buffer.get(position) & 0xff;
            dataType = dt & 0x3f;
            pad = dt >>> 6;
            // If only 7th bit set, that can only be the legacy tagsegment type
            // with no padding information - convert it properly.
            if (dt == 0x40) {
                dataType = DataType.TAGSEGMENT.getValue();
                pad = 0;
            }
        }
        else {
            // skip num
            position++;

            int dt = buffer.get(position) & 0xff;
            dataType = dt & 0x3f;
            pad = dt >>> 6;
            if (dt == 0x40) {
                dataType = DataType.TAGSEGMENT.getValue();
                pad = 0;
            }
        }

        // Test value of dataType
        DataType dataTypeObj = DataType.getDataType(dataType);
        node.dataType = dataType;
        // If type or pad is a bad value, error
        if (dataTypeObj == null || (pad > 0 && !dataTypeHasPadding(dataTypeObj))) {
            // Return error condition
            if (dataTypeObj == null ) {
                node.error = "Bad data type (= " + dataType + ")";
            }
            else {
                node.error = "Padding (= " + pad + ") does not match data type (= "
                        + dataTypeObj + ")";
            }
System.out.println("Error2: " + node.error);
            return node;
        }

        // Scan through all evio structures looking for bad format
        return scanStructureForError(node);
    }


    /**
     * This method recursively stores, in the given list, all the information
     * about an evio structure's children found in the given ByteBuffer object.
     * It uses absolute gets so buffer's position does <b>not</b> change.
     *
     * @param node node being scanned
     */
    private EvioNode scanStructureForError(EvioNode node) {

        // Type of evio structure being scanned
        DataType type = node.getDataTypeObj();

        // If node does not contain containers, return since we can't drill any further down
        if (!type.isStructure()) {
            return null;
        }
 //System.out.println("scanStructure: scanning evio struct with len = " + node.dataLen);
 //System.out.println("scanStructure: data type of node to be scanned = " + type);

        // Start at beginning position of evio structure being scanned
        int position = node.dataPos;
        // Don't go past the data's end which is (position + length)
        // of evio structure being scanned in bytes.
        int endingPos = position + 4*node.dataLen;
        // Buffer we're using
        ByteBuffer buffer = node.buffer;
//System.out.println("scanStructure: pos = " + position + ", ending pos = " + endingPos +
//", lim = " + buffer.limit() + ", cap = " + buffer.capacity());

        int thisStructureDataWords = node.len;
        DataType nodeType = node.getTypeObj();
        if (nodeType == DataType.BANK || nodeType == DataType.ALSOBANK) {
            // Account for extra word in bank header
            thisStructureDataWords--;
        }

        EvioNode returnedNode;
        DataType dataTypeObj;
        int totalKidWords = 0;
        int dt, dataType, dataLen, len, pad;

        // Do something different depending on what node contains
        switch (type) {
            case BANK:
            case ALSOBANK:

                // Extract all the banks from this bank of banks.
                // Make allowance for reading header (2 ints).
                while (position < endingPos - 8) {
//System.out.println("scanStructure: buf is at pos " + buffer.position() +
//                   ", limit =  " + buffer.limit() + ", remaining = " + buffer.remaining() +
//                   ", capacity = " + buffer.capacity());

                    // Read first header word
                    len = buffer.getInt(position);

                    // Len of data (no header) for a bank
                    dataLen = len - 1;
                    position += 4;

                    // Read & parse second header word
                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        // skip tag
                        position += 2;

                        dt = buffer.get(position++) & 0xff;
                        dataType = dt & 0x3f;
                        pad = dt >>> 6;
                        // If only 7th bit set, that can only be the legacy tagsegment type
                        // with no padding information - convert it properly.
                        if (dt == 0x40) {
                            dataType = DataType.TAGSEGMENT.getValue();
                            pad = 0;
                        }
                        // skip num
                        position++;
                    }
                    else {
                        // skip num
                        position++;

                        dt = buffer.get(position++) & 0xff;
                        dataType = dt & 0x3f;
                        pad = dt >>> 6;
                        if (dt == 0x40) {
                            dataType = DataType.TAGSEGMENT.getValue();
                            pad = 0;
                        }

                        // skip tag
                        position += 2;
                    }

                    // Total length in words of this bank (including header)
                    totalKidWords += len + 1;

                    // Cloning is a fast copy that eliminates the need
                    // for setting stuff that's the same as the parent.
                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 8;
                    kidNode.type = DataType.BANK.getValue();  // This is a bank

                    kidNode.dataLen  = dataLen;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;

                    kidNode.isEvent = false;

                    // Test value of dataType
                    dataTypeObj = DataType.getDataType(dataType);
                    // If type or pad is a bad value , error
                    if (dataTypeObj == null || (pad > 0 && !dataTypeHasPadding(dataTypeObj))) {
                        // Return error condition
                        if (dataTypeObj == null ) {
                            kidNode.error = "Bad data type (= " + dataType + ")";
                        }
                        else {
                            kidNode.error = "Padding (= " + pad + ") does not match data type (= "
                                    + dataTypeObj + ")";
                        }
System.out.println("Error 1: " + kidNode.error);
                        return kidNode;
                    }

//System.out.println("scanStructure: kid bank at pos = " + kidNode.pos +
//                   " with type " +  DataType.getDataType(dataType) + ", tag/num = " + kidNode.tag +
//                   "/" + kidNode.num + ", list size = " + node.eventNode.allNodes.size());

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
                while (position < endingPos - 4) {

                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        // skip tag
                        position++;
                        dt = buffer.get(position++) & 0xff;
                        dataType = dt & 0x3f;
                        pad = dt >>> 6;
                        if (dt == 0x40) {
                            dataType = DataType.TAGSEGMENT.getValue();
                            pad = 0;
                        }
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                    }
                    else {
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                        dt = buffer.get(position++) & 0xff;
                        dataType = dt & 0x3f;
                        pad = dt >>> 6;
                        if (dt == 0x40) {
                            dataType = DataType.TAGSEGMENT.getValue();
                            pad = 0;
                        }
                        // skip tag
                        position++;
                    }

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 4;
                    kidNode.type = DataType.SEGMENT.getValue();  // This is a segment

                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;

                    kidNode.isEvent = false;

                    // Test value of dataType
                    dataTypeObj = DataType.getDataType(dataType);
                    // If type or pad is a bad value , error
                    if (dataTypeObj == null || (pad > 0 && !dataTypeHasPadding(dataTypeObj))) {
                        // Return error condition
                        if (dataTypeObj == null ) {
                            kidNode.error = "Bad data type (= " + dataType + ")";
                        }
                        else {
                            kidNode.error = "Padding (= " + pad + ") does not match data type (= "
                                    + dataTypeObj + ")";
                        }
System.out.println("Error 2: " + kidNode.error);
                        return kidNode;
                    }

// System.out.println("scanStructure: kid seg at pos = " + kidNode.pos +
//                    " with type " +  DataType.getDataType(dataType) + ", tag/num = " + kidNode.tag +
//                    "/" + kidNode.num + ", list size = " + node.eventNode.allNodes.size());

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
                while (position < endingPos - 4) {

                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        int temp = buffer.getShort(position) & 0xffff;
                        position += 2;
                        dataType = temp & 0xf;
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                    }
                    else {
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                        int temp = buffer.getShort(position) & 0xffff;
                        position += 2;
                        dataType = temp & 0xf;
                    }

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 4;
                    kidNode.type = DataType.TAGSEGMENT.getValue();  // This is a tag segment

                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;

                    kidNode.isEvent = false;

                    dataTypeObj = DataType.getDataType(dataType);
                    if (dataTypeObj == null) {
                        kidNode.error = "Bad data type (= " + dataType + ")";
System.out.println("Error 3: " + kidNode.error);
                        return kidNode;
                    }

// System.out.println("scanStructure: kid tagseg at pos = " + kidNode.pos +
//                    " with type " +  DataType.getDataType(dataType) + ", tag/num = " + kidNode.tag +
//                    "/" + kidNode.num + ", list size = " + node.eventNode.allNodes.size());

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
            node.error = "Bad word length(s): node's = " + thisStructureDataWords +
                         ", kids' = " + totalKidWords;
System.out.println("Error 4: " + node.error);
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
    public boolean scanFileForErrors() throws IOException, EvioException {

        int      bufPos, byteInfo, byteLen, magicNum, lengthOfEventsInBlock;
        int      blockHdrWordSize, blockWordSize, blockEventCount;
        int      mapByteSize, mapBytesLeft;
        boolean  firstBlock=true, foundError=false;
        ByteBuffer memoryMapBuf;
        BlockNode blockNode = null;
        EvioNode node;

        // Keep track of the # of blocks & events in file
        blockCount = 0;
        eventCount = 0;

        // Bytes from file beginning to map beginning
        // which allows translation of positions in EvioNode
        // to absolute file positions.
        long fileOffset = 0L;

        // Keep track of position in file
        long fileByteSize   = memoryHandler.getFileSize();
        long fileBytesLeft  = fileByteSize;
        FileChannel channel = memoryHandler.getFileChannel();
        try {
            channel.position(0L);
        }
        catch (IOException e) {/*Should never happen */}


        // Need enough data to at least read 1 block header (32 bytes)
        if (fileBytesLeft < 32) {
            throw new EvioException("File too small (" + fileBytesLeft + " bytes)");
        }

        try {
            // While there's still unprocessed data in file ...
            while (fileBytesLeft > 0) {

                // Update progress
                if (errorTask != null) {
                    int progressPercent = (int) (100*(fileByteSize - fileBytesLeft)/(fileByteSize));
System.out.println("progress = " + progressPercent);
                    errorTask.setTaskProgress(progressPercent);
                }

                // Map memory but not more than max allowed at one time
                mapBytesLeft = mapByteSize = (int) Math.min(fileBytesLeft, Integer.MAX_VALUE);

//System.out.println("MMH constr: sz = " + sz + ", offset = " + offset);

                memoryMapBuf = channel.map(FileChannel.MapMode.READ_ONLY, fileOffset, mapByteSize);
                memoryMapBuf.order(fileByteOrder);

                // Start at the beginning of mapped buffer without
                // changing position. Do this with absolute gets.
                bufPos = 0;

                // It's too difficult to process an evio block which is split between
                // 2 different memory maps.
                // To accommodate, we stop using a map just after the last complete
                // block in that map. The next map will start at the following block.

                do {

                    if (errorTask != null && errorTask.stopSearch()) {
                        return foundError;
                    }

                    // Read in block header info, swapping is taken care of
                    blockWordSize    = memoryMapBuf.getInt(bufPos);
                    byteInfo         = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_VERSION);
                    blockHdrWordSize = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_HEADERSIZE);
                    blockEventCount  = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_COUNT);
                    magicNum         = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_MAGIC);

//                System.out.println("    genEvTablePos: blk ev count = " + blockEventCount +
//                                           ", blockSize = " + blockWordSize +
//                                           ", blockHdrSize = " + blockHdrWordSize +
//                                           ", byteInfo  = " + byteInfo);

                    // Positioned before block header so get its info
                    blockNode         = new BlockNode();
                    blockNode.pos     = bufPos;
                    blockNode.filePos = fileOffset + bufPos;
                    blockNode.len     = blockWordSize;
                    blockNode.count   = blockEventCount;
                    blockNode.place   = blockCount++;

                    lengthOfEventsInBlock = 0;

                    // If magic # is not right, file is not in proper format
                    if (magicNum != BlockHeaderV4.MAGIC_NUMBER) {
                        blockNode.error = "Block header magic # incorrect";
                        blockErrorNode = blockNode;
                        blockErrorNodes.add(blockNode);
System.out.println("scanFile: fatal error = " + blockNode.error);
                        return true;
                    }

                    // Block lengths are too small
                    if (blockWordSize < 8 || blockHdrWordSize < 8) {
                        blockNode.error = "Block len too small: len = " +
                                           blockWordSize + ", header len = " + blockHdrWordSize;
                        blockErrorNode = blockNode;
                        blockErrorNodes.add(blockNode);
System.out.println("scanFile: fatal error = " + blockNode.error);
                        return true;
                    }

                    // Block header length not = 8
                    if (blockHdrWordSize != 8) {
                        System.out.println("Warning, suspicious block header size, " + blockHdrWordSize);
                    }

//                curLastBlock    = BlockHeaderV4.isLastBlock(byteInfo);

                    // Hop over block header to events
                    bufPos        += 4*blockHdrWordSize;
                    mapBytesLeft  -= 4*blockHdrWordSize;
                    fileBytesLeft -= 4*blockHdrWordSize;

//System.out.println("    hopped blk hdr, pos = " + bufPos);

                    // Check for a dictionary - the first event in the first block.
                    // It's not included in the header block count, but we must take
                    // it into account by skipping over it.
                    if (firstBlock && BlockHeaderV4.hasDictionary(byteInfo)) {
                        firstBlock = false;

                        // Get its length - bank's len does not include itself
                        byteLen = 4*(memoryMapBuf.getInt(bufPos) + 1);

                        // Skip over dictionary
                        bufPos        += byteLen;
                        mapBytesLeft  -= byteLen;
                        fileBytesLeft -= byteLen;
                        lengthOfEventsInBlock += byteLen;
//System.out.println("    hopped dict, pos = " + bufPos);
                    }

                    // For each event in block, store its location
                    for (int i=0; i < blockEventCount; i++) {
                        // Sanity check - must have at least 1 header's amount left
                        if (mapBytesLeft < 8) {
                            blockNode.error = "Not enough data to read event (bad bank len?)";
                            blockErrorNode = blockNode;
                            blockErrorNodes.add(blockNode);
System.out.println("scanFile: fatal error = " + blockNode.error);
                            return true;
                        }

                        // Only returns node containing evio error, else null if OK
                        node = searchForErrorInEvent(memoryMapBuf, bufPos,
                                                     eventCount + i, mapBytesLeft, fileOffset);
                        // If there's been an error detected in this event ...
                        if (node != null) {
                            // Try to salvage things by skipping this block and going to next
System.out.println("Error in event #" + (eventCount + i));
System.out.println("Skip the rest of block #" + blockCount);
                            eventErrorNodes.add(node);
                            blockErrorNodes.add(blockNode);
                            // We're done with this block
                            mapBytesLeft = 0;
                            // This will compensate for the line below
                            eventCount -= blockEventCount;
                            foundError = true;
                            break;
                        }

                        // Hop over header + data
                        byteLen = 4*(memoryMapBuf.getInt(bufPos) + 1);

                        bufPos        += byteLen;
                        mapBytesLeft  -= byteLen;
                        fileBytesLeft -= byteLen;
                        lengthOfEventsInBlock += byteLen;

//System.out.println("    Hopped event " + (i+eventCount) + ", file bytes left = " + fileBytesLeft +
//                   ", file offset = " + fileOffset + "\n");
                    }

                    if (lengthOfEventsInBlock != 4*(blockNode.len - blockHdrWordSize)) {
                        blockNode.error = "Byte len of events in block (" + lengthOfEventsInBlock +
                                          ") doesn't match block header (" +
                                           4*(blockNode.len - blockHdrWordSize) + ")";
//                        blockNode.error = "Byte len of events in block\n(" + lengthOfEventsInBlock +
//                                          " doesn't match block header\n(" +
//                                           4*(blockNode.len - blockHdrWordSize) + ")";
                        blockErrorNode = blockNode;
                        blockErrorNodes.add(blockNode);
                        foundError = true;
System.out.println("scanFile: try again error = " + blockNode.error);
                        // Try skipping this block and going to next
                    }

                    eventCount += blockEventCount;

                    // Check to see if all map data is already examined,
                    // if so create the next map.
                    if (mapBytesLeft == 0) {
                        break;
                    }

                    // Length of next block
                    blockWordSize = memoryMapBuf.getInt(bufPos);

                } while (4*blockWordSize <= mapBytesLeft);

                // If we're here, not enough data in map for next block

                // Check to see if we're at the end of the file
                if (fileBytesLeft == 0) {
                    break;
                }
                // If not enough data in rest of file ...
                else if (4*blockWordSize > fileBytesLeft) {
                    blockNode.error = "Block len too large (not enough data)";
                    blockErrorNode = blockNode;
                    blockErrorNodes.add(blockNode);
                    return true;
                }
                // or not enough data to read next block header (32 bytes)
                else if (fileBytesLeft < 32) {
                    blockNode.error = "Extra " + fileBytesLeft + " bytes at file end";
                    blockErrorNode = blockNode;
                    blockErrorNodes.add(blockNode);
                    return true;
                }

                // Next map begins here in file
                fileOffset += mapByteSize - mapBytesLeft;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }


    /**
     * Reads the first block (physical record) header in order to determine
     * characteristics of the file or buffer in question. These things
     * do <b>not</b> need to be examined in subsequent block headers.
     * File or buffer must be evio version 4 or greater.
     *
     * @throws EvioException if endianness or version is bad or too little data
     */
    private void readFirstHeader() throws EvioException {
        BlockHeaderV4 blockHeader = new BlockHeaderV4();

        // Get first block header
        ByteBuffer byteBuffer = memoryHandler.getFirstMap();

        // Have enough remaining bytes to read header?
        if (byteBuffer.limit() < 32) {
            throw new EvioException("");
        }

        // Set the byte order to match the file's ordering.

        // Check the magic number for endianness (buffer defaults to big endian)
        ByteOrder byteOrder = byteBuffer.order();

        int magicNumber = byteBuffer.getInt(4*BlockHeaderV4.EV_MAGIC);

        if (magicNumber != IBlockHeader.MAGIC_NUMBER) {

            if (byteOrder == ByteOrder.BIG_ENDIAN) {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
            }
            else {
                byteOrder = ByteOrder.BIG_ENDIAN;
            }
            byteBuffer.order(byteOrder);

            // Reread magic number to make sure things are OK
            magicNumber = byteBuffer.getInt(4*BlockHeaderV4.EV_MAGIC);
            if (magicNumber != IBlockHeader.MAGIC_NUMBER) {
                throw new EvioException("Reread magic # (" + magicNumber + ") & still not right");
            }
        }

        fileByteOrder = byteOrder;

        // Check the version number
        int bitInfo = byteBuffer.getInt(4*BlockHeaderV4.EV_VERSION);
        int evioVersion = bitInfo & 0xff;
        if (evioVersion < 4)  {
            throw new EvioException("EvioCompactReader: unsupported evio version (" + evioVersion + ")");
        }
    }


}
