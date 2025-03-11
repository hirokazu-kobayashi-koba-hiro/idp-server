package org.idp.server.presentation.api;

import java.util.Map;
import org.idp.server.application.service.TokenService;
import org.idp.server.core.handler.tokenrevocation.io.TokenRevocationResponse;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.presentation.api.ParameterTransformable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens/revocation")
public class TokenRevocationV1Api implements ParameterTransformable {

  TokenService tokenService;

  public TokenRevocationV1Api(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);

    TokenRevocationResponse response =
        tokenService.revoke(tenantId, request, authorizationHeader, clientCert);

    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
