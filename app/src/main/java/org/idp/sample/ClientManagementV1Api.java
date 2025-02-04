package org.idp.sample;

import org.idp.server.api.ClientManagementApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementListResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.TokenIssuer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/{tenant-id}/api/v1/management/clients")
public class ClientManagementV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;

  public ClientManagementV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> request(
          @PathVariable("tenant-id") String tenantId,
          @RequestBody(required = false) String body) {
    Tenant tenant = Tenant.of(tenantId);
    String client = clientManagementApi.register(body);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(client, httpHeaders, HttpStatus.OK);
  }

  @GetMapping
  public ResponseEntity<?> getList(@PathVariable("tenant-id") String tenantId,
                               @RequestParam(value = "limit", defaultValue = "20") String limitValue,
                               @RequestParam(value = "offset", defaultValue = "0") String offsetValue) {
    Tenant tenant = Tenant.of(tenantId);
    TokenIssuer tokenIssuer = new TokenIssuer(tenant.issuer());
    ClientConfigurationManagementListResponse response = clientManagementApi.find(tokenIssuer, Integer.parseInt(limitValue), Integer.parseInt(offsetValue));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(response.content(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @GetMapping("/{client-id}")
  public ResponseEntity<?> get(@PathVariable("tenant-id") String tenantId,
                               @PathVariable("client-id") String clientId) {
    Tenant tenant = Tenant.of(tenantId);
    TokenIssuer tokenIssuer = new TokenIssuer(tenant.issuer());
    ClientConfigurationManagementResponse response = clientManagementApi.get(tokenIssuer, new ClientId(clientId));

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(response.content(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
