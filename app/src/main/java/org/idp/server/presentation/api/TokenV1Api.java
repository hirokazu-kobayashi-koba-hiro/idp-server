package org.idp.server.presentation.api;

import java.util.Map;
import org.idp.server.application.service.TokenService;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens")
public class TokenV1Api implements ParameterTransformable {

  TokenService tokenService;

  public TokenV1Api(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);

    TokenRequestResponse response =
        tokenService.request(tenantId, request, authorizationHeader, clientCert);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
