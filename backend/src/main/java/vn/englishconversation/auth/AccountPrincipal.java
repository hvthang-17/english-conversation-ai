package vn.englishconversation.auth;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

/**
 * Reads JDBC sessions written before the package refactor. New logins use
 * auth.security.AccountPrincipal.
 */
@Deprecated(forRemoval = false)
public record AccountPrincipal(Long id, long securityVersion) implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 0L;

    @Override
    public String getName() {
        return id.toString();
    }

    @Serial
    private Object readResolve() {
        return new vn.englishconversation.auth.security.AccountPrincipal(id, securityVersion);
    }
}
