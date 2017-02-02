package org.jlab.coda.eventViewer.test;

import org.jlab.coda.jevio.ByteDataTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Created by timmer on 1/24/17.
 */
public class BadFileGenerator {

    // Introduce known errors by deliberately putting in wrong values in block headers
    // or evio structure headers.
    static int data1[] = {
        0x00000014,   // 18 words, block hdr
        0x00000001,
        0x00000008,
        0x00000001,   // 2 events
        0x00000000,
        0x00000004,
        0x00000000,
        0xc0da0100,

        0x00000006,
        0x00010e01,   // tag=1, bank, num=1
            0x00000004,
            0x00028102,   // tag=2, unsigned int, num=2
            0x00000001,
            0x00000001,
            0x00000001,

        0x00000004,
        0x00010101,
        0x00000002,
        0x00000002,
        0x00000002,

        0x00000017,    // block hdr
        0x00000002,
        0x00000008,
        0x00000003,    // 3 events
        0x00000000,
        0x00000004,
        0x00000000,
        0xc0da0100,

        0x00000004,
        0x00010101,
        0x00000003,
        0x00000003,
        0x00000003,

        0x00000004,
        0x00010101,
        0x00000004,
        0x00000004,
        0x00000004,

        0x00000004,
        0x00010101,
        0x00000005,
        0x00000005,
        0x00000005,

        0x00000008,   // ending block
        0x00000003,
        0x00000008,
        0x00000000,
        0x00000000,
        0x00000204,
        0x00000000,
        0xc0da0100
    };

    /** For writing out a 5GByte file. */
    public static void main(String args[]) {
        // xml dictionary
        String dictionary =
                "<xmlDict>\n" +
                        "\t<xmldumpDictEntry name=\"bank\"           tag=\"1\"   num=\"1\"/>\n" +
                        "\t<xmldumpDictEntry name=\"bank of shorts\" tag=\"2\"   num=\"2\"/>\n" +
                        "\t<xmldumpDictEntry name=\"shorts pad0\"    tag=\"3\"   num=\"3\"/>\n" +
                        "\t<xmldumpDictEntry name=\"shorts pad2\"    tag=\"4\"   num=\"4\"/>\n" +
                        "\t<xmldumpDictEntry name=\"bank of chars\"  tag=\"5\"   num=\"5\"/>\n" +
                        "</xmlDict>";


        // write evio file
        try {
            byte[] be  = ByteDataTransformer.toBytes(data1, ByteOrder.BIG_ENDIAN);
            ByteBuffer buf = ByteBuffer.wrap(be);
            String fileName  = "./BadFile.ev";
            File file = new File(fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            FileChannel fileChannel = fileOutputStream.getChannel();
            fileChannel.write(buf);
            fileChannel.close();
        }
        catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


}
