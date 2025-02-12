package org.idp.sample.presentation.view;

import jakarta.websocket.server.PathParam;
import java.util.UUID;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.oauth.identity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping
public class UserController {
  UserService userService;
  TenantService tenantService;
  Logger log = LoggerFactory.getLogger(UserController.class);

  public UserController(UserService userService, TenantService tenantService) {
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @GetMapping("/v1/users")
  public String showRegistrationPage(
      @ModelAttribute("tenantId") TenantIdentifier tenantId, Model model) {
    Tenant tenant = tenantService.get(tenantId);
    model.addAttribute("tenantId", tenant.identifierValue());
    return "user";
  }

  @GetMapping("/v1/users/registration-success")
  public String showSuccessPage(@PathParam("tenantId") TenantIdentifier tenantId, Model model) {
    Tenant tenant = tenantService.get(tenantId);
    model.addAttribute("tenantId", tenant.identifierValue());
    return "registration-success";
  }

  @PostMapping("/v1/users/registration")
  public String processRegistration(
      @RequestParam String email,
      @RequestParam String password,
      @ModelAttribute("tenantId") TenantIdentifier tenantId,
      Model model) {
    Tenant tenant = tenantService.get(tenantId);
    User existingUser = userService.findBy(tenant, email);
    if (existingUser.exists()) {
      log.warn("email already exists: " + email);
      model.addAttribute("error", "email already exists");
      return "user";
    }
    User user = new User();
    user.setSub(UUID.randomUUID().toString());
    user.setEmail(email);
    user.setPassword(password);
    userService.register(tenant, user);

    return "redirect:/v1/users/registration-success?tenantId="
        + tenantId; // Redirect to a success page
  }

  @ExceptionHandler(Exception.class)
  public String handleError(Exception e, Model model) {
    log.error(e.getMessage(), e);
    model.addAttribute("error", "server_error");
    return "error";
  }
}
