package org.idp.sample;

import org.idp.server.IdpServerApplication;
import org.idp.server.ServerManagementApi;
import org.idp.server.handler.ciba.io.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/server/configurations")
public class ServerManagementV1Api implements ParameterTransformable {

  ServerManagementApi serverManagementApi;

  public ServerManagementV1Api(IdpServerApplication idpServerApplication) {
    this.serverManagementApi = idpServerApplication.serverManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(@RequestBody(required = false) String body) {
    serverManagementApi.register(body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
