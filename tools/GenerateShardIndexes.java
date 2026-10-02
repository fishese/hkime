import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Generate paired compact indexes for bundled dictionaries, never modifying their source assets. */
public class GenerateShardIndexes {
    private record Row(int start, String key) {}
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root) || !root.endsWith(Path.of("assets", "mck")))
            throw new IllegalArgumentException("Expected the project's assets/mck directory");
        int count = 0;
        long size = 0;
        try (var files = Files.walk(root)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".cs2")).sorted().toList()) {
                byte[] zipBytes = Files.readAllBytes(source);
                String text;
                try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                    if (zip.getNextEntry() == null) throw new IOException("Missing source entry");
                    try (var objects = new ObjectInputStream(zip)) { text = (String) objects.readObject(); }
                }
                var rows = new ArrayList<Row>();
                for (int start = 0; start < text.length();) {
                    int end = text.indexOf('\n', start);
                    if (end < 0) end = text.length();
                    int tab = text.indexOf('\t', start);
                    if (tab >= start && tab < end) rows.add(new Row(start, text.substring(start, tab)));
                    start = end + 1;
                }
                rows.sort(Comparator.comparing(Row::key).thenComparingInt(Row::start));
                CRC32 crc = new CRC32(); crc.update(zipBytes);
                Path target = source.resolveSibling(source.getFileName() + ".idx");
                if (!target.normalize().startsWith(root)) throw new IOException("Index target outside assets");
                try (var output = new DataOutputStream(Files.newOutputStream(target))) {
                    output.writeInt(0x484b4931); output.writeInt(text.length());
                    output.writeInt((int) crc.getValue()); output.writeInt(rows.size());
                    for (Row row : rows) output.writeInt(row.start());
                }
                count++; size += Files.size(target);
            }
        }
        System.out.println("Generated " + count + " indexes; " + size + " bytes total");
    }
}
