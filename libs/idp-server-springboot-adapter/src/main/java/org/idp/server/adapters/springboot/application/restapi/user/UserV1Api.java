package org.idp.server.adapters.springboot.application.restapi.user;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.model.ResourceOwnerPrincipal;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserOperationApi;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/users")
public class UserV1Api implements ParameterTransformable {

  UserOperationApi userOperationApi;

  public UserV1Api(IdpServerApplication idpServerApplication) {
    this.userOperationApi = idpServerApplication.userOperationApi();
  }

  @DeleteMapping
  public ResponseEntity<?> apply(
      @AuthenticationPrincipal ResourceOwnerPrincipal resourceOwnerPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest) {

    User user = resourceOwnerPrincipal.getUser();
    OAuthToken oAuthToken = resourceOwnerPrincipal.getOAuthToken();
    RequestAttributes requestAttributes = transform(httpServletRequest);

    userOperationApi.delete(tenantIdentifier, user, oAuthToken, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(Map.of(), httpHeaders, HttpStatus.valueOf(204));
  }
}
