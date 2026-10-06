package com.nepopay.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Fictional 10-digit account numbers: "99" + 7 random digits + Luhn check digit.
 * The 99 prefix keeps NepOpay numbers visibly separate from system accounts (00...).
 * The check digit catches typos, the same idea real banks use.
 */
@Component
public class AccountNumberGenerator {

    private static final String PREFIX = "99";
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String base = PREFIX + String.format("%07d", random.nextInt(10_000_000));
        return base + checkDigit(base);
    }

    static int checkDigit(String base) {
        int sum = 0;
        boolean doubleIt = true; // rightmost digit of the base is doubled because the check digit follows it
        for (int i = base.length() - 1; i >= 0; i--) {
            int d = base.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("\\d{2,}")) {
            return false;
        }
        return checkDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }
}
