package org.idp.sample;

import java.util.List;
import org.idp.sample.user.UserService;
import org.idp.server.oauth.identity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/management/users")
public class UserManagementApi {

  UserService userService;

  public UserManagementApi(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") String tenantId,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {
    Tenant tenant = Tenant.of(tenantId);
    List<User> userList =
        userService.find(tenant, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));
    return new ResponseEntity<>(new UserListResponse(userList), HttpStatus.OK);
  }

  @GetMapping("/{user-id}")
  public ResponseEntity<?> getById(
      @PathVariable("tenant-id") String tenantId, @PathVariable("user-id") String userId) {
    Tenant tenant = Tenant.of(tenantId);
    User user = userService.find(userId);
    return new ResponseEntity<>(new UserResponse(user), HttpStatus.OK);
  }
}
