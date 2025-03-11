package org.idp.server.presentation.api;

import java.util.Map;
import org.idp.server.application.service.TokenService;
import org.idp.server.core.handler.tokenintrospection.io.TokenIntrospectionResponse;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens/introspection")
public class TokenIntrospectionV1Api implements ParameterTransformable {

  TokenService tokenService;

  public TokenIntrospectionV1Api(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);

    TokenIntrospectionResponse response = tokenService.inspect(tenantId, request);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
