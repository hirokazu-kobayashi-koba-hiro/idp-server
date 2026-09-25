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

package org.idp.server.core.openid.oauth.verifier.extension;

import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetailsInvalidException;

public class VerifiableCredentialVerifier {
  AuthorizationDetails authorizationDetails;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public VerifiableCredentialVerifier(
      AuthorizationDetails authorizationDetails,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationDetails = authorizationDetails;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    throwExceptionIfNotContainsType();
    throwExceptionIfUnSupportedType();
    throwExceptionIfUnauthorizedType();
    throwExceptionIfUnknownCredentialConfiguration();
  }

  void throwExceptionIfNotContainsType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationDetail.hasType()) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details", "authorization details does not contains type");
          }
        });
  }

  void throwExceptionIfUnSupportedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationServerConfiguration.isSupportedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unsupported authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }

  void throwExceptionIfUnauthorizedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!clientConfiguration.isAuthorizedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unauthorized authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }

  /**
   * OpenID4VCI 1.0 Section 5.1.1: {@code credential_configuration_id} is REQUIRED and names an
   * entry of the Credential Issuer's {@code credential_configurations_supported}.
   */
  void throwExceptionIfUnknownCredentialConfiguration() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationDetail.isVerifiableCredential()) {
            return;
          }
          String credentialConfigurationId = authorizationDetail.credentialConfigurationId();
          if (credentialConfigurationId.isEmpty()) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                "openid_credential authorization details does not contain credential_configuration_id");
          }
          if (!authorizationServerConfiguration
              .credentialIssuerMetadata()
              .hasCredentialConfiguration(credentialConfigurationId)) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unknown credential_configuration_id (%s)", credentialConfigurationId));
          }
        });
  }
}
