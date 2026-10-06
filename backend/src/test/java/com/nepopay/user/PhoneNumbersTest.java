package com.nepopay.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void plus234FormBecomesLocalForm() {
        assertThat(PhoneNumbers.normalize("+2348031234567")).isEqualTo("08031234567");
    }

    @Test
    void localFormIsUnchanged() {
        assertThat(PhoneNumbers.normalize("08031234567")).isEqualTo("08031234567");
    }
}
