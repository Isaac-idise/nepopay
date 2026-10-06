package com.nepopay.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void nairaConvertsToKobo() {
        assertThat(Money.ofNaira(20_000).kobo()).isEqualTo(2_000_000L);
        assertThat(Money.fromNaira(new BigDecimal("20000.50")).kobo()).isEqualTo(2_000_050L);
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> Money.fromNaira(new BigDecimal("1.005")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trailingZerosAreFine() {
        assertThat(Money.fromNaira(new BigDecimal("10.000")).kobo()).isEqualTo(1_000L);
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThatThrownBy(() -> Money.ofKobo(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.fromNaira(new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addAndSubtract() {
        Money balance = Money.ofNaira(100_000);
        Money after = balance.minus(Money.ofNaira(20_000));
        assertThat(after).isEqualTo(Money.ofNaira(80_000));
        assertThat(after.plus(Money.ofNaira(20_000))).isEqualTo(balance);
    }

    @Test
    void cannotSubtractMoreThanYouHave() {
        assertThatThrownBy(() -> Money.ofNaira(10).minus(Money.ofNaira(11)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowIsDetected() {
        assertThatThrownBy(() -> Money.ofKobo(Long.MAX_VALUE).plus(Money.ofKobo(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsForDisplay() {
        assertThat(Money.ofKobo(2_000_050L).toString()).isEqualTo("₦20,000.50");
        assertThat(Money.ZERO.toString()).isEqualTo("₦0.00");
    }
}
