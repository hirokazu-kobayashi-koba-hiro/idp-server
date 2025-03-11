package org.idp.server.presentation.api.admin;

import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.ClientManagementApi;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.presentation.api.ParameterTransformable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/admin/client/registration")
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
