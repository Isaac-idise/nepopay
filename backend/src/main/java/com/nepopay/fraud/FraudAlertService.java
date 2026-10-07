package com.nepopay.fraud;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepopay.transaction.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saves the "why" behind a risk score so an admin (and you, in the demo) can read it. */
@Service
public class FraudAlertService {

    private final FraudAlertRepository repository;
    private final ObjectMapper mapper;

    public FraudAlertService(FraudAlertRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** held = OPEN (waiting for an admin); otherwise INFO (processed, logged for monitoring). */
    @Transactional(propagation = Propagation.MANDATORY)
    public FraudAlert record(Transaction tx, FraudAssessment assessment, boolean held) {
        try {
            String json = mapper.writeValueAsString(assessment.hits());
            return repository.save(new FraudAlert(tx, assessment.score(), assessment.level(), json,
                    held ? AlertStatus.OPEN : AlertStatus.INFO));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise fraud reasons", e);
        }
    }
}
