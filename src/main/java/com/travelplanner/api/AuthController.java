package com.travelplanner.api;

import com.travelplanner.api.ApiModels.AuthView;
import com.travelplanner.api.ApiModels.LoginRequest;
import com.travelplanner.api.ApiModels.SignupRequest;
import com.travelplanner.api.ApiModels.UserView;
import com.travelplanner.model.AppUser;
import com.travelplanner.repository.AppUserRepository;
import com.travelplanner.service.CsrfTokens;
import com.travelplanner.service.PasswordHasher;
import com.travelplanner.service.SessionAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordHasher passwords;
    private final CsrfTokens csrf;

    public AuthController(AppUserRepository users, PasswordHasher passwords, CsrfTokens csrf) {
        this.users = users;
        this.passwords = passwords;
        this.csrf = csrf;
    }

    @GetMapping("/me")
    public AuthView me(HttpSession session) {
        String token = csrf.current(session);
        Object userId = session.getAttribute(SessionAuth.userSessionKey());
        if (!(userId instanceof Long id)) return new AuthView(null, token);
        AppUser user = users.findById(id).orElse(null);
        if (user == null) return new AuthView(null, token);
        return new AuthView(userView(user), token);
    }

    @PostMapping("/signup") @Transactional
    public AuthView signup(@Valid @RequestBody SignupRequest request,
                           @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                           HttpSession session, HttpServletRequest httpRequest) {
        csrf.requireValid(session, token);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken.");
        }
        AppUser user = users.save(new AppUser(username, passwords.encode(request.password())));
        signIn(user, session, httpRequest);
        return new AuthView(userView(user), csrf.rotate(session));
    }

    @PostMapping("/login") @Transactional(readOnly = true)
    public AuthView login(@Valid @RequestBody LoginRequest request,
                          @RequestHeader(value = "X-CSRF-TOKEN", required = false) String token,
                          HttpSession session, HttpServletRequest httpRequest) {
        csrf.requireValid(session, token);
        AppUser user = users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or password is incorrect.");
        }
        signIn(user, session, httpRequest);
        return new AuthView(userView(user), csrf.rotate(session));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "X-CSRF-TOKEN", required = false) String token, HttpSession session) {
        csrf.requireValid(session, token);
        session.invalidate();
    }

    private void signIn(AppUser user, HttpSession session, HttpServletRequest request) {
        request.changeSessionId();
        session.setAttribute(SessionAuth.userSessionKey(), user.getId());
    }

    private UserView userView(AppUser user) { return new UserView(user.getId(), user.getUsername()); }
}
