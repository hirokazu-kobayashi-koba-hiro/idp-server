package org.idp.sample;

import java.util.List;
import java.util.Map;
import org.idp.server.CredentialApi;
import org.idp.server.IdpServerApplication;
import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.handler.credential.io.*;
import org.idp.server.oauth.vc.CredentialDefinition;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegate;
import org.idp.server.verifiablecredential.VerifiableCredentialDelegateResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("{tenant-id}/api/v1/credentials")
public class CredentialV1Api implements ParameterTransformable, VerifiableCredentialDelegate {

  CredentialApi credentialApi;

  public CredentialV1Api(
      IdpServerApplication idpServerApplication) {
    this.credentialApi = idpServerApplication.credentialApi();
    credentialApi.setDelegate(this);
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

  @PostMapping("/deferred-request")
  public ResponseEntity<?> requestDeferred(
      @RequestHeader(required = false, value = "Authorization") String authorizationHeader,
      @RequestHeader(required = false, value = "x-ssl-cert") String clientCert,
      @PathVariable("tenant-id") String tenantId,
      @RequestBody(required = false) Map<String, Object> params) {
    Tenant tenant = Tenant.of(tenantId);
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
  public VerifiableCredentialDelegateResponse getCredential(
      TokenIssuer tokenIssuer, Subject subject, List<CredentialDefinition> credentialDefinitions) {
    if (subject.value().equals("pending")) {
      return VerifiableCredentialDelegateResponse.pending();
    }
    return VerifiableCredentialDelegateResponse.issued(getCredential());
  }

  public VerifiableCredential getCredential() {
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
                        "issuer": "https://example.edu/issuers/565049",
                        "issuanceDate": "2010-01-01T19:23:24Z",
                        "credentialSubject": {
                            "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
                            "alumniOf": {
                                "id": "did:example:c276e12ec21ebfeb1f712ebc6f1",
                                "name": [
                                    {
                                        "value": "Example University",
                                        "lang": "en"
                                    },
                                    {
                                        "value": "Exemple d'Universite",
                                        "lang": "fr"
                                    }
                                ]
                            }
                        },
                        "proof": {
                            "type": "RsaSignature2018",
                            "created": "2017-06-18T21:19:10Z",
                            "proofPurpose": "assertionMethod",
                            "verificationMethod": "https://example.edu/issuers/565049#key-1",
                            "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5XsITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUcX16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtjPAYuNzVBAh4vGHSrQyHUdBBPM"
                        }
                    }
                    """;
    return VerifiableCredential.parse(vc);
  }
}
