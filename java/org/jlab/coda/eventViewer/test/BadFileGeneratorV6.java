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
public class BadFileGeneratorV6 {

    // Introduce known errors by deliberately putting in wrong values in block headers
    // or evio structure headers.
    static int data[] = {

            // File Header
            0x4556494f,  // Evio file
            0x00000001,  // file #
            0x0000000e,  // header word len (14)
            0x00000003,  // record count
            0x00000000,  // index len
            0x10000006,  // bit info (evio file, version 6)
            0xc,         // usr header length ( 3 ints)
            0xc0da0100,  // magic int
            0x08070605,  // user reg (long), if big endian, this first
            0x04030201,  // user reg (long), if little endian, this first
            0,             // trailer pos (bytes), if big E, this first
            332,           // trailer pos, if little E, this first
            0,
            0,


            // file's user header (nonsense, but normally used for dictionary and first event)
            0x00000111,
            0x00000222,
            0x00000333,



            // Record header
            0x0000001F,   // 31 words
            0x00000001,   // rec #
            0x0000000e,   // header word len (14)
            0x00000002,   // 2 events
            0x00000008,   // index array bytes
            0x00000006,   // bitinfo & version
            0x0000000c,   // user header bytes (= 12 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x08070605,   // user reg1 (long), if big endian, this first
            0x04030201,   // user reg1 (long), if little endian, this first
            0x08070605,   // user reg2 (long), if big endian, this first
            0x04030201,   // user reg2 (long), if little endian, this first

            // index
            0x0000001c,   // index, ev 1 byte len
            0x00000014,   // index, ev 2 byte len

            // user header
            0x00000001,
            0x00000002,
            0x00000003,

            // ev 1
            0x00000006,
            0x00010e01,   // tag=1, bank, num=1
              0x00000004,
              0x00028102,   // tag=2, unsigned int, num=2, type = UINT32 (1), pad = 2 (should be 0x00020102)
                0x00000001,
                0x00000001,
                0x00000001,

            // ev 2
            0x00000004,
            0x00030103,   // tag=3, unsigned int, num=3
              0x00000002,
              0x00000002,
              0x00000002,

            // Record header
            0x00000023,   // 35 words
            0x00000002,   // rec #
            0x0000000e,   // header word len (14)
            0x00000003,   // 3 events
            0x0000000c,   // index array bytes (12)
            0x00000006,   // bitinfo & version
            0x0000000c,   // user header bytes (= 12 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x00000001,   // user reg1
            0x00000002,   // user reg1
            0x00000003,   // user reg2
            0x00000004,   // user reg2

            // index
            0x00000014,   // index, ev 1 byte len
            0x00000014,   // index, ev 2 byte len
            0x00000014,   // index, ev 3 byte len

            // user header
            0x00000111,
            0x00000222,
            0x00000333,

            // ev 1
            0x00000004,
            0x00018501,   // tag=1, unsigned short, num=1, padding = 2
              0x00010002,
              0x00030004,
              0x00056666,

            // ev 2
            0x00000004,
            0x0001c701,   // tag=1, unsigned byte, num=1, padding = 3
                0x01020304,
                0x05060708,
                0x09333333,

            // ev 3
            0x00000004,
            0x00010101,
              0x00000005,
              0x00000005,
              0x00000005,

            // Ending Record header or Trailer
            0x0000000e,   // 14 words
            0x00000003,   // rec #
            0x0000000e,   // header word len (14)
            0x00000000,   // 0 events
            0x00000000,   // index array bytes
            0x30000206,   // version 6, is file trailer, is last record
            0x00000000,   // user header bytes (= 24 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x00000011,   // user reg1
            0x00000022,   // user reg1
            0x00000033,   // user reg2
            0x00000044,   // user reg2
     };


    // Introduce known errors by deliberately putting in wrong values in block headers
    // or evio structure headers.
    static int data2[] = {

            // File Header
            0x4556494f,  // Evio file
            0x00000001,  // file #
            0x0000000e,  // header word len (14)
            0x00000003,  // record count
            0x00000010,  // index len
            0x10000406,  // bit info (evio file, trailer w/index, version 6)
            0xc,         // usr header length ( 3 ints)
            0xc0da0100,  // magic int
            0x08070605,  // user reg (long), if big endian, this first
            0x04030201,  // user reg (long), if little endian, this first
            0,             // trailer pos (bytes), if big E, this first
            348,           // trailer pos, if little E, this first
            0,
            0,

            // index (same format as trailer index)
            0x0000007c,   // 31 words, 124 bytes
            0x00000002,   // 2 events
            0x0000008c,   // 35 words, 140 bytes
            0x00000003,   // 3 events

            // file's user header (nonsense, but normally used for dictionary and first event)
            0x00000111,
            0x00000222,
            0x00000333,



            // Record header
            0x0000001F,   // 31 words
            0x00000001,   // rec #
            0x0000000e,   // header word len (14)
            0x00000003,   // 2 events (set to 3)
            0x00000008,   // index array bytes
            0x00000006,   // bitinfo & version
            0x0000000c,   // user header bytes (= 12 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x08070605,   // user reg1 (long), if big endian, this first
            0x04030201,   // user reg1 (long), if little endian, this first
            0x08070605,   // user reg2 (long), if big endian, this first
            0x04030201,   // user reg2 (long), if little endian, this first

            // index
            0x0000001c,   // index, ev 1 byte len
            0x00000014,   // index, ev 2 byte len

            // user header
            0x00000001,
            0x00000002,
            0x00000003,

            // ev 1
            0x00000006,
            0x00018e01,   // tag=1, bank, num=1 (should be 0x10e01), not 0x18e01 (pad = 2)
            0x00000004,
            0x00028102,   // tag=2, unsigned int, num=2, type = UINT32 (1),  (should be 0x00020102) not 0x28102 (pad = 2)
            0x00000001,
            0x00000001,
            0x00000001,

            // ev 2
            0x00000004,
            0x00030103,   // tag=3, unsigned int, num=3 should be 0x30103 not 0x3C103 (pad = 3)
            0x00000002,
            0x00000002,
            0x00000002,

            // Record header
            0x00000023,   // 35 words
            0x00000002,   // rec #
            0x0000000e,   // header word len (14)
            0x00000003,   // 3 events
            0x0000000c,   // index array bytes (12)
            0x00000006,   // bitinfo & version
            0x0000000c,   // user header bytes (= 12 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x00000001,   // user reg1
            0x00000002,   // user reg1
            0x00000003,   // user reg2
            0x00000004,   // user reg2

            // index
            0x00000014,   // index, ev 1 byte len
            0x00000014,   // index, ev 2 byte len
            0x00000014,   // index, ev 3 byte len

            // user header
            0x00000111,
            0x00000222,
            0x00000333,

            // ev 1
            0x00000004,
            0x00018501,   // tag=1, unsigned short, num=1, padding = 2
            0x00010002,
            0x00030004,
            0x00056666,

            // ev 2
            0x00000004,
            0x0001c701,   // tag=1, unsigned byte, num=1, padding = 3
            0x01020304,
            0x05060708,
            0x09333333,

            // ev 3
            0x00000004,
            0x00010101,
            0x00000005,
            0x00000005,
            0x00000005,

            // Ending Record header or Trailer
            0x00000012,   // 14 + 4 words
            0x00000003,   // rec #
            0x0000000e,   // header word len (14)
            0x00000000,   // 0 events
            0x00000010,   // index array bytes
            0x30000206,   // version 6, is file trailer, is last record
            0x00000000,   // user header bytes (= 24 bytes = 3 ints)
            0xc0da0100,   // magic int
            0x00000000,   // uncom data bytes
            0x00000000,   // comp type and comp data words
            0x00000011,   // user reg1
            0x00000022,   // user reg1
            0x00000033,   // user reg2
            0x00000044,   // user reg2

            // index (1 word of record len bytes + 1 word of event count for each record)
            // Does not include this trailer.
            0x0000007c,   // 31 words = 124 bytes
            0x00000002,   // 2 events
            0x0000008c,   // 35 words = 140 bytes
            0x00000003,   // 3 events
    };


    /** For writing out a file. */
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
            //byte[] be  = ByteDataTransformer.toBytes(data, ByteOrder.BIG_ENDIAN);
            byte[] be  = ByteDataTransformer.toBytes(data2, ByteOrder.BIG_ENDIAN);
            ByteBuffer buf = ByteBuffer.wrap(be);
            String fileName  = "/Users/timmer/coda/evioDataFiles/HandCreatedV6.ev";
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
