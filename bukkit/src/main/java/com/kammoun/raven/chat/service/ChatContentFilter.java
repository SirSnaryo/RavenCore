package com.kammoun.raven.chat.service;

import com.kammoun.raven.chat.model.ChatFilterSettings;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatContentFilter {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3})\\s*\\.\\s*(\\d{1,3})\\s*\\.\\s*(\\d{1,3})\\s*\\.\\s*(\\d{1,3})(?!\\d)"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*\\.[a-z]{2,24})\\b"
    );
    private static final Pattern DISCORD_INVITE_PATTERN = Pattern.compile(
            "(?i)\\b(?:discord(?:app)?\\.com/invite|discord\\.gg)/?[a-z0-9-]*\\b"
    );

    public String validate(String message, ChatFilterSettings settings) {
        if (message == null || message.isBlank() || settings == null) {
            return null;
        }

        if (settings.lagTestEnabled() && isLagTest(message, settings)) {
            return "lag-test";
        }

        if (settings.advertisingEnabled() && hasAdvertising(message, settings)) {
            return "advertising";
        }

        if (settings.profanityEnabled() && hasProfanity(message, settings.profanityWords())) {
            return "profanity";
        }

        return null;
    }

    private boolean hasAdvertising(String message, ChatFilterSettings settings) {
        String normalized = normalizeAdvertising(message);

        if (settings.blockIps()) {
            Matcher matcher = IPV4_PATTERN.matcher(normalized);
            while (matcher.find()) {
                if (validIp(matcher)) {
                    return true;
                }
            }
        }

        if (settings.blockDiscordInvites()
                && DISCORD_INVITE_PATTERN.matcher(normalized).find()) {
            return true;
        }

        if (!settings.blockDomains()) {
            return false;
        }

        Matcher matcher = DOMAIN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String domain = cleanDomain(matcher.group(1));
            if (!isAllowedDomain(domain, settings.allowedDomains())) {
                return true;
            }
        }
        return false;
    }

    private boolean validIp(Matcher matcher) {
        for (int i = 1; i <= 4; i++) {
            int value;
            try {
                value = Integer.parseInt(matcher.group(i));
            } catch (NumberFormatException e) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isAllowedDomain(String domain, List<String> allowedDomains) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        String normalized = cleanDomain(domain);
        for (String allowed : allowedDomains) {
            String allowedDomain = cleanDomain(allowed);
            if (allowedDomain.isBlank()) {
                continue;
            }
            if (normalized.equals(allowedDomain) || normalized.endsWith("." + allowedDomain)) {
                return true;
            }
        }
        return false;
    }

    private String cleanDomain(String value) {
        String domain = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        while (domain.startsWith("www.")) {
            domain = domain.substring(4);
        }
        return domain;
    }

    private boolean hasProfanity(String message, List<String> configuredWords) {
        Set<String> words = normalizedWords(configuredWords);
        if (words.isEmpty()) {
            return false;
        }

        List<String> tokens = normalizedTokens(message);
        for (String token : tokens) {
            String collapsed = collapseRuns(token);
            for (String word : words) {
                if (collapsed.equals(word) || (word.length() >= 4 && collapsed.contains(word))) {
                    return true;
                }
            }
        }

        String compact = collapseRuns(normalizeCompact(message));
        for (String word : words) {
            if (word.length() >= 3 && compact.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizedWords(List<String> configuredWords) {
        Set<String> words = new HashSet<>();
        for (String word : configuredWords) {
            String normalized = collapseRuns(normalizeCompact(word));
            if (normalized.length() >= 2) {
                words.add(normalized);
                String withoutVowels = withoutEnglishVowels(normalized);
                if (withoutVowels.length() >= 3) {
                    words.add(withoutVowels);
                }
            }
        }
        return words;
    }

    private String withoutEnglishVowels(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == 'a' || current == 'e' || current == 'i' || current == 'o' || current == 'u') {
                continue;
            }
            out.append(current);
        }
        return out.toString();
    }

    private boolean isLagTest(String message, ChatFilterSettings settings) {
        List<String> tokens = normalizedTokens(message);
        if (tokens.size() < settings.lagTestMinTokens()) {
            return false;
        }

        for (char dominant : dominantRepeatedChars(tokens, settings.lagTestMinRepeatedLength())) {
            int repeated = 0;
            int noise = 0;
            for (String token : tokens) {
                if (isRepeatedToken(token, dominant, settings.lagTestMinRepeatedLength())) {
                    repeated++;
                    noise++;
                } else if (isShortSameCharToken(token, dominant)) {
                    noise++;
                }
            }
            if (repeated >= settings.lagTestMinRepeatedTokens() && noise == tokens.size()) {
                return true;
            }
        }
        return false;
    }

    private Set<Character> dominantRepeatedChars(List<String> tokens, int minLength) {
        Set<Character> characters = new HashSet<>();
        for (String token : tokens) {
            if (token.length() < minLength) {
                continue;
            }
            Character dominant = dominantChar(token);
            if (dominant != null && repeatedRatio(token, dominant) >= 0.85D) {
                characters.add(dominant);
            }
        }
        return characters;
    }

    private boolean isRepeatedToken(String token, char dominant, int minLength) {
        return token.length() >= minLength && repeatedRatio(token, dominant) >= 0.85D;
    }

    private boolean isShortSameCharToken(String token, char dominant) {
        if (token.length() > 2 || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) != dominant) {
                return false;
            }
        }
        return true;
    }

    private Character dominantChar(String token) {
        int best = 0;
        char bestChar = 0;
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            int count = 0;
            for (int j = 0; j < token.length(); j++) {
                if (token.charAt(j) == current) {
                    count++;
                }
            }
            if (count > best) {
                best = count;
                bestChar = current;
            }
        }
        return best == 0 ? null : bestChar;
    }

    private double repeatedRatio(String token, char character) {
        int count = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == character) {
                count++;
            }
        }
        return (double) count / token.length();
    }

    private List<String> normalizedTokens(String message) {
        String normalized = normalizeLeet(message);
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String normalizeCompact(String message) {
        String normalized = normalizeLeet(message);
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                out.append(current);
            }
        }
        return out.toString();
    }

    private String normalizeLeet(String message) {
        // Normalize only the filter's comparison copy. The chat itself is never rewritten.
        // NFKC folds Arabic presentation forms/ligatures; dropping combining marks makes
        // harakat optional for matching, while preserving Arabic letters and punctuation.
        String normalized = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
                .replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\u0640') continue; // tatweel/kashida
            out.append(mapLeet(mapArabic(c)));
        }
        return out.toString();
    }

    private char mapArabic(char value) {
        return switch (value) {
            case '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627'; // alef variants
            case '\u0649' -> '\u064a'; // alef maqsura / ya
            case '\u0624' -> '\u0648'; // waw with hamza
            case '\u0626' -> '\u064a'; // ya with hamza
            default -> value;
        };
    }

    private char mapLeet(char value) {
        return switch (value) {
            case '@', '4' -> 'a';
            case '3' -> 'e';
            case '1', '!' -> 'i';
            case '0' -> 'o';
            case '$', '5' -> 's';
            case '7' -> 't';
            case '8' -> 'b';
            default -> value;
        };
    }

    private String collapseRuns(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        char previous = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != previous) {
                out.append(current);
                previous = current;
            }
        }
        return out.toString();
    }

    private String normalizeAdvertising(String message) {
        String normalized = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        normalized = normalized.replaceAll("(?i)\\s*(?:\\[\\s*dot\\s*]|\\(\\s*dot\\s*\\)|\\{\\s*dot\\s*}|\\s+dot\\s+)\\s*", ".");
        normalized = normalized.replaceAll("\\s*[\\[({<]\\s*\\.\\s*[\\])}>]\\s*", ".");
        normalized = normalized.replaceAll("\\s*\\.\\s*", ".");
        return normalized;
    }
}
