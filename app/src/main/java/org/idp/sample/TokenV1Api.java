package org.idp.sample;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.TokenApi;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens")
public class TokenV1Api implements ParameterTransformable {

  TokenApi tokenApi;

  public TokenV1Api(IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.tokenApi = idpServerApplication.tokenApi();
    tokenApi.setPasswordCredentialsGrantDelegate(userMockService);
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    TokenRequest tokenRequest = new TokenRequest(authorizationHeader, request, tenant.issuer());
    tokenRequest.setClientCert(clientCert);
    TokenRequestResponse response = tokenApi.request(tokenRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.responseHeaders());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
