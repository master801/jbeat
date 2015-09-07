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
import java.nio.channels.FileChannel;

/**
 * beat version 1 compliant binary patcher.
 */
public final class Patcher extends PatcherIO {

    /**
     * Create a new beat patcher instance. In order to complete the patch
     * process {@link #patch()} method must be called.
     *
     * @param patchFile the beat format patch file
     * @param sourceFile original file from which the patch was created
     * @param targetFile location to which the new, patched file will be output
     * @throws FileNotFoundException when one of the files cannot be opened for
     * read or write access
     */
    public Patcher(File patchFile, File sourceFile, File targetFile) throws FileNotFoundException {
        super(new FileInputStream(patchFile), new FileInputStream(sourceFile), new FileOutputStream(targetFile));
    }

    public static void main(final String[] arguments) throws IOException {
        if (arguments == null || arguments.length != 3) {//Check for valid arguments
            System.out.println("You must have valid arguments!");
            System.out.println("The first argument, should be where the patch file is located.");
            System.out.println("The second argument, should be where the source file is located (the file we're going to copy, then patch later on).");
            System.out.println("The third argument, should be where the patched file should be put into. (The is a copied version of the source file, then it is patched with the patch file.)");
            System.out.println("\"FileNotFoundException\" means that the file could not be created, located, wrote to, or read from.");
            return;
        }
        File patchFile = new File(arguments[0]), inputFile = new File(arguments[1]), outputFile = new File(arguments[2]);
        if (!patchFile.exists()) {//Check if the patch file exists, if it doesn't, throw a FileNotFoundException.
            throw new FileNotFoundException("The patch file does not exist at location \"" + arguments[0] + "\"!");
        }
        if (!inputFile.exists()) {//Check if the input file exists, if it doesn't, throw a FileNotFoundException.
            throw new FileNotFoundException("The input file does not exist at location \"" + arguments[1] + "\"");
        }
        new Patcher(patchFile, inputFile, outputFile).patch();
    }

}
