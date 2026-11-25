package vn.englishconversation.admin.controller;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.englishconversation.admin.dto.UpdateAccountAccessRequest;
import vn.englishconversation.admin.dto.AdminPage;
import vn.englishconversation.admin.service.AdminService;
import vn.englishconversation.auth.security.AccountPrincipal;
import vn.englishconversation.auth.service.AccountService;
import vn.englishconversation.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/admin/users")
@Profile("!bootstrap & !g1 & !g2 & !g3 & !g4 & !g5")
public class AccountAdminController {
  private final AccountService accounts;
  private final AdminService admin;

  public AccountAdminController(AccountService accounts, AdminService admin) {
    this.accounts = accounts;
    this.admin = admin;
  }

  @GetMapping
  public AdminPage<UserResponse> list(
      @RequestParam(defaultValue = "") String email,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size) {
    return admin.users(email, page, size);
  }

  @PatchMapping("/{id}/access")
  public UserResponse access(
      @AuthenticationPrincipal AccountPrincipal actor,
      @PathVariable long id, @Valid @RequestBody UpdateAccountAccessRequest access) {
    return accounts.changeAccess(actor, id, access.role(), access.status());
  }
}
