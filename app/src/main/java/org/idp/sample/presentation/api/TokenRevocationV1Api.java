package org.idp.sample.presentation.api;

import java.util.Map;
import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.TokenRevocationApi;
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
  TenantService tenantService;

  public TokenRevocationV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.tokenRevocationApi = idpServerApplication.tokenRevocationApi();
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @RequestBody(required = false) MultiValueMap<String, String> body,
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId) {
    Map<String, String[]> request = transform(body);
    Tenant tenant = tenantService.get(tenantId);
    TokenRevocationRequest revocationRequest =
        new TokenRevocationRequest(authorizationHeader, request, tenant.issuer());
    revocationRequest.setClientCert(clientCert);
    TokenRevocationResponse response = tokenRevocationApi.revoke(revocationRequest);
    return new ResponseEntity<>(response.response(), HttpStatus.valueOf(response.statusCode()));
  }
}
