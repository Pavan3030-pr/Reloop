package app.reloop.config;

import app.reloop.entity.User;
import app.reloop.entity.UserProfile;
import app.reloop.repository.UserProfileRepository;
import app.reloop.repository.UserRepository;
import app.reloop.security.PasswordService;
import app.reloop.security.Role;
import app.reloop.security.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps the first ADMIN account when ADMIN_EMAIL and ADMIN_PASSWORD are provided.
 * No default password exists anywhere in the codebase.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordService passwordService;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin admin = properties.admin();
        if (admin == null || admin.email() == null || admin.email().isBlank()
                || admin.password() == null || admin.password().isBlank()) {
            log.info("Admin bootstrap skipped (set ADMIN_EMAIL and ADMIN_PASSWORD to create the first admin)");
            return;
        }
        String email = admin.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Admin bootstrap skipped: user {} already exists", email);
            return;
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("Admin bootstrap skipped: an admin already exists");
            return;
        }

        User adminUser = new User();
        adminUser.setEmail(email);
        adminUser.setPasswordHash(passwordService.hash(admin.password()));
        adminUser.setRole(Role.ADMIN);
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setEmailVerified(true);
        adminUser = userRepository.save(adminUser);

        UserProfile profile = new UserProfile();
        profile.setUser(adminUser);
        profile.setFullName("ReLoop Admin");
        userProfileRepository.save(profile);

        log.info("Bootstrap admin account created for {}", email);
    }
}
