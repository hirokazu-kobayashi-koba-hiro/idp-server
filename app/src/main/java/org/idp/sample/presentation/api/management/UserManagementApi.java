package org.idp.sample.presentation.api.management;

import java.util.List;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.application.service.user.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.oauth.identity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/tenants/{tenant-id}/users")
public class UserManagementApi {

  UserService userService;
  TenantService tenantService;

  public UserManagementApi(UserService userService, TenantService tenantService) {
    this.userService = userService;
    this.tenantService = tenantService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {
    Tenant tenant = tenantService.get(tenantId);
    List<User> userList =
        userService.find(tenant, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));
    return new ResponseEntity<>(new UserListResponse(userList), HttpStatus.OK);
  }

  @GetMapping("/{user-id}")
  public ResponseEntity<?> getById(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("user-id") String userId) {
    Tenant tenant = tenantService.get(tenantId);
    User user = userService.get(userId);
    return new ResponseEntity<>(new UserResponse(user), HttpStatus.OK);
  }
}
