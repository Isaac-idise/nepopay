package com.nepopay.beneficiary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    @Query("select b from Beneficiary b join fetch b.beneficiaryAccount a join fetch a.user "
            + "where b.user.id = :userId order by b.createdAt desc")
    List<Beneficiary> findAllForUser(@Param("userId") UUID userId);

    boolean existsByUserIdAndBeneficiaryAccountId(UUID userId, UUID accountId);

    long countByUserId(UUID userId);

    @Query("select b from Beneficiary b where b.user.id = :userId "
            + "and lower(b.beneficiaryAccount.user.username) = lower(:username)")
    Optional<Beneficiary> findByUserAndUsername(@Param("userId") UUID userId, @Param("username") String username);

    @Modifying
    @Query("delete from Beneficiary b where b.user.id = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
