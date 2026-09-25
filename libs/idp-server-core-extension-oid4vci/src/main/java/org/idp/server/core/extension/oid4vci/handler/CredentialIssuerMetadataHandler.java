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
package org.idp.server.core.extension.oid4vci.handler;

import java.util.Map;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataResponse;
import org.idp.server.core.extension.oid4vci.io.CredentialIssuerMetadataStatus;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Serves the Credential Issuer Metadata (OpenID4VCI 1.0 Section 12.2).
 *
 * <p>A tenant that issues no credential answers 404, the same as a path nothing is served at: the
 * metadata document is what makes a tenant a Credential Issuer.
 */
public class CredentialIssuerMetadataHandler {

  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;

  public CredentialIssuerMetadataHandler(
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository) {
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
  }

  public CredentialIssuerMetadataResponse getMetadata(Tenant tenant) {
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
      return new CredentialIssuerMetadataResponse(
          CredentialIssuerMetadataStatus.NOT_FOUND,
          Map.of("error", "not_found", "error_description", "this tenant issues no credential"));
    }

    Map<String, Object> metadata =
        authorizationServerConfiguration
            .credentialIssuerMetadata()
            .toMetadata(authorizationServerConfiguration.issuer());
    return new CredentialIssuerMetadataResponse(CredentialIssuerMetadataStatus.OK, metadata);
  }
}
