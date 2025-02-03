package org.idp.sample;

import org.idp.server.api.DiscoveryApi;
import org.idp.server.IdpServerApplication;
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

  public OpenIdDiscoveryV1Api(IdpServerApplication idpServerApplication) {
    this.discoveryApi = idpServerApplication.discoveryApi();
  }

  @GetMapping
  public ResponseEntity<?> request(@PathVariable("tenant-id") String tenantId) {
    Tenant tenant = Tenant.of(tenantId);
    ServerConfigurationRequestResponse response = discoveryApi.getConfiguration(tenant.issuer());
    return new ResponseEntity<>(response.content(), HttpStatus.valueOf(response.statusCode()));
  }
}
