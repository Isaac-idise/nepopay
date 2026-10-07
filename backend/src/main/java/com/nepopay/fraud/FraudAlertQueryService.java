package com.nepopay.fraud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepopay.account.Account;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.fraud.FraudDtos.AlertView;
import com.nepopay.fraud.FraudDtos.PartyView;
import com.nepopay.fraud.FraudDtos.TransactionSummary;
import com.nepopay.transaction.Transaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudAlertQueryService {

    private final FraudAlertRepository repository;
    private final ObjectMapper mapper;

    public FraudAlertQueryService(FraudAlertRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** status == null means "all". */
    @Transactional(readOnly = true)
    public Page<AlertView> page(AlertStatus status, Pageable pageable) {
        Page<FraudAlert> page = status == null ? repository.findEvery(pageable) : repository.findByStatus(status, pageable);
        return page.map(this::view);
    }

    @Transactional(readOnly = true)
    public AlertView get(UUID id) {
        return view(repository.findDetail(id)
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "Alert not found.")));
    }

    AlertView view(FraudAlert a) {
        Transaction t = a.getTransaction();
        TransactionSummary summary = new TransactionSummary(t.getReference(), t.getType().name(), t.getStatus().name(),
                t.getAmountKobo(), t.getDescription(), t.getCreatedAt(), party(t.getSender()), party(t.getReceiver()));
        return new AlertView(a.getId(), a.getStatus().name(), a.getRiskScore(), a.getRiskLevel().name(),
                reasons(a), summary, a.getCreatedAt(), a.getReviewedAt(), a.getReviewNote());
    }

    private List<RuleHit> reasons(FraudAlert a) {
        try {
            return mapper.readValue(a.getReasons(), new TypeReference<List<RuleHit>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Could not read fraud reasons", e);
        }
    }

    private static PartyView party(Account acc) {
        return acc.getUser() == null
                ? new PartyView(null, "NepOpay")
                : new PartyView(acc.getUser().getUsername(), acc.getUser().getDisplayName());
    }
}
