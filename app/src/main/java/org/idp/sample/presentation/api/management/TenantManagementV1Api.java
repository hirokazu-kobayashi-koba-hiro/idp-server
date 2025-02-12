package org.idp.sample.presentation.api.management;

import java.util.UUID;
import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.sample.domain.model.tenant.TenantName;
import org.idp.sample.domain.model.tenant.TenantType;
import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/management/tenants")
@Transactional
public class TenantManagementV1Api implements ParameterTransformable {

  ServerManagementApi serverManagementApi;
  TenantService tenantService;

  public TenantManagementV1Api(
      IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {
    Tenant tenant = tenantService.get(tenantId);
    String newTenantId = UUID.randomUUID().toString();
    String issuer = "http://localhost:8080/" + newTenantId;
    String replacedBody = body.replaceAll("IDP_ISSUER", issuer);
    String response = serverManagementApi.register(replacedBody);

    Tenant newTenant =
        new Tenant(
            new TenantIdentifier(newTenantId),
            new TenantName(newTenantId),
            TenantType.PUBLIC,
            issuer);
    tenantService.register(newTenant);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
