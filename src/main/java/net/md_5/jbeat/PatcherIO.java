package net.md_5.jbeat;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import static net.md_5.jbeat.Shared.*;
import static net.md_5.jbeat.Shared.charset;
import static net.md_5.jbeat.Shared.checksum;

/**
 * <p>
 *     I/O implementation of {@link Patcher}.
 * </p>
 * Created by Master801 on 9/1/2015 at 4:16 PM.
 * @author Master801
 */
public final class PatcherIO {

    private final InputStream originalFileInputStream, patchFileInputStream;
    private final OutputStream outputFileStream;

    public PatcherIO(InputStream originalFileInputStream, InputStream patchFileInputStream, OutputStream outputFileStream) {
        this.originalFileInputStream = originalFileInputStream;
        this.patchFileInputStream = patchFileInputStream;
        this.outputFileStream = outputFileStream;
    }

    public void patch() throws IOException {
        try {
            // map patch file into memory

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int read;
            while((read = patchFileInputStream.read()) != -1) {
                byteArrayOutputStream.write(read);
            }

            final byte[] patchFileData = byteArrayOutputStream.toByteArray();
            // store patch length
            final long patchLength = patchFileData.length;

            ByteBuffer patch = ByteBuffer.wrap(patchFileData);
            byteArrayOutputStream.reset();
            // check the header
            for (char c : magicHeader) {
                if (patch.get() != c) {
                    throw new IOException("Patch file does not contain correct BPS header!");
                }
            }
            // read source size
            long sourceSize = decode(patch);
            // map as much of the source file as we need into memory


            while((read = originalFileInputStream.read()) != -1) {
                byteArrayOutputStream.write(read);
            }

            ByteBuffer source = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.reset();
            // read target size
            long targetSize = decode(patch);

            // map a large enough chunk of the target into memory
            ByteBuffer target = ByteBuffer.allocate((int)targetSize);

            // read metadata
            String metadata = readString(patch);
            // store last offsets
            int sourceOffset = 0, targetOffset = 0;
            // do the actual patching
            while (patch.position() < patchLength - 12) {
                long length = decode(patch);
                long mode = length & 3;
                length = (length >> 2) + 1;
                // branch per mode
                if (mode == SOURCE_READ) {
                    while (length-- != 0) {
                        target.put(source.get(target.position()));
                    }
                } else if (mode == TARGET_READ) {
                    while (length-- != 0) {
                        target.put(patch.get());
                    }
                } else {
                    // start the same
                    long data = decode(patch);
                    long offset = (((data & 1) != 0) ? -1 : 1) * (data >> 1);
                    // descend deeper
                    if (mode == SOURCE_COPY) {
                        sourceOffset += offset;
                        while (length-- != 0) {
                            target.put(source.get(sourceOffset++));
                        }
                    } else {
                        targetOffset += offset;
                        while (length-- != 0) {
                            target.put(target.get(targetOffset++));
                        }
                    }
                }
            }
            // flip to little endian mode
            patch.order(ByteOrder.LITTLE_ENDIAN);
            // checksum of the source
            long sourceChecksum = readInt(patch);
            if (checksum(source, sourceSize) != sourceChecksum) {
                throw new IOException("Source checksum does not match!");
            }
            // checksum of the target
            long targetChecksum = readInt(patch);
            if (checksum(target, targetSize) != targetChecksum) {
                throw new IOException("Target checksum does not match!");
            }
            // checksum of the patch itself
            long patchChecksum = readInt(patch);
            if (checksum(patch, patchLength - 4) != patchChecksum) {
                throw new IOException("Patch checksum does not match!");
            }

            outputFileStream.write(target.array());//Write the "target" to a byte array output stream.
        } finally {
            // close the streams
            originalFileInputStream.close();
            patchFileInputStream.close();
            outputFileStream.close();
        }
    }

    /**
     * Read a UTF-8 string with variable length number length descriptor. Will
     * return null if there is no data read, or the string is of 0 length.
     */
    private String readString(ByteBuffer in) throws IOException {
        int length = (int) decode(in);
        String ret = null;
        if (length != 0) {
            int limit = in.limit();
            in.limit(in.position() + length);
            ret = charset.decode(in).toString();
            in.limit(limit);
        }
        return ret;
    }

    /**
     * Read a set of bytes from a buffer return them as a unsigned integer.
     */
    private long readInt(ByteBuffer in) throws IOException {
        return in.getInt() & 0xFFFFFFFFL;
    }

    /**
     * Read a single variable length number from the input stream.
     */
    private long decode(ByteBuffer in) throws IOException {
        long data = 0, shift = 1;
        while (true) {
            byte x = in.get();
            data += (x & 0x7F) * shift;
            if ((x & 0x80) != 0x00) {
                break;
            }
            shift <<= 7;
            data += shift;
        }
        return data;
    }

}
