package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.AppUserRepository;

/** Resolves the signed-in account from the e-mail address stored in the security context. */
@Component
public class CurrentUsers {
    private final AppUserRepository users;
    public CurrentUsers(AppUserRepository users) { this.users = users; }

    public AppUser require(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
    }
    public String nameOf(Long userId) {
        return userId == null ? "Unknown" : users.findById(userId).map(AppUser::getFullName).orElse("Unknown");
    }
}
