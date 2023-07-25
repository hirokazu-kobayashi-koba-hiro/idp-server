package org.idp.sample;

import java.util.List;
import java.util.Map;
import org.idp.server.CredentialApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.handler.credential.io.BatchCredentialRequest;
import org.idp.server.handler.credential.io.BatchCredentialResponse;
import org.idp.server.handler.credential.io.CredentialRequest;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.oauth.vc.CredentialDefinition;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/credentials")
public class CredentialV1Api implements ParameterTransformable, VerifiableCredentialDelegate {

  CredentialApi credentialApi;
  UserMockService userMockService;

  public CredentialV1Api(
      IdpServerApplication idpServerApplication, UserMockService userMockService) {
    this.credentialApi = idpServerApplication.credentialApi();
    credentialApi.setDelegate(this);
    this.userMockService = userMockService;
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = Tenant.of(tenantId);
    CredentialRequest credentialRequest =
        new CredentialRequest(authorizationHeader, params, tenant.issuer());
    credentialRequest.setClientCert(clientCert);
    CredentialResponse response = credentialApi.request(credentialRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.headers());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/batch-requests")
  public ResponseEntity<?> requestBatch(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = Tenant.of(tenantId);
    BatchCredentialRequest request =
        new BatchCredentialRequest(authorizationHeader, params, tenant.issuer());
    request.setClientCert(clientCert);
    BatchCredentialResponse response = credentialApi.requestBatch(request);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.headers());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @Override
  public VerifiableCredential getCredential(
      TokenIssuer tokenIssuer, Subject subject, List<CredentialDefinition> credentialDefinitions) {
    return userMockService.getCredential();
  }
}
