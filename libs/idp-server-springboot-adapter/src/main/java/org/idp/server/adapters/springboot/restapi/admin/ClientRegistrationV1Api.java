package org.idp.server.adapters.springboot.restapi.admin;

import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.admin.ClientManagementApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/admin/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(@RequestBody(required = false) String body) {

    clientManagementApi.register(body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
