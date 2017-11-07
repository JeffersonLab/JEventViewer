package org.jlab.coda.eventViewer;

import org.jlab.coda.jevio.Utilities;

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
    private final long maxMapSize = 1000000000L;

    /** Byte order of data in buffer. */
    private ByteOrder order;

    /** Number of memory maps needed to fully map file. */
    private int mapCount;

    /** List containing each event's memory map. */
    private ArrayList<ByteBuffer> maps = new ArrayList<>(20);

    /** Channel used to create memory maps. */
    private FileChannel fileChannel;

    /** The number of extra bytes at the very end of
     *  this file beyond an integral number of ints. */
    private int extraByteCount;


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
        if (fileSize < 4) {
            throw new IOException("file too small at " + fileSize + " bytes");
        }
        extraByteCount = (int)(fileSize % 4);

        long sz, offset = 0L;
        ByteBuffer memoryMapBuf=null;

        if (remainingSize < 1) return;

        // Divide the memory into chunks or regions
        while (remainingSize > 0) {
            // Break into chunks of 200MB
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
