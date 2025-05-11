package org.idp.server.adapters.springboot.control_plane.restapi.admin;

import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.control_plane.management.client.ClientManagementApi;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/client/registration")
public class ClientRegistrationV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(@RequestBody(required = false) String body) {

    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    clientManagementApi.register(adminTenantIdentifier, body);
    return new ResponseEntity<>(HttpStatus.OK);
  }
}
