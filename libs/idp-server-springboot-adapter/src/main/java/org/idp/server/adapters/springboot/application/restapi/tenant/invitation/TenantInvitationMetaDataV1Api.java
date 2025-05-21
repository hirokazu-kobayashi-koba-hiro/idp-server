package org.idp.server.adapters.springboot.application.restapi.tenant.invitation;

import jakarta.servlet.http.HttpServletRequest;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationIdentifier;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataApi;
import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitationMetaDataResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/v1/invitations")
public class TenantInvitationMetaDataV1Api implements ParameterTransformable {

  TenantInvitationMetaDataApi tenantInvitationMetaDataApi;

  public TenantInvitationMetaDataV1Api(IdpServerApplication idpServerApplication) {
    this.tenantInvitationMetaDataApi = idpServerApplication.tenantInvitationMetaDataApi();
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") TenantInvitationIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    TenantInvitationMetaDataResponse response =
        tenantInvitationMetaDataApi.get(tenantIdentifier, identifier, requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
