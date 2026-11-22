package vn.englishconversation.auth.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.common.exception.ApiErrors;
import vn.englishconversation.user.repository.UserRepository;

public class AccountStateFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountStateFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal p) {
            var u = users.findById(p.id());
            if (u.isEmpty()
                    || !AccountService.valid(u.get(), p)
                    || auth.getAuthorities().stream()
                            .noneMatch(a -> a.getAuthority().equals("ROLE_" + u.get().role))) {
                var session = req.getSession(false);
                if (session != null)
                    session.invalidate();
                SecurityContextHolder.clearContext();
                ApiErrors.write(res, 401, "SESSION_REVOKED", "Phiên đã kết thúc. Vui lòng đăng nhập lại.");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
