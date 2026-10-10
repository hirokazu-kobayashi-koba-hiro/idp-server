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
package org.idp.server.core.extension.oid4vci.verifier;

import org.idp.server.core.extension.oid4vci.exception.CredentialRequestInvalidException;
import org.idp.server.core.extension.oid4vci.request.CredentialRequestParameters;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialConfiguration;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialIssuerMetadataConfiguration;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetail;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInsufficientScopeException;

/**
 * Checks what a Credential Request asks for against the metadata and the access token (OpenID4VCI
 * 1.0 Section 8.2).
 *
 * <p>No {@code credential_identifiers} are handed out in the Token Response, so a request names its
 * credential by {@code credential_configuration_id}; a {@code credential_identifier} cannot be one
 * this issuer knows.
 */
public class CredentialRequestVerifier {

  CredentialRequestParameters parameters;
  CredentialIssuerMetadataConfiguration metadata;
  OAuthToken oAuthToken;

  public CredentialRequestVerifier(
      CredentialRequestParameters parameters,
      CredentialIssuerMetadataConfiguration metadata,
      OAuthToken oAuthToken) {
    this.parameters = parameters;
    this.metadata = metadata;
    this.oAuthToken = oAuthToken;
  }

  public void verify() {
    throwExceptionIfBothOrNeitherIdentifier();
    throwExceptionIfCredentialIdentifier();
    throwExceptionIfUnknownConfiguration();
    throwExceptionIfNotAuthorized();
    throwExceptionIfEncryptionRequested();
  }

  /** Section 8.2: each of the two "MUST NOT be present" when the other is used. */
  void throwExceptionIfBothOrNeitherIdentifier() {
    boolean identifier = parameters.hasCredentialIdentifier();
    boolean configuration = parameters.hasCredentialConfigurationId();
    if (identifier && configuration) {
      throw new CredentialRequestInvalidException(
          "invalid_credential_request",
          "credential_identifier and credential_configuration_id must not both be present");
    }
    if (!identifier && !configuration) {
      throw new CredentialRequestInvalidException(
          "invalid_credential_request",
          "credential_configuration_id or credential_identifier is required");
    }
  }

  void throwExceptionIfCredentialIdentifier() {
    if (parameters.hasCredentialIdentifier()) {
      throw new CredentialRequestInvalidException(
          "unknown_credential_identifier",
          "unknown credential_identifier: " + parameters.credentialIdentifier());
    }
  }

  void throwExceptionIfUnknownConfiguration() {
    if (!metadata.hasCredentialConfiguration(parameters.credentialConfigurationId())) {
      throw new CredentialRequestInvalidException(
          "unknown_credential_configuration",
          "unknown credential_configuration_id: " + parameters.credentialConfigurationId());
    }
  }

  /**
   * Section 8.2: the configuration "MUST contain one of the value(s) used in the scope parameter in
   * the Authorization Request". An {@code openid_credential} authorization detail naming it also
   * authorizes it (Section 5.1.1). Otherwise the token does not enable this issuance, which Section
   * 8.3.1.1 answers as an authorization error.
   */
  void throwExceptionIfNotAuthorized() {
    String credentialConfigurationId = parameters.credentialConfigurationId();
    CredentialConfiguration configuration =
        metadata.credentialConfiguration(credentialConfigurationId);

    boolean byScope =
        configuration.hasScope() && oAuthToken.scopes().contains(configuration.scope());
    boolean byAuthorizationDetails =
        oAuthToken.authorizationDetails().values().stream()
            .filter(AuthorizationDetail::isVerifiableCredential)
            .anyMatch(
                detail -> credentialConfigurationId.equals(detail.credentialConfigurationId()));

    if (!byScope && !byAuthorizationDetails) {
      throw new TokenInsufficientScopeException(
          "access token does not authorize credential_configuration_id: "
              + credentialConfigurationId);
    }
  }

  /** The issuer publishes no credential_response_encryption, so it cannot honour one. */
  void throwExceptionIfEncryptionRequested() {
    if (parameters.hasCredentialResponseEncryption()) {
      throw new CredentialRequestInvalidException(
          "invalid_encryption_parameters", "credential response encryption is not supported");
    }
  }
}
