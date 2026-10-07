package com.nepopay.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransactionStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,PROCESSING", "PENDING,FAILED",
            "PROCESSING,SUCCESSFUL", "PROCESSING,FAILED", "PROCESSING,FLAGGED",
            "FLAGGED,SUCCESSFUL", "FLAGGED,REVERSED",
            "SUCCESSFUL,REVERSED"})
    void allowedTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(TransactionStateMachine.canTransition(from, to)).isTrue();
        TransactionStateMachine.require(from, to); // must not throw
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING,SUCCESSFUL", "PENDING,FLAGGED", "PROCESSING,REVERSED", "PROCESSING,PENDING",
            "SUCCESSFUL,FAILED", "SUCCESSFUL,FLAGGED", "SUCCESSFUL,PROCESSING",
            "FLAGGED,FAILED", "FLAGGED,PROCESSING",
            "FAILED,SUCCESSFUL", "FAILED,PROCESSING", "FAILED,REVERSED",
            "REVERSED,SUCCESSFUL", "REVERSED,FLAGGED", "REVERSED,FAILED"})
    void forbiddenTransitions(TransactionStatus from, TransactionStatus to) {
        assertThat(TransactionStateMachine.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> TransactionStateMachine.require(from, to)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({"FAILED", "REVERSED"})
    void finalStatesHaveNoExit(TransactionStatus finalState) {
        for (TransactionStatus to : TransactionStatus.values()) {
            assertThat(TransactionStateMachine.canTransition(finalState, to)).isFalse();
        }
    }
}
