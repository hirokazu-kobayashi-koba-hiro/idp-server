package org.idp.sample.presentation.api;

import java.util.List;
import java.util.Map;
import org.idp.sample.application.service.TenantService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.domain.model.tenant.TenantIdentifier;
import org.idp.server.IdpServerApplication;
import org.idp.server.api.CredentialApi;
import org.idp.server.basic.vc.Credential;
import org.idp.server.handler.credential.io.*;
import org.idp.server.oauth.vc.CredentialDefinition;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.CredentialDelegateResponse;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/credentials")
public class CredentialV1Api implements ParameterTransformable, VerifiableCredentialDelegate {

  CredentialApi credentialApi;
  TenantService tenantService;

  public CredentialV1Api(IdpServerApplication idpServerApplication, TenantService tenantService) {
    this.credentialApi = idpServerApplication.credentialApi();
    credentialApi.setDelegate(this);
    this.tenantService = tenantService;
  }

  @PostMapping
  public ResponseEntity<?> post(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = tenantService.get(tenantId);
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
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = tenantService.get(tenantId);
    BatchCredentialRequest request =
        new BatchCredentialRequest(authorizationHeader, params, tenant.issuer());
    request.setClientCert(clientCert);
    BatchCredentialResponse response = credentialApi.requestBatch(request);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.headers());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @PostMapping("/deferred-request")
  public ResponseEntity<?> requestDeferred(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") TenantIdentifier tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = tenantService.get(tenantId);
    DeferredCredentialRequest request =
        new DeferredCredentialRequest(authorizationHeader, params, tenant.issuer());
    request.setClientCert(clientCert);
    DeferredCredentialResponse response = credentialApi.requestDeferred(request);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setAll(response.headers());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  @Override
  public CredentialDelegateResponse getCredential(
      TokenIssuer tokenIssuer, Subject subject, List<CredentialDefinition> credentialDefinitions) {
    if (subject.value().equals("pending")) {
      return CredentialDelegateResponse.pending();
    }
    return CredentialDelegateResponse.issued(getCredential());
  }

  public Credential getCredential() {
    String vc =
        """
                    {
                        "@context": [
                            "https://www.w3.org/2018/credentials/v1",
                            "https://www.w3.org/2018/credentials/examples/v1"
                        ],
                        "id": "http://example.edu/credentials/1872",
                        "type": [
                            "VerifiableCredential",
                            "AlumniCredential"
                        ],
                        "issuer": "did:web:assets.dev.trustid.sbi-fc.com",
                        "issuanceDate": "2010-01-01T19:23:24Z",
                        "credentialSubject": {
                            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
                            "alumniOf": {
                                "id": "did:example:c276e12ec21ebfeb1f712ebc6f1"
                            }
                        }
                    }
                    """;
    return Credential.parse(vc);
  }
}
