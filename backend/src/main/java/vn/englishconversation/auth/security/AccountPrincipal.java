package vn.englishconversation.auth.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

public record AccountPrincipal(Long id, long securityVersion) implements Principal, Serializable {
  @Serial private static final long serialVersionUID = 0L;

  @Override
  public String getName() {
    return id.toString();
  }
}
