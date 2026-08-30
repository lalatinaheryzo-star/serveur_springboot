package mg.manao.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {}

    public static SecurityUserDetails get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUserDetails details)) {
            return null;
        }
        return details;
    }

    public static boolean isAdmin() {
        SecurityUserDetails u = get();
        return u != null && u.getRole() == mg.manao.backend.entity.Utilisateur.Role.ADMIN;
    }

    public static boolean isPresident() {
        SecurityUserDetails u = get();
        return u != null && u.getRole() == mg.manao.backend.entity.Utilisateur.Role.PRESIDENT;
    }
}
