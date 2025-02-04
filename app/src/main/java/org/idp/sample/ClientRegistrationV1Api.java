package org.idp.sample;

import org.idp.server.IdpServerApplication;
import org.idp.server.api.ClientManagementApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") String tenantId, @RequestBody(required = false) String body) {
    Tenant tenant = Tenant.of(tenantId);
    clientManagementApi.register(body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
