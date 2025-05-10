package org.idp.server.adapters.springboot.restapi.admin;

import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.control_plane.oidc.AuthorizationServerManagementApi;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/server/registration")
public class ServerRegistrationV1Api implements ParameterTransformable {

  ServerDomain serverDomain;
  AuthorizationServerManagementApi authorizationServerManagementApi;

  public ServerRegistrationV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String publicTenantDomainValue) {
    this.authorizationServerManagementApi = idpServerApplication.serverManagementApi();

    this.serverDomain = new ServerDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(@RequestBody(required = false) String body) {

    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    String response =
        authorizationServerManagementApi.register(
            adminTenantIdentifier, TenantType.PUBLIC, serverDomain, body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
