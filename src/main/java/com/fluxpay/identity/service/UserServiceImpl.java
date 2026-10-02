package com.fluxpay.identity.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.domain.UserAccount;
import com.fluxpay.identity.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyHash;

    public UserServiceImpl(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
    }

    @Override
    @Transactional
    public void reserveEmail(String email) {
        String normalized = UserService.normalizeEmail(email);
        users.lockEmail(normalized);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
    }

    @Override
    public HashedPassword hashPassword(String rawPassword) {
        PasswordPolicy.validate(rawPassword);
        return new HashedPassword(passwordEncoder.encode(rawPassword));
    }

    @Override
    @Transactional
    public DashboardPrincipal createMerchantOwner(String email, HashedPassword password, UUID merchantId) {
        String normalized = UserService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        UserAccount account = UserAccount.merchantOwner(normalized, password.value(), merchantId, Instant.now(clock));
        try {
            return toPrincipal(users.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
    }

    @Override
    public DashboardPrincipal authenticate(String email, String rawPassword) {
        Optional<UserAccount> account = users.findByEmail(UserService.normalizeEmail(email));
        String hash = account.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = rawPassword != null && passwordEncoder.matches(rawPassword, hash);
        if (account.isEmpty() || !matches) {
            throw FluxpayException.unauthenticated("INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return toPrincipal(account.get());
    }

    @Override
    public void ensurePlatformAdmin(String email, String rawPassword) {
        String normalized = UserService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            log.info("Platform admin bootstrap skipped: user already exists");
            return;
        }
        HashedPassword password = hashPassword(rawPassword);
        users.save(UserAccount.platformAdmin(normalized, password.value(), Instant.now(clock)));
        log.info("Platform admin created");
    }

    private static DashboardPrincipal toPrincipal(UserAccount account) {
        return new DashboardPrincipal(account.getId(), account.getEmail(), account.getRole(), account.getMerchantId());
    }

    private static FluxpayException emailTaken() {
        return FluxpayException.conflict("EMAIL_TAKEN", "An account with this email already exists");
    }
}
