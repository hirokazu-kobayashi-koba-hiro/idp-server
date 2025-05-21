package org.idp.server.adapters.springboot.control_plane.restapi.management;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.IdpServerApplication;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.control_plane.model.OperatorPrincipal;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementApi;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/identity-verification-configurations")
public class IdentityVerificationConfigurationManagementV1Api implements ParameterTransformable {

  IdentityVerificationConfigManagementApi identityVerificationConfigManagementApi;

  public IdentityVerificationConfigurationManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.identityVerificationConfigManagementApi =
        idpServerApplication.identityVerificationConfigManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationConfigManagementResponse response =
        identityVerificationConfigManagementApi.create(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new IdentityVerificationConfigRegistrationRequest(body),
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationConfigManagementResponse response =
        identityVerificationConfigManagementApi.findList(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            Integer.parseInt(limitValue),
            Integer.parseInt(offsetValue),
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationConfigurationIdentifier identifier,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationConfigManagementResponse response =
        identityVerificationConfigManagementApi.get(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> put(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationConfigurationIdentifier identifier,
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationConfigManagementResponse response =
        identityVerificationConfigManagementApi.update(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            identifier,
            new IdentityVerificationConfigUpdateRequest(body),
            requestAttributes,
            dryRun);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("id") IdentityVerificationConfigurationIdentifier identifier,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    RequestAttributes requestAttributes = transform(httpServletRequest);

    IdentityVerificationConfigManagementResponse response =
        identityVerificationConfigManagementApi.delete(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            identifier,
            requestAttributes,
            dryRun);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
