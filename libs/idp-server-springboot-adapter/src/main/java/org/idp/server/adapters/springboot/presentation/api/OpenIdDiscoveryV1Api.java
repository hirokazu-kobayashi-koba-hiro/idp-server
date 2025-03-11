package org.idp.server.adapters.springboot.presentation.api;

import org.idp.server.adapters.springboot.application.service.OidcMetaDataService;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.adapters.springboot.domain.model.tenant.TenantIdentifier;
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
