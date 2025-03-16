package org.idp.server.adapters.springboot.restapi.admin;

import java.util.UUID;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.ServerManagementFunction;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
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
  ServerManagementFunction serverManagementFunction;


  public ServerRegistrationV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String publicTenantDomainValue) {
    this.serverManagementFunction = idpServerApplication.serverManagementFunction();

    this.publicTenantDomain = new PublicTenantDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) String body) {

    String newTenantId = UUID.randomUUID().toString();
    String issuer = publicTenantDomain.value() + newTenantId;
    String replacedBody = body.replaceAll("IDP_ISSUER", issuer);

    Tenant newTenant =
            new Tenant(
                    new TenantIdentifier(newTenantId),
                    new TenantName(newTenantId),
                    TenantType.PUBLIC,
                    issuer);

    String response = serverManagementFunction.register(newTenant, replacedBody);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
