package org.idp.server.presentation.api.management;

import java.util.UUID;
import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.domain.model.tenant.*;
import org.idp.server.presentation.api.ParameterTransformable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/tenants")
@Transactional
public class TenantManagementV1Api implements ParameterTransformable {

  String idpServerDomain;
  ServerManagementApi serverManagementApi;
  TenantService tenantService;

  public TenantManagementV1Api(
      IdpServerApplication idpServerApplication,
      TenantService tenantService,
      @Value("${idp.configurations.serverUrl}") String idpServerDomain) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
    this.tenantService = tenantService;
    this.idpServerDomain = idpServerDomain;
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {
    Tenant tenant = tenantService.get(tenantId);
    String newTenantId = UUID.randomUUID().toString();
    String issuer = idpServerDomain + newTenantId;
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
