package org.idp.server.adapters.springboot.restapi.admin;

import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.ServerManagementApi;
import org.idp.server.core.tenant.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/server/registration")
public class ServerRegistrationV1Api implements ParameterTransformable {

  ServerDomain serverDomain;
  ServerManagementApi serverManagementApi;

  public ServerRegistrationV1Api(
      IdpServerApplication idpServerApplication,
      @Value("${idp.configurations.serverUrl}") String publicTenantDomainValue) {
    this.serverManagementApi = idpServerApplication.serverManagementFunction();

    this.serverDomain = new ServerDomain(publicTenantDomainValue);
  }

  @PostMapping
  public ResponseEntity<?> post(@RequestBody(required = false) String body) {

    String response = serverManagementApi.register(TenantType.PUBLIC, serverDomain, body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
