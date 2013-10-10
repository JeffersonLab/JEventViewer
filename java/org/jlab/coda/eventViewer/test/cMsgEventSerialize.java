package org.jlab.coda.eventViewer.test;


import org.jlab.coda.cMsg.*;
import org.jlab.coda.jevio.*;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Class for testing EventTreeFrame graphics application with events
 * coming in cMsg messages.
 */
public class cMsgEventSerialize {

    private String  subject = "evio";
    private String  type = "anything";
    private String  name = "producer";
    private String  description = "java event producer";
    private String  UDL = "cMsg://localhost/cMsg/myNameSpace";


    /** Constructor. */
    cMsgEventSerialize(String[] args) {
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
            "        [-h]                 print this help\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            cMsgEventSerialize producer = new cMsgEventSerialize(args);
            producer.run();
        }
        catch (cMsgException e) {
            System.out.println(e.toString());
            System.exit(-1);
        }
    }



    /**
     * This method is executed as a thread.
     */
    public void run() throws cMsgException {

        System.out.println("Running cMsg producer sending to:\n" +
                                 "    subject = " + subject +
                               "\n    type    = " + type +
                               "\n    UDL     = " + UDL);

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

        //--------------------------------------
        // create a simple evio event
        //--------------------------------------

        ByteBuffer myBuf = ByteBuffer.allocate(10000);

        // xml dictionary
        String dictionary =
                "<xmlDict>\n" +
                        "  <xmldumpDictEntry name=\"bank\"  tag=\"1\"   num=\"1\"/>\n" +
                "</xmlDict>";

        // data
        int[] data = new int[] {1,2,3,4};

        try {
            int maxBanksPerBlock    = 100;
            int maxBlockSizeInBytes = 1000000;

            EventWriter eventWriter = new EventWriter(myBuf, maxBlockSizeInBytes,
                                                      maxBanksPerBlock, dictionary, null);

            // event - bank of ints, tag = 1, num = 1
            EventBuilder eventBuilder = new EventBuilder(1, DataType.INT32, 1);
            EvioEvent ev = eventBuilder.getEvent();
            ev.appendIntData(data);
            eventWriter.writeEvent(ev);

            // all done writing
            eventWriter.close();
            myBuf.flip();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (EvioException e) {
            e.printStackTrace();
        }


        // Add event buffer as byte array
        msg.setByteArray(myBuf.array(), 0, myBuf.limit());

        // send message
        coda.send(msg);

    }

}
