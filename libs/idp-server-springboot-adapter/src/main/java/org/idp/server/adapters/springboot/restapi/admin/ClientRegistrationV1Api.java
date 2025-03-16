package org.idp.server.adapters.springboot.restapi.admin;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.function.ClientManagementFunction;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/admin/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementFunction clientManagementFunction;

  public ClientRegistrationV1Api(
      IdpServerApplication idpServerApplication) {
    this.clientManagementFunction = idpServerApplication.clientManagementFunction();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) String body) {

    clientManagementFunction.register(tenantIdentifier, body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
