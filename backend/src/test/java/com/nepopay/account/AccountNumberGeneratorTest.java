package com.nepopay.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

    private final AccountNumberGenerator generator = new AccountNumberGenerator();

    @Test
    void generatesTenDigitsStartingWith99() {
        for (int i = 0; i < 1000; i++) {
            String n = generator.generate();
            assertThat(n).hasSize(10).startsWith("99").containsOnlyDigits();
        }
    }

    @Test
    void everyGeneratedNumberPassesTheCheckDigitTest() {
        for (int i = 0; i < 1000; i++) {
            assertThat(AccountNumberGenerator.isValid(generator.generate())).isTrue();
        }
    }

    @Test
    void luhnKnownExample() {
        assertThat(AccountNumberGenerator.isValid("79927398713")).isTrue();
        assertThat(AccountNumberGenerator.isValid("79927398710")).isFalse();
    }

    @Test
    void aSingleMistypedDigitIsDetected() {
        String n = generator.generate();
        char wrong = n.charAt(4) == '7' ? '3' : '7';
        String typo = n.substring(0, 4) + wrong + n.substring(5);
        assertThat(AccountNumberGenerator.isValid(typo)).isFalse();
    }
}
