package com.winlator.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Relocates only the official runtime's embedded rootfs prefix, preserving ELF offsets. */
public final class RootFSPathRelocator {
    public static final String ROOTFS_ALIAS = "r";
    public static final String ORIGINAL_ROOT_PATH = "/data/data/com.winlator/files/rootfs";
    private final String relocatedRootPath;
    private final byte[] original = ORIGINAL_ROOT_PATH.getBytes(StandardCharsets.US_ASCII);
    private final byte[] replacement;

    public RootFSPathRelocator(String applicationId) {
        relocatedRootPath = "/data/data/"+applicationId+"/files/"+ROOTFS_ALIAS;
        replacement = relocatedRootPath.getBytes(StandardCharsets.US_ASCII);
        if (replacement.length != original.length) {
            throw new IllegalArgumentException("Runtime relocation must preserve the rootfs prefix length");
        }
    }

    public String relocateLink(String target) {
        return target.replace(ORIGINAL_ROOT_PATH, relocatedRootPath);
    }

    /** Copies an archive entry without changing its length or bytes outside matching prefixes. */
    public void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int held = 0;
        int read;
        while ((read = input.read(buffer, held, buffer.length-held)) != -1) {
            if (read == 0) continue;
            int available = held+read;
            int writable = Math.max(0, available-original.length+1);
            for (int i = 0; i < writable; i++) {
                if (buffer[i] != original[0]) continue;
                int j = 1;
                while (j < original.length && buffer[i+j] == original[j]) j++;
                if (j == original.length) {
                    System.arraycopy(replacement, 0, buffer, i, replacement.length);
                    i += original.length-1;
                }
            }
            output.write(buffer, 0, writable);
            held = available-writable;
            System.arraycopy(buffer, writable, buffer, 0, held);
        }
        output.write(buffer, 0, held);
    }
}
