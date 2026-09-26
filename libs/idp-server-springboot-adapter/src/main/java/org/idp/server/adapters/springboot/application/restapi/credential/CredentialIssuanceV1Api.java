/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.idp.server.adapters.springboot.application.restapi.credential;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.adapters.springboot.application.restapi.SecurityHeaderConfigurable;
import org.idp.server.core.extension.oid4vci.CredentialIssuanceApi;
import org.idp.server.core.extension.oid4vci.io.CredentialNonceResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialResponse;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The Credential Issuer's endpoints a Wallet calls (OpenID4VCI 1.0). */
@RestController
@RequestMapping("{tenant-id}/v1/credentials")
public class CredentialIssuanceV1Api implements ParameterTransformable, SecurityHeaderConfigurable {

  CredentialIssuanceApi credentialIssuanceApi;

  public CredentialIssuanceV1Api(IdpServerApplication idpServerApplication) {
    this.credentialIssuanceApi = idpServerApplication.credentialIssuanceApi();
  }

  /**
   * Section 8: the Credential Request. The body is JSON (Section 8.2: "If the Credential Request is
   * not encrypted, the media type of the request MUST be set to application/json").
   */
  @PostMapping(consumes = "application/json")
  public ResponseEntity<?> credential(
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    HttpRequestInputs inputs = transformInputs(null, httpServletRequest);
    CredentialResponse response =
        credentialIssuanceApi.requestCredential(tenantIdentifier, inputs, body);

    HttpHeaders httpHeaders = createSecurityHeaders();
    httpHeaders.add("Content-Type", "application/json");
    httpHeaders.add("Cache-Control", "no-store");
    applyWwwAuthenticateIfUnauthorized(
        httpHeaders, response.statusCode(), response.contents(), inputs.authorizationHeader());
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }

  /** Section 7: not a protected resource, so no access token is read. */
  @PostMapping("/nonce")
  public ResponseEntity<?> nonce(@PathVariable("tenant-id") TenantIdentifier tenantIdentifier) {

    CredentialNonceResponse response = credentialIssuanceApi.issueNonce(tenantIdentifier);

    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("Content-Type", "application/json");
    // Section 7.2: the Credential Issuer MUST make the response uncacheable.
    httpHeaders.add("Cache-Control", "no-store");
    return new ResponseEntity<>(
        response.contents(), httpHeaders, HttpStatus.valueOf(response.statusCode()));
  }
}
