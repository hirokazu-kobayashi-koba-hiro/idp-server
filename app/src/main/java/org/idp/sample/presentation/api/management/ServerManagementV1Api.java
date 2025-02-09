package org.idp.sample.presentation.api.management;

import org.idp.sample.presentation.api.ParameterTransformable;
import org.idp.sample.presentation.api.Tenant;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.ServerManagementApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/management/tenants")
public class ServerManagementV1Api implements ParameterTransformable {

  ServerManagementApi serverManagementApi;

  public ServerManagementV1Api(IdpServerApplication idpServerApplication) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @PathVariable("tenant-id") String tenantId, @RequestBody(required = false) String body) {
    Tenant tenant = Tenant.of(tenantId);

    String response = serverManagementApi.register(body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
