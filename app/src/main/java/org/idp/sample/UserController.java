package org.idp.sample;

import java.util.UUID;
import org.idp.sample.user.UserService;
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
  Logger log = LoggerFactory.getLogger(UserController.class);

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/v1/users")
  public String showRegistrationPage(@ModelAttribute("tenantId") String tenantId, Model model) {
    Tenant tenant = Tenant.of(tenantId);
    model.addAttribute("tenantId", tenant.id());
    return "user";
  }

  @GetMapping("/v1/users/registration-success")
  public String showSuccessPage(@ModelAttribute("tenantId") String tenantId, Model model) {
    Tenant tenant = Tenant.of(tenantId);
    model.addAttribute("tenantId", tenant.id());
    return "registration-success";
  }

  @PostMapping("/v1/users/registration")
  public String processRegistration(
      @RequestParam String email,
      @RequestParam String password,
      @ModelAttribute("tenantId") String tenantId,
      Model model) {
    Tenant tenant = Tenant.of(tenantId);
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

    return "redirect:/v1/users/registration-success"; // Redirect to a success page
  }

  @ExceptionHandler(Exception.class)
  public String handleError(Exception e, Model model) {
    log.error(e.getMessage(), e);
    model.addAttribute("error", "server_error");
    return "error";
  }
}
