package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.identity.UserManagementApi;
import org.idp.server.control_plane.management.identity.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.io.UserRegistrationRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/users")
public class UserManagementV1Api implements ParameterTransformable {

  UserManagementApi userManagementApi;

  public UserManagementV1Api(IdpServerApplication idpServerApplication) {
    this.userManagementApi = idpServerApplication.userManagementAPi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    UserManagementResponse response =
        userManagementApi.register(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new UserRegistrationRequest(body),
            requestAttributes);
    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");

    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    List<User> userList =
        userManagementApi.find(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            Integer.parseInt(limitValue),
            Integer.parseInt(offsetValue),
            requestAttributes);

    return new ResponseEntity<>(Map.of(), HttpStatus.OK);
  }

  @GetMapping("/{user-id}")
  public ResponseEntity<?> getById(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("user-id") UserIdentifier userIdentifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    User user =
        userManagementApi.get(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            userIdentifier,
            requestAttributes);
    return new ResponseEntity<>(Map.of(), HttpStatus.OK);
  }
}
