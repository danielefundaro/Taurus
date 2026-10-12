package com.fundaro.zodiac.taurus.service.loansheet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Text helpers of the loan sheet: formatting of dates and amounts and normalization of the characters printed with
 * the standard PDF fonts.
 */
public final class LoanSheetText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private LoanSheetText() {
    }

    /**
     * Returns whether the standard PDF fonts can print the character with WinAnsi encoding.
     */
    public static boolean printable(char character) {
        if (character == '\n' || character == '\r' || character == '\t') return true;
        return !Character.isISOControl(character) && WIN_ANSI.newEncoder().canEncode(character);
    }

    /**
     * Replaces the characters the standard PDF fonts cannot print. Used for data coming from other features.
     */
    public static String printable(String value) {
        if (value == null) return null;
        CharsetEncoder encoder = WIN_ANSI.newEncoder();
        StringBuilder result = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            if (character == '\n') {
                result.append('\n');
            } else if (character == '\r' || character == '\t') {
                result.append(' ');
            } else if (Character.isISOControl(character)) {
                continue;
            } else {
                result.append(encoder.canEncode(character) ? character : '?');
            }
        }
        return result.toString();
    }

    public static String date(LocalDate value) {
        return value == null ? null : DATE.format(value);
    }

    public static String amount(BigDecimal value) {
        if (value == null) return null;
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ITALY));
        return format.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Agreed unit value of an item: its estimated unit value, kept only when expressed in euro like the sheet columns.
     */
    public static BigDecimal euroUnitValue(BigDecimal estimatedUnitValue, String currency) {
        return estimatedUnitValue != null && "EUR".equalsIgnoreCase(currency) ? estimatedUnitValue : null;
    }

    /**
     * Agreed value of a goods row: the total, followed by the unit value when the quantity is greater than one.
     */
    public static String agreedValue(BigDecimal unitValue, int quantity) {
        if (unitValue == null) return null;
        if (quantity <= 1) return amount(unitValue);
        return amount(unitValue.multiply(BigDecimal.valueOf(quantity))) + " (" + amount(unitValue) + " cad.)";
    }

    public static String serialOrInventory(String serialNumber, String inventoryNumber) {
        return blankToNull(serialNumber) == null ? inventoryNumber : join(" / ", serialNumber, inventoryNumber);
    }

    /**
     * Joins the non-blank values; returns {@code null} when none is left.
     */
    public static String join(String separator, String... values) {
        String joined = Stream.of(values).map(LoanSheetText::blankToNull).filter(Objects::nonNull).collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
