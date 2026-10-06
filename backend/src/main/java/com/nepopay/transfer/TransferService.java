package com.nepopay.transfer;

import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.PinService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one transfer attempt. Deliberately NOT transactional itself: each step below
 * runs in its own transaction, and only the money step (TransferProcessor.execute) can roll back money.
 *
 *   1. claim idempotency key      (own tx, committed immediately)
 *   2. verify PIN + lockout       (own tx, failures are committed)
 *   3. move the money             (one atomic tx, completes the key)
 *   on failure: release the key so the user can retry
 */
@Service
public class TransferService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final IdempotencyService idempotency;
    private final PinService pins;
    private final TransferProcessor processor;
    private final AuditService audit;

    public TransferService(IdempotencyService idempotency, PinService pins,
                           TransferProcessor processor, AuditService audit) {
        this.idempotency = idempotency;
        this.pins = pins;
        this.processor = processor;
        this.audit = audit;
    }

    public TransferResponse transfer(UUID userId, TransferRequest req, String idempotencyKey) {
        if (idempotencyKey == null || !KEY_FORMAT.matcher(idempotencyKey).matches()) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key header is required (8-64 letters, digits, '-' or '_').");
        }

        IdempotencyService.Begin begin = idempotency.begin(userId, idempotencyKey, requestHash(req));
        if (begin.replayReference() != null) {
            return processor.replay(begin.replayReference());
        }

        try {
            pins.verify(userId, req.pin());
            return processor.execute(userId, req, idempotencyKey);
        } catch (RuntimeException e) {
            idempotency.release(userId, idempotencyKey);
            if (e instanceof NepOpayException ne
                    && ne.getErrorCode() != ErrorCode.INVALID_PIN
                    && ne.getErrorCode() != ErrorCode.PIN_LOCKED) {
                audit.record(userId, "TRANSFER_FAILED", "TRANSFER", null, Map.of("errorCode", ne.getErrorCode().name()));
            }
            throw e;
        }
    }

    /** Fingerprint of WHAT is being asked (not the PIN). Same key + different fingerprint = client bug. */
    private static String requestHash(TransferRequest r) {
        String canonical = r.recipientUsername().trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "")
                + "|" + r.amountKobo() + "|" + (r.description() == null ? "" : r.description().trim());
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
