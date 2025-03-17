package org.idp.server.adapters.springboot.restapi;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.OidcMetaDataApi;
import org.idp.server.core.handler.discovery.io.ServerConfigurationRequestResponse;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("{tenant-id}/.well-known/openid-configuration")
public class OpenIdDiscoveryV1Api {

  OidcMetaDataApi oidcMetaDataApi;

  public OpenIdDiscoveryV1Api(IdpServerApplication idpServerApplication) {
    this.oidcMetaDataApi = idpServerApplication.oidcMetaDataFunction();
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    ServerConfigurationRequestResponse response = oidcMetaDataApi.getConfiguration(tenantId);
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
