package org.jlab.coda.eventViewer.test;

import org.jlab.coda.hipo.CompressionType;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/**
 * Test program.
 * @author timmer
 * Apr 14, 2021
 */
public class CompactBuilderTest {

    static String  xmlDict =
            "<xmlDict>\n" +
                    "  <bank name=\"HallD\"             tag=\"6-8\"  type=\"bank\" >\n" +
                    "      <description format=\"New Format\" >hall_d_tag_range</description>\n" +
                    "      <bank name=\"DC(%t)\"        tag=\"6\" num=\"4\" >\n" +
                    "          <leaf name=\"xpos(%n)\"  tag=\"6\" num=\"5\" />\n" +
                    "          <bank name=\"ypos(%n)\"  tag=\"6\" num=\"6\" />\n" +
                    "      </bank >\n" +
                    "      <bank name=\"BCAL\"      tag=\"7\" >\n" +
                    "          <leaf name=\"x(%n)\" tag=\"7\" num=\"1-3\" />\n" +
                    "      </bank >\n" +
                    "  </bank >\n" +
                    "  <dictEntry name=\"SEG5\" tag=\"5\" >\n" +
                    "       <description format=\"Old Format\" >tag 5 description</description>\n" +
                    "  </dictEntry>\n" +
                    "</xmlDict>\n";


    int[]    int1;
    byte[]   byte1;
    short[]  short1;
    long[]   long1;
    float[]  float1;
    double[] double1;
    String[] string1;

    int dataElementCount = 203;
    int bufSize = 2000000;

    ByteBuffer buffer;

    // files for input & output
    //String writeFileName = "/daqfs/home/timmer/coda/evioTestFiles/compactEvioBuild.ev";
    String writeFileName = "/Users/timmer/coda/evioDataFiles/compactEvioBuild.ev";
    ByteOrder order = ByteOrder.BIG_ENDIAN;


    /**
     * Method to decode the command line used to start this application.
     * @param args command line arguments
     */
    private void decodeCommandLine(String[] args) {

        // loop over all args
        for (int i = 0; i < args.length; i++) {

            if (args[i].equalsIgnoreCase("-h")) {
                usage();
                System.exit(-1);
            }
            else if (args[i].equalsIgnoreCase("-count")) {
                dataElementCount = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-size")) {
                bufSize = Integer.parseInt(args[i + 1]);
System.out.println("SET buf size to " + bufSize);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-f")) {
                writeFileName = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-little")) {
                order = ByteOrder.LITTLE_ENDIAN;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
            "   java CompactBuilderTest\n" +
            "        [-count <elements>]  number of data elements of each type\n"+
            "        [-size <buf size>]   use buffer of this size (bytes)\n" +
            "        [-f <file>]          output to file\n" +
            "        [-little]            use little endian buffer\n" +
            "        [-h]                 print this help\n");
    }




    public CompactBuilderTest(String[] args) throws Exception {

        decodeCommandLine(args);


        int tag=1, num=1;
        byte[] array = new byte[bufSize];
        buffer = ByteBuffer.wrap(array);
        buffer.order(order);


        System.out.println("Running with:");
        System.out.println("  arraySize = " + dataElementCount);
        System.out.println("    bufSize = " + bufSize);



        EventWriter writer = new EventWriter(writeFileName, null, null, 1, 0,
                0, 2, order, xmlDict, true, false, null,
                1, 1, 1, 1, CompressionType.RECORD_UNCOMPRESSED,
                1, 8, 0);

        // Different data for each event

        setDataSize(dataElementCount);
        ByteBuffer ev = createCompactEvent(tag, num);
        writer.writeEvent(ev);

        setDataSize(dataElementCount);
        ev = createCompactEvent(tag, num);
        writer.writeEvent(ev);

        setDataSize(dataElementCount);
        ev = createCompactEvent(tag, num);
        writer.writeEvent(ev);
        writer.close();

    }


    /** For WRITING a local file. */
    public static void main(String[] args) {
        try {
            new CompactBuilderTest(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    void setDataSize(int elementCount) {

        int1    = new int   [elementCount];
        byte1   = new byte  [elementCount];
        short1  = new short [elementCount];
        long1   = new long  [elementCount];
        float1  = new float [elementCount];
        double1 = new double[elementCount];
        string1 = new String[elementCount];

        Random rand = new Random();

        for (int i=0; i < elementCount; i++) {
            int1[i]    = rand.nextInt(Integer.MAX_VALUE);
            byte1[i]   = (byte)  rand.nextInt(Integer.MAX_VALUE);
            short1[i]  = (short) rand.nextInt(Integer.MAX_VALUE);
            long1[i]   = rand.nextLong();
            float1[i]  = rand.nextFloat() * i;
            double1[i] = rand.nextDouble() * i;
            string1[i] = "hey" + (i+1);
        }

        for (int i=0; i < elementCount; i++) {
            int1[i]    = 0xc0da0100;
            byte1[i]   = (byte)  i;
            short1[i]  = (short) i;
            long1[i]   = i;
            float1[i]  = i;
            double1[i] = i;
            string1[i] = "hey" + (i+1);
        }


    }




    /**
     * Example of providing a format string and some data
     * in order to create a CompositeData object.
     */
    public CompositeData  createCompositeData() {

        // Format to write a N shorts, 1 float, 1 double a total of N times
        String format = "N(NS,F,D)";

        // Now create some data
        CompositeData.Data myData = new CompositeData.Data();
        myData.addN(2);
        myData.addN(3);
        myData.addShort(new short[] {1,2,3}); // use array for convenience
        myData.addFloat(1.0F);
        myData.addDouble(Math.PI);
        myData.addN(1);
        myData.addShort((short)4); // use array for convenience
        myData.addFloat(2.0F);
        myData.addDouble(2.*Math.PI);

        // Create CompositeData object
        CompositeData cData = null;
        try {
            cData = new CompositeData(format, 0x11, myData, 0x22 ,0x33);
        }
        catch (EvioException e) {
            e.printStackTrace();
        }

        return cData;
    }



    /** Writing to a buffer using new interface. */
    public ByteBuffer createCompactEvent(int tag, int num) throws Exception {

        CompactEventBuilder builder = new CompactEventBuilder(buffer);

        // add top/event level bank of banks
        builder.openBank(tag, num, DataType.BANK);

        // add bank of banks
        builder.openBank(tag+1, num+1, DataType.BANK);

        // add bank of ints
        builder.openBank(tag+2, num+2, DataType.INT32);
        builder.addIntData(int1);
        builder.closeStructure();

        // add bank of bytes
        builder.openBank(tag + 3, num + 3, DataType.CHAR8);
        builder.addByteData(byte1);
        builder.closeStructure();

        // add bank of shorts
        builder.openBank(tag + 4, num + 4, DataType.SHORT16);
        builder.addShortData(short1);
        builder.closeStructure();

        // add bank of longs
        builder.openBank(tag + 40, num + 40, DataType.LONG64);
        builder.addLongData(long1);
        builder.closeStructure();

        // add bank of floats
        builder.openBank(tag+5, num+5, DataType.FLOAT32);
        builder.addFloatData(float1);
        builder.closeStructure();

        // add bank of doubles
        builder.openBank(tag+6, num+6, DataType.DOUBLE64);
        builder.addDoubleData(double1);
        builder.closeStructure();

        // add bank of strings
        builder.openBank(tag+7, num+7, DataType.CHARSTAR8);
        builder.addStringData(string1);
        builder.closeStructure();

        // add bank of composite
        builder.openBank(tag+7, num+8, DataType.COMPOSITE);
        CompositeData[] cdArray = {createCompositeData()};
        builder.addCompositeData(cdArray);
        builder.closeStructure();

        builder.closeStructure();


        // add bank of segs
        builder.openBank(tag+14, num+14, DataType.SEGMENT);

        // add seg of ints
        builder.openSegment(tag+8, DataType.INT32);
        builder.addIntData(int1);
        builder.closeStructure();

        // add seg of bytes
        builder.openSegment(tag+9, DataType.CHAR8);
        builder.addByteData(byte1);
        builder.closeStructure();

        // add seg of shorts
        builder.openSegment(tag+10, DataType.SHORT16);
        builder.addShortData(short1);
        builder.closeStructure();

        // add seg of longs
        builder.openSegment(tag+40, DataType.LONG64);
        builder.addLongData(long1);
        builder.closeStructure();

        // add seg of floats
        builder.openSegment(tag+11, DataType.FLOAT32);
        builder.addFloatData(float1);
        builder.closeStructure();

        // add seg of doubles
        builder.openSegment(tag+12, DataType.DOUBLE64);
        builder.addDoubleData(double1);
        builder.closeStructure();

        // add seg of strings
        builder.openSegment(tag+13, DataType.CHARSTAR8);
        builder.addStringData(string1);
        builder.closeStructure();

        builder.closeStructure();


        // add bank of tagsegs
        builder.openBank(tag+15, num+15, DataType.TAGSEGMENT);

        // add tagseg of ints
        builder.openTagSegment(tag + 16, DataType.INT32);
        builder.addIntData(int1);
        builder.closeStructure();

        // add tagseg of bytes
        builder.openTagSegment(tag + 17, DataType.CHAR8);
        builder.addByteData(byte1);
        builder.closeStructure();

        // add tagseg of shorts
        builder.openTagSegment(tag + 18, DataType.SHORT16);
        builder.addShortData(short1);
        builder.closeStructure();

        // add tagseg of longs
        builder.openTagSegment(tag + 40, DataType.LONG64);
        builder.addLongData(long1);
        builder.closeStructure();

        // add tagseg of floats
        builder.openTagSegment(tag + 19, DataType.FLOAT32);
        builder.addFloatData(float1);
        builder.closeStructure();

        // add tagseg of doubles
        builder.openTagSegment(tag + 20, DataType.DOUBLE64);
        builder.addDoubleData(double1);
        builder.closeStructure();

        // add tagseg of strings
        builder.openTagSegment(tag + 21, DataType.CHARSTAR8);
        builder.addStringData(string1);
        builder.closeStructure();

        builder.closeAll();
        return builder.getBuffer();
    }



}
