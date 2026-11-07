import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Indexer {

    static final int BLOCK_DOCS = 1000;

    static TreeMap<String, TreeMap<Integer, List<Integer>>> block = new TreeMap<>();
    static List<File> blockFiles = new ArrayList<>();
    static int docCount = 0;
    static int docsInBlock = 0;
    static File outDir;
    static PrintWriter docsWriter;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java Indexer <input file or folder> <output folder>");
            return;
        }
        Path input = Paths.get(args[0]);
        outDir = new File(args[1]);
        outDir.mkdirs();

        docsWriter = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(new File(outDir, "docs.txt")), StandardCharsets.UTF_8));

        List<Path> files;
        try (Stream<Path> s = Files.walk(input)) {
            files = s.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
        }
        for (Path f : files) {
            readFile(f);
        }
        if (docsInBlock > 0) {
            writeBlock();
        }
        docsWriter.close();
        System.out.println("Documents indexed: " + docCount);
        System.out.println("Blocks written   : " + blockFiles.size());

        mergeBlocks();
        System.out.println("Done. Index saved in " + outDir.getPath());
    }

    static void readFile(Path file) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            String title = "";
            StringBuilder text = new StringBuilder();
            boolean inDoc = false;

            while ((line = br.readLine()) != null) {
                if (line.startsWith("<doc ")) {
                    inDoc = true;
                    text.setLength(0);
                    title = getTitle(line);
                } else if (line.startsWith("</doc>")) {
                    if (inDoc) {
                        addDocument(title, text.toString());
                    }
                    inDoc = false;
                } else if (inDoc) {
                    text.append(line).append(' ');
                }
            }
        }
    }

    static String getTitle(String docTag) {
        int a = docTag.indexOf("title=\"");
        if (a < 0) return "";
        a += 7;
        int b = docTag.indexOf('"', a);
        return b < 0 ? "" : docTag.substring(a, b);
    }

    static void addDocument(String title, String text) throws IOException {
        docCount++;
        int docId = docCount;
        docsWriter.println(docId + "\t" + title);

        List<String> tokens = Tokenizer.tokenize(text);
        for (int i = 0; i < tokens.size(); i++) {
            int position = i + 1;  // positions start at 1
            block.computeIfAbsent(tokens.get(i), k -> new TreeMap<>())
                 .computeIfAbsent(docId, k -> new ArrayList<>())
                 .add(position);
        }

        docsInBlock++;
        if (docsInBlock >= BLOCK_DOCS) {
            writeBlock();
        }
    }

    static void writeBlock() throws IOException {
        File f = new File(outDir, "block" + blockFiles.size() + ".tmp");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, TreeMap<Integer, List<Integer>>> e : block.entrySet()) {
                pw.println(e.getKey() + "\t" + postingsToString(e.getValue()));
            }
        }
        blockFiles.add(f);
        block.clear();
        docsInBlock = 0;
    }

    static String postingsToString(TreeMap<Integer, List<Integer>> postings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<Integer>> e : postings.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append(':');
            List<Integer> pos = e.getValue();
            for (int i = 0; i < pos.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(pos.get(i));
            }
        }
        return sb.toString();
    }

    static void mergeBlocks() throws IOException {
        int n = blockFiles.size();
        BufferedReader[] readers = new BufferedReader[n];
        String[] currentTerm = new String[n];
        String[] currentPost = new String[n];

        for (int i = 0; i < n; i++) {
            readers[i] = new BufferedReader(new InputStreamReader(
                    new FileInputStream(blockFiles.get(i)), StandardCharsets.UTF_8));
            advance(readers[i], currentTerm, currentPost, i);
        }

        long offset = 0;
        try (OutputStream postingsOut = new BufferedOutputStream(
                     new FileOutputStream(new File(outDir, "postings.txt")));
             PrintWriter vocabOut = new PrintWriter(new OutputStreamWriter(
                     new FileOutputStream(new File(outDir, "vocab.txt")), StandardCharsets.UTF_8))) {

            while (true) {
                String smallest = null;
                for (int i = 0; i < n; i++) {
                    if (currentTerm[i] != null && (smallest == null || currentTerm[i].compareTo(smallest) < 0)) {
                        smallest = currentTerm[i];
                    }
                }
                if (smallest == null) break;

                StringBuilder merged = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (smallest.equals(currentTerm[i])) {
                        if (merged.length() > 0) merged.append(';');
                        merged.append(currentPost[i]);
                        advance(readers[i], currentTerm, currentPost, i);
                    }
                }

                String postings = merged.toString();
                byte[] bytes = postings.getBytes(StandardCharsets.UTF_8);

                int df = 0, cf = 0;
                for (String entry : postings.split(";")) {
                    df++;
                    cf += entry.substring(entry.indexOf(':') + 1).split(",").length;
                }

                postingsOut.write(bytes);
                postingsOut.write('\n');
                vocabOut.println(smallest + "\t" + cf + "\t" + df + "\t" + offset + "\t" + bytes.length);
                offset += bytes.length + 1;
            }
        }

        for (int i = 0; i < n; i++) {
            readers[i].close();
            blockFiles.get(i).delete();
        }
    }

    static void advance(BufferedReader r, String[] terms, String[] posts, int i) throws IOException {
        String line = r.readLine();
        if (line == null) {
            terms[i] = null;
            posts[i] = null;
        } else {
            int tab = line.indexOf('\t');
            terms[i] = line.substring(0, tab);
            posts[i] = line.substring(tab + 1);
        }
    }
}
