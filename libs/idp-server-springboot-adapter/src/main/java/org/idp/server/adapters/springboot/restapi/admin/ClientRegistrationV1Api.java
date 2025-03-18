package org.idp.server.adapters.springboot.restapi.admin;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.ClientManagementApi;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/admin/tenants/{tenant-id}/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientRegistrationV1Api(
      IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementFunction();
  }

  @PostMapping
  public ResponseEntity<?> request(
          @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) String body) {

    clientManagementApi.register(tenantIdentifier, body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
