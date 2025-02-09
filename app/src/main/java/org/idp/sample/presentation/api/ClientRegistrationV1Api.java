package org.idp.sample.presentation.api;

import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ClientManagementApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;
  TenantService tenantService;

  public ClientRegistrationV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {
    Tenant tenant = tenantService.get(tenantId);
    clientManagementApi.register(body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
