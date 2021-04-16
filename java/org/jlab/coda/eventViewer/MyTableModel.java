package org.jlab.coda.eventViewer;

import org.jlab.coda.hipo.RecordHeader;
import org.jlab.coda.jevio.Utilities;

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
final class MyTableModel extends AbstractTableModel {

    /** Buffer of memory mapped file. */
    private SimpleMappedMemoryHandler mappedMemoryHandler;

    /** Table's custom renderer. */
    private MyRenderer dataTableRenderer;

    /** Remember comments placed into 7th column of table. */
    private HashMap<String,String> comments;

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
    public MyTableModel() {
        this.comments = new HashMap<String,String>();
    }

    /** Constructor used for viewing event in tree form in EventTreePanel. */
    public MyTableModel(int version) {
        evioVersion = version;
        this.comments = new HashMap<String,String>();
    }


    /** Constructor used for viewing memory mapped file in FileFrame. */
    public MyTableModel(SimpleMappedMemoryHandler mappedMemoryHandler,
                        HashMap<String,String> comments, int version) {

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

    public void setTableRenderer(MyRenderer dataTableRenderer) {
        this.dataTableRenderer = dataTableRenderer;
    }

    /**
     * Set the label of the first column of data table.
     * @param label label of the first column of data table.
     */
    public void setFirstColLabel(String label) {
        names[0] = label;
        columnNames[0] = label;
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

        if (evioVersion > 5) {
            // Ignore the file header and the beginning of the first record header
            long entryIndex = getWordIndexOf(row,col);
            if (mapIndex == 0 && entryIndex < mappedMemoryHandler.getFirstDataIndex()) {
                return;
            }
        }

        // Highlight starting point
        dataTableRenderer.setHighlightCell(color, row, col, isError);
        fireTableCellUpdated(row, col);
        // Also highlight the next (tag/num) header word, but
        // not if we're starting in non-data col
        if (isDataColumn(col)) {
            long pos  = getWordIndexOf(row, col);
            int[] mrc = getMapRowCol(pos + 1);
            if (mrc == null) return;
            setMapIndex(mrc[0]);
            dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
            fireTableCellUpdated(mrc[1], mrc[2]);
        }
    }


    /**
     * Remove the highlight of given table entry along with the next one.
     * Refresh view.
     * @param row     row
     * @param col     column
     */
    public void clearHighLightEventHeader(int row, int col) {
        long entryIndex = getWordIndexOf(row,col);

        if (evioVersion > 5) {
            // Ignore the file header and the beginning of the first record header
            if (mapIndex == 0 && entryIndex < mappedMemoryHandler.getFirstDataIndex()) {
                return;
            }
        }

        // First the chosen cell
        dataTableRenderer.removeHighlightCell(row, col, false);
        fireTableCellUpdated(row, col);

        // Now the next cell
        entryIndex++;
        int[] mrc = getMapRowCol(entryIndex);
        if (mrc == null) {
            return;
        }
        setMapIndex(mrc[0]);
        dataTableRenderer.removeHighlightCell(mrc[1], mrc[2], false);
        fireTableCellUpdated(mrc[1], mrc[2]);
    }


    /**
     * Highlight a file header. This is only valid for evio version 6.
     * It's always at the very beginning of the file. Refresh view.
     * This is done once for each file.
     *
     * @param color1   color of highlight for 14 word header
     * @param color2   color of highlight for header's index
     * @param color3   color of highlight for user header
     * @param headerWords  number of 32bit words the the file header proper.
     * @param indexWords   number of 32bit words the the file header's index array.
     * @param userHdrWords number of 32bit words the the file header's user header (including padding).
     */
    public void highLightFileHeader(Color color1, Color color2, Color color3,
                                    int headerWords, int indexWords, int userHdrWords) {
        if (evioVersion < 6) {
            return;
        }

        // For evio 6 we want to light the 14 word header differently from
        // the index array and differently from the user header.
        // All will be in related colors.

        // How many rows are we talking about?
        int totalWords = headerWords + indexWords + userHdrWords;
        int totalRows = totalWords / wordsPerRow;

        // Always in first map
        setMapIndex(0);

        // First the 14 word header
        for (int i = 0; i < headerWords; i++) {
            int[] mrc = getMapRowCol(i);
            if (mrc == null) {
                return;
            }
            dataTableRenderer.setHighlightCell(color1, mrc[1], mrc[2], false);
        }

        // Second the index
        for (int i = headerWords; i < headerWords+indexWords; i++) {
            int[] mrc = getMapRowCol(i);
            if (mrc == null) {
                return;
            }
            dataTableRenderer.setHighlightCell(color2, mrc[1], mrc[2], false);
        }

        // Finally the user header
        for (int i = headerWords+indexWords; i < totalWords; i++) {
            int[] mrc = getMapRowCol(i);
            if (mrc == null) {
                return;
            }
            dataTableRenderer.setHighlightCell(color3, mrc[1], mrc[2], false);
        }

        // Put comment in first map, second row
        comments.put("0:1", "File Header");
        fireTableRowsUpdated(0, totalRows-1);
    }


    /**
     * Highlight a block header. The given row & col are for
     * the magic # word of the header. Refresh view.
     * This is used only for evio version < 6.
     *
     * @param color   color of highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     * @return int array containing all the block header values; null if error or file header
     */
    public int[] highLightBlockHeader(Color color, int row, int col, boolean isError) {
        // 8 words size of evio header in versions < 6
        int headerSize = 8;

        int[] blockData = new int[headerSize];
        // Index of the magic # found in search =
        // index of last word of block header
        long lastIndex = getWordIndexOf(row,col);

        for (int i = 0; i < headerSize; i++) {
            blockData[headerSize - i - 1] = (int) getLongValueAt(lastIndex - i);
            int[] mrc = getMapRowCol(lastIndex - i);
            if (mrc == null) {
                return null;
            }

            setMapIndex(mrc[0]);
            dataTableRenderer.setHighlightCell(color, mrc[1], mrc[2], isError);
            fireTableCellUpdated(mrc[1], mrc[2]);
        }
        return blockData;
    }


    /**
     * Highlight a block header. The given row & col are for
     * the magic # word of the header. Refresh view.
     * This used only for evio version 6+ as account must be made for file header.
     *
     * @param color1   color of highlight
     * @param row     row
     * @param col     column
     * @param isError true if highlighting an error
     * @return int array containing all the block header values; null if error or file header
     */
    public int[] highLightBlockHeader(Color color1, Color color2, Color color3,
                                      int row, int col, boolean isError) {

        // Index of the magic # found in search
        long magicNumIndex = getWordIndexOf(row,col);

        // If at beginning of file, skip over the file header
        // and the beginning of the first record header
//System.out.println("highLightBlockHeader: magicIndex = " + magicNumIndex + ", min acceptable index = " +
//((mappedMemoryHandler.getTotalFileHeaderBytes() + 28)/4));
        if (mapIndex == 0 && magicNumIndex < (mappedMemoryHandler.getTotalFileHeaderBytes() + 28)/4) {
            return null;
        }

        boolean dataCompressed = mappedMemoryHandler.isCompressed();
        boolean isTrailer = false;
        long trailerPos = mappedMemoryHandler.getFileHeader().getTrailerPosition();
        if ((trailerPos != 0) && (trailerPos + RecordHeader.MAGIC_OFFSET <= 4L*magicNumIndex)) {
            isTrailer = true;
        }

        // For evio 6 we want to light the 14 word header differently from
        // the index array and differently from the user header.
        // All will be in related colors.

        // Theoretically the usually 14 word header can be longer.
        // So don't assume anything and read its actual size.
        int headerSize = (int) getLongValueAt(magicNumIndex - 5);
        // Index of last word of record header
        long lastIndex = magicNumIndex + 6 + (headerSize - 14);
        int[] blockData = new int[headerSize];

        // First do the regular header
        for (int i = 0; i < headerSize; i++) {
            blockData[headerSize - i - 1] = (int) getLongValueAt(lastIndex - i);
            int[] mrc = getMapRowCol(lastIndex - i);
            if (mrc == null) {
                return null;
            }
            setMapIndex(mrc[0]);
            dataTableRenderer.setHighlightCell(color1, mrc[1], mrc[2], isError);
            fireTableCellUpdated(mrc[1], mrc[2]);
        }

        // If data is compressed, so is the index array and user header.
        // Trailer is never compressed.
        if (!dataCompressed || isTrailer) {
            // Next the index array whose length is always a multiple of 4
            int indexWords = blockData[RecordHeader.INDEX_ARRAY_OFFSET / 4] / 4;
            lastIndex += indexWords;
            for (int i = 0; i < indexWords; i++) {
                int[] mrc = getMapRowCol(lastIndex - i);
                if (mrc == null) {
                    return null;
                }
                setMapIndex(mrc[0]);
                dataTableRenderer.setHighlightCell(color2, mrc[1], mrc[2], isError);
                fireTableCellUpdated(mrc[1], mrc[2]);
            }

            // Finally the user header, the length which we round up to the
            // nearest 4-byte boundary to include padding.
            int userHdrWords = Utilities.getWords(blockData[RecordHeader.USER_LENGTH_OFFSET / 4]);
            lastIndex += userHdrWords;
            for (int i = 0; i < userHdrWords; i++) {
                int[] mrc = getMapRowCol(lastIndex - i);
                if (mrc == null) {
                    return null;
                }
                setMapIndex(mrc[0]);
                dataTableRenderer.setHighlightCell(color3, mrc[1], mrc[2], isError);
                fireTableCellUpdated(mrc[1], mrc[2]);
            }
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
            if (comments == null) return "";
            String key = mapIndex + ":" + row;
            if (comments.containsKey(key)) return comments.get(key);
            return "";
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
     * @return file word index or 0 if first/last column.
     */
    public long getWordIndexOf(int row, int col) {
        // 1st column is row or integer #
        if (col == 0 || col == 6) {
            return 0;
        }

        return wordOffset + (row * 5) + col - 1;
    }

    /**
     * Get the closest file word index of the entry at the given row and column.
     * This accounts for the first and last columns which don't contain any data.
     * Those columns will be treated as the last data-containing column.
     * Row set accordingly.
     *
     * @param row   row
     * @param col   column
     * @return file word index or 0 if column = 0 and row = 0.
     */
    public long getClosestWordIndexOf(int row, int col) {
        if (row == 0  &&  col == 0) return 0;

        // 1st column is treated as last row's column 5
        if (col == 0) {
            col = 5;
            row--;
        }
        // Last column is treated as current row's column 5
        else if (col == 6) {
            col = 5;
        }

        return wordOffset + (row * 5) + col - 1;
    }

    /**
     * Get the closest, data-containing, row and col for the given row and column.
     * This accounts for the first and last columns which don't contain any data.
     * Those columns will be treated as the last data-containing column.
     * Row set accordingly.
     *
     * @param row   row
     * @param col   column
     * @return array containing array[0] = row, and array[1] = col of
     *         the closest, data-containing, row/col for the given row/column.
     */
    public int[] getClosestDataRowCol(int row, int col) {
        int[] rc = new int[2];

        if (row == 0  &&  col == 0) {
            // Already zeroed
            return rc;
        }

        rc[0] = row;
        rc[1] = col;

        // 1st column is treated as last row's column 5
        if (col == 0) {
            rc[1] = 5;
            rc[0] = row - 1;
        }
        // Last column is treated as current row's column 5
        else if (col == 6) {
            rc[1] = 5;
        }

        return rc;
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
            comments.put(mapIndex + ":" + row, (String)value);
        }
        fireTableCellUpdated(row, col);
    }
}

