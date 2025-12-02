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

package org.idp.server.core.openid.discovery;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;

public class ServerConfigurationResponseCreator {
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public ServerConfigurationResponseCreator(
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    Map<String, Object> map = new HashMap<>();
    map.put("issuer", authorizationServerConfiguration.issuer());
    map.put("authorization_endpoint", authorizationServerConfiguration.authorizationEndpoint());
    if (authorizationServerConfiguration.hasTokenEndpoint()) {
      map.put("token_endpoint", authorizationServerConfiguration.tokenEndpoint());
    }
    if (authorizationServerConfiguration.hasUserinfoEndpoint()) {
      map.put("userinfo_endpoint", authorizationServerConfiguration.userinfoEndpoint());
    }
    if (authorizationServerConfiguration.hasRegistrationEndpoint()) {
      map.put("registration_endpoint", authorizationServerConfiguration.registrationEndpoint());
    }
    map.put("jwks_uri", authorizationServerConfiguration.jwksUri());
    if (authorizationServerConfiguration.hasScopesSupported()) {
      map.put("scopes_supported", authorizationServerConfiguration.scopesSupported());
    }
    map.put("response_types_supported", authorizationServerConfiguration.responseTypesSupported());
    if (authorizationServerConfiguration.hasResponseModesSupported()) {
      map.put(
          "response_modes_supported", authorizationServerConfiguration.responseModesSupported());
    }
    if (authorizationServerConfiguration.hasGrantTypesSupported()) {
      map.put("grant_types_supported", authorizationServerConfiguration.grantTypesSupported());
    }
    if (authorizationServerConfiguration.hasAcrValuesSupported()) {
      map.put("acr_values_supported", authorizationServerConfiguration.acrValuesSupported());
    }
    map.put("subject_types_supported", authorizationServerConfiguration.subjectTypesSupported());
    map.put(
        "id_token_signing_alg_values_supported",
        authorizationServerConfiguration.idTokenSigningAlgValuesSupported());
    if (authorizationServerConfiguration.hasIdTokenEncryptionAlgValuesSupported()) {
      map.put(
          "id_token_encryption_alg_values_supported",
          authorizationServerConfiguration.idTokenEncryptionAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put(
          "id_token_encryption_enc_values_supported",
          authorizationServerConfiguration.idTokenEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoSigningAlgValuesSupported()) {
      map.put(
          "userinfo_signing_alg_values_supported",
          authorizationServerConfiguration.userinfoSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoEncryptionAlgValuesSupported()) {
      map.put(
          "userinfo_encryption_alg_values_supported",
          authorizationServerConfiguration.userinfoEncryptionAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoEncryptionEncValuesSupported()) {
      map.put(
          "userinfo_encryption_enc_values_supported",
          authorizationServerConfiguration.userinfoEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectSigningAlgValuesSupported()) {
      map.put(
          "request_object_signing_alg_values_supported",
          authorizationServerConfiguration.requestObjectSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectEncryptionAlgValuesSupported()) {
      map.put(
          "request_object_encryption_alg_values_supported",
          authorizationServerConfiguration.requestObjectEncryptionAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectEncryptionEncValuesSupported()) {
      map.put(
          "request_object_encryption_enc_values_supported",
          authorizationServerConfiguration.requestObjectEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasTokenEndpointAuthMethodsSupported()) {
      map.put(
          "token_endpoint_auth_methods_supported",
          authorizationServerConfiguration.tokenEndpointAuthMethodsSupported());
    }
    if (authorizationServerConfiguration.hasTokenEndpointAuthSigningAlgValuesSupported()) {
      map.put(
          "token_endpoint_auth_signing_alg_values_supported",
          authorizationServerConfiguration.tokenEndpointAuthSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasDisplayValuesSupported()) {
      map.put(
          "display_values_supported", authorizationServerConfiguration.displayValuesSupported());
    }
    if (authorizationServerConfiguration.hasClaimTypesSupported()) {
      map.put("claim_types_supported", authorizationServerConfiguration.claimTypesSupported());
    }
    if (authorizationServerConfiguration.hasClaimsSupported()) {
      map.put("claims_supported", authorizationServerConfiguration.claimsSupported());
    }
    map.put(
        "claims_parameter_supported", authorizationServerConfiguration.claimsParameterSupported());
    map.put(
        "request_parameter_supported",
        authorizationServerConfiguration.requestParameterSupported());
    map.put(
        "request_uri_parameter_supported",
        authorizationServerConfiguration.requestUriParameterSupported());
    map.put(
        "require_request_uri_registration",
        authorizationServerConfiguration.requireRequestUriRegistration());
    map.put(
        "tls_client_certificate_bound_access_tokens",
        authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens());
    if (authorizationServerConfiguration.hasMtlsEndpointAliases()) {
      map.put("mtls_endpoint_aliases", authorizationServerConfiguration.mtlsEndpointAliases());
    }

    // Introspection and Revocation endpoints
    if (authorizationServerConfiguration.hasIntrospectionEndpoint()) {
      map.put("introspection_endpoint", authorizationServerConfiguration.introspectionEndpoint());
    }
    if (authorizationServerConfiguration.hasRevocationEndpoint()) {
      map.put("revocation_endpoint", authorizationServerConfiguration.revocationEndpoint());
    }

    // Authorization details types (RAR - RFC 9396)
    if (!authorizationServerConfiguration.authorizationDetailsTypesSupported().isEmpty()) {
      map.put(
          "authorization_details_types_supported",
          authorizationServerConfiguration.authorizationDetailsTypesSupported());
    }

    if (authorizationServerConfiguration.hasBackchannelTokenDeliveryModesSupported()) {
      map.put(
          "backchannel_token_delivery_modes_supported",
          authorizationServerConfiguration.backchannelTokenDeliveryModesSupported());
    }
    if (authorizationServerConfiguration.hasBackchannelAuthenticationEndpoint()) {
      map.put(
          "backchannel_authentication_endpoint",
          authorizationServerConfiguration.backchannelAuthenticationEndpoint());
    }
    if (authorizationServerConfiguration
        .hasBackchannelAuthenticationRequestSigningAlgValuesSupported()) {
      map.put(
          "backchannel_authentication_request_signing_alg_values_supported",
          authorizationServerConfiguration
              .backchannelAuthenticationRequestSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasBackchannelUserCodeParameterSupported()) {
      map.put(
          "backchannel_user_code_parameter_supported",
          authorizationServerConfiguration.backchannelUserCodeParameterSupported());
    }

    // ida
    map.put(
        "verified_claims_supported", authorizationServerConfiguration.verifiedClaimsSupported());
    if (authorizationServerConfiguration.verifiedClaimsSupported()) {
      map.put(
          "trust_frameworks_supported",
          authorizationServerConfiguration.trustFrameworksSupported());
      map.put("evidence_supported", authorizationServerConfiguration.evidenceSupported());
      map.put("id_documents_supported", authorizationServerConfiguration.idDocumentsSupported());
      map.put(
          "id_documents_verification_methods_supported",
          authorizationServerConfiguration.idDocumentsVerificationMethodsSupported());
      map.put(
          "claims_in_verified_claims_supported",
          authorizationServerConfiguration.claimsInVerifiedClaimsSupported());
    }
    return map;
  }
}
