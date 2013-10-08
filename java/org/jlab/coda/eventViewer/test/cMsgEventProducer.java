package org.jlab.coda.jevio.test;

import org.jlab.coda.cMsg.*;
import org.jlab.coda.jevio.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
//import java.nio.IntBuffer;

/**
 * Class for testing EventTreeFrame graphics application with events
 * coming in cMsg messages.
 */
public class cMsgEventProducer {
    private String  subject = "evio";
    private String  type = "anything";
    private String  name = "producer";
    private String  description = "java event producer";
    private String  UDL = "cMsg://localhost/cMsg/myNameSpace";

    private int     delay, count = 50000;
    private boolean debug;


    /** Constructor. */
    cMsgEventProducer(String[] args) {
        decodeCommandLine(args);
    }


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
            else if (args[i].equalsIgnoreCase("-n")) {
                name = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-d")) {
                description = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-u")) {
                UDL= args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-s")) {
                subject = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-t")) {
                type = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-c")) {
                count = Integer.parseInt(args[i + 1]);
                if (count < 1)
                    System.exit(-1);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-delay")) {
                delay = Integer.parseInt(args[i + 1]);
                i++;
            }
            else if (args[i].equalsIgnoreCase("-debug")) {
                debug = true;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        return;
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
            "   java cMsgProducer\n" +
            "        [-n <name>]          set client name\n"+
            "        [-d <description>]   set description of client\n" +
            "        [-u <UDL>]           set UDL to connect to cMsg\n" +
            "        [-s <subject>]       set subject of sent messages\n" +
            "        [-t <type>]          set type of sent messages\n" +
            "        [-c <count>]         set # of messages to send before printing output\n" +
            "        [-delay <time>]      set time in millisec between sending of each message\n" +
            "        [-debug]             turn on printout\n" +
            "        [-h]                 print this help\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            cMsgEventProducer producer = new cMsgEventProducer(args);
            producer.run();
        }
        catch (cMsgException e) {
            System.out.println(e.toString());
            System.exit(-1);
        }
    }


    /**
     * Method to convert a double to a string with a specified number of decimal places.
     *
     * @param d double to convert to a string
     * @param places number of decimal places
     * @return string representation of the double
     */
    private static String doubleToString(double d, int places) {
        if (places < 0) places = 0;

        double factor = Math.pow(10,places);
        String s = "" + (double) (Math.round(d * factor)) / factor;

        if (places == 0) {
            return s.substring(0, s.length()-2);
        }

        while (s.length() - s.indexOf(".") < places+1) {
            s += "0";
        }

        return s;
    }

    /**
     * This class defines the callback to be run when a message matching
     * our subscription arrives.
     */
    class myCallback extends cMsgCallbackAdapter {
        public void callback(cMsgMessage msg, Object userObject) {
            // keep track of how many messages we receive
            //count++;

            System.out.println("Received msg: ");
            System.out.println(msg.toString(true, false, true));
        }
    }


    private ByteBuffer getEvioBuffer(int tag, boolean addDictionary) {
         //--------------------------------------
        // create an array of simple evio events
        //--------------------------------------

        ByteBuffer myBuf = ByteBuffer.allocate(10000);
        myBuf.order(ByteOrder.LITTLE_ENDIAN);

        // xml dictionary
        String dictionary =
                "<xmlDict>\n" +
                        "  <dictEntry name=\"bank\"           tag=\"1\"   num=\"1\"/>\n" +
                        "  <dictEntry name=\"bank of short banks\" tag=\"2\"   num=\"2\"/>\n" +
                        "  <dictEntry name=\"shorts pad0\"    tag=\"3\"   num=\"3\"/>\n" +
                        "  <dictEntry name=\"shorts pad2\"    tag=\"4\"   num=\"4\"/>\n" +
                        "  <dictEntry name=\"bank of char banks\"  tag=\"5\"   num=\"5\"/>\n" +
                        "  <dictEntry name=\"chars pad0\"     tag=\"6\"   num=\"6\"/>\n" +
                        "  <dictEntry name=\"chars pad3\"     tag=\"7\"   num=\"7\"/>\n" +
                        "  <dictEntry name=\"chars pad2\"     tag=\"8\"   num=\"8\"/>\n" +
                        "  <dictEntry name=\"chars pad1\"     tag=\"9\"   num=\"9\"/>\n" +
                "</xmlDict>";

        // use just a bunch of zeros for data
        byte[]  byteData1   = new byte[]  {1,2,3,4};
        byte[]  byteData2   = new byte[]  {1,2,3,4,5};
        byte[]  byteData3   = new byte[]  {1,2,3,4,5,6};
        byte[]  byteData4   = new byte[]  {1,2,3,4,5,6,7};
        short[] shortData1  = new short[] {11,22};
        short[] shortData2  = new short[] {11,22,33};

        try {
            EventWriter eventWriterNew = null;
            if (addDictionary) {
                eventWriterNew = new EventWriter(myBuf, 100, 3, dictionary, null);
            }
            else {
                eventWriterNew = new EventWriter(myBuf, 100, 3, null, null);
            }

            // event - bank of banks
            EventBuilder eventBuilder2 = new EventBuilder(tag, DataType.BANK, 1);
            EvioEvent eventShort = eventBuilder2.getEvent();

            // bank of short banks
            EvioBank bankBanks = new EvioBank(2, DataType.BANK, 2);

            // 3 shorts
            EvioBank shortBank1 = new EvioBank(3, DataType.SHORT16, 3);
            shortBank1.appendShortData(shortData1);
            eventBuilder2.addChild(bankBanks, shortBank1);

            EvioBank shortBank2 = new EvioBank(4, DataType.SHORT16, 4);
            shortBank2.appendShortData(shortData2);
            eventBuilder2.addChild(bankBanks, shortBank2);

            eventBuilder2.addChild(eventShort, bankBanks);
            eventWriterNew.writeEvent(eventShort);



            // each event is a trivial event containing an array of ints - all zeros
            EventBuilder eventBuilder = new EventBuilder(5, DataType.BANK, 5);
            EvioEvent event = eventBuilder.getEvent();

            // event 1
            EvioBank charBank1 = new EvioBank(6, DataType.CHAR8, 6);
            charBank1.appendByteData(byteData1);
            eventBuilder.addChild(event, charBank1);

            // event 2
            EvioBank charBank2 = new EvioBank(7, DataType.CHAR8, 7);
            charBank2.appendByteData(byteData2);
            eventBuilder.addChild(event, charBank2);

            // event 3
            EvioBank charBank3 = new EvioBank(8, DataType.CHAR8, 8);
            charBank3.appendByteData(byteData3);
            eventBuilder.addChild(event, charBank3);

            // event 4
            EvioBank charBank4 = new EvioBank(9, DataType.CHAR8, 9);
            charBank4.appendByteData(byteData4);
            eventBuilder.addChild(event, charBank4);

            eventWriterNew.writeEvent(event);

            // all done writing
            eventWriterNew.close();
            myBuf.flip();
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        catch (EvioException e) {
            e.printStackTrace();
            return null;
        }

        return myBuf;
    }


    /**
     * Create a buffer containing a sample CODA physics event.
     *
     * @param addDictionary
     * @return buffer containing generated CODA physics event
     */
    private ByteBuffer getPhysicsBuffer(int physicsTag, boolean addDictionary) {
        //--------------------------------------
        // create an array of simple evio events
        //--------------------------------------

        ByteBuffer myBuf = ByteBuffer.allocate(500);// 160 for ev + dict
        myBuf.order(ByteOrder.LITTLE_ENDIAN);

        // xml dictionary
        String dictionary =
                "<xmlDict>\n" +
                    "  <dictEntry name=\"Physics Event\"  tag=\"0xFF50\" />\n" +
                    "  <dictEntry name=\"Trigger Bank\"   tag=\"0xFF23\" />\n" +
                "</xmlDict>";

        try {
            EventWriter writer = null;
            if (addDictionary) {
                writer = new EventWriter(myBuf, 100, 3, dictionary, null);
            }
            else {
                writer = new EventWriter(myBuf, 100, 3, null, null);
            }

            int numEvents=2, numRocs=2, ebId=3, roc1Id=1, roc2Id=2, detId=4;
            long firstEvNum = 1L;

            // Event = bank of banks
            EventBuilder builder = new EventBuilder(physicsTag, DataType.BANK, numEvents);
            EvioEvent ev = builder.getEvent();

            //---------------------------
            // Trigger = bank of segments
            //---------------------------
            // (contains timestamps, run# & run type, run-specific data)
            EvioBank triggerBank = new EvioBank(0xFF23, DataType.SEGMENT, numRocs);

            // 1st Segment w/ event #, avg TS's, run #, run type
            EvioSegment firstSeg = new EvioSegment(ebId, DataType.ULONG64);
            // ev #, avg timestamp ev 1, ts ev 2, run # high 32 bits, run type low 32 bits
            long[] fsData = new long[] {firstEvNum, 2L, 3L, (4L << 32 | 5L)};
            firstSeg.appendLongData(fsData);

            // 2nd Segment w/ event types
            EvioSegment secondSeg = new EvioSegment(ebId, DataType.USHORT16);
            // event type 1, 2
            short[] etData = new short[] {(short)1, (short)2};
            secondSeg.appendShortData(etData);

            // Add ROC-specific data for Roc1
            EvioSegment roc1Seg = new EvioSegment(roc1Id, DataType.UINT32);
            int[] iData = new int[] {2,0,3,0};  // ts 1, 2  (low 32 first)
            roc1Seg.appendIntData(iData);

            // Add ROC-specific data for Roc2
            EvioSegment roc2Seg = new EvioSegment(roc2Id, DataType.UINT32);
            roc2Seg.appendIntData(iData);

            // build trigger bank
            builder.addChild(triggerBank, firstSeg);
            builder.addChild(triggerBank, secondSeg);
            builder.addChild(triggerBank, roc1Seg);
            builder.addChild(triggerBank, roc2Seg);
            builder.addChild(ev, triggerBank);

            //---------------------------
            // Data Banks for Rocs
            //---------------------------
            EvioBank dBank1 = new EvioBank((0<<12 | (0xFFF & roc1Id)),   // shift 4 for big endian
                                            DataType.BANK, numEvents);

            // Contains a data block bank
            EvioBank dbBank1 = new EvioBank((0<<12 | (0xFFF & detId)),
                                             DataType.UINT32, numEvents);
            int[] dbData = new int[] {(int)firstEvNum, 1,2,3};
            dbBank1.appendIntData(dbData);

            EvioBank dBank2 = new EvioBank((0<<12 | (0xFFF & roc2Id)),
                                            DataType.BANK, numEvents);

            builder.addChild(dBank1, dbBank1);
            builder.addChild(dBank2, dbBank1);
            builder.addChild(ev, dBank1);
            builder.addChild(ev, dBank2);

            //---------------------------

            writer.writeEvent(ev);

            // All done writing, prepare buffer for reading
            writer.close();
            myBuf.flip();
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        catch (EvioException e) {
            e.printStackTrace();
            return null;
        }

        return myBuf;
    }

    private int controlTypeIndex = -1;

    /**
     * Create a buffer containing a sample CODA control event.
     * @return buffer containing generated CODA control event
     */
    private ByteBuffer getControlBuffer() {
        //--------------------------------------
        // create an array of simple evio events
        //--------------------------------------

        ByteBuffer myBuf = ByteBuffer.allocate(100);
        myBuf.order(ByteOrder.LITTLE_ENDIAN);


        try {
            EventWriter writer = new EventWriter(myBuf, 100, 3, null, null);
                                 //         sync, prestart,  go,    pause,  end
            int controlTypes[] = new int[] {0xFFD0, 0xFFD1, 0xFFD2, 0xFFD3, 0xFFD4};

             // Cycle through all the control event types
            controlTypeIndex = (controlTypeIndex + 1) % controlTypes.length;

            // Event = bank of banks
            EventBuilder builder = new EventBuilder(controlTypes[controlTypeIndex],
                                                    DataType.UINT32, 0xcc);

            EvioEvent ev = builder.getEvent();

            int[] iData = new int[] {2,1,2};  // time, run #/events since sync, event in run/run type
            ev.appendIntData(iData);

            writer.writeEvent(ev);

            // All done writing, prepare buffer for reading
            writer.close();
            myBuf.flip();
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        catch (EvioException e) {
            e.printStackTrace();
            return null;
        }

        return myBuf;
    }


    /**
     * This method is executed as a thread.
     */
    public void run() throws cMsgException {

        if (debug) {
            System.out.println("Running cMsg producer sending to:\n" +
                                 "    subject = " + subject +
                               "\n    type    = " + type +
                               "\n    UDL     = " + UDL);
        }

        // connect to cMsg server
        cMsg coda = new cMsg(UDL, name, description);
        try {
            coda.connect();
        }
        catch (cMsgException e) {
            e.printStackTrace();
            return;
        }

        // create a message
        cMsgMessage msg = new cMsgMessage();
        msg.setSubject(subject);
        msg.setType(type);

        // variables to track message rate
        double freq=0., freqAvg=0.;
        long t1, t2, deltaT, totalT=0, totalC=0;

        // Ignore the first N values found for freq in order
        // to get better avg statistics. Since the JIT compiler in java
        // takes some time to analyze & compile code, freq may initially be low.
        long ignore=0;
        int tag=0;
        ByteBuffer myBuf;

        while (true) {
            t1 = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                // Add event buffer as byte array
                tag = (tag+1) % 255;
                // Every other buffer has no dictionary ...

//                if (tag%2 == 0) {
//                    myBuf = getEvioBuffer(tag, true);
//                }
//                else {
//                    myBuf = getEvioBuffer(tag, false);
//                }

                if (tag%3 == 0) {
                    // fully-built physics event
                    if (tag%2 == 0) {
                        myBuf = getPhysicsBuffer(0xFF50, true);
                    }
                    else {
                        myBuf = getPhysicsBuffer(0xFF50, false);
                    }
                }
                else if (tag%3 == 1) {
                    // partially-built physics event (ebId = 2)
                    if (tag%2 == 0) {
                        myBuf = getPhysicsBuffer(2, true);
                    }
                    else {
                        myBuf = getPhysicsBuffer(2, false);
                    }
                }
                else {
                    myBuf = getControlBuffer();
                }

                //                    // print out buffer so it can be used with et_producer1.c
                //                    IntBuffer ibuf = myBuf.asIntBuffer();
                //                    for (int j=0; j < ibuf.limit(); j++) {
                //                        System.out.println("0x"+ Integer.toHexString(ibuf.get(j)));
                //                    }

                msg.setByteArray(myBuf.array(), 0, myBuf.limit());

                System.out.println("SEND MSG");
                coda.send(msg);
                coda.flush(0);

                // delay between messages sent
                if (delay != 0) {
                    try {Thread.sleep(delay);}
                    catch (InterruptedException e) {}
                }
            }
            t2 = System.currentTimeMillis();

            if (ignore == 0) {
                deltaT = t2 - t1; // millisec
                freq = (double) count / deltaT * 1000;
                totalT += deltaT;
                totalC += count;
                freqAvg = (double) totalC / totalT * 1000;

                if (debug) {
                    System.out.println(doubleToString(freq, 1) + " Hz, Avg = " +
                                       doubleToString(freqAvg, 1) + " Hz");
                }
            }
            else {
                ignore--;
            }
        }
    }

}
