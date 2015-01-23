package org.jlab.coda.eventViewer;


import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * This class is used to scan a file for evio errors
 * and catalog them for later viewing.
 *
 * @author timmer (1/9/15)
 */
public class EvioScanner {

    /** Stores info of the first evio structure in a block with an error. */
    private final ArrayList<BlockHeader> blockErrorNodes = new ArrayList<BlockHeader>(10);

    /** Stores info of the first evio structure in a block with an error. */
    private final ArrayList<EvioNode> eventErrorNodes = new ArrayList<EvioNode>(1000);

    /** Object to memory map the entire file. */
    private final SimpleMappedMemoryHandler memoryHandler;

    /** Byte order of the file. */
    private ByteOrder fileByteOrder;

    /** Reference needed to update progress bar when searching file for evio errors. */
    private FileFrameBig.ErrorTask errorTask;


    /**
     * Is the given data type one that can have non-zero padding?
     * @param type evio data type
     * @return {@code true} if the data type can have non-zero padding,
     *         else {@code false}.
     */
    static public boolean dataTypeHasPadding(DataType type) {
        return type == DataType.CHAR8 ||
               type == DataType.UCHAR8 ||
               type == DataType.SHORT16 ||
               type == DataType.USHORT16;
    }


    /**
     * This class is copied from jevio.BlockNode and slightly modified.
     */
    static final class BlockHeader {

        /** Block's length value (32-bit words). */
        int len;
        /** Block's header length value (32-bit words). */
        int headerLen;
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
            infoWord      = blockInts[5];
            version       = infoWord & 0xff;
            isLast        = BlockHeaderV4.isLastBlock(infoWord);
            hasDictionary = BlockHeaderV4.hasDictionary(infoWord);
        }
    }


    /**
     * Abbreviated form of org.jlab.code.jevio.EvioNode class.
     */
    static final class EvioNode implements Cloneable {

        /** Header's length value (32-bit words). */
        int len;
        /** Position of header in file/buffer in bytes.  */
        int pos;
        /** This node's (evio container's) type. Must be bank, segment, or tag segment. */
        int type;
        /** Tag. */
        int tag;
        /** Num, only meaningful for Bank. */
        int num;
        /** Padding, only meaningful if type is 8 or 16 bit int. */
        int pad;

        /** Length of node's data in 32-bit words. */
        int dataLen;
        /** Position of node's data in file/buffer in bytes. */
        int dataPos;
        /** Type of data stored in node. */
        int dataType;

        /** Offset in bytes from beginning of file to beginning of map
         * used to find pos & dataPos. */
        long offset;

        /** Contains description of any error in event's header. */
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
         * an event (top level).
         *
         * @param headerWord1  event's first header word
         * @param headerWord1  event's second header word
         */
        EvioNode(int headerWord1, int headerWord2) {
            len  = headerWord1;
            tag  = headerWord2 >> 16 & 0xffff;
            num  = headerWord2 & 0xff;
            pad  = headerWord2 >> 14 & 0x3;
            dataType = headerWord2 >> 8 & 0x3f;
            // Assume we're hopping from bank to bank
            type = DataType.BANK.getValue();
        }

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
        // Getters  &  Setters
        //-------------------------------

        /**
         * Get the position of this event in file.
         * @return position of this event in file.
         */
        public long getFilePosition() { return offset + pos; }


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
    }



    /**
     * Constructor.
     * @param memoryHandler object with file memory maps.
     * @param errorTask     object doing file scan in background,
     *                      use to update its progress.
     */
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
     * Get the list of BlockHeader objects containing evio errors.
     * @return list of BlockHeader objects containing evio errors.
     */
    public ArrayList<BlockHeader> getBlockErrorNodes() { return blockErrorNodes; }


    /**
     * Get the list of EvioNode objects containing evio errors.
     * @return list of EvioNode objects containing evio errors.
     */
    public ArrayList<EvioNode> getEventErrorNodes() { return eventErrorNodes; }


    /**
     * Did the scan of the file show any evio errors?
     * @return {@code true} if there were errors, else {@code false}.
     */
    public boolean hasError() {return (blockErrorNodes.size() > 0 ||
                                       eventErrorNodes.size() > 0); }


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
     * @return EvioNode object containing evio structure with error or null if none
     */
    private EvioNode searchForErrorInEvent(ByteBuffer buffer,
                                           int position, int place, int bytesLeft,
                                           long fileOffset) {
        boolean debug = true;
        int pad, tag, num, dataType;

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
if (debug) System.out.println("Error1: " + node.error);
            return node;
        }

        // Hop over length word
        position += 4;

        // Read second header word
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            tag = buffer.getShort(position) & 0xffff;
            position += 2;

            int dt = buffer.get(position++) & 0xff;
            dataType = dt & 0x3f;
            pad = dt >>> 6;
            // If only 7th bit set, that can only be the legacy tagsegment type
            // with no padding information - convert it properly.
            if (dt == 0x40) {
                dataType = DataType.TAGSEGMENT.getValue();
                pad = 0;
            }

            num = buffer.get(position) & 0xff;
        }
        else {
            num = buffer.get(position++) & 0xff;

            int dt = buffer.get(position++) & 0xff;
            dataType = dt & 0x3f;
            pad = dt >>> 6;
            if (dt == 0x40) {
                dataType = DataType.TAGSEGMENT.getValue();
                pad = 0;
            }
            tag = buffer.getShort(position) & 0xffff;
        }

        // Test value of dataType
        DataType dataTypeObj = DataType.getDataType(dataType);
        node.dataType = dataType;
        node.pad = pad;
        node.tag = tag;
        node.num = num;

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
if (debug) System.out.println("Error2: " + node.error);
            return node;
        }

        // Scan through all evio structures looking for bad format
        return scanStructureForError(node);
    }


    /**
     * This method examines recursively all the information
     * about an evio structure's children. If there are any evio errors,
     * it returns an EvioNode object corresponding to the lowest level
     * structure containing the error.
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

        // Start at beginning position of evio structure being scanned
        int position = node.dataPos;
        // Don't go past the data's end which is (position + length)
        // of evio structure being scanned in bytes.
        int endingPos = position + 4*node.dataLen;
        // Buffer we're using
        ByteBuffer buffer = node.buffer;

        // How much memory do it child structures take up?
        int thisStructureDataWords = node.len;
        DataType nodeType = node.getTypeObj();
        if (nodeType == DataType.BANK || nodeType == DataType.ALSOBANK) {
            // Account for extra word in bank header
            thisStructureDataWords--;
        }

        EvioNode returnedNode;
        DataType dataTypeObj;
        int totalKidWords = 0;
        int dt, dataType, dataLen, len, pad, tag, num;
        boolean debug = true;

        // Do something different depending on what node contains
        switch (type) {
            case BANK:
            case ALSOBANK:

                // Extract all the banks from this bank of banks.
                // Make allowance for reading header (2 ints).
                while (position < endingPos - 8) {
                    // Read first header word
                    len = buffer.getInt(position);

                    // Len of data (no header) for a bank
                    dataLen = len - 1;
                    position += 4;

                    // Read & parse second header word
                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        tag = buffer.getShort(position) & 0xffff;
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
                        num = buffer.get(position++) & 0xff;
                    }
                    else {
                        num = buffer.get(position++) & 0xff;

                        dt = buffer.get(position++) & 0xff;
                        dataType = dt & 0x3f;
                        pad = dt >>> 6;
                        if (dt == 0x40) {
                            dataType = DataType.TAGSEGMENT.getValue();
                            pad = 0;
                        }

                        tag = buffer.getShort(position) & 0xffff;
                        position += 2;
                    }

                    // Total length in words of this bank (including header)
                    totalKidWords += len + 1;

                    // Cloning is a fast copy that eliminates the need
                    // for setting stuff that's the same as the parent.
                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 8;
                    kidNode.tag  = tag;
                    kidNode.num  = num;
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
                while (position < endingPos - 4) {

                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        tag = buffer.get(position++) & 0xff;
                        dt  = buffer.get(position++) & 0xff;
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
                        tag = buffer.get(position++) & 0xff;
                    }

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 4;
                    kidNode.tag  = tag;
                    kidNode.type = DataType.SEGMENT.getValue();  // This is a segment

                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;

                    kidNode.isEvent = false;

                    dataTypeObj = DataType.getDataType(dataType);
                    if (dataTypeObj == null || (pad > 0 && !dataTypeHasPadding(dataTypeObj))) {
                        if (dataTypeObj == null ) {
                            kidNode.error = "Bad data type (= " + dataType + ")";
                        }
                        else {
                            kidNode.error = "Padding (= " + pad + ") does not match data type (= "
                                    + dataTypeObj + ")";
                        }
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
                while (position < endingPos - 4) {

                    if (buffer.order() == ByteOrder.BIG_ENDIAN) {
                        int temp = buffer.getShort(position) & 0xffff;
                        position += 2;
                        tag = temp >>> 4;
                        dataType = temp & 0xf;
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                    }
                    else {
                        len = buffer.getShort(position) & 0xffff;
                        position += 2;
                        int temp = buffer.getShort(position) & 0xffff;
                        position += 2;
                        tag = temp >>> 4;
                        dataType = temp & 0xf;
                    }

                    // Total length in words of this seg (including header)
                    totalKidWords += len + 1;

                    EvioNode kidNode = (EvioNode)node.clone();

                    kidNode.len  = len;
                    kidNode.pos  = position - 4;
                    kidNode.tag  = tag;
                    kidNode.type = DataType.TAGSEGMENT.getValue();  // This is a tag segment

                    kidNode.dataLen  = len;
                    kidNode.dataPos  = position;
                    kidNode.dataType = dataType;

                    kidNode.isEvent = false;

                    dataTypeObj = DataType.getDataType(dataType);
                    if (dataTypeObj == null) {
                        kidNode.error = "Bad data type (= " + dataType + ")";
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
            node.error = "Bad word length(s): node's = " + thisStructureDataWords +
                         ", kids' = " + totalKidWords;
if (debug) System.out.println("Error 4: " + node.error);
            return node;
        }

        return null;
    }



    /**
     * Scan the file for evio errors. Unfortunately, we need to re-memory map the file
     * a piece at a time since it's too difficult to process evio structures which are
     * split across more than one map.
     *
     * @returns {@code true if error occurred}, else {@code false}
     * @throws EvioException if file cannot even be attempted to be parsed
     */
    public boolean scanFileForErrors() throws IOException, EvioException {

        int      bufPos, bufPosInBlock, byteInfo, byteLen, magicNum, lengthOfEventsInBlock;
        int      blockNum, blockHdrWordSize, blockWordSize, blockEventCount;
        int      mapByteSize, mapBytesLeft;
        boolean  firstBlock=true, foundError=false, goToNextBlock;
        ByteBuffer memoryMapBuf;
        BlockHeader blockNode;
        EvioNode node=null;

        // Keep track of the # of events in file
        int eventCount = 0;

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

                // Update progress in scanning file for errors
                if (errorTask != null) {
                    int progressPercent = (int) (100*(fileByteSize - fileBytesLeft)/(fileByteSize));
                    errorTask.setTaskProgress(progressPercent);
                }

                // Map memory but not more than max allowed at one time (2.1GB)
                mapBytesLeft = mapByteSize = (int) Math.min(fileBytesLeft, Integer.MAX_VALUE);
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

                    // We were told to stop by user
                    if (errorTask != null && errorTask.stopSearch()) {
                        return foundError;
                    }

                    // Read in block header info, swapping is taken care of
                    blockWordSize    = memoryMapBuf.getInt(bufPos);
                    blockNum         = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_BLOCKNUM);
                    byteInfo         = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_VERSION);
                    blockHdrWordSize = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_HEADERSIZE);
                    blockEventCount  = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_COUNT);
                    magicNum         = memoryMapBuf.getInt(bufPos + 4*BlockHeaderV4.EV_MAGIC);

                    // Store block header info in object
                    blockNode           = new BlockHeader();
                    blockNode.pos       = bufPos;
                    blockNode.filePos   = fileOffset + bufPos;
                    blockNode.len       = blockWordSize;
                    blockNode.headerLen = blockHdrWordSize;
                    blockNode.count     = blockEventCount;
                    blockNode.place     = blockNum;
                    blockNode.setInfoWord(byteInfo);

                    /// Init variables
                    goToNextBlock = false;
                    lengthOfEventsInBlock = 0;

                    // If magic # is not right, file is not in proper format
                    if (magicNum != BlockHeaderV4.MAGIC_NUMBER) {
                        blockNode.error = "Block header magic # incorrect";
                        blockErrorNodes.add(blockNode);
System.out.println("scanFile: fatal error = " + blockNode.error);
                        return true;
                    }

                    // Block lengths are too small
                    if (blockWordSize < 8 || blockHdrWordSize < 8) {
                        blockNode.error = "Block len too small: len = " +
                                           blockWordSize + ", header len = " + blockHdrWordSize;
                        blockErrorNodes.add(blockNode);
System.out.println("scanFile: fatal error = " + blockNode.error);
                        return true;
                    }

                    // Block header length not = 8
                    if (blockHdrWordSize != 8) {
                        System.out.println("Warning, suspicious block header size, " + blockHdrWordSize);
                    }

                    // Hop over block header to events
                    bufPosInBlock  = bufPos += 4*blockHdrWordSize;
                    mapBytesLeft  -= 4*blockHdrWordSize;
                    fileBytesLeft -= 4*blockHdrWordSize;

                    // Check for a dictionary - the first event in the first block.
                    // It's not included in the header block count, but we must take
                    // it into account by skipping over it.
                    if (firstBlock && BlockHeaderV4.hasDictionary(byteInfo)) {
                        firstBlock = false;

                        // Get its length - bank's len does not include itself
                        byteLen = 4*(memoryMapBuf.getInt(bufPosInBlock) + 1);

                        // Skip over dictionary
                        bufPosInBlock += byteLen;
                        lengthOfEventsInBlock += byteLen;
//System.out.println("    hopped dict, pos = " + bufPosInBlock);
                    }

                    // For each event in block ...
                    for (int i=0; i < blockEventCount; i++) {

                        // Sanity check - must have at least 1 header's amount to read
                        if (mapBytesLeft - lengthOfEventsInBlock < 8) {
                            blockNode.error = "Not enough data to read event (bad bank len?)";
                            blockErrorNodes.add(blockNode);
                            // We're done with this block
                            foundError = true;
                            goToNextBlock = true;
System.out.println("scanFile: fatal error = " + blockNode.error);
                            break;
                        }

                        try {
                            // Only returns node containing evio error, else null if OK
                            node = searchForErrorInEvent(memoryMapBuf, bufPosInBlock,
                                                         eventCount + i,
                                                         mapBytesLeft - lengthOfEventsInBlock,
                                                         fileOffset);
                        }
                        catch (Exception e) {
                            // Any error is here is probably due to a bad bank length
                            // causing an IndexOutOfBoundException.
                            foundError = true;
                            goToNextBlock = true;
                            blockNode.error = e.getMessage() + " (bad bank len?)";
                            blockErrorNodes.add(blockNode);
                        }

                        // If there's been an error detected in this event ...
                        if (node != null) {
                            // Try to salvage things by skipping this block and going to next
System.out.println("scanfile: error in event #" + (eventCount + i) + ", buf pos = " + bufPosInBlock);
                            node.place = eventCount + i;
                            eventErrorNodes.add(node);
                            blockNode.error = "contained event #" +  node.place + " has error";
                            blockErrorNodes.add(blockNode);
                            foundError = true;
                            // We're done with this block
                            goToNextBlock = true;
                            break;
                        }
                        else if (goToNextBlock) {
                            // This handles any exception caught just above
                            break;
                        }

                        // Hop over header + data to next event or block
                        byteLen = 4*(memoryMapBuf.getInt(bufPosInBlock) + 1);

                        bufPosInBlock += byteLen;
                        lengthOfEventsInBlock += byteLen;
//System.out.println("    Hopped event " + (i+eventCount) + ", file offset = " + fileOffset + "\n");
                    }

                    // If the length of events taken from the block header is not the same
                    // as the length of all the events in the block added up, there's a problem.
                    if (!goToNextBlock && (lengthOfEventsInBlock != 4*(blockNode.len - blockHdrWordSize))) {
                        blockNode.error = "Byte len of events in block (" + lengthOfEventsInBlock +
                                          ") doesn't match block header (" +
                                           4*(blockNode.len - blockHdrWordSize) + ")";
                        blockErrorNodes.add(blockNode);
System.out.println("scanFile: try again error = " + blockNode.error);
                    }

                    // If there is a difference, assume that the block length is good and
                    // the problems is with events' lengths. Then try to continue on.
                    lengthOfEventsInBlock = 4*(blockNode.len - blockHdrWordSize);
                    bufPos        += lengthOfEventsInBlock;
                    mapBytesLeft  -= lengthOfEventsInBlock;
                    fileBytesLeft -= lengthOfEventsInBlock;
                    eventCount    += blockEventCount;

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
                    blockErrorNodes.add(blockNode);
System.out.println("scanFile: not enough data for block len");
                    return true;
                }
                // or not enough data to read next block header (32 bytes)
                else if (fileBytesLeft < 32) {
                    blockNode.error = "Extra " + fileBytesLeft + " bytes at file end";
                    blockErrorNodes.add(blockNode);
System.out.println("scanFile: data left at file end");
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
