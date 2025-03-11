package org.idp.server.presentation.api;

import org.idp.server.application.service.OidcMetaDataService;
import org.idp.server.core.handler.discovery.io.JwksRequestResponse;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/jwks")
public class JwksV1Api {

  OidcMetaDataService oidcMetaDataService;

  public JwksV1Api(OidcMetaDataService oidcMetaDataService) {
    this.oidcMetaDataService = oidcMetaDataService;
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    JwksRequestResponse response = oidcMetaDataService.getJwks(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
