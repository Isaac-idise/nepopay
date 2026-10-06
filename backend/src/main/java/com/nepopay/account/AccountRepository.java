package com.nepopay.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountType(AccountType type);

    /** Scalar lookup (no entity loaded), for code that will lock the row through the ledger. */
    @Query("select a.id from Account a where a.accountType = :type")
    Optional<UUID> findIdByAccountType(@Param("type") AccountType type);

    Optional<Account> findByUserIdAndAccountType(UUID userId, AccountType type);

    /** Scalar projection: does NOT load the Account entity, so a later FOR UPDATE read is always fresh. */
    @Query("select new com.nepopay.account.AccountRef(a.id, a.status) from Account a "
            + "where a.user.id = :userId and a.accountType = com.nepopay.account.AccountType.USER")
    Optional<AccountRef> findUserAccountRef(@Param("userId") UUID userId);

    /** SELECT ... FOR UPDATE: other transactions wanting this row wait until we commit or roll back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
