package com.nepopay.beneficiary;

import com.nepopay.account.Account;
import com.nepopay.account.AccountRef;
import com.nepopay.account.AccountRepository;
import com.nepopay.audit.AuditService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import com.nepopay.user.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {

    private static final int MAX_PER_USER = 50;

    private final BeneficiaryRepository repo;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final AuditService audit;

    public BeneficiaryService(BeneficiaryRepository repo, UserRepository users,
                              AccountRepository accounts, AuditService audit) {
        this.repo = repo;
        this.users = users;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryView> list(UUID userId) {
        return repo.findAllForUser(userId).stream().map(b -> view(b.getBeneficiaryAccount(), b)).toList();
    }

    @Transactional
    public BeneficiaryView add(UUID userId, AddBeneficiaryRequest req) {
        User target = users.findByUsernameIgnoreCase(stripAt(req.username()))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new NepOpayException(ErrorCode.RECIPIENT_UNAVAILABLE));
        if (target.getId().equals(userId)) {
            throw new NepOpayException(ErrorCode.SELF_TRANSFER);
        }
        AccountRef ref = accounts.findUserAccountRef(target.getId())
                .orElseThrow(() -> new NepOpayException(ErrorCode.RECIPIENT_UNAVAILABLE));
        if (repo.existsByUserIdAndBeneficiaryAccountId(userId, ref.id())) {
            throw new NepOpayException(ErrorCode.BENEFICIARY_EXISTS);
        }
        if (repo.countByUserId(userId) >= MAX_PER_USER) {
            throw new NepOpayException(ErrorCode.LIMIT_EXCEEDED, "You can save up to " + MAX_PER_USER + " beneficiaries.");
        }

        Account account = accounts.findById(ref.id()).orElseThrow();
        String nickname = req.nickname() == null || req.nickname().isBlank() ? null : req.nickname().trim();
        Beneficiary saved;
        try {
            saved = repo.saveAndFlush(new Beneficiary(users.getReferenceById(userId), account, nickname));
        } catch (DataIntegrityViolationException e) { // two identical requests raced
            throw new NepOpayException(ErrorCode.BENEFICIARY_EXISTS);
        }
        audit.record(userId, "BENEFICIARY_ADDED", "BENEFICIARY", target.getUsername(), Map.of("username", target.getUsername()));
        return view(account, saved);
    }

    @Transactional
    public void remove(UUID userId, String username) {
        Beneficiary b = repo.findByUserAndUsername(userId, stripAt(username))
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "Beneficiary not found."));
        repo.delete(b);
        audit.record(userId, "BENEFICIARY_REMOVED", "BENEFICIARY", stripAt(username).toLowerCase(), null);
    }

    @Transactional
    public int deleteAll(UUID userId) {
        return repo.deleteAllForUser(userId);
    }

    private static BeneficiaryView view(Account a, Beneficiary b) {
        String number = a.getAccountNumber();
        return new BeneficiaryView(a.getUser().getUsername(), a.getUser().getDisplayName(),
                number.substring(number.length() - 4), b.getNickname(), b.getCreatedAt());
    }

    private static String stripAt(String s) {
        String t = s.trim();
        return t.startsWith("@") ? t.substring(1) : t;
    }
}
