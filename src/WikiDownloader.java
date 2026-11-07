import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class WikiDownloader {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java WikiDownloader <lang> <titles file> <output file>");
            return;
        }
        String lang = args[0];
        List<String> titles = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        File outFile = new File(args[2]);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();

        int id = 0;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
            for (String title : titles) {
                title = title.trim();
                if (title.isEmpty()) continue;

                String json = download(lang, title);
                String realTitle = getValue(json, "\"title\":\"");
                String text = getValue(json, "\"extract\":\"");

                if (text == null || text.trim().isEmpty()) {
                    System.out.println("NOT FOUND / EMPTY: " + title);
                    continue;
                }
                id++;
                out.println("<doc id=\"" + id + "\" url=\"\" title=\"" + realTitle.replace("\"", "") + "\">");
                out.println(text);
                out.println("</doc>");
                System.out.println("Saved: " + realTitle + " (" + text.length() + " characters)");
            }
        }
        System.out.println("Articles saved: " + id + " -> " + outFile.getPath());
    }

    static String download(String lang, String title) throws IOException {
        String url = "https://" + lang + ".wikipedia.org/w/api.php"
                + "?action=query&prop=extracts&explaintext=1&redirects=1"
                + "&format=json&formatversion=2&titles=" + URLEncoder.encode(title, "UTF-8");
        HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
        con.setRequestProperty("User-Agent", "IR-Assignment-Student/1.0 (college project)");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                con.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    static String getValue(String json, String key) {
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();

        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\') {
                char n = json.charAt(i + 1);
                if (n == 'n') { sb.append('\n'); i += 2; }
                else if (n == 't') { sb.append(' '); i += 2; }
                else if (n == 'u') {
                    sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    i += 6;
                } else { sb.append(n); i += 2; }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
