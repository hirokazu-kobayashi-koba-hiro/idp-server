package org.idp.server.adapters.springboot.presentation.api.management;

import java.util.List;
import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/tenants/{tenant-id}/users")
public class UserManagementV1Api {

  UserManagementApi userManagementApi;
  TenantService tenantService;

  public UserManagementV1Api(IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.userManagementApi = idpServerApplication.userManagementApi();
    this.tenantService = tenantService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {
    Tenant tenant = tenantService.get(tenantId);
    List<User> userList =
            userManagementApi.find(tenant, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));
    return new ResponseEntity<>(new UserListResponse(userList), HttpStatus.OK);
  }

  @GetMapping("/{user-id}")
  public ResponseEntity<?> getById(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @PathVariable("user-id") String userId) {
    Tenant tenant = tenantService.get(tenantId);
    User user = userManagementApi.get(userId);
    return new ResponseEntity<>(new UserResponse(user), HttpStatus.OK);
  }
}
