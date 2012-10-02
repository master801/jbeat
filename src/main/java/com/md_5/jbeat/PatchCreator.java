package com.md_5.jbeat;

import static com.md_5.jbeat.Shared.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

abstract class PatchCreator {

    protected final RandomAccessFile original;
    protected final RandomAccessFile modified;
    protected ByteBuffer source;
    protected ByteBuffer target;
    protected final File output;
    protected final FileOutputStream out;
    private final String header;
    private final CRC32 crc = new CRC32();

    protected PatchCreator(File original, File modified, File output, String header) throws FileNotFoundException {
        this.original = new RandomAccessFile(original, "r");
        this.modified = new RandomAccessFile(modified, "r");
        this.output = output;
        this.out = new FileOutputStream(output);
        this.header = header;
    }

    protected PatchCreator(File original, File modified, File output) throws FileNotFoundException {
        this(original, modified, output, null);
    }

    public void create() throws IOException {
        try {
            // map the files
            source = original.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, original.length());
            target = modified.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, modified.length());
            // write header
            for (char c : magicHeader) {
                out.write(c);
            }
            // write original size
            encode(out, original.length());
            // write modified size
            encode(out, modified.length());
            // write header length
            int headerLength = (header == null) ? 0 : header.length();
            encode(out, headerLength);
            // write the header
            if (header != null) {
                ByteBuffer encoded = encoder.encode(CharBuffer.wrap(header));
                out.write(encoded.array(), encoded.arrayOffset(), encoded.limit());
            }
            // do the actual patch
            doPatch();
            // write original checksum
            writeIntLE(out, (int) checksum(source, original.length(), crc));
            // write target checksum
            writeIntLE(out, (int) checksum(target, modified.length(), crc));
            // map ourselves to ram
            ByteBuffer self = new RandomAccessFile(output, "rw").getChannel().map(FileChannel.MapMode.READ_ONLY, 0, output.length());
            // write self checksum
            writeIntLE(out, (int) checksum(self, output.length(), crc));
        } finally {
            // close the streams
            original.close();
            modified.close();
            out.close();
        }
    }

    public static void writeIntLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    protected void encode(OutputStream out, long data) throws IOException {
        while (true) {
            long x = data & 0x7f;
            data >>= 7;
            if (data == 0) {
                out.write((byte) (0x80 | x));
                break;
            }
            out.write((byte) x);
            data--;
        }
    }

    protected abstract void doPatch() throws IOException;
}
