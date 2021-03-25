package org.jlab.coda.eventViewer;

import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.nio.ByteOrder;
import java.util.HashMap;

/**
 * This class describes the data table's data including column names.
 * Each file is broken up into 200MB (maxMapByteSize) memory maps
 * (last one is probably smaller than that since file size is probably
 * not an exact multiple of 200MB).
 * A single map is loaded into the table at any one time.
 */
final class MyTableModel2 extends AbstractTableModel {

    /** Buffer of memory mapped file. */
    private SimpleMappedMemoryHandler mappedMemoryHandler;

    /** Table's custom renderer. */
    private MyRenderer2 dataTableRenderer;

    /** Remember comments placed into 7th column of table. */
    private HashMap<Integer,String> comments;

    /** Offset in bytes from beginning of file to
     * beginning of map currently being viewed. */
    private long wordOffset;

    /** Index to map we are using. */
    private int mapIndex;

    /** Index to last word in file (first = 0). */
    private long maxWordIndex;

    /** Size of file in bytes. */
    private long fileSize;

    /** Number of memory maps used for this file. */
    private int mapCount;

    /** Number of words in each table row. */
    private final int wordsPerRow = 5;

    /** Number of bytes in each table row. */
    private final int bytesPerRow = 20;

    /** Max number of bytes per memory map. */
    private long maxMapByteSize;

    /** Max number of rows per memory map. */
    private int  maxRowsPerMap;

    /** Max number of words (32 bit ints) per memory map. */
    private long maxWordsPerMap;

    /** Total number of rows currently in table. */
    private long totalRows;

    /** Is the data taken from a memory mapped file or not? */
    private boolean dataFromFile = false;

    /** The evio version of the file being viewed. */
    private int evioVersion = 6;


    /** Column names. */
    String[] names = {"Word Position", "+1", "+2", "+3", "+4", "+5", "Comments"};
    String[] columnNames = {names[0], names[1], names[2],
                            names[3], names[4], names[5],
                            names[6]};

    /** Store original data here for convenience
     *  for the Evio event tree display in EvenTreePanel. */
    Object[][] data;



    /** Constructor used for viewing event in tree form in EventTreePanel. */
    public MyTableModel2() {
        this.comments = new HashMap<Integer,String>();
    }

    /** Constructor used for viewing event in tree form in EventTreePanel. */
    public MyTableModel2(int version) {
        evioVersion = version;
        this.comments = new HashMap<Integer,String>();
    }


    /** Constructor used for viewing memory mapped file in FileFrame. */
    public MyTableModel2(SimpleMappedMemoryHandler mappedMemoryHandler,
                         HashMap<Integer,String> comments, int version) {

        evioVersion = version;
        this.fileSize = mappedMemoryHandler.getFileSize();
        this.mappedMemoryHandler = mappedMemoryHandler;
        this.comments = comments;

        // Min file size = 4 bytes
        if (mappedMemoryHandler.haveExtraBytes()) {
            maxWordIndex = (fileSize-4L)/4L + 1L;
        }
        else {
            maxWordIndex = (fileSize-4L)/4L;
        }
        mapCount = mappedMemoryHandler.getMapCount();
        maxMapByteSize = mappedMemoryHandler.getMaxMapSize();
        maxWordsPerMap = maxMapByteSize/4;
        maxRowsPerMap  = (int) (maxWordsPerMap/wordsPerRow);
        dataFromFile = true;
    }

    public void setTableRenderer(MyRenderer2 dataTableRenderer) {
        this.dataTableRenderer = dataTableRenderer;
    }

    /**
     * Get the evio version of the file being viewed.
     * @return evio version of the file being viewed.
     */
    public int getEvioVersion() {return evioVersion;}

    /**
     * Set the evio version of the file being viewed.
     * @param version evio version of the file being viewed.
     */
    public void setEvioVersion(int version) {evioVersion = version;}

    /**
     * Get the size of the file in bytes.
     * @return size of the file in bytes.
     */
    public long getFileSize() { return fileSize; }

    /**
     * Get the mapped memory handler object.
     * @return mapped memory handler object.
     */
    public SimpleMappedMemoryHandler getMemoryHandler() { return mappedMemoryHandler; }

    /**
     * Get the size of the file in bytes.
     * @return size of the file in bytes.
     */
    public ByteOrder order() { return mappedMemoryHandler.getOrder(); }

    /**
     * Get the max # of rows per map.
     * @return max # of rows per map.
     */
    public int getMaxRowsPerMap() { return maxRowsPerMap; }

    /**
     * Get the # of memory maps.
     * @return # of memory maps.
     */
    public int getMapCount() {
        return mapCount;
    }

    /**
     * Get the total # of rows in file. This takes into account
     * the situation in which there are 1, 2, or 3 "left over"
     * bytes at the end of the file.
     * @return total # of rows in file.
     */
    public long getTotalRows() {
        // Only calculate this once
        if (totalRows < 1) {
            totalRows = fileSize/bytesPerRow;
            // May need to round up if the last row is not full
            int addOne = fileSize % bytesPerRow > 0 ? 1 : 0;
            totalRows += addOne;
        }

        return totalRows;
    }

    /**
     * Get the percentage (or progress) of the way through the file that the given row is.
     * @param currentRow the current row in current map (starting at 0)
     * @return percentage of the way through the file that the given row is.
     */
    public int getRowProgress(int currentRow) {
        long rowFromBeginning = mapIndex*maxRowsPerMap + currentRow;
        int percent = (int) (100 * rowFromBeginning/getTotalRows());
        return Math.min(percent, 100);
    }

    /**
     * Set data to next map if there is one, but
     * do not refresh table view.
     * @return {@code true} if next map loaded,
     *         else {@code false} if already using last map.
     */
    public boolean nextMap() {
        if (mapIndex == mapCount -1) {
            return false;
        }

        mapIndex++;
        wordOffset = mapIndex*maxWordsPerMap;
        return true;
    }

    /**
     * Set data to previous map if there is one, but
     * do not refresh table view.
     * @return {@code true} if previous map loaded,
     *         else {@code false} if already using first map.
     */
    public boolean previousMap() {
        if (mapIndex == 0) {
            return false;
        }

        mapIndex--;
        wordOffset = mapIndex*maxWordsPerMap;
        return true;
    }

    /**
     * Get the index of the currently used memory map.
     * @return index of the currently used memory map.
     */
    public int getMapIndex() {
        return mapIndex;
    }

    /**
     * Set the index of the desired memory map and
     * refresh table view.
     * @param mi index of the desired memory map.
     */
    public void setMapIndex(int mi) {
        if (mi == mapIndex) return;
        mapIndex = mi;
        wordOffset = mapIndex*maxWordsPerMap;
        fireTableDataChanged();
    }

    /** Refresh view of table. */
    public void dataChanged() {fireTableDataChanged();}

    /**
     * Is the given column one which contains data or not?
     * @param col column index
     * @return true if col contains data, else false
     */
    public boolean isDataColumn(int col) {
        return  (col > 0 && col < getColumnCount() - 1);
    }

    /**
     * Set the table's data to the map containing the given word
     * and refresh view.
     * @param wordIndex index to the word to view.
     */
    public void setWindowData(long wordIndex) {
        long oldMapIndex = mapIndex;

        mapIndex = mappedMemoryHandler.getMapIndex(wordIndex);

        if (oldMapIndex == mapIndex) {
            return;
        }
        fireTableDataChanged();
    }

    /**
     * Get the map index, row, and column of the given word
     * and refresh the view.
     * @param wordIndex index to the word.
     * @return array of 3 ints, 1st is map index, 2nd is row, 3rd is column.
     *         Return null if index out of bounds
     */
    public int[] getMapRowCol(long wordIndex) {
        if (wordIndex > maxWordIndex || wordIndex < 0) return null;

        int[] dat = new int[3];

        // map index
        dat[0] = (int) (wordIndex*4/maxMapByteSize);

        int  byteIndex = (int) (wordIndex*4 - (dat[0] * maxMapByteSize));
        // row
        dat[1] =  byteIndex / bytesPerRow;
        // column
        dat[2] = (byteIndex % bytesPerRow)/4 + 1;

        return dat;
    }

    /**
     * Highlight the given row & col entry, then refresh view.
     * @param color   color of highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     */
    public void highLightCell(Color color, int row, int col, boolean isError) {
        dataTableRenderer.setHighlightCell(color, row, col, isError);
        fireTableCellUpdated(row, col);
    }

    /**
     * Highlight an event header. The given row & col are for
     * the first word of the header. Refresh view.
     * @param color   color of highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     */
    public void highLightEventHeader(Color color, int row, int col, boolean isError) {
        // Highlight starting point
        dataTableRenderer.setHighlightCell(color, row, col, isError);
        fireTableCellUpdated(row, col);
        // Also highlight the next (tag/num) header word, but
        // not if we're starting at pos = 0, col 0
        if (col != 0) {
            long pos  = getWordIndexOf(row, col);
            int[] mrc = getMapRowCol(pos + 1);
            if (mrc == null) return;
            setMapIndex(mrc[0]);
            dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
            fireTableCellUpdated(mrc[1], mrc[2]);
        }
    }


    /**
     * Highlight a block header. The given row & col are for
     * the magic # word of the header. Refresh view.
     * @param color   color of highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     * @return int array containing all the block header values; null if error
     */
    public int[] highLightBlockHeader(Color color, int row, int col, boolean isError) {
        // 8 words size of evio header in versions < 6
        int headerSize = 8;
        int after = 0;

        // In version 6 ...
        if (evioVersion > 5) {
            // The magic # is 8th word, but header is 14 words
            headerSize = 14;
            // 7 prior and 6 after the magic #.
            after = 6;
        }

        int[] blockData = new int[headerSize];
        // Index of the magic # found in search
        long magicNumIndex = getWordIndexOf(row,col);
        // Index of last word of block header
        long lastIndex = magicNumIndex + after;

        for (int i=0; i<headerSize; i++) {
            blockData[headerSize-i-1] = (int)getLongValueAt(lastIndex - i);
            int[] mrc = getMapRowCol(lastIndex - i);
            if (mrc == null) {
                // Undo our recent highlighting
                for (int j=0; j<i; j++) {
                    int[] mrc2 = getMapRowCol(lastIndex - j);
                    setMapIndex(mrc2[0]);
                    dataTableRenderer.removeHighlightCell(mrc2[1], mrc2[2], isError);
                    fireTableCellUpdated(mrc2[1], mrc2[2]);
                }
                return null;
            }
            setMapIndex(mrc[0]);
            dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
            fireTableCellUpdated(mrc[1], mrc[2]);
        }
        return blockData;
    }


    /**
     * Enter block header position into highlight hashmap for future highlighting.
     * The given position is for the first word of the header.
     * @param color   color of highlight
     * @param pos     byte index of beginning of block header
     * @param isError true if highlighting an error
     */
    public void highLightBlockHeader(Color color, long pos, boolean isError) {
        // 8 words size of evio header in versions < 6, 14 words for versions 6+
        int headerSize = evioVersion > 5 ? 14 : 8;

        for (int i=0; i<headerSize; i++) {
            dataTableRenderer.setHighlightCell(color, pos + 4*i, isError);
        }
    }


    /**
     * Enter event header position into highlight hashmap for future highlighting.
     * The given position is for the first word of the header.
     * @param color   color of highlight
     * @param pos     byte index of beginning of event header
     * @param isError true if highlighting an error
     */
    public void highLightEventHeader(Color color, long pos, boolean isError) {
        for (int i=0; i<2; i++) {
            dataTableRenderer.setHighlightCell(color, pos + 4*i, isError);
        }
    }


    /** Clear all highlights and refresh view. */
    public void clearHighLights() {dataTableRenderer.clearHighlights();}


    public void setTableData(String[] dataArg) {
        if (dataArg == null || dataArg.length < 1) {
            data = null;
            fireTableDataChanged();
            return;
        }

        // Don't count first and last cols (word-position and comments)
        int colCount = columnNames.length - 2;
        int rowCount = dataArg.length / colCount;
        rowCount = dataArg.length % colCount > 0 ? rowCount + 1 : rowCount;
        data = new Object[rowCount][colCount];
        int counter = 0, dataCount = dataArg.length;

        out:
        for (int row=0; row < rowCount; row++) {
            for (int col=0; col < colCount; col++) {
                data[row][col] = dataArg[counter];
//System.out.println("setTableData:  data[" + row + "][" + col + "] = " + dataArg[counter]);
                if (++counter >= dataCount) {
                    break out;
                }
            }
        }

        fireTableDataChanged();
    }


    /** {@inheritDoc} */
    public int getColumnCount() {return columnNames.length;}

    /** {@inheritDoc} */
    public int getRowCount() {
        if (!dataFromFile) {
            if (data == null) return 0;
            return data.length;
        }

        if (mappedMemoryHandler == null) return 0;

        // All maps are maxMapByteSize bytes in size except
        // the last one which may be any non-zero size.
        int mapCount = mappedMemoryHandler.getMapCount();

        if (mapIndex == mapCount - 1) {
            // Need to round up since the last row may not be "full"
            int mapSize = mappedMemoryHandler.getMapSize(mapIndex);
            int addOne = mapSize % bytesPerRow > 0 ? 1 : 0;
            return mapSize/bytesPerRow + addOne;
        }
        return maxRowsPerMap;
    }

    /** {@inheritDoc} */
    public String getColumnName(int col) {
        return columnNames[col];
    }

    /** {@inheritDoc} */
    public Object getValueAt(int row, int col) {
        if (row < 0) {
            return "";
        }

        // 1st column is wordIndex # just before 1st col
        if (col <= 0) {
            return String.format("%,d", (wordOffset + row*5));
        }

        // Remember comments are placed into 7th column
        if (col == 6) {
            if ((comments == null) || comments.size() < row+1) {
                return "";
            }
            return comments.get(row);
        }

        if (!dataFromFile) {
            // Remember that the first col of the table is the position and not the data
            if (data != null) return data[row][col - 1];
            return "";
        }

        long index = wordOffset + (row * 5) + col - 1;

        if (index > maxWordIndex) {
            //System.out.println("\nIndex (" + index + ") is > maxWordIndex !!!\n");
            return "";
        }
        else if (index == maxWordIndex) {
            // If we're looking at the very last bits of data,
            // make adjustments if it does not end on a 4-byte boundary.
            switch (mappedMemoryHandler.getExtraByteCount()) {
                case 3:
                    return String.format("0x%06x", mappedMemoryHandler.getInt(index) & 0xffffff);
                case 2:
                    return String.format("0x%04x", mappedMemoryHandler.getInt(index) & 0xffff);
                case 1:
                    return String.format("0x%02x", mappedMemoryHandler.getInt(index) & 0xff);
                default:
                    // Fall down to last statement
            }
        }

        // Value of cell is in memory map handler
        return String.format("0x%08x", mappedMemoryHandler.getInt(index));
    }

    /**
     * Get the word value of the given row and column.
     * @param row   row
     * @param col   column
     * @return word value
     */
    public long getLongValueAt(int row, int col) {
        // 1st column is row or integer #
        if (row < 0 || col == 0 || col == 6) {
            return 0;
        }

        return (getLongValueAt(wordOffset + (row * 5) + col - 1));
    }

    /**
     * Get the long value at the given file word index.
     * @param wordIndex file word index.
     * @return long value
     */
    public long getLongValueAt(long wordIndex) {
        if (wordIndex < 0 || wordIndex > maxWordIndex) {
            return 0;
        }

        return (((long)mappedMemoryHandler.getInt(wordIndex)) & 0xffffffffL);
    }

    /**
     * Get the int value at the given file byte index.
     * @param byteIndex file byte index.
     * @return int value
     */
    public int getInt(long byteIndex) {
        if (byteIndex < 0 || byteIndex > fileSize - 1) {
            return 0;
        }

        return (mappedMemoryHandler.getIntAtBytePos(byteIndex));
    }

    /**
     * Get the short value at the given file byte index.
     * @param byteIndex file byte index.
     * @return short value
     */
    public int getShort(long byteIndex) {
        if (byteIndex < 0 || byteIndex > fileSize - 1) {
            return 0;
        }

        return (mappedMemoryHandler.getShortAtBytePos(byteIndex));
    }

    /**
     * Get the byte value at the given file byte index.
     * @param byteIndex file byte index.
     * @return byte value
     */
    public int get(long byteIndex) {
        if (byteIndex < 0 || byteIndex > fileSize - 1) {
            return 0;
        }

        return (mappedMemoryHandler.getByteAtBytePos(byteIndex));
    }

    /**
     * Get the file word index of the entry at the given row and column.
     * @param row   row
     * @param col   column
     * @return file word index
     */
    public long getWordIndexOf(int row, int col) {
        // 1st column is row or integer #
        if (col == 0 || col == 6) {
            return 0;
        }

        return wordOffset + (row * 5) + col - 1;
    }

    /** {@inheritDoc} */
    public Class getColumnClass(int c) {
        if (c == 0) return Long.class;
        return String.class;
    }

    /** {@inheritDoc} */
    public boolean isCellEditable(int row, int col) {
        return col > 0;
    }

    /** {@inheritDoc}. Only used to set comments. */
    public void setValueAt(Object value, int row, int col) {
        if (col == 6) {
            comments.put(row, (String)value);
        }
        fireTableCellUpdated(row, col);
    }
}

