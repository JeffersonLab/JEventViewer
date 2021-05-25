package org.jlab.coda.eventViewer;

import org.jlab.coda.hipo.FileHeader;
import org.jlab.coda.hipo.HipoException;
import org.jlab.coda.hipo.RecordHeader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * This is a class designed to handle access files with size greater than 2.1 GBytes.
 * Currently the largest size memory map that Java can handle
 * is Integer.MAX_VALUE which limits the use of memory mapping to looking at
 * files that size or smaller. This class circumvents this limit by viewing
 * large files as a collection of multiple memory maps.<p>
 *
 * Just a note about synchronization. This object is <b>NOT</b> thread-safe.
 */
public class SimpleMappedMemoryHandler {
    /** Size of file in bytes. */
    private long fileSize;

    /** Max map size in bytes (1GB) */
    private long maxMapSize = 1000000000L;
    //private long maxMapSize = 4052L; // For testing, must be multiple of 4

    /** Byte order of data in buffer. */
    private ByteOrder order;

    /** Number of memory maps needed to fully map file. */
    private int mapCount;

    /** List containing each event's memory map. */
    private final ArrayList<ByteBuffer> maps = new ArrayList<>(20);

    /** Channel used to create memory maps. */
    private FileChannel fileChannel;

    /** The number of extra bytes at the very end of
     *  this file beyond an integral number of ints. */
    private int extraByteCount;

    /**
     * Get the entire evio version 6 file header, including the initial 14 words,
     * its index, and its user header in a ByteBuffer.
     */
    private ByteBuffer fileHeaderData;

    /** Get the evio version 6 file header object. */
    private FileHeader fileHeader;

    /** The evio version 6 file header total bytes, header + index + user header. */
    private int fileHeaderBytes;

    /** The index of the first data.
     *  It's the number of 32 bit words comprising the
     *  file and first block headers for evio version 6.
     *  For earlier evio versions this is 8 words which will be our default. */
    private int firstDataIndex = 8;

    /** Is the data in file compressed? Always false if evio version < 6. */
    private boolean isCompressed;



    /**
     * Constructor.
     *
     * @param file   file's file channel object
     * @param order byte order of the data
     * @throws java.io.IOException   if could not map file
     */
    public SimpleMappedMemoryHandler(File file, ByteOrder order)
            throws IOException {

        this.order = order;

        // Map the file to get access to its data
        // without having to read the whole thing.
        FileInputStream fileInputStream = new FileInputStream(file);
        fileChannel = fileInputStream.getChannel();

        long remainingSize = fileSize = fileChannel.size();
        if (fileSize < 4*8) {
            // For a bare minimum there must be 8 words in evio files version < 6 block header,
            // and 14 words for version 6+ file header.
            throw new IOException("file too small at " + fileSize + " bytes");
        }
        extraByteCount = (int)(fileSize % 4);

        long sz, offset = 0L;
        ByteBuffer memoryMapBuf;

        // Ensure that the map size is a multiple of 20 bytes (1 row)
        // so things don't get impossible to deal with
        if ((maxMapSize % 20) != 0) {
            maxMapSize = 20*(maxMapSize/20);
        }

        // Divide the memory into chunks or regions
        while (remainingSize > 0) {
            // Break into chunks of maxMapSize bytes
            sz = Math.min(remainingSize, maxMapSize);
//System.out.println("mmapHandler: remaining size = " + remainingSize +
//                   ", map size = " + sz + ", mapCount = " + mapCount);

            memoryMapBuf = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, sz);
            memoryMapBuf.order(order);

            // Store the map
            maps.add(memoryMapBuf);

            offset += sz;
            remainingSize -= sz;
            mapCount++;
        }

        // Read in evio version 6 file header
        try {
            // This call gets version and sets ByteBuffer arg's order to correct endianness
            int version = org.jlab.coda.jevio.Utilities.getEvioVersion(maps.get(0));
            ByteOrder actualOrder = maps.get(0).order();

            // For version 6+ get the file header data
            if (version > 5) {
                // Create byte buffer to hold file header data
                fileHeaderData = ByteBuffer.wrap(new byte[4 * 14]);
                fileHeaderData.order(actualOrder);

                // Do absolute read from map into file header byte buffer
                // For Java 13+ do:
                // maps.get(0).get(0, fileHeaderData.array(), 0, 4 * 14);
                // else do:
                ByteBuffer bb = maps.get(0);
                bb.get(fileHeaderData.array(), 0, 4 * 14);
                bb.position(0);
                // Create FileHeader object
                fileHeader = new FileHeader();
                // Have the object parse the buffer and store it in fields.
                // This method also sets fileHeaderData's byte order to its proper value.
                fileHeader.readHeader(fileHeaderData, 0);

                // Find total size, which includes index and user header, then read rest of data if necessary
                fileHeaderBytes = fileHeader.getLength();
                if (fileHeaderBytes > 4*14) {
                    fileHeaderData = ByteBuffer.wrap(new byte[fileHeaderBytes]);
                    fileHeaderData.order(actualOrder);
                    // For Java 13+ do:
                    // maps.get(0).get(0, fileHeaderData.array(), 0, fileHeaderBytes);
                    // else do:
                    bb = maps.get(0);
                    bb.get(fileHeaderData.array(), 0, fileHeaderBytes);
                    bb.position(0);
                }

                // Another useful quantity is the index of where the very first data starts,
                // past the file header the first record header.
                ByteBuffer firstRecordHdr = ByteBuffer.wrap(new byte[4 * 14]);

                // For Java 13+ do:
                // maps.get(0).get(fileHeaderBytes, firstRecordHdr.array(), 0, 4 * 14);
                // else do:
                bb = maps.get(0);
                bb.position(fileHeaderBytes);
                bb.get(fileHeaderData.array(), 0, 4 * 14);
                bb.position(0);

                // Create RecordHeader object
                RecordHeader recHeader = new RecordHeader();
                // Parse the buffer and store it in fields.
                recHeader.readHeader(firstRecordHdr, 0);
                firstDataIndex = (fileHeaderBytes + recHeader.getTotalHeaderLength())/4;
//System.out.println("mmapHandler: fileHeaderBytes = " + fileHeaderBytes);
//System.out.println("mmapHandler: recordHeaderTotalLen = " + recHeader.getTotalHeaderLength());
//System.out.println("mmapHandler: firstDataIndex = " + firstDataIndex);

                // Now take this one step further and find out if data in the first record is compressed.
                try {
                    isCompressed = RecordHeader.isCompressed(maps.get(0), fileHeaderBytes);
                }
                catch (HipoException e) {}
            }

            // If the actual order is not what it was initially set to, fix it
            if (actualOrder != order) {
                this.order = actualOrder;
                for (ByteBuffer buf : maps) {
                    buf.order(actualOrder);
                }
            }

        }
        catch (Exception e) {
            // If this fails, it's most likely an earlier evio version
            fileHeaderData = null;
            fileHeader = null;
        }

        //Utilities.printBufferBytes(memoryMapBuf, 0, 1000, "File bytes");
    }


    /**
     * Constructor.
     * @param buf buffer to analyze
     */
    public SimpleMappedMemoryHandler(ByteBuffer buf) {
        if (buf == null) {
            return;
        }

        mapCount = 1;
        maps.add(buf);
    }


    /**
     * Does the first record of the first map contain compressed data?
     * @return {@code true} if data in first record of the first
     *          memory map is compressed, else {@code false}.
     */
    public boolean isCompressed() {return isCompressed;}


    /**
     * Get the number of 32 bit words comprising the
     * file and first record headers for evio version 6 files, or
     * comprising the first block header for evio versions &lt; 6.
     * @return the position, in 32 bit words, of the first data word from the file start.
     */
    public int getFirstDataIndex() {return firstDataIndex;}


    /**
     * Get the total number of bytes in file header + index + user header for evio version 6 file.
     * @return total number of bytes in file header + index + user header for evio version 6 file.
     */
    public int getTotalFileHeaderBytes() {return fileHeaderBytes;}


    /**
     * Get the file header data of a version 6 evio file including index and user header.
     * @return file header data of a version 6 evio file including index and user header,
     *         or null if evio version &lt; 6.
     */
    public ByteBuffer getFileHeaderData() {return fileHeaderData;}


    /**
     * Get the file header of a version 6 evio file.
     * @return file header of a version 6 evio file,
     *         or null if evio version &lt; 6.
     */
    public FileHeader getFileHeader() {return fileHeader;}


    /** Close unneeded file channel object. */
    public void close() {
        try {fileChannel.close();}
        catch (IOException e) {}
    }


    /**
     * Does this file have 1, 2, or 3 bytes more
     * than an integral number of integers?
     * @return false if file is integral number of ints, else true.
     */
    public boolean haveExtraBytes() { return extraByteCount > 0; }


    /**
     * Get the number of extra bytes at the very end of
     * this file beyond an integral number of ints.
     * @return  number of extra bytes at the very end of
     *          this file beyond an integral number of ints.
     */
    public int getExtraByteCount() { return extraByteCount; }


    /**
     * Get the maximum byte size of each memory map (last one will be smaller).
     * @return max byte size of each memory map.
     */
    public long getMaxMapSize() { return maxMapSize; }


    /**
     * Get the file channel object.
     * @return file channel object.
     */
    public FileChannel getFileChannel() { return fileChannel; }


    /**
     * Get the file size in bytes.
     * @return file size in bytes.
     */
    public long getFileSize() {return fileSize;}


    /**
     * Get the size of the given map.
     * @param mapIndex index of map.
     * @return size of map in bytes
     */
    public int getMapSize(int mapIndex) {
        if (mapIndex < 0 || mapIndex > mapCount - 1) {
            return 0;
        }
        return maps.get(mapIndex).limit();
    }


    /**
     * Get the number of memory maps used to fully map file.
     * @return number of memory maps used to fully map file.
     */
    public int getMapCount() {return mapCount;}


    /**
     * Get the first memory map - used to map the beginning of the file.
     * @return first memory map - used to map the beginning of the file.
     */
    public ByteBuffer getFirstMap() {return maps.get(0);}


    /**
     * Set the byte order of the data.
     * @param order byte order of the data.
     */
    public void setByteOrder(ByteOrder order) {
        if (this.order == order) return;
        this.order = order;
        for (ByteBuffer map : maps) {
            map.order(order);
        }
    }


    /**
     * Get the byte order of the data.
     * @return byte order of the data.
     */
    public ByteOrder getOrder() { return order; }


    /**
     * Get the indicated memory map.
     * @param mapIndex index into map holding memory mapped ByteBuffers
     * @return indicated memory map.
     */
    public ByteBuffer getMap(int mapIndex) {
        if (mapIndex < 0 || mapIndex > mapCount - 1) {
            return null;
        }
        return maps.get(mapIndex);
    }


    /**
     * Get the int value in the file at the given word (4 byte) position.
     * @param wordPosition word position in file
     * @return int value in file at word position
     */
    public int getInt(long wordPosition) {
        return getIntAtBytePos(4*wordPosition);
    }


    /**
     * Get the int value in the file at the given byte index.
     * @param bytePosition byte index into file
     * @return int value in file at byte index
     */
    public int getIntAtBytePos(long bytePosition) {
        int mapIndex  = (int) (bytePosition/maxMapSize);
        int byteIndex = (int) (bytePosition - (mapIndex * maxMapSize));
        ByteBuffer buf = getMap(mapIndex);
        if (buf == null) return 0;

        // Check for end effects if 1, 2, or 3 bytes left.
        // Assume highest (most significant) bytes are missing.
        long remainingBytes = fileSize - bytePosition;
        if (remainingBytes < 4L && remainingBytes > 0L) {
           int lastInt=0;
            switch ((int)remainingBytes) {
                case 1:
                    lastInt = ((int) buf.get(byteIndex)) & 0xff;
                    break;

                case 2:
                    if (buf.order() == ByteOrder.BIG_ENDIAN) {
                        lastInt = ((((int) buf.get(byteIndex)) << 8) & 0xff00) |
                                   (((int) buf.get(byteIndex + 1) & 0xff));
                    }
                    else {
                        lastInt = ((((int) buf.get(byteIndex + 1)) << 8) & 0xff00) |
                                   (((int) buf.get(byteIndex) & 0xff));
                    }
                    break;

                case 3:
                    if (buf.order() == ByteOrder.BIG_ENDIAN) {
                        lastInt = ((((int) buf.get(byteIndex)) << 16) & 0xff0000)  |
                                  ((((int) buf.get(byteIndex + 1)) << 8) & 0xff00) |
                                   (((int) buf.get(byteIndex + 2)) & 0xff);
                    }
                    else {
                        lastInt = ((((int) buf.get(byteIndex + 2)) << 16) & 0xff0000) |
                                  ((((int) buf.get(byteIndex + 1)) << 8)  & 0xff00)   |
                                   (((int) buf.get(byteIndex)) & 0xff);
                    }
                    break;

                default:
            }
            return lastInt;
        }

        return buf.getInt(byteIndex);
    }


    /**
     * Get the short value in the file at the given byte index.
     * @param bytePosition byte index into file
     * @return short value in file at byte index
     */
    public short getShortAtBytePos(long bytePosition) {
        int mapIndex  = (int) (bytePosition/maxMapSize);
        int byteIndex = (int) (bytePosition - (mapIndex * maxMapSize));
        ByteBuffer buf = getMap(mapIndex);
        if (buf == null) return 0;

        // Check for end effect if 1 byte left
        // Assume highest (most significant) byte is missing.
        if ((fileSize - bytePosition) == 1L) {
            return (short)(((int) buf.get(byteIndex)) & 0xff);
        }

        return buf.getShort(byteIndex);
    }


    /**
     * Get the byte value in the file at the given byte index.
     * @param bytePosition byte index into file
     * @return byte value in file at byte index
     */
    public byte getByteAtBytePos(long bytePosition) {
        int mapIndex  = (int) (bytePosition/maxMapSize);
        int byteIndex = (int) (bytePosition - (mapIndex * maxMapSize));
        ByteBuffer buf = getMap(mapIndex);
        if (buf == null) return 0;
        return buf.get(byteIndex);
    }


    /**
     * Get the index of the map for a given word index.
     * @param wordIndex index of word
     * @return index of map containing word
     */
    public int getMapIndex(long wordIndex) {return (int) (wordIndex*4/maxMapSize);}

}
