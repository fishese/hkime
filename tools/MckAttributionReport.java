import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Offline audit of MCK entries against method-membership.tsv. */
public class MckAttributionReport {
    public static void main(String[] args) throws Exception {
        Path shards = Paths.get(args[0]);
        Path membership = Paths.get(args[1]);
        Path report = Paths.get(args[2]);
        Map<String, Map<String, Set<String>>> references = new HashMap<>();
        for (String line : Files.readAllLines(membership, StandardCharsets.UTF_8)) {
            if (line.startsWith("#")) continue;
            String[] parts = line.split("\t", 3);
            if (parts.length != 3) continue;
            Map<String, Set<String>> methods = references.computeIfAbsent(parts[0], k -> new HashMap<>());
            Set<String> chars = methods.computeIfAbsent(parts[1], k -> new HashSet<>());
            parts[2].codePoints().forEach(cp -> chars.add(new String(Character.toChars(cp))));
        }

        Path priority = report.resolveSibling("method-attribution-priority.tsv");
        Path shortlist = report.resolveSibling("method-attribution-shortlist.tsv");
        long total = 0, unidentified = 0, ambiguous = 0, absentCode = 0;
        long inferredCode = 0, ambiguousCode = 0;
        try (BufferedWriter out = Files.newBufferedWriter(report, StandardCharsets.UTF_8);
             BufferedWriter shortOut = Files.newBufferedWriter(priority, StandardCharsets.UTF_8);
             BufferedWriter shortlistOut = Files.newBufferedWriter(shortlist, StandardCharsets.UTF_8)) {
            out.write("code\tcandidate\treason\tcode_methods\tmck_rank\n");
            shortOut.write("code\tcandidate\treason\tcode_methods\tmck_rank\n");
            shortlistOut.write("code\tcandidate\treason\tcode_methods\tmck_rank\n");
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(shards, "mix_map_ext_*.cs2")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString();
                    if (!name.matches("mix_map_ext_[a-z]1?\\.cs2")) continue;
                    String first = name.substring("mix_map_ext_".length(), "mix_map_ext_".length() + 1);
                    String data;
                    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
                        if (zip.getNextEntry() == null) continue;
                        try (ObjectInputStream objects = new ObjectInputStream(zip)) {
                            data = (String) objects.readObject();
                        }
                    }
                    for (String row : data.split("\n")) {
                        int tab = row.indexOf('\t');
                        if (tab < 0) continue;
                        String code = first + row.substring(0, tab);
                        Map<String, Set<String>> methods = references.getOrDefault(code, Map.of());
                        List<String> candidates = decode(row.substring(tab + 1));
                        for (int rank = 0; rank < candidates.size(); rank++) {
                            total++;
                            String candidate = candidates.get(rank);
                            List<String> hits = new ArrayList<>();
                            for (var entry : methods.entrySet()) {
                                if (entry.getValue().contains(candidate)) hits.add(entry.getKey());
                            }
                            Collections.sort(hits);
                            if (hits.size() == 1) continue;
                            String reason = hits.isEmpty()
                                ? (methods.isEmpty() ? "code_absent" :
                                   methods.size() == 1 ? "inferred_unique_code" : "candidate_absent_ambiguous")
                                : "multiple_methods";
                            if (hits.isEmpty()) unidentified++; else ambiguous++;
                            if (reason.equals("code_absent")) absentCode++;
                            if (reason.equals("inferred_unique_code")) inferredCode++;
                            if (reason.equals("candidate_absent_ambiguous")) ambiguousCode++;
                            List<String> codeMethods = new ArrayList<>(methods.keySet());
                            Collections.sort(codeMethods);
                            String output = code + "\t" + clean(candidate) + "\t" + reason + "\t" +
                                String.join(",", hits.isEmpty() ? codeMethods : hits) + "\t" + (rank + 1) + "\n";
                            out.write(output);
                            if (rank < 3 && code.length() <= 5) shortOut.write(output);
                            if (rank == 0 && code.length() <= 3 && !reason.equals("inferred_unique_code"))
                                shortlistOut.write(output);
                        }
                    }
                }
            }
        }
        System.out.printf("MCK candidates: %,d; no exact match: %,d; multiple method matches: %,d%n",
            total, unidentified, ambiguous);
        System.out.printf("Absent code: %,d; inferred by unique code: %,d; ambiguous code: %,d%n",
            absentCode, inferredCode, ambiguousCode);
        System.out.println(report.toAbsolutePath());
        System.out.println(priority.toAbsolutePath());
        System.out.println(shortlist.toAbsolutePath());
    }

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static List<String> decode(String value) {
        int backspace = value.indexOf('\b');
        String section = value.substring(0, backspace < 0 ? value.length() : backspace);
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < section.length();) {
            char ch = section.charAt(i);
            if (ch == '\0' || ch == '\f' || ch == '\r' || ch == '\u2022') { i++; continue; }
            if (ch >= '1' && ch <= '9') {
                int end = i + 1;
                if (end < section.length() && Character.isDigit(section.charAt(end))) end++;
                int size = Integer.parseInt(section.substring(i, end));
                int finish = Math.min(end + size, section.length());
                if (finish > end) result.add(section.substring(end, finish));
                i = Math.max(finish, i + 1);
            } else {
                int cp = section.codePointAt(i);
                result.add(new String(Character.toChars(cp)));
                i += Character.charCount(cp);
            }
        }
        return new ArrayList<>(result);
    }
}
