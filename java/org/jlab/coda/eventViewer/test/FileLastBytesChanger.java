package org.jlab.coda.eventViewer.test;


import java.io.*;

/**
 * Class for reading in a file, changing the last 1,2, or 3 bytes
 * and writing it out to a new file. Used for testing how file data
 * is displayed in FileFrameBig class when data is not even multiple
 * of 4 bytes.
 */
public class FileLastBytesChanger {

    private String inputFileName = null;
    private String outputFileName="outputFile";
    private byte b1, b2, b3;
    private boolean b1Defined, b2Defined, b3Defined;


    /** Constructor. */
    FileLastBytesChanger(String[] args) {
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
            else if (args[i].equalsIgnoreCase("-i")) {
                inputFileName = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-o")) {
                outputFileName = args[i + 1];
                i++;
            }
            else if (args[i].equalsIgnoreCase("-b1")) {
                try {
                    b1 = Byte.decode(args[i + 1]);
                    b1Defined = true;
                }
                catch (NumberFormatException e) {
                    System.out.println("b1 arg must be a numerical byte");
                }
                i++;
            }
            else if (args[i].equalsIgnoreCase("-b2")) {
                try {
                    b2 = Byte.decode(args[i + 1]);
                    b2Defined = true;
                }
                catch (NumberFormatException e) {
                    System.out.println("b2 arg must be a numerical byte");
                }
                i++;
            }
            else if (args[i].equalsIgnoreCase("-b3")) {
                try {
                    b3 = Byte.decode(args[i + 1]);
                    b3Defined = true;
                }
                catch (NumberFormatException e) {
                    System.out.println("b3 arg must be a numerical byte");
                }
                i++;
            }
            else {
                usage();
                System.exit(-1);
            }
        }

        if (inputFileName == null) {
            usage();
            System.out.println("-i arg must be defined\n");
            System.exit(-1);
        }

        if (!b1Defined) {
            usage();
            System.out.println("-b1 arg must be defined\n");
            System.exit(-1);
        }

        if (b3Defined && !b2Defined) {
            usage();
            System.out.println("-b2 arg must be defined if -b3 is\n");
            System.exit(-1);
        }

        return;
    }


    /** Method to print out correct program command line usage. */
    private static void usage() {
        System.out.println("\nUsage:\n\n" +
            "   java cMsgProducer\n" +
            "         -i  <input file name>   name of file to read\n"+
            "        [-o  <output file name>] name of file to write\n" +
            "         -b1 <1st byte>          first byte to change at file's end\n" +
            "        [-b2 <2nd byte>]         second byte to change at file's end\n" +
            "        [-b3 <3rd byte>]         third byte to change at file's end\n" +
            "        [-h]                     print this help\n");
    }


    /**
     * Run as a stand-alone application.
     */
    public static void main(String[] args) {
        try {
            FileLastBytesChanger producer = new FileLastBytesChanger(args);
            producer.run();
        }
        catch (Exception e) {
            System.out.println(e.toString());
            System.exit(-1);
        }
    }



    /**
     * This method is executed as a thread.
     */
    public void run() throws IOException {
        File inFile  = new File(inputFileName);
        File outFile = new File(outputFileName);

        try {
            long fileBytes = inFile.length();
            if (fileBytes > 2100000000L) {
                throw new IOException("File is too big");
            }

            int extraBytes = (int) (fileBytes % 4);

            FileInputStream in   = new FileInputStream(inFile);
            FileOutputStream out = new FileOutputStream(outFile);

            // First copy over everything except the very last bytes
            // beyond the last full 4-byte chunk.
            for (int i=0; i < fileBytes - extraBytes; i++) {
                out.write(in.read());
            }

            // Now deal with the last 0, 1, 2, or 3 extra bytes
            switch (extraBytes) {
                case 3:
                    if (b3Defined) {
                        out.write(b1);
                        out.write(b2);
                        out.write(b3);
                    }
                    // If we have 2 bytes to write ...
                    else if (b2Defined) {
                        out.write(in.read());
                        out.write(b1);
                        out.write(b2);
                    }
                    // If we have 1 byte to write ...
                    else {
                        out.write(in.read());
                        out.write(in.read());
                        out.write(b1);
                    }
                    break;

                case 2:
                    if (b3Defined || b2Defined) {
                        out.write(b1);
                        out.write(b2);
                    }
                    else {
                        out.write(in.read());
                        out.write(b1);
                    }
                    break;

                case 1:
                    out.write(b1);
                    break;

                default:
            }

            out.close();
            in.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }


    }

}
