package org.idp.server.adapters.springboot.restapi.management;

import java.util.List;
import org.idp.server.core.admin.UserManagementApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/tenants/{tenant-id}/users")
public class UserManagementV1Api {

  UserManagementApi userManagementApi;

  public UserManagementV1Api(IdpServerApplication idpServerApplication) {
    this.userManagementApi = idpServerApplication.userManagementAPi();
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {

    List<User> userList =
        userManagementApi.find(
            tenantIdentifier, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));

    return new ResponseEntity<>(new UserListResponse(userList), HttpStatus.OK);
  }

  @GetMapping("/{user-id}")
  public ResponseEntity<?> getById(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("user-id") UserIdentifier userIdentifier) {

    User user = userManagementApi.get(tenantIdentifier, userIdentifier);
    return new ResponseEntity<>(new UserResponse(user), HttpStatus.OK);
  }
}
