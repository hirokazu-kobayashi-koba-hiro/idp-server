package org.idp.sample.presentation.api;

import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.DiscoveryApi;
import org.idp.server.handler.discovery.io.ServerConfigurationRequestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("{tenant-id}/.well-known/openid-configuration")
public class OpenIdDiscoveryV1Api {

  DiscoveryApi discoveryApi;
  TenantService tenantService;

  public OpenIdDiscoveryV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.discoveryApi = idpServerApplication.discoveryApi();
    this.tenantService = tenantService;
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {
    Tenant tenant = tenantService.get(tenantId);
    ServerConfigurationRequestResponse response = discoveryApi.getConfiguration(tenant.issuer());
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
