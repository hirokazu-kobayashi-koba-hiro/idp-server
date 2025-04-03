package org.idp.server.adapters.springboot.restapi.admin;

import java.util.Map;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.IdpServerStarterApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/registration")
public class AdminRegistrationV1Api implements ParameterTransformable {

  IdpServerStarterApi idpServerStarterApi;

  public AdminRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.idpServerStarterApi = idpServerApplication.idpServerStarterFunction();
  }

  @PostMapping
  public ResponseEntity<?> post(@RequestBody(required = false) Map<String, Object> body) {

    Map<String, Object> response = idpServerStarterApi.initialize(body);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(response, headers, HttpStatus.OK);
  }
}
