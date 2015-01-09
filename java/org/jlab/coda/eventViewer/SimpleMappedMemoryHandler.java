package org.jlab.coda.eventViewer;

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
 * Just a note about synchronization. This object is <b>NOT</b> threadsafe.
 */
public class SimpleMappedMemoryHandler {

    private long fileSize;

    /** Max map size in bytes (200MB) */
    private final long maxMapSize = 200000000L;

    /** Byte order of data in buffer. */
    private ByteOrder order;

    /** Number of memory maps needed to fully map file. */
    private int mapCount;

    /** List containing each event's memory map. */
    private ArrayList<ByteBuffer> maps = new ArrayList<ByteBuffer>(20);


    /**
     * Constructor.
     *
     * @param channel   file's file channel object
     * @param order byte order of the data
     * @throws java.io.IOException   if could not map file
     */
    public SimpleMappedMemoryHandler(FileChannel channel, ByteOrder order)
            throws IOException {

        this.order = order;

        long remainingSize = fileSize = channel.size();
        if (fileSize < 4) {
            throw new IOException("file too small at " + fileSize + " byes");
        }
        long sz, offset = 0L;
        ByteBuffer memoryMapBuf = null;

        if (remainingSize < 1) return;

        // Divide the memory into chunks or regions
        while (remainingSize > 0) {
            // Break into chunks of 2^30
            sz = Math.min(remainingSize, maxMapSize);
            System.out.println("mmapHandler: remaining size = " + remainingSize +
                               ", map size = " + sz + ", mapCount = " + mapCount);

            memoryMapBuf = channel.map(FileChannel.MapMode.READ_ONLY, offset, sz);
            memoryMapBuf.order(order);

            // Store the map
            maps.add(memoryMapBuf);

            offset += sz;
            remainingSize -= sz;
            mapCount++;
        }
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


    public long getFileSize() {
        return fileSize;
    }

    public int getMapSize(int mapIndex) {
        if (mapIndex < 0 || mapIndex > mapCount - 1) {
            return 0;
        }
        return maps.get(mapIndex).limit();
    }


    /**
     * Get the number of memory maps used to fully map file.
     *
     * @return number of memory maps used to fully map file.
     */
    public int getMapCount() {return mapCount;}


    /**
     * Get the first memory map - used to map the beginning of the file.
     *
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


    public int getInt(int byteIndex, int mapIndex) {
        ByteBuffer buf = getMap(mapIndex);
        if (buf == null) return 0;
        return buf.getInt(byteIndex);
    }



    public int getInt(long wordIndex) {
        int mapIndex  = (int) (wordIndex*4/maxMapSize);
        int byteIndex = (int) (wordIndex*4 - (mapIndex * maxMapSize));
        ByteBuffer buf = getMap(mapIndex);
        if (buf == null) return 0;
//            System.out.println("getInt: index = " + index + ", region = " + region + ", mapIndex = " + mapIndex);
        return buf.getInt(byteIndex);
    }


    public int getMapIndex(long wordIndex) {
        return (int) (wordIndex*4/maxMapSize);
    }


}
