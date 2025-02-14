package org.idp.sample.presentation.api;

import org.idp.sample.application.service.OidcMetaDataService;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
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

  OidcMetaDataService oidcMetaDataService;

  public OpenIdDiscoveryV1Api(OidcMetaDataService oidcMetaDataService) {
    this.oidcMetaDataService = oidcMetaDataService;
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    ServerConfigurationRequestResponse response = oidcMetaDataService.getConfiguration(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
