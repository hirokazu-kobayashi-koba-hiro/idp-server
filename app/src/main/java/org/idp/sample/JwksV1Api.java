package org.idp.sample;

import org.idp.server.IdpServerApplication;
import org.idp.server.JwksApi;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/jwks")
public class JwksV1Api {

  JwksApi jwksApi;

  public JwksV1Api(IdpServerApplication idpServerApplication) {
    this.jwksApi = idpServerApplication.jwksApi();
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    JwksRequestResponse response = jwksApi.getJwks(tenant.issuer());
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
