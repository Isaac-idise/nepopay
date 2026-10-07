package com.nepopay.admin;

import com.nepopay.user.User;
import com.nepopay.user.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the one admin account at startup, from environment variables. It runs only when ADMIN_EMAIL is set,
 * never overwrites an existing account (so a changed password survives restarts), and never logs the password.
 * The admin has NO bank account and a random PIN nobody knows: it can administer, not move money.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public AdminBootstrap(AdminProperties props, UserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.email() == null || props.email().isBlank()) {
            log.info("ADMIN_EMAIL is not set: no admin account seeded.");
            return;
        }
        String password = props.initialPassword();
        if (password == null || password.length() < 12) {
            throw new IllegalStateException("ADMIN_INITIAL_PASSWORD must be at least 12 characters when ADMIN_EMAIL is set.");
        }
        String email = props.email().trim().toLowerCase(Locale.ROOT);
        String username = (props.username() == null || props.username().isBlank() ? "admin" : props.username())
                .trim().toLowerCase(Locale.ROOT);

        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            log.info("Admin account already exists: nothing to do.");
            return;
        }
        String unknownPin = String.format("%04d", random.nextInt(10_000));
        users.save(User.newAdmin(username, "NepOpay", "Admin", email, "0000000000",
                encoder.encode(password), encoder.encode(unknownPin)));
        log.info("Seeded admin account '{}'. Change its password after the first login.", username);
    }
}
