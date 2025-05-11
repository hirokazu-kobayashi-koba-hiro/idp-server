package org.idp.server.adapters.springboot.control_plane.restapi.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.admin.tenant.TenantInitializationApi;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationRequest;
import org.idp.server.control_plane.admin.tenant.io.TenantInitializationResponse;
import org.idp.server.core.multi_tenancy.tenant.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/public-tenant/initialization")
public class PublicTenantInitializationV1Api implements ParameterTransformable {

  TenantInitializationApi tenantInitializationApi;

  public PublicTenantInitializationV1Api(IdpServerApplication idpServerApplication) {
    this.tenantInitializationApi = idpServerApplication.tenantInitializationApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestBody Map<String, Object> body, HttpServletRequest httpServletRequest) {

    TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
    RequestAttributes requestAttributes = transform(httpServletRequest);
    TenantInitializationResponse response =
        tenantInitializationApi.initialize(
            adminTenantIdentifier, new TenantInitializationRequest(body), requestAttributes);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Content-Type", "application/json");
    return new ResponseEntity<>(
        response.contents(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}
