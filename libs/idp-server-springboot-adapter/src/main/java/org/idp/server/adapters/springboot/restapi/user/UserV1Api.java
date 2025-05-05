package org.idp.server.adapters.springboot.restapi.user;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.operation.ResourceOwnerPrincipal;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserApi;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/users")
public class UserV1Api implements ParameterTransformable {

  UserApi userApi;

  public UserV1Api(IdpServerApplication idpServerApplication) {
    this.userApi = idpServerApplication.userApi();
  }

  @DeleteMapping
  public ResponseEntity<?> apply(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    userApi.delete(tenantIdentifier, user, oAuthToken, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(Map.of(), httpHeaders, HttpStatus.valueOf(204));
  }
}
