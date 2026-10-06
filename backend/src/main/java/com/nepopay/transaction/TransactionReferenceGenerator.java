package com.nepopay.transaction;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/** Public, human-friendly transaction reference such as NPY-20261005-8F3K2A7Q. */
@Component
public class TransactionReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("NPY-")
                .append(LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE))
                .append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
