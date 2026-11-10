package com.clinicready.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.security.dto.CurrentUserResponse;
import com.clinicready.security.dto.LoginRequest;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUser;
    /** Checked when the email is unknown, so both failure paths cost one BCrypt comparison. */
    private final String dummyHash;

    AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            CurrentUserService currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.dummyHash = passwordEncoder.encode("timing-equalizer");
    }

    @Transactional(readOnly = true)
    LoginResult login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return new LoginResult(jwtService.issue(user), CurrentUserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me() {
        return users.findById(currentUser.userId())
                .filter(AppUser::isEnabled)
                .map(CurrentUserResponse::from)
                .orElseThrow(UnauthenticatedException::new);
    }

    record LoginResult(JwtService.IssuedToken token, CurrentUserResponse user) {
    }
}
