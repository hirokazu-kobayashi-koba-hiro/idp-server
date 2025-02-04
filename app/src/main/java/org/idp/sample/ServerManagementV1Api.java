package org.idp.sample;

import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/server/configurations")
public class ServerManagementV1Api implements ParameterTransformable {

  ServerManagementApi serverManagementApi;

  public ServerManagementV1Api(IdpServerApplication idpServerApplication) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
      @PathVariable("tenant-id") String tenantId, @RequestBody(required = false) String body) {
    Tenant tenant = Tenant.of(tenantId);
    serverManagementApi.register(body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
