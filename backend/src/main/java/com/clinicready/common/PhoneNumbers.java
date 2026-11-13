package com.clinicready.common;

import java.util.regex.Pattern;

/**
 * Format check for phone numbers typed by staff. A number that passes is well formed, nothing more:
 * it says nothing about whether anyone answers it.
 */
public final class PhoneNumbers {

    /** Optional leading +, then digits, spaces, dots, dashes or parentheses; 8 to 15 digits in total. */
    public static final String REGEX = "^[+]?[0-9 ().-]{8,25}$";

    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PhoneNumbers() {
    }

    public static boolean isWellFormed(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            return false;
        }
        long digits = value.chars().filter(Character::isDigit).count();
        return digits >= 8 && digits <= 15;
    }
}
