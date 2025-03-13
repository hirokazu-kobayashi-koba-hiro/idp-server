package org.idp.server.adapters.springboot.presentation.api.admin;

import java.util.UUID;

import org.idp.server.adapters.springboot.application.service.tenant.TenantService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.ServerManagementApi;
import org.idp.server.adapters.springboot.presentation.api.ParameterTransformable;
import org.idp.server.core.tenant.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/admin/server/registration")
@Transactional
public class ServerRegistrationV1Api implements ParameterTransformable {

  PublicTenantDomain publicTenantDomain;
  ServerManagementApi serverManagementApi;
  TenantService tenantService;

  public ServerRegistrationV1Api(
      IdpServerApplication idpServerApplication,
      TenantService tenantService,
      @Value("${idp.configurations.serverUrl}") String publicTenantDomainValue) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
    this.tenantService = tenantService;
    this.publicTenantDomain = new PublicTenantDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {
    Tenant tenant = tenantService.get(tenantId);
    String newTenantId = UUID.randomUUID().toString();
    String issuer = publicTenantDomain.value() + newTenantId;
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
