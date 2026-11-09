package dev.jagadeesh.oms.auth;

import dev.jagadeesh.oms.common.web.BusinessRuleException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService implements ApplicationRunner {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final AuthProperties props;

    public AuthService(UserAccountRepository users, PasswordEncoder encoder, JwtEncoder jwt, AuthProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    public record Token(String accessToken, String tokenType, long expiresIn, String role) {
    }

    @Transactional
    public UserAccount register(String email, String fullName, String password) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("EMAIL_TAKEN", "An account with this email already exists");
        }
        return users.save(new UserAccount(email.toLowerCase(), fullName, encoder.encode(password), UserAccount.Role.CUSTOMER));
    }

    @Transactional(readOnly = true)
    public Token login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Wrong email or password"));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("oms-auth")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtl()))
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token, "Bearer", props.tokenTtl().toSeconds(), user.getRole().name());
    }

    /** Creates the first admin from config on an empty database, so a fresh environment can add products. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (props.adminEmail() != null && !users.existsByEmailIgnoreCase(props.adminEmail())) {
            users.save(new UserAccount(props.adminEmail(), "Admin", encoder.encode(props.adminPassword()), UserAccount.Role.ADMIN));
        }
    }
}
