package org.idp.server.adapters.springboot.restapi;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.OidcMetaDataFunction;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/jwks")
public class JwksV1Api {

  OidcMetaDataFunction oidcMetaDataFunction;

  public JwksV1Api(IdpServerApplication idpServerApplication) {
    this.oidcMetaDataFunction = idpServerApplication.oidcMetaDataFunction();
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    JwksRequestResponse response = oidcMetaDataFunction.getJwks(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
