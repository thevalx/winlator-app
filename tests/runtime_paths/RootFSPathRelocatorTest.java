import com.winlator.core.RootFSPathRelocator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class RootFSPathRelocatorTest {
    private static final RootFSPathRelocator RELOCATOR = new RootFSPathRelocator("com.winlator.trig");
    private static final byte[] ORIGINAL = RootFSPathRelocator.ORIGINAL_ROOT_PATH.getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RELOCATED = "/data/data/com.winlator.trig/files/r".getBytes(StandardCharsets.US_ASCII);
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] relocate(byte[] source, int chunk) throws Exception {
        InputStream input = new ByteArrayInputStream(source) {
            @Override
            public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, chunk));
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        RELOCATOR.copy(input, output);
        return output.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            try (java.util.stream.Stream<Path> paths = Files.walk(Paths.get(args[0]))) {
                for (Path input : (Iterable<Path>)paths.filter(path -> path.toString().endsWith(".original"))::iterator) {
                    Path output = Paths.get(input.toString()+".relocated");
                    try (InputStream in = Files.newInputStream(input);
                         java.io.OutputStream out = Files.newOutputStream(output)) {
                        RELOCATOR.copy(in, out);
                    }
                }
            }
            return;
        }
        check(ORIGINAL.length == RELOCATED.length, "fixed-width prefix");
        check(RELOCATOR.relocateLink(RootFSPathRelocator.ORIGINAL_ROOT_PATH+"/lib/libc.so.6")
                .equals("/data/data/com.winlator.trig/files/r/lib/libc.so.6"), "absolute archive link");
        check(RELOCATOR.relocateLink("../lib/libc.so.6").equals("../lib/libc.so.6"), "relative archive link");
        check(RELOCATOR.relocateLink("/system/lib64/libc.so").equals("/system/lib64/libc.so"), "unrelated archive link");
        boolean rejected = false;
        try { new RootFSPathRelocator("com.example.wrong.length"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "reject a prefix that would move ELF offsets");
        for (int size : new int[]{0, 1, 35, 36, 37, 65535, 65536, 65537, 131100}) {
            byte[] source = new byte[size];
            for (int i = 0; i < size; i++) source[i] = (byte)(i % 251);
            for (int chunk : new int[]{1, 7, 35, 36, 37, 65536}) {
                check(Arrays.equals(source, relocate(source, chunk)), "unchanged arbitrary binary size "+size);
            }
        }
        for (int offset : new int[]{0, 1, 35, 36, 65500, 65501, 65502, 65535, 65536, 131070}) {
            byte[] source = new byte[offset+ORIGINAL.length*2+3];
            Arrays.fill(source, (byte)0x7f);
            System.arraycopy(ORIGINAL, 0, source, offset, ORIGINAL.length);
            System.arraycopy(ORIGINAL, 0, source, offset+ORIGINAL.length, ORIGINAL.length);
            byte[] expected = source.clone();
            System.arraycopy(RELOCATED, 0, expected, offset, RELOCATED.length);
            System.arraycopy(RELOCATED, 0, expected, offset+RELOCATED.length, RELOCATED.length);
            for (int chunk : new int[]{1, 7, 35, 36, 37, 65536}) {
                byte[] actual = relocate(source, chunk);
                check(Arrays.equals(expected, actual), "prefix at byte "+offset+", chunk "+chunk);
                check(Arrays.equals(actual, relocate(actual, chunk)), "idempotence at byte "+offset);
            }
        }
        System.out.println("Runtime path relocation: "+checks+" host checks passed");
    }
}
