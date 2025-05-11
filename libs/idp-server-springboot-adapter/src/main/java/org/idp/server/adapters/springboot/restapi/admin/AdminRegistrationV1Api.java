package org.idp.server.adapters.springboot.restapi.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.starter.IdpServerStarterApi;
import org.idp.server.control_plane.starter.io.IdpServerStarterRequest;
import org.idp.server.control_plane.starter.io.IdpServerStarterResponse;
import org.idp.server.core.multi_tenancy.tenant.AdminTenantContext;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/registration")
public class AdminRegistrationV1Api implements ParameterTransformable {

  IdpServerStarterApi idpServerStarterApi;

  public AdminRegistrationV1Api(IdpServerApplication idpServerApplication) {
    this.idpServerStarterApi = idpServerApplication.idpServerStarterApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);
    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    IdpServerStarterResponse response =
        idpServerStarterApi.initialize(
            adminTenantIdentifier, new IdpServerStarterRequest(body), requestAttributes);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}
