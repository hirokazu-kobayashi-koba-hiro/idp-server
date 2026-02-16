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

/**
 * Creates the OpenID Connect Discovery response (/.well-known/openid-configuration).
 *
 * <p>Converts {@link AuthorizationServerConfiguration} into a JSON-compatible Map following the
 * OpenID Connect Discovery 1.0 specification.
 *
 * <p>The response includes metadata from multiple specifications:
 *
 * <ul>
 *   <li><b>OpenID Connect Discovery 1.0</b> - Core discovery metadata (issuer, endpoints, supported
 *       algorithms, claims)
 *   <li><b>RFC 8414</b> - OAuth 2.0 Authorization Server Metadata
 *       (code_challenge_methods_supported, introspection/revocation endpoints)
 *   <li><b>RFC 9126</b> - Pushed Authorization Requests (PAR endpoint)
 *   <li><b>RFC 9101</b> - JAR (require_signed_request_object)
 *   <li><b>RFC 9207</b> - Authorization Response Issuer Identifier
 *       (authorization_response_iss_parameter_supported)
 *   <li><b>RFC 8705</b> - mTLS (tls_client_certificate_bound_access_tokens, mtls_endpoint_aliases)
 *   <li><b>RFC 9396</b> - RAR (authorization_details_types_supported)
 *   <li><b>JARM</b> - JWT Secured Authorization Response Mode
 *       (authorization_signing_alg_values_supported)
 *   <li><b>CIBA Core 1.0</b> - Backchannel authentication metadata
 *   <li><b>OpenID Connect for IDA</b> - Identity assurance metadata (verified_claims_supported)
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect
 *     Discovery 1.0</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8414">RFC 8414</a>
 */
public class ServerConfigurationResponseCreator {
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public ServerConfigurationResponseCreator(
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    Map<String, Object> map = new HashMap<>();

    // OpenID Connect Discovery 1.0 - Core metadata
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
    if (authorizationServerConfiguration.hasEndSessionEndpoint()) {
      map.put("end_session_endpoint", authorizationServerConfiguration.endSessionEndpoint());
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

    // OpenID Connect Discovery 1.0 - Algorithm metadata
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

    // RFC 9101 - JAR (JWT-Secured Authorization Request)
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

    // OpenID Connect Discovery 1.0 - Client authentication
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

    // OpenID Connect Discovery 1.0 - Display, claims, request parameters
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

    // RFC 9126 - Pushed Authorization Requests (PAR)
    if (authorizationServerConfiguration.hasPushedAuthorizationRequestEndpoint()) {
      map.put(
          "pushed_authorization_request_endpoint",
          authorizationServerConfiguration.pushedAuthorizationRequestEndpoint());
    }

    // RFC 8414 - PKCE code challenge methods
    if (authorizationServerConfiguration.hasCodeChallengeMethodsSupported()) {
      map.put(
          "code_challenge_methods_supported",
          authorizationServerConfiguration.codeChallengeMethodsSupported());
    }

    // RFC 9101 - Require signed request object
    map.put(
        "require_signed_request_object",
        authorizationServerConfiguration.requireSignedRequestObject());

    // RFC 9207 - Authorization response issuer identifier
    map.put(
        "authorization_response_iss_parameter_supported",
        authorizationServerConfiguration.authorizationResponseIssParameterSupported());

    // JARM - JWT Secured Authorization Response Mode
    if (authorizationServerConfiguration.hasAuthorizationSigningAlgValuesSupported()) {
      map.put(
          "authorization_signing_alg_values_supported",
          authorizationServerConfiguration.authorizationSigningAlgValuesSupported());
    }

    // RFC 8705 - mTLS
    map.put(
        "tls_client_certificate_bound_access_tokens",
        authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens());
    if (authorizationServerConfiguration.hasMtlsEndpointAliases()) {
      map.put("mtls_endpoint_aliases", authorizationServerConfiguration.mtlsEndpointAliases());
    }

    // RFC 8414 - Introspection and Revocation endpoints
    if (authorizationServerConfiguration.hasIntrospectionEndpoint()) {
      map.put("introspection_endpoint", authorizationServerConfiguration.introspectionEndpoint());
    }
    if (authorizationServerConfiguration.hasRevocationEndpoint()) {
      map.put("revocation_endpoint", authorizationServerConfiguration.revocationEndpoint());
    }

    // RFC 9396 - Rich Authorization Requests (RAR)
    if (!authorizationServerConfiguration.authorizationDetailsTypesSupported().isEmpty()) {
      map.put(
          "authorization_details_types_supported",
          authorizationServerConfiguration.authorizationDetailsTypesSupported());
    }

    // CIBA Core 1.0 - Backchannel authentication
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

    // OpenID Connect for Identity Assurance (IDA)
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
