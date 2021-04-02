package org.jlab.coda.eventViewer.test;

import org.jlab.coda.hipo.*;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class ReadWriteTest {



    static String  xmlDict =
            "<xmlDict>\n" +
            "  <bank name=\"HallD\"             tag=\"6-8\"  type=\"bank\" >\n" +
            "      <description format=\"New Format\" >hall_d_tag_range</description>\n" +
            "      <bank name=\"DC(%t)\"        tag=\"6\" num=\"4\" >\n" +
            "          <leaf name=\"xpos(%n)\"  tag=\"6\" num=\"5\" />\n" +
            "          <bank name=\"ypos(%n)\"  tag=\"6\" num=\"6\" />\n" +
            "      </bank >\n" +
            "      <bank name=\"TOF\"     tag=\"8\" num=\"0\" >\n" +
            "          <leaf name=\"x\"   tag=\"8\" num=\"1\" />\n" +
            "          <bank name=\"y\"   tag=\"8\" num=\"2\" />\n" +
            "      </bank >\n" +
            "      <bank name=\"BCAL\"      tag=\"7\" >\n" +
            "          <leaf name=\"x(%n)\" tag=\"7\" num=\"1-3\" />\n" +
            "      </bank >\n" +
            "  </bank >\n" +
            "  <dictEntry name=\"JUNK\" tag=\"5\" num=\"0\" />\n" +
            "  <dictEntry name=\"SEG5\" tag=\"5\" >\n" +
            "       <description format=\"Old Format\" >tag 5 description</description>\n" +
            "  </dictEntry>\n" +
            "  <bank name=\"Rangy\" tag=\"75 - 78\" >\n" +
            "      <leaf name=\"BigTag\" tag=\"76\" />\n" +
            "  </bank >\n" +
            "</xmlDict>\n";


    /**
     * Write shorts.
     * @param size number of SHORTS
     * @param order byte order of shorts in memory
     * @return
     */
    static byte[] generateSequentialShorts(int size, ByteOrder order) {
        short[] buffer = new short[size];

        for (int i = 0; i < size; i++) {
            buffer[i] = (short)i;
        }

        byte[] bArray = null;
        try {
            bArray = ByteDataTransformer.toBytes(buffer, order);
        }
        catch (EvioException e) {
            e.printStackTrace();
        }

        return bArray;
    }


    // Create a fake Evio Event
    static ByteBuffer generateEvioBuffer(ByteOrder order, int dataWords) {

        // Create an evio bank of banks, containing a bank of ints
        ByteBuffer evioDataBuf = ByteBuffer.allocate(16 + 4*dataWords);
        evioDataBuf.order(order);
        evioDataBuf.putInt(3+dataWords);  // event length in words

        int tag  = 0x1234;
        int type = 0x10;  // contains evio banks
        int num  = 0x12;
        int secondWord = tag << 16 | type << 8 | num;

        evioDataBuf.putInt(secondWord);  // 2nd evio header word

        // now put in a bank of ints
        evioDataBuf.putInt(1+dataWords);  // bank of ints length in words
        tag = 0x5678; type = 0x1; num = 0x56;
        secondWord = tag << 16 | type << 8 | num;
        evioDataBuf.putInt(secondWord);  // 2nd evio header word

        // Int data
        for (int i=0; i < dataWords; i++) {
            evioDataBuf.putInt(i);
        }

        evioDataBuf.flip();
        return evioDataBuf;
    }





    static String eventWriteFileMT(String filename) {

        // Variables to track record build rate
        long loops = 6;


        String dictionary = null;

        byte[] firstEvent = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int firstEventLen = 10;
        boolean addTrailerIndex = true;
        //ByteOrder order = ByteOrder.BIG_ENDIAN;
        ByteOrder order = ByteOrder.LITTLE_ENDIAN;
        //CompressionType compType = CompressionType.RECORD_COMPRESSION_GZIP;
        CompressionType compType = CompressionType.RECORD_UNCOMPRESSED;

        // Create files
        String directory = null;
        int runNum = 123;
        long split = 0; // 1K
        int maxRecordSize = 0; // 0 -> use default
        int maxEventCount = 4; // 0 -> use default
        boolean overWriteOK = true;
        boolean append = false;
        int streamId = 3;
        int splitNumber = 2;
        int splitIncrement = 1;
        int streamCount = 2;

//        int compThreads = 2;
//        int ringSize = 16;

        int compThreads = 1;
        int ringSize = 1;
        int bufSize = 1;

        try {
            EventWriterUnsync writer = new EventWriterUnsync(filename, directory, "runType",
                                                             runNum, split, maxRecordSize, maxEventCount,
                                                             order, xmlDict, overWriteOK, append,
                                                             null, streamId, splitNumber, splitIncrement, streamCount,
                                                             compType, compThreads, ringSize, bufSize);


            //  When appending, it's possible the byte order gets switched
            order = writer.getByteOrder();

            // Create an event containing a bank of ints (100 bytes)
            ByteBuffer evioDataBuf = generateEvioBuffer(order, 184);

            do {
                // event in evio format
                writer.writeEvent(evioDataBuf);

            } while (--loops >= 1);

            writer.close();
            System.out.println("Past close");
            System.out.println("Finished writing file "+ writer.getCurrentFilename());

            return writer.getCurrentFilename();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }




    public static void main(String args[]) {
        String filename = "/Users/timmer/coda/evioDataFiles/GuiTest.evio";
        System.out.println("Try writing " + filename);

        // Write files
        String actualFilename = ReadWriteTest.eventWriteFileMT(filename);
        System.out.println("Finished writing, now try reading n" + actualFilename);

        System.out.println("----------------------------------------\n");
    }



};




