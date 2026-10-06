package com.nepopay.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);

    /** Row lock on the user: serialises PIN attempts and one sender's transfers. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            select new com.nepopay.user.UserSearchResult(u.username, u.firstName, u.lastName, a.accountNumber)
            from Account a join a.user u
            where a.accountType = com.nepopay.account.AccountType.USER
              and u.status = com.nepopay.user.UserStatus.ACTIVE
              and u.id <> :selfId
              and (lower(u.username) like :prefix escape '!'
                or lower(u.firstName) like :prefix escape '!'
                or lower(u.lastName) like :prefix escape '!'
                or lower(concat(u.firstName, ' ', u.lastName)) like :prefix escape '!')
            order by u.username
            """,
            countQuery = """
            select count(a)
            from Account a join a.user u
            where a.accountType = com.nepopay.account.AccountType.USER
              and u.status = com.nepopay.user.UserStatus.ACTIVE
              and u.id <> :selfId
              and (lower(u.username) like :prefix escape '!'
                or lower(u.firstName) like :prefix escape '!'
                or lower(u.lastName) like :prefix escape '!'
                or lower(concat(u.firstName, ' ', u.lastName)) like :prefix escape '!')
            """)
    Page<UserSearchResult> search(@Param("selfId") UUID selfId, @Param("prefix") String prefix, Pageable pageable);
}
