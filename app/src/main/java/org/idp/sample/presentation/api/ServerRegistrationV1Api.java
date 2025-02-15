package org.idp.sample.presentation.api;

import org.idp.sample.application.service.tenant.TenantService;
import org.idp.sample.domain.model.tenant.*;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/{tenant-id}/api/v1/server/registration")
@Transactional
public class ServerRegistrationV1Api implements ParameterTransformable {

  PublicTenantDomain publicTenantDomain;
  ServerManagementApi serverManagementApi;
  TenantService tenantService;

  public ServerRegistrationV1Api(
      IdpServerApplication idpServerApplication,
      TenantService tenantService,
      @Value("${idp.configurations.publicTenantDomain}") String publicTenantDomainValue) {
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
