package org.idp.sample.presentation.api;

import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.JwksApi;
import org.idp.server.handler.discovery.io.JwksRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/jwks")
public class JwksV1Api {

  JwksApi jwksApi;
  TenantService tenantService;

  public JwksV1Api(IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.jwksApi = idpServerApplication.jwksApi();
    this.tenantService = tenantService;
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {
    Tenant tenant = tenantService.get(tenantId);
    JwksRequestResponse response = jwksApi.getJwks(tenant.issuer());
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
