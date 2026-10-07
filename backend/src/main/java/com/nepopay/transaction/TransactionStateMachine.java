package com.nepopay.transaction;

import static com.nepopay.transaction.TransactionStatus.*;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The ONLY rules for how a transaction may change status. Everything else must go through require().
 *
 *   PENDING --> PROCESSING --> SUCCESSFUL --> REVERSED
 *      |            |  \
 *      |            |   --> FLAGGED --> SUCCESSFUL (admin approves)
 *      v            v            \----> REVERSED   (admin rejects)
 *   FAILED       FAILED
 *
 * FAILED and REVERSED are final.
 */
public final class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(PROCESSING, FAILED),
            PROCESSING, EnumSet.of(SUCCESSFUL, FAILED, FLAGGED),
            FLAGGED, EnumSet.of(SUCCESSFUL, REVERSED),
            SUCCESSFUL, EnumSet.of(REVERSED),
            FAILED, EnumSet.noneOf(TransactionStatus.class),
            REVERSED, EnumSet.noneOf(TransactionStatus.class));

    private TransactionStateMachine() {
    }

    public static boolean canTransition(TransactionStatus from, TransactionStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void require(TransactionStatus from, TransactionStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal transaction status change: " + from + " -> " + to);
        }
    }
}
