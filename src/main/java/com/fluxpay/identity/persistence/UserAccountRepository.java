package com.fluxpay.identity.persistence;

import com.fluxpay.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Blocks until no other transaction holds the lock for this email; released at commit/rollback. */
    @Query(
            value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('email:' || :email))) AS l",
            nativeQuery = true)
    Integer lockEmail(@Param("email") String email);
}
