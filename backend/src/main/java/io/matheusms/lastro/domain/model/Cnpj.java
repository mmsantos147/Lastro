package io.matheusms.lastro.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * CNPJ (Cadastro Nacional da Pessoa Jurídica): Brazilian company registration number.
 * <p>
 * Stored as 14 characters without punctuation. Supports the alphanumeric format introduced in July 2026
 * (first 12 characters may be letters; the 2 check digits are always numeric) and validates the check digits,
 * so typos are rejected on input.
 */
public record Cnpj(String value) {

    private static final Pattern FORMAT = Pattern.compile("[0-9A-Z]{12}[0-9]{2}");
    private static final Pattern PUNCTUATION = Pattern.compile("[./\\-\\s]");
    private static final int[] FIRST_DIGIT_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_DIGIT_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /**
     * Accepts the value with or without punctuation ({@code 11.222.333/0001-81} or {@code 11222333000181}).
     */
    public Cnpj {
        Objects.requireNonNull(value, "value must not be null");
        value = PUNCTUATION.matcher(value).replaceAll("").toUpperCase();
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid CNPJ format: " + value);
        }
        if (value.chars().distinct().count() == 1) {
            throw new IllegalArgumentException("Invalid CNPJ: all characters are equal");
        }
        if (!hasValidCheckDigits(value)) {
            throw new IllegalArgumentException("Invalid CNPJ check digits: " + value);
        }
    }

    private static boolean hasValidCheckDigits(String cnpj) {
        int first = checkDigit(cnpj, FIRST_DIGIT_WEIGHTS);
        int second = checkDigit(cnpj, SECOND_DIGIT_WEIGHTS);
        return cnpj.charAt(12) - '0' == first && cnpj.charAt(13) - '0' == second;
    }

    /** Each character is worth its ASCII code minus 48 ('0' = 0, 'A' = 17), as defined by Receita Federal. */
    private static int checkDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    @Override
    public String toString() {
        return value;
    }
}
