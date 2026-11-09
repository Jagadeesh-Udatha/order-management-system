package dev.jagadeesh.oms.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RegisterRequest(@Email @NotBlank String email, @NotBlank String fullName, @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record Registered(UUID id, String email) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Registered register(@Valid @RequestBody RegisterRequest r) {
        UserAccount u = auth.register(r.email(), r.fullName(), r.password());
        return new Registered(u.getId(), u.getEmail());
    }

    @PostMapping("/login")
    public AuthService.Token login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        p.setProperty("code", "BAD_CREDENTIALS");
        return p;
    }
}
