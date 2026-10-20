package com.fundaro.zodiac.taurus.service.signature;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Italian tax code of a person: format, including the letters that replace digits in homonymous codes, and check
 * character.
 */
final class ItalianTaxCode {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
    private static final int[] ODD_DIGITS = { 1, 0, 5, 7, 9, 13, 15, 17, 19, 21 };
    private static final int[] ODD_LETTERS = { 1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23 };

    private ItalianTaxCode() {
    }

    /**
     * The code in upper case and without spaces.
     */
    static String normalize(String value) {
        return value == null ? null : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    static boolean valid(String value) {
        String code = normalize(value);
        if (code == null || !FORMAT.matcher(code).matches()) return false;
        int sum = 0;
        for (int index = 0; index < 15; index++) {
            char character = code.charAt(index);
            boolean digit = Character.isDigit(character);
            int position = digit ? character - '0' : character - 'A';
            // Positions are counted from one: the first character is in an odd position.
            if (index % 2 == 0) sum += digit ? ODD_DIGITS[position] : ODD_LETTERS[position];
            else sum += position;
        }
        return code.charAt(15) == (char) ('A' + sum % 26);
    }
}
