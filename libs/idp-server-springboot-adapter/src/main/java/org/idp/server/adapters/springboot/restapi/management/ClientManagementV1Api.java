package org.idp.server.adapters.springboot.restapi.management;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.ClientManagementApi;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.core.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.adapters.springboot.restapi.ParameterTransformable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientManagementV1Api(
      IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementFunction();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal User operator,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) String body) {

    String client = clientManagementApi.register(tenantIdentifier, body);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(client, httpHeaders, HttpStatus.OK);
  }

  @GetMapping
  public ResponseEntity<?> getList(
      @AuthenticationPrincipal User operator,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestParam(value = "limit", defaultValue = "20") String limitValue,
      @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {

    ClientConfigurationManagementListResponse response =
        clientManagementApi.find(
            tenantIdentifier, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.content(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{client-id}")
  public ResponseEntity<?> get(
      @AuthenticationPrincipal User operator,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @PathVariable("client-id") String clientId) {

    ClientConfigurationManagementResponse response =
        clientManagementApi.get(tenantIdentifier, new RequestedClientId(clientId));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.content(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PutMapping("/{client-id}")
  public ResponseEntity<?> put(
          @AuthenticationPrincipal User operator,
          @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
          @RequestBody(required = false) String body) {

    String client = clientManagementApi.update(tenantIdentifier, body);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(client, httpHeaders, HttpStatus.OK);
  }

  @DeleteMapping("/{client-id}")
  public ResponseEntity<?> delete(
          @AuthenticationPrincipal User operator,
          @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
          @PathVariable("client-id") String clientId) {

    ClientConfigurationManagementResponse response =
            clientManagementApi.delete(tenantIdentifier, new RequestedClientId(clientId));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
            response.content(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
