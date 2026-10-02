package com.fluxpay.merchants.service;

import com.fluxpay.common.id.Base62;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.persistence.MerchantRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantOnboardingServiceImpl implements MerchantOnboardingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantRepository merchants;
    private final UserService userService;
    private final MerchantProperties properties;
    private final Clock clock;

    public MerchantOnboardingServiceImpl(
            MerchantRepository merchants, UserService userService, MerchantProperties properties, Clock clock) {
        this.merchants = merchants;
        this.userService = userService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DashboardPrincipal signUp(SignupCommand command) {
        userService.reserveEmail(command.email());
        String name = command.businessName().trim();
        Merchant merchant =
                new Merchant(name, uniqueSlug(name), properties.defaultPlatformFeeBps(), Instant.now(clock));
        merchants.saveAndFlush(merchant);
        return userService.createMerchantOwner(command.email(), command.password(), merchant.getId());
    }

    private String uniqueSlug(String businessName) {
        String base = SlugGenerator.baseSlug(businessName);
        merchants.lockSlug(base);
        String candidate = base;
        while (merchants.existsBySlug(candidate)) {
            candidate = base + "-" + Base62.random(6, RANDOM).toLowerCase(Locale.ROOT);
        }
        return candidate;
    }
}
