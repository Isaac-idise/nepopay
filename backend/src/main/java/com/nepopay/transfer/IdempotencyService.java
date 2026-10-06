package com.nepopay.transfer;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes "press Send twice" safe.
 * 1. begin():    claims the key in its own committed transaction (so a parallel request sees it at once).
 * 2. complete(): runs INSIDE the money transaction, so "key done" and "money moved" commit together or not at all.
 * 3. release():  frees the key if the transfer failed, so the user can retry with the same key.
 */
@Service
public class IdempotencyService {

    /** replayReference != null means: this exact request already succeeded; return that result. */
    public record Begin(String replayReference) {
    }

    private static final Duration STALE_AFTER = Duration.ofSeconds(60);

    private final IdempotencyRepository repo;

    public IdempotencyService(IdempotencyRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Begin begin(UUID userId, String key, String requestHash) {
        Optional<IdempotencyRecord> existing = repo.findByUserIdAndIdemKey(userId, key);
        if (existing.isPresent()) {
            IdempotencyRecord r = existing.get();
            if (!r.getRequestHash().equals(requestHash)) {
                throw new NepOpayException(ErrorCode.IDEMPOTENCY_CONFLICT);
            }
            if (r.isCompleted()) {
                return new Begin(r.getTransactionReference());
            }
            if (r.getCreatedAt().isAfter(Instant.now().minus(STALE_AFTER))) {
                throw new NepOpayException(ErrorCode.DUPLICATE_REQUEST); // first attempt still running
            }
            repo.delete(r); // abandoned attempt (for example the server crashed): take over
            repo.flush();
        }
        try {
            repo.saveAndFlush(new IdempotencyRecord(userId, key, requestHash));
        } catch (DataIntegrityViolationException e) {
            throw new NepOpayException(ErrorCode.DUPLICATE_REQUEST); // lost a race with an identical request
        }
        return new Begin(null);
    }

    @Transactional
    public void complete(UUID userId, String key, String reference) {
        repo.findByUserIdAndIdemKey(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency record missing"))
                .complete(reference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID userId, String key) {
        repo.deleteByStatus(userId, key, IdempotencyRecord.Status.IN_PROGRESS);
    }
}
