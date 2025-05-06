package org.idp.server.adapters.springboot.restapi.metadata;

import org.idp.server.core.authentication.AuthenticationMetaDataApi;
import org.idp.server.core.authentication.fidouaf.FidoUafExecutionResult;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class FidoUafDiscoveryV1Api {

  AuthenticationMetaDataApi authenticationMetaDataApi;

  public FidoUafDiscoveryV1Api(IdpServerApplication idpServerApplication) {
    this.authenticationMetaDataApi = idpServerApplication.authenticationMetaDataApi();
  }

  @GetMapping("{tenant-id}/.well-known/fido/facets")
  public ResponseEntity<?> getConfiguration(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    FidoUafExecutionResult result = authenticationMetaDataApi.getFidoUafFacets(tenantId);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        result.contents(), httpHeaders, HttpStatus.valueOf(result.statusCode()));
  }
}
