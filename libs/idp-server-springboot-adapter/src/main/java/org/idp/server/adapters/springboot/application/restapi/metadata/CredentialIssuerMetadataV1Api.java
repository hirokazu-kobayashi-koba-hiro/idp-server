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
package org.idp.server.adapters.springboot.application.restapi.metadata;

import org.idp.server.adapters.springboot.application.restapi.SecurityHeaderConfigurable;
import org.idp.server.core.extension.oid4vci.Oid4vciMetaDataApi;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataResponse;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credential Issuer Metadata (OpenID4VCI 1.0 Section 12.2.2).
 *
 * <p>The tenant's Credential Issuer Identifier has a path component ({@code
 * https://host/{tenant}}), and the well-known segment goes between the host and that path.
 */
@RestController
@RequestMapping
public class CredentialIssuerMetadataV1Api implements SecurityHeaderConfigurable {

  Oid4vciMetaDataApi oid4vciMetaDataApi;

  public CredentialIssuerMetadataV1Api(IdpServerApplication idpServerApplication) {
    this.oid4vciMetaDataApi = idpServerApplication.oid4vciMetaDataApi();
  }

  @GetMapping(".well-known/openid-credential-issuer/{tenant-id}")
  public ResponseEntity<?> getMetadata(@PathVariable("tenant-id") TenantIdentifier tenantId) {

    CredentialIssuerMetadataResponse response = oid4vciMetaDataApi.getMetadata(tenantId);

    HttpHeaders headers = createSecurityHeaders();
    headers.setCacheControl("public, max-age=3600");
    headers.setContentType(MediaType.APPLICATION_JSON);

    return new ResponseEntity<>(
        response.content(), headers, HttpStatus.valueOf(response.statusCode()));
  }
}
