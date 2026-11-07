import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Search {

    static HashMap<String, long[]> vocab = new HashMap<>();
    static HashMap<Integer, String> titles = new HashMap<>();
    static RandomAccessFile postingsFile;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java Search <index folder>");
            return;
        }
        File dir = new File(args[0]);
        loadVocab(new File(dir, "vocab.txt"));
        loadTitles(new File(dir, "docs.txt"));
        postingsFile = new RandomAccessFile(new File(dir, "postings.txt"), "r");
        System.out.println("Vocabulary size: " + vocab.size());
        System.out.println("Enter a term / phrase  (:rare, :quit)");

        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals(":quit")) break;
            out.println("\n> " + line);
            if (line.equals(":rare")) {
                showRareTerms(out);
            } else {
                List<String> words = Tokenizer.tokenize(line);
                if (words.size() == 1) {
                    showTerm(words.get(0), out);
                } else if (words.size() > 1) {
                    phraseSearch(words, out);
                }
            }
        }
        postingsFile.close();
    }

    static void loadVocab(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\t");
                vocab.put(p[0], new long[]{Long.parseLong(p[1]), Long.parseLong(p[2]),
                                           Long.parseLong(p[3]), Long.parseLong(p[4])});
            }
        }
    }

    static void loadTitles(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\t", 2);
                titles.put(Integer.parseInt(p[0]), p.length > 1 ? p[1] : "");
            }
        }
    }

    static TreeMap<Integer, List<Integer>> readPostings(String term) throws IOException {
        long[] v = vocab.get(term);
        if (v == null) return null;
        byte[] buf = new byte[(int) v[3]];
        postingsFile.seek(v[2]);
        postingsFile.readFully(buf);
        String text = new String(buf, StandardCharsets.UTF_8);

        TreeMap<Integer, List<Integer>> result = new TreeMap<>();
        for (String entry : text.split(";")) {
            String[] parts = entry.split(":");
            List<Integer> positions = new ArrayList<>();
            for (String p : parts[1].split(",")) positions.add(Integer.parseInt(p));
            result.put(Integer.parseInt(parts[0]), positions);
        }
        return result;
    }

    static void showTerm(String term, PrintStream out) throws IOException {
        long[] v = vocab.get(term);
        if (v == null) {
            out.println("Term not found: " + term);
            return;
        }
        out.println(term + ": CF=" + v[0] + ", DF=" + v[1]);
        for (Map.Entry<Integer, List<Integer>> e : readPostings(term).entrySet()) {
            out.println("  doc" + e.getKey() + " [" + titles.get(e.getKey()) + "]: " + e.getValue());
        }
    }

    static void phraseSearch(List<String> words, PrintStream out) throws IOException {
        List<TreeMap<Integer, List<Integer>>> lists = new ArrayList<>();
        for (String w : words) {
            TreeMap<Integer, List<Integer>> pl = readPostings(w);
            if (pl == null) {
                out.println("Term not found: " + w);
                return;
            }
            lists.add(pl);
        }
        int matches = 0;
        for (int doc : lists.get(0).keySet()) {
            boolean inAll = true;
            for (TreeMap<Integer, List<Integer>> pl : lists) {
                if (!pl.containsKey(doc)) { inAll = false; break; }
            }
            if (!inAll) continue;

            List<Integer> starts = new ArrayList<>();
            for (int p : lists.get(0).get(doc)) {
                boolean ok = true;
                for (int i = 1; i < lists.size(); i++) {
                    if (!lists.get(i).get(doc).contains(p + i)) { ok = false; break; }
                }
                if (ok) starts.add(p);
            }
            if (!starts.isEmpty()) {
                matches++;
                out.println("  doc" + doc + " [" + titles.get(doc) + "]: phrase starts at " + starts);
            }
        }
        out.println("Phrase \"" + String.join(" ", words) + "\" found in " + matches + " document(s)");
    }

    static void showRareTerms(PrintStream out) throws IOException {
        TreeMap<String, long[]> sorted = new TreeMap<>(vocab);
        List<String> rare = new ArrayList<>();
        for (Map.Entry<String, long[]> e : sorted.entrySet()) {
            String term = e.getKey();
            if (e.getValue()[1] <= 2 && term.length() >= 4 && isTelugu(term)) {
                rare.add(term);
            }
        }
        int step = Math.max(1, rare.size() / 10);
        int shown = 0;
        for (int i = 0; i < rare.size() && shown < 10; i += step) {
            showTerm(rare.get(i), out);
            shown++;
        }
    }

    static boolean isTelugu(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 0x0C00 || s.charAt(i) > 0x0C7F) return false;
        }
        return true;
    }
}
