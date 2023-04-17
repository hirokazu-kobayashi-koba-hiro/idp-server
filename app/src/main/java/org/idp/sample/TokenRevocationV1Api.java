package org.idp.sample;

import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.TokenRevocationApi;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationRequest;
import org.idp.server.handler.tokenrevocation.io.TokenRevocationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/tokens/revocation")
public class TokenRevocationV1Api implements ParameterTransformable {

  TokenRevocationApi tokenRevocationApi;

  public TokenRevocationV1Api(IdpServerApplication idpServerApplication) {
    this.tokenRevocationApi = idpServerApplication.tokenRevocationApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @PathVariable("tenant-id") String tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = Tenant.of(tenantId);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(authorizationHeader, request, tenant.issuer());
    TokenRevocationResponse response = tokenRevocationApi.revoke(revocationRequest);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
