import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Tokenizer {

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (isWordChar(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) {
                tokens.add(normalize(word.toString()));
                word.setLength(0);
            }
        }
        if (word.length() > 0) {
            tokens.add(normalize(word.toString()));
        }
        return tokens;
    }

    private static boolean isWordChar(int cp) {
        int type = Character.getType(cp);
        return Character.isLetterOrDigit(cp)
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || cp == 0x200C || cp == 0x200D;
    }

    private static String normalize(String w) {
        return w.toLowerCase(Locale.ROOT);
    }
}
