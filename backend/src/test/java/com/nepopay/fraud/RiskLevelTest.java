package com.nepopay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RiskLevelTest {

    @ParameterizedTest
    @CsvSource({"0,LOW", "29,LOW", "30,MEDIUM", "59,MEDIUM", "60,HIGH", "79,HIGH", "80,CRITICAL", "100,CRITICAL"})
    void scoreBands(int score, RiskLevel expected) {
        assertThat(RiskLevel.of(score)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"LOW,false", "MEDIUM,false", "HIGH,true", "CRITICAL,true"})
    void onlyHighAndAboveHoldFunds(RiskLevel level, boolean holds) {
        assertThat(level.holds()).isEqualTo(holds);
    }
}
