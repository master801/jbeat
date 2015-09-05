/**
 * Copyright (c) 2012, md_5. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * The name of the author may not be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.md_5.jbeat;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PatcherIO {

    /**
     * The Input stream for the patch file.
     */
    private final InputStream patchFileInputStream;

    /**
     * The input stream for the input file.
     */
    private final InputStream inputFileInputStream;

    /**
     * The output stream for the output file.
     */
    private final OutputStream outputFileOutStream;

    /**
     * Create a new beat patcher instance. In order to complete the patch
     * process {@link #patch()} method must be called.
     *
     * @param patchFileInputStream the input stream for the beat format patch file
     * @param inputFileInputStream the input stream for the original file from which the patch was created (The "clean" file.)
     * @param outputFileOutStream the output stream of where patched file will be wrote to (The output file we're going to create from both the patch file and "clean" file.)
     */
    public PatcherIO(InputStream patchFileInputStream, InputStream inputFileInputStream, OutputStream outputFileOutStream) {
        this.patchFileInputStream = patchFileInputStream;
        this.inputFileInputStream = inputFileInputStream;
        this.outputFileOutStream = outputFileOutStream;
    }

    public void patch() throws IOException {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int read;
            while((read = patchFileInputStream.read()) != -1) {//Read the patch file's input stream to be wrote into the byte array output stream, which then will be converted into a ByteBuffer (by first converting it into a byte array then wrapping to a ByteBuffer).
                byteArrayOutputStream.write(read);
            }

            // store patch length
            final long patchLength = byteArrayOutputStream.size();

            // map patch file into memory
            ByteBuffer patch = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.reset();//Reset the byte array output stream for later usage

            // check the header
            for (char c : Shared.MAGIC_HEADER) {
                if (patch.get() != c) {
                    throw new IOException("Patch file does not contain correct BPS header!");
                }
            }
            // read source size
            long sourceSize = PatcherIO.decode(patch);

            // map as much of the source file as we need into memory
            while((read = inputFileInputStream.read()) != -1) {//Read the input file's input stream to be wrote into the byte array output stream, which then will be converted into a ByteBuffer (by first converting it into a byte array then wrapping to a ByteBuffer).
                byteArrayOutputStream.write(read);
            }
            ByteBuffer source = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
            byteArrayOutputStream.reset();//Reset the byte array output stream for later usage

            // read target size
            final long targetSize = PatcherIO.decode(patch);

            // map a large enough chunk of the target into memory
            ByteBuffer target = ByteBuffer.allocate((int)targetSize);

            // read metadata
            String metadata = PatcherIO.readString(patch);

            // store last offsets
            int sourceOffset = 0, targetOffset = 0;

            // do the actual patching
            while (patch.position() < patchLength - 12) {
                long length = PatcherIO.decode(patch);
                long mode = length & 3;
                length = (length >> 2) + 1;
                // branch per mode
                if (mode == Shared.SOURCE_READ) {
                    while (length-- != 0) {
                        target.put(source.get(target.position()));
                    }
                } else if (mode == Shared.TARGET_READ) {
                    while (length-- != 0) {
                        target.put(patch.get());
                    }
                } else {
                    // start the same
                    long data = PatcherIO.decode(patch);
                    long offset = (((data & 1) != 0) ? -1 : 1) * (data >> 1);
                    // descend deeper
                    if (mode == Shared.SOURCE_COPY) {
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
            final long sourceChecksum = PatcherIO.readInt(patch);
            if (Shared.checksum(source, sourceSize) != sourceChecksum) {
                throw new IOException("Source checksum does not match!");
            }
            // checksum of the target
            final long targetChecksum = PatcherIO.readInt(patch);
            if (Shared.checksum(target, targetSize) != targetChecksum) {
                throw new IOException("Target checksum does not match!");
            }
            // checksum of the patch itself
            long patchChecksum = PatcherIO.readInt(patch);
            if (Shared.checksum(patch, patchLength - 4) != patchChecksum) {
                throw new IOException("Patch checksum does not match!");
            }
            outputFileOutStream.write(target.array());
        } finally {
            // close the streams
            patchFileInputStream.close();
            inputFileInputStream.close();
            outputFileOutStream.close();
        }
    }

    /**
     * Read a UTF-8 string with variable length number length descriptor. Will
     * return null if there is no data read, or the string is of 0 length.
     */
    public static String readString(ByteBuffer in) throws IOException {
        int length = (int) PatcherIO.decode(in);
        String ret = null;
        if (length != 0) {
            int limit = in.limit();
            in.limit(in.position() + length);
            ret = Shared.CHARSET.decode(in).toString();
            in.limit(limit);
        }
        return ret;
    }

    /**
     * Read a set of bytes from a buffer return them as a unsigned integer.
     */
    public static long readInt(ByteBuffer in) throws IOException {
        return in.getInt() & 0xFFFFFFFFL;
    }

    /**
     * Read a single variable length number from the input stream.
     */
    public static long decode(ByteBuffer in) throws IOException {
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
