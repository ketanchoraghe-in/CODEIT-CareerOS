package com.codeit.careeros.service;

import com.codeit.careeros.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprint 5 CV text extraction. Apache Tika safely parses PDF/DOC/DOCX
 * (never executes embedded content); only plain text plus the contact
 * basics needed for display are returned — nothing else is retained.
 */
@Slf4j
@Service
public class CvParsingService {

    /** Cap so a hostile file cannot blow up memory or the database. */
    static final int MAX_TEXT_CHARS = 100_000;

    private static final Pattern EMAIL =
            Pattern.compile("([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE =
            Pattern.compile("(\\+?\\d[\\d\\s\\-().]{7,}\\d)");

    private final Tika tika = new Tika();

    public record ParsedCv(String text, String name, String email, String phone) {
    }

    public ParsedCv parse(byte[] bytes, String filename) {
        String text;
        try {
            text = tika.parseToString(new ByteArrayInputStream(bytes));
        } catch (TikaException | java.io.IOException e) {
            log.warn("CV parsing failed for {}", filename, e);
            throw BusinessException.badRequest("Could not read text from this CV file. "
                    + "Please upload a valid PDF, DOC or DOCX file.");
        }
        if (text == null || text.isBlank()) {
            throw BusinessException.badRequest("No readable text found in this CV file. "
                    + "Scanned images without text cannot be analysed.");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        return new ParsedCv(text.strip(), guessName(text), firstMatch(EMAIL, text), findPhoneNumber(text));
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).replaceAll("\\s+", " ").strip();
        return value.length() > 40 ? null : value;
    }

    /**
     * Phone numbers need 10–15 digits (E.164 range). The raw pattern also
     * matches year ranges like "2020 - 2024", so candidates with fewer
     * digits are skipped instead of being stored as the candidate's phone.
     */
    static String findPhoneNumber(String text) {
        Matcher matcher = PHONE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1).replaceAll("\\s+", " ").strip();
            if (value.length() > 40) {
                continue;
            }
            String digits = value.replaceAll("\\D", "");
            if (digits.length() >= 10 && digits.length() <= 15) {
                return value;
            }
        }
        return null;
    }

    /** Headings that must never be mistaken for the candidate's name. */
    private static final Pattern HEADING_LIKE = Pattern.compile(
            "\\b(summary|objective|profile|about|contact|skills?|experience|employment|education|"
                    + "projects?|certifications?|courses?|resume|curriculum|vitae)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Best-effort display name: the first text line that looks like a human
     * name (short, mostly letters, no digits/@, not a section heading).
     * Never trusted for identity.
     */
    static String guessName(String text) {
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.length() > 60 || line.matches(".*[\\d@].*")) {
                continue;
            }
            if (HEADING_LIKE.matcher(line).find()) {
                continue;
            }
            String[] words = line.split("\\s+");
            if (words.length >= 2 && words.length <= 4
                    && line.matches("[\\p{L} .,'-]+")) {
                return line;
            }
        }
        return null;
    }
}
