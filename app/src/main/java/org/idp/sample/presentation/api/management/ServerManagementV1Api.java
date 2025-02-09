package org.idp.sample.presentation.api.management;

import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/management/tenants")
public class ServerManagementV1Api implements ParameterTransformable {

  ServerManagementApi serverManagementApi;
  TenantService tenantService;

  public ServerManagementV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {
    Tenant tenant = tenantService.get(tenantId);

    String response = serverManagementApi.register(body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
