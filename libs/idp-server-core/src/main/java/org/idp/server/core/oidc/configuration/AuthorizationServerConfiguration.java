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

package org.idp.server.core.oidc.configuration;

import java.util.*;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.ResponseType;
import org.idp.server.basic.type.oauth.TokenIssuer;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.core.oidc.configuration.vc.VerifiableCredentialConfiguration;
import org.idp.server.platform.json.JsonReadable;

/** ServerConfiguration */
public class AuthorizationServerConfiguration implements JsonReadable {
  String issuer;
  String authorizationEndpoint;
  String tokenEndpoint = "";
  String userinfoEndpoint = "";
  String jwks;
  String jwksUri;
  String registrationEndpoint = "";
  List<String> scopesSupported = new ArrayList<>();
  List<String> responseTypesSupported = new ArrayList<>();
  List<String> responseModesSupported = new ArrayList<>();
  List<String> grantTypesSupported = new ArrayList<>();
  List<String> acrValuesSupported = new ArrayList<>();
  List<String> subjectTypesSupported = new ArrayList<>();
  List<String> idTokenSigningAlgValuesSupported = new ArrayList<>();
  List<String> idTokenEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> idTokenEncryptionEncValuesSupported = new ArrayList<>();
  List<String> userinfoSigningAlgValuesSupported = new ArrayList<>();
  List<String> userinfoEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> userinfoEncryptionEncValuesSupported = new ArrayList<>();
  List<String> requestObjectSigningAlgValuesSupported = new ArrayList<>();
  List<String> requestObjectEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> requestObjectEncryptionEncValuesSupported = new ArrayList<>();
  List<String> authorizationSigningAlgValuesSupported = new ArrayList<>();
  List<String> authorizationEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> authorizationEncryptionEncValuesSupported = new ArrayList<>();
  List<String> tokenEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> tokenEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  List<String> displayValuesSupported = new ArrayList<>();
  List<String> claimTypesSupported = new ArrayList<>();
  List<String> claimsSupported = new ArrayList<>();
  boolean claimsParameterSupported = true;
  boolean requestParameterSupported = true;
  boolean requestUriParameterSupported = true;
  boolean requireRequestUriRegistration = true;
  String revocationEndpoint = "";
  List<String> revocationEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> revocationEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  String introspectionEndpoint = "";
  List<String> introspectionEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> introspectionEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  List<String> codeChallengeMethodsSupported = new ArrayList<>();
  boolean tlsClientCertificateBoundAccessTokens = false;
  boolean requireSignedRequestObject = false;
  boolean authorizationResponseIssParameterSupported = false;
  List<String> backchannelTokenDeliveryModesSupported = new ArrayList<>();
  String backchannelAuthenticationEndpoint = "";
  List<String> backchannelAuthenticationRequestSigningAlgValuesSupported = new ArrayList<>();
  boolean backchannelUserCodeParameterSupported = false;
  List<String> authorizationDetailsTypesSupported = new ArrayList<>();
  VerifiableCredentialConfiguration credentialIssuerMetadata =
      new VerifiableCredentialConfiguration();

  AuthorizationServerExtensionConfiguration extension =
      new AuthorizationServerExtensionConfiguration();

  public AuthorizationServerConfiguration() {}

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public String issuer() {
    return issuer;
  }

  public String authorizationEndpoint() {
    return authorizationEndpoint;
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public String userinfoEndpoint() {
    return userinfoEndpoint;
  }

  public String jwks() {
    return jwks;
  }

  public String jwksUri() {
    return jwksUri;
  }

  public String registrationEndpoint() {
    return registrationEndpoint;
  }

  public List<String> scopesSupported() {
    return scopesSupported;
  }

  public List<String> responseTypesSupported() {
    return responseTypesSupported;
  }

  public List<String> responseModesSupported() {
    return responseModesSupported;
  }

  public List<String> grantTypesSupported() {
    return grantTypesSupported;
  }

  public List<String> acrValuesSupported() {
    return acrValuesSupported;
  }

  public List<String> subjectTypesSupported() {
    return subjectTypesSupported;
  }

  public List<String> idTokenSigningAlgValuesSupported() {
    return idTokenSigningAlgValuesSupported;
  }

  public List<String> idTokenEncryptionAlgValuesSupported() {
    return idTokenEncryptionAlgValuesSupported;
  }

  public List<String> idTokenEncryptionEncValuesSupported() {
    return idTokenEncryptionEncValuesSupported;
  }

  public List<String> userinfoSigningAlgValuesSupported() {
    return userinfoSigningAlgValuesSupported;
  }

  public List<String> userinfoEncryptionAlgValuesSupported() {
    return userinfoEncryptionAlgValuesSupported;
  }

  public List<String> userinfoEncryptionEncValuesSupported() {
    return userinfoEncryptionEncValuesSupported;
  }

  public List<String> requestObjectSigningAlgValuesSupported() {
    return requestObjectSigningAlgValuesSupported;
  }

  public List<String> requestObjectEncryptionAlgValuesSupported() {
    return requestObjectEncryptionAlgValuesSupported;
  }

  public List<String> requestObjectEncryptionEncValuesSupported() {
    return requestObjectEncryptionEncValuesSupported;
  }

  public List<String> authorizationSigningAlgValuesSupported() {
    return authorizationSigningAlgValuesSupported;
  }

  public List<String> authorizationEncryptionAlgValuesSupported() {
    return authorizationEncryptionAlgValuesSupported;
  }

  public List<String> authorizationEncryptionEncValuesSupported() {
    return authorizationEncryptionEncValuesSupported;
  }

  public List<String> tokenEndpointAuthMethodsSupported() {
    return tokenEndpointAuthMethodsSupported;
  }

  public boolean isSupportedClientAuthenticationType(String value) {
    return tokenEndpointAuthMethodsSupported.contains(value);
  }

  public List<String> tokenEndpointAuthSigningAlgValuesSupported() {
    return tokenEndpointAuthSigningAlgValuesSupported;
  }

  public List<String> displayValuesSupported() {
    return displayValuesSupported;
  }

  public List<String> claimTypesSupported() {
    return claimTypesSupported;
  }

  public List<String> claimsSupported() {
    return claimsSupported;
  }

  public boolean claimsParameterSupported() {
    return claimsParameterSupported;
  }

  public boolean requestParameterSupported() {
    return requestParameterSupported;
  }

  public boolean requestUriParameterSupported() {
    return requestUriParameterSupported;
  }

  public boolean requireRequestUriRegistration() {
    return requireRequestUriRegistration;
  }

  public String revocationEndpoint() {
    return revocationEndpoint;
  }

  public List<String> revocationEndpointAuthMethodsSupported() {
    return revocationEndpointAuthMethodsSupported;
  }

  public List<String> revocationEndpointAuthSigningAlgValuesSupported() {
    return revocationEndpointAuthSigningAlgValuesSupported;
  }

  public String introspectionEndpoint() {
    return introspectionEndpoint;
  }

  public List<String> introspectionEndpointAuthMethodsSupported() {
    return introspectionEndpointAuthMethodsSupported;
  }

  public List<String> introspectionEndpointAuthSigningAlgValuesSupported() {
    return introspectionEndpointAuthSigningAlgValuesSupported;
  }

  public List<String> codeChallengeMethodsSupported() {
    return codeChallengeMethodsSupported;
  }

  public boolean isTlsClientCertificateBoundAccessTokens() {
    return tlsClientCertificateBoundAccessTokens;
  }

  public boolean requireSignedRequestObject() {
    return requireSignedRequestObject;
  }

  public boolean authorizationResponseIssParameterSupported() {
    return authorizationResponseIssParameterSupported;
  }

  public List<String> filteredScope(String spacedScopes) {
    List<String> scopes = Arrays.stream(spacedScopes.split(" ")).toList();
    return scopes.stream().filter(scope -> scopesSupported.contains(scope)).toList();
  }

  public List<String> filteredScope(List<String> scopes) {
    return scopes.stream().filter(scope -> scopesSupported.contains(scope)).toList();
  }

  public boolean hasFapiBaselineScope(Set<String> scopes) {
    return extension.hasFapiBaselineScope(scopes);
  }

  public boolean hasFapiAdvanceScope(Set<String> scopes) {
    return extension.hasFapiAdvanceScope(scopes);
  }

  public boolean hasRequiredIdentityVerificationScope(Set<String> scopes) {
    return extension.hasRequiredIdentityVerificationScope(scopes);
  }

  public List<String> requiredIdentityVerificationScope() {
    return extension.requiredIdentityVerificationScopes();
  }

  public boolean isIdentifierAccessTokenType() {
    return extension.isIdentifierAccessTokenType();
  }

  public int authorizationCodeValidDuration() {
    return extension.authorizationCodeValidDuration();
  }

  public String tokenSignedKeyId() {
    return extension.tokenSignedKeyId();
  }

  public String idTokenSignedKeyId() {
    return extension.idTokenSignedKeyId();
  }

  public long accessTokenDuration() {
    return extension.accessTokenDuration();
  }

  public long refreshTokenDuration() {
    return extension.refreshTokenDuration();
  }

  public long idTokenDuration() {
    return extension.idTokenDuration();
  }

  public boolean isIdTokenStrictMode() {
    return extension.idTokenStrictMode();
  }

  public boolean hasTokenEndpoint() {
    return Objects.nonNull(tokenEndpoint) && !tokenEndpoint.isEmpty();
  }

  public boolean hasUserinfoEndpoint() {
    return Objects.nonNull(userinfoEndpoint) && !userinfoEndpoint.isEmpty();
  }

  public boolean hasRegistrationEndpoint() {
    return Objects.nonNull(revocationEndpoint) && !registrationEndpoint.isEmpty();
  }

  public boolean isSupportedResponseType(ResponseType responseType) {
    return responseTypesSupported.contains(responseType.value());
  }

  public boolean hasScopesSupported() {
    return !scopesSupported.isEmpty();
  }

  public boolean hasResponseTypesSupported() {
    return !responseTypesSupported.isEmpty();
  }

  public boolean hasResponseModesSupported() {
    return !responseModesSupported.isEmpty();
  }

  public boolean hasGrantTypesSupported() {
    return !grantTypesSupported.isEmpty();
  }

  public boolean hasAcrValuesSupported() {
    return !acrValuesSupported.isEmpty();
  }

  public boolean hasSubjectTypesSupported() {
    return !subjectTypesSupported.isEmpty();
  }

  public boolean hasIdTokenSigningAlgValuesSupported() {
    return !idTokenSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasIdTokenEncryptionAlgValuesSupported() {
    return !idTokenEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasIdTokenEncryptionEncValuesSupported() {
    return !idTokenEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasUserinfoSigningAlgValuesSupported() {
    return !userinfoSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasUserinfoEncryptionAlgValuesSupported() {
    return !userinfoEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasUserinfoEncryptionEncValuesSupported() {
    return !userinfoEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectSigningAlgValuesSupported() {
    return !requestObjectSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectEncryptionAlgValuesSupported() {
    return !requestObjectEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectEncryptionEncValuesSupported() {
    return !requestObjectEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationSigningAlgValuesSupported() {
    return !authorizationSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationEncryptionAlgValuesSupported() {
    return !authorizationEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationEncryptionEncValuesSupported() {
    return !authorizationEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasTokenEndpointAuthMethodsSupported() {
    return !tokenEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasTokenEndpointAuthSigningAlgValuesSupported() {
    return !tokenEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasDisplayValuesSupported() {
    return !displayValuesSupported.isEmpty();
  }

  public boolean hasClaimTypesSupported() {
    return !claimTypesSupported.isEmpty();
  }

  public boolean hasClaimsSupported() {
    return !claimsSupported.isEmpty();
  }

  public boolean hasRevocationEndpoint() {
    return Objects.nonNull(revocationEndpoint) && !revocationEndpoint.isEmpty();
  }

  public boolean hasRevocationEndpointAuthMethodsSupported() {
    return !revocationEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasRevocationEndpointAuthSigningAlgValuesSupported() {
    return !revocationEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasIntrospectionEndpoint() {
    return Objects.nonNull(introspectionEndpoint) && !introspectionEndpoint.isEmpty();
  }

  public boolean hasIntrospectionEndpointAuthMethodsSupported() {
    return !introspectionEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasIntrospectionEndpointAuthSigningAlgValuesSupported() {
    return !introspectionEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasCodeChallengeMethodsSupported() {
    return !codeChallengeMethodsSupported.isEmpty();
  }

  public List<String> backchannelTokenDeliveryModesSupported() {
    return backchannelTokenDeliveryModesSupported;
  }

  public boolean hasBackchannelTokenDeliveryModesSupported() {
    return !backchannelTokenDeliveryModesSupported.isEmpty();
  }

  public String backchannelAuthenticationEndpoint() {
    return backchannelAuthenticationEndpoint;
  }

  public boolean hasBackchannelAuthenticationEndpoint() {
    return Objects.nonNull(backchannelAuthenticationEndpoint)
        && !backchannelAuthenticationEndpoint.isEmpty();
  }

  public List<String> backchannelAuthenticationRequestSigningAlgValuesSupported() {
    return backchannelAuthenticationRequestSigningAlgValuesSupported;
  }

  public boolean hasBackchannelAuthenticationRequestSigningAlgValuesSupported() {
    return !backchannelAuthenticationRequestSigningAlgValuesSupported.isEmpty();
  }

  public boolean backchannelUserCodeParameterSupported() {
    return backchannelUserCodeParameterSupported;
  }

  public boolean hasBackchannelUserCodeParameterSupported() {
    return Objects.nonNull(backchannelUserCodeParameterSupported);
  }

  public boolean isSupportedGrantType(GrantType grantType) {
    return grantTypesSupported.contains(grantType.value());
  }

  public long defaultMaxAge() {
    return extension.defaultMaxAge();
  }

  public List<String> authorizationDetailsTypesSupported() {
    return authorizationDetailsTypesSupported;
  }

  public boolean isSupportedAuthorizationDetailsType(String type) {
    return authorizationDetailsTypesSupported.contains(type);
  }

  public long authorizationResponseDuration() {
    return extension.authorizationResponseDuration();
  }

  public boolean hasKey(String algorithm) {
    return jwks.contains(algorithm);
  }

  public VerifiableCredentialConfiguration credentialIssuerMetadata() {
    return credentialIssuerMetadata;
  }

  public boolean hasCredentialIssuerMetadata() {
    return credentialIssuerMetadata.exists();
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    return extension.defaultCibaAuthenticationInteractionType();
  }

  public int backchannelAuthRequestExpiresIn() {
    return extension.backchannelAuthRequestExpiresIn();
  }

  public int backchannelAuthPollingInterval() {
    return extension.backchannelAuthPollingInterval();
  }

  public boolean requiredBackchannelAuthUserCode() {
    return extension.requiredBackchannelAuthUserCode();
  }

  public String backchannelAuthUserCodeType() {
    return extension.backchannelAuthUserCodeType();
  }

  public int oauthAuthorizationRequestExpiresIn() {
    return extension.oauthAuthorizationRequestExpiresIn();
  }

  public List<AuthenticationPolicy> authenticationPolicies() {
    return extension.authenticationPolicies();
  }

  public boolean enabledCustomClaimsScopeMapping() {
    return extension.enabledCustomClaimsScopeMapping();
  }

  public boolean enabledAccessTokenUserCustomProperties() {
    return extension.enabledAccessTokenUserCustomProperties();
  }

  public boolean enabledAccessTokenSelectiveUserCustomProperties() {
    return extension.enabledAccessTokenSelectiveUserCustomProperties();
  }

  public boolean enabledAccessTokenVerifiedClaims() {
    return extension.enabledAccessTokenVerifiedClaims();
  }

  public boolean enabledAccessTokenSelectiveVerifiedClaims() {
    return extension.enabledAccessTokenSelectiveVerifiedClaims();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("issuer", issuer);
    map.put("authorization_endpoint", authorizationEndpoint);
    if (hasTokenEndpoint()) {
      map.put("token_endpoint", tokenEndpoint);
    }
    if (hasUserinfoEndpoint()) {
      map.put("userinfo_endpoint", userinfoEndpoint());
    }
    if (hasRegistrationEndpoint()) {
      map.put("registration_endpoint", registrationEndpoint());
    }
    map.put("jwks_uri", jwksUri());
    if (hasScopesSupported()) {
      map.put("scopes_supported", scopesSupported());
    }
    map.put("response_types_supported", responseTypesSupported());
    if (hasResponseModesSupported()) {
      map.put("response_modes_supported", responseModesSupported());
    }
    if (hasGrantTypesSupported()) {
      map.put("grant_types_supported", grantTypesSupported());
    }
    if (hasAcrValuesSupported()) {
      map.put("acr_values_supported", acrValuesSupported());
    }
    map.put("subject_types_supported", subjectTypesSupported());
    map.put("id_token_signing_alg_values_supported", idTokenSigningAlgValuesSupported);
    if (hasIdTokenEncryptionEncValuesSupported()) {
      map.put("id_token_encryption_alg_values_supported", idTokenEncryptionEncValuesSupported);
    }
    if (hasIdTokenEncryptionEncValuesSupported()) {
      map.put("id_token_encryption_enc_values_supported", idTokenEncryptionEncValuesSupported);
    }
    if (hasUserinfoSigningAlgValuesSupported()) {
      map.put("userinfo_signing_alg_values_supported", userinfoSigningAlgValuesSupported);
    }
    if (hasUserinfoEncryptionAlgValuesSupported()) {
      map.put("userinfo_encryption_alg_values_supported", userinfoEncryptionAlgValuesSupported);
    }
    if (hasUserinfoEncryptionEncValuesSupported()) {
      map.put("userinfo_encryption_enc_values_supported", userinfoEncryptionEncValuesSupported);
    }
    if (hasRequestObjectSigningAlgValuesSupported()) {
      map.put(
          "request_object_signing_alg_values_supported", requestObjectSigningAlgValuesSupported);
    }
    if (hasRequestObjectEncryptionAlgValuesSupported()) {
      map.put(
          "request_object_encryption_alg_values_supported",
          requestObjectEncryptionAlgValuesSupported);
    }
    if (hasRequestObjectEncryptionEncValuesSupported()) {
      map.put(
          "request_object_encryption_enc_values_supported",
          requestObjectEncryptionEncValuesSupported);
    }
    if (hasTokenEndpointAuthMethodsSupported()) {
      map.put("token_endpoint_auth_methods_supported", tokenEndpointAuthMethodsSupported);
    }
    if (hasTokenEndpointAuthSigningAlgValuesSupported()) {
      map.put(
          "token_endpoint_auth_signing_alg_values_supported", tokenEndpointAuthMethodsSupported);
    }
    if (hasDisplayValuesSupported()) {
      map.put("display_values_supported", displayValuesSupported);
    }
    if (hasClaimTypesSupported()) {
      map.put("claim_types_supported", claimTypesSupported);
    }
    if (hasClaimsSupported()) {
      map.put("claims_supported", claimsSupported);
    }
    map.put("claims_parameter_supported", claimsParameterSupported);
    map.put("request_parameter_supported", requestParameterSupported);
    map.put("request_uri_parameter_supported", requestUriParameterSupported);
    map.put("require_request_uri_registration", requireRequestUriRegistration);

    if (hasBackchannelTokenDeliveryModesSupported()) {
      map.put(
          "backchannel_token_delivery_modes_supported", backchannelTokenDeliveryModesSupported());
    }
    if (hasBackchannelAuthenticationEndpoint()) {
      map.put("backchannel_authentication_endpoint", hasBackchannelAuthenticationEndpoint());
    }
    if (hasBackchannelAuthenticationRequestSigningAlgValuesSupported()) {
      map.put(
          "backchannel_authentication_request_signing_alg_values_supported",
          backchannelAuthenticationRequestSigningAlgValuesSupported);
    }
    if (hasBackchannelUserCodeParameterSupported()) {
      map.put("backchannel_user_code_parameter_supported", backchannelUserCodeParameterSupported);
    }
    return map;
  }
}
