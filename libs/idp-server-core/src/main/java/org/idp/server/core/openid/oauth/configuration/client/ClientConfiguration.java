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

package org.idp.server.core.openid.oauth.configuration.client;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.openid.authentication.AuthenticationInteractionType;
import org.idp.server.core.openid.oauth.type.ciba.BackchannelTokenDeliveryMode;
import org.idp.server.core.openid.oauth.type.extension.RegisteredRedirectUris;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.platform.configuration.Configurable;
import org.idp.server.platform.json.JsonReadable;

/** ClientConfiguration */
public class ClientConfiguration implements JsonReadable, Configurable {
  String clientId;
  String clientIdAlias;
  String clientSecret;
  List<String> redirectUris = new ArrayList<>();
  List<String> postLogoutRedirectUris = new ArrayList<>();
  String tokenEndpointAuthMethod;
  List<String> grantTypes = new ArrayList<>();
  List<String> responseTypes = new ArrayList<>();
  String clientName = "";
  String clientUri = "";
  String logoUri = "";
  String scope;
  List<String> contacts = new ArrayList<>();
  String tosUri = "";
  String policyUri = "";
  String jwksUri;
  String jwks;
  String softwareId = "";
  String softwareVersion = "";
  List<String> requestUris = new ArrayList<>();
  String backchannelTokenDeliveryMode = "poll";
  String backchannelClientNotificationEndpoint = "";
  String backchannelAuthenticationRequestSigningAlg = "";
  boolean backchannelUserCodeParameter = false;
  String applicationType = "web";
  String idTokenEncryptedResponseAlg;
  String idTokenEncryptedResponseEnc;
  List<String> authorizationDetailsTypes = new ArrayList<>();
  String tlsClientAuthSubjectDn;
  String tlsClientAuthSanDns;
  String tlsClientAuthSanUri;
  String tlsClientAuthSanIp;
  String tlsClientAuthSanEmail;
  boolean tlsClientCertificateBoundAccessTokens = false;
  String authorizationSignedResponseAlg;
  String authorizationEncryptedResponseAlg;
  String authorizationEncryptedResponseEnc;
  String backchannelLogoutUri;
  boolean backchannelLogoutSessionRequired = false;
  String frontchannelLogoutUri;
  boolean frontchannelLogoutSessionRequired = false;
  boolean enabled = true;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  // extension
  ClientExtensionConfiguration extension = new ClientExtensionConfiguration();

  public ClientConfiguration() {}

  public ClientIdentifier clientIdentifier() {
    return new ClientIdentifier(clientId);
  }

  public String clientIdAlias() {
    return clientIdAlias;
  }

  public String clientIdValue() {
    return clientId;
  }

  public String clientSecretValue() {
    return clientSecret;
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(clientSecret);
  }

  public List<String> redirectUris() {
    return redirectUris;
  }

  public boolean hasRedirectUri() {
    return !redirectUris.isEmpty();
  }

  public boolean isMultiRegisteredRedirectUri() {
    return redirectUris.size() >= 2;
  }

  public RedirectUri getFirstRedirectUri() {
    return new RedirectUri(redirectUris.get(0));
  }

  public RegisteredRedirectUris registeredRedirectUris() {
    return new RegisteredRedirectUris(redirectUris);
  }

  public List<String> postLogoutRedirectUris() {
    return postLogoutRedirectUris;
  }

  public boolean hasPostLogoutRedirectUris() {
    return postLogoutRedirectUris != null && !postLogoutRedirectUris.isEmpty();
  }

  /**
   * OpenID Connect RP-Initiated Logout 1.0 Section 4: post_logout_redirect_uri MUST match a
   * registered URI exactly.
   *
   * @param postLogoutRedirectUri the URI to validate
   * @return true if the URI is registered
   */
  public boolean isRegisteredPostLogoutRedirectUri(String postLogoutRedirectUri) {
    return postLogoutRedirectUris.contains(postLogoutRedirectUri);
  }

  public String tokenEndpointAuthMethod() {
    return tokenEndpointAuthMethod;
  }

  public List<String> grantTypes() {
    return grantTypes;
  }

  public List<String> responseTypes() {
    return responseTypes;
  }

  public String clientNameValue() {
    return clientName;
  }

  public ClientName clientName() {
    return new ClientName(clientName);
  }

  public String clientUri() {
    return clientUri;
  }

  public String logoUri() {
    return logoUri;
  }

  public String scope() {
    return scope;
  }

  public List<String> contacts() {
    return contacts;
  }

  public String tosUri() {
    return tosUri;
  }

  public boolean hasTosUri() {
    return tosUri != null && !tosUri.isEmpty();
  }

  public String policyUri() {
    return policyUri;
  }

  public boolean hasPolicyUri() {
    return policyUri != null && !policyUri.isEmpty();
  }

  public String jwksUri() {
    return jwksUri;
  }

  public String jwks() {
    return jwks;
  }

  public String softwareId() {
    return softwareId;
  }

  public String softwareVersion() {
    return softwareVersion;
  }

  public List<String> scopes() {
    if (Objects.isNull(scope)) {
      return List.of();
    }
    return Arrays.stream(scope.split(" ")).toList();
  }

  public Set<String> filteredScope(String spacedScopes) {
    if (Objects.isNull(spacedScopes) || spacedScopes.isEmpty()) {
      return Set.of();
    }
    List<String> scopes = Arrays.stream(spacedScopes.split(" ")).toList();
    return scopes.stream().filter(scope -> scopes().contains(scope)).collect(Collectors.toSet());
  }

  public Set<String> filteredScope(List<String> scopes) {
    return scopes.stream().filter(scope -> scopes().contains(scope)).collect(Collectors.toSet());
  }

  public boolean isSupportedJar() {
    return extension.isSupportedJar();
  }

  public boolean isRegisteredRequestUri(String requestUri) {
    return requestUris.contains(requestUri);
  }

  public boolean isRegisteredRedirectUri(String redirectUri) {
    return redirectUris.contains(redirectUri);
  }

  public boolean isSupportedResponseType(ResponseType responseType) {
    return responseTypes.contains(responseType.value());
  }

  public boolean matchClientSecret(String that) {
    return clientSecret.equals(that);
  }

  public ClientAuthenticationType clientAuthenticationType() {
    return ClientAuthenticationType.valueOf(tokenEndpointAuthMethod);
  }

  public BackchannelTokenDeliveryMode backchannelTokenDeliveryMode() {
    return BackchannelTokenDeliveryMode.of(backchannelTokenDeliveryMode);
  }

  public boolean hasBackchannelTokenDeliveryMode() {
    return backchannelTokenDeliveryMode().isDefined();
  }

  public String backchannelClientNotificationEndpoint() {
    return backchannelClientNotificationEndpoint;
  }

  public boolean hasBackchannelClientNotificationEndpoint() {
    return !backchannelClientNotificationEndpoint.isEmpty();
  }

  public String backchannelAuthenticationRequestSigningAlg() {
    return backchannelAuthenticationRequestSigningAlg;
  }

  public boolean hasBackchannelAuthenticationRequestSigningAlg() {
    return !backchannelAuthenticationRequestSigningAlg.isEmpty();
  }

  public Boolean backchannelUserCodeParameter() {
    return backchannelUserCodeParameter;
  }

  public boolean hasBackchannelUserCodeParameter() {
    return Objects.nonNull(backchannelUserCodeParameter);
  }

  public boolean isSupportedGrantType(GrantType grantType) {
    return grantTypes.contains(grantType.value());
  }

  public boolean isWebApplication() {
    return applicationType.equals("web");
  }

  /**
   * RFC 8252 Section 7: Native Applications
   *
   * @return true if application_type is "native"
   */
  public boolean isNativeApplication() {
    return applicationType.equals("native");
  }

  public String idTokenEncryptedResponseAlg() {
    return idTokenEncryptedResponseAlg;
  }

  public String idTokenEncryptedResponseEnc() {
    return idTokenEncryptedResponseEnc;
  }

  public boolean hasEncryptedIdTokenMeta() {
    return Objects.nonNull(idTokenEncryptedResponseAlg)
        && Objects.nonNull(idTokenEncryptedResponseEnc);
  }

  public List<String> authorizationDetailsTypes() {
    return authorizationDetailsTypes;
  }

  public boolean isAuthorizedAuthorizationDetailsType(String type) {
    return authorizationDetailsTypes.contains(type);
  }

  public String tlsClientAuthSubjectDn() {
    return tlsClientAuthSubjectDn;
  }

  public String tlsClientAuthSanDns() {
    return tlsClientAuthSanDns;
  }

  public String tlsClientAuthSanUri() {
    return tlsClientAuthSanUri;
  }

  public String tlsClientAuthSanIp() {
    return tlsClientAuthSanIp;
  }

  public String tlsClientAuthSanEmail() {
    return tlsClientAuthSanEmail;
  }

  public boolean isTlsClientCertificateBoundAccessTokens() {
    return tlsClientCertificateBoundAccessTokens;
  }

  public String authorizationSignedResponseAlg() {
    return authorizationSignedResponseAlg;
  }

  public boolean hasAuthorizationSignedResponseAlg() {
    return Objects.nonNull(authorizationSignedResponseAlg);
  }

  public String authorizationEncryptedResponseAlg() {
    return authorizationEncryptedResponseAlg;
  }

  public String authorizationEncryptedResponseEnc() {
    return authorizationEncryptedResponseEnc;
  }

  public boolean hasEncryptedAuthorizationResponseMeta() {
    return Objects.nonNull(idTokenEncryptedResponseAlg)
        && Objects.nonNull(idTokenEncryptedResponseEnc);
  }

  public String backchannelLogoutUri() {
    return backchannelLogoutUri;
  }

  public boolean hasBackchannelLogoutUri() {
    return backchannelLogoutUri != null && !backchannelLogoutUri.isEmpty();
  }

  public boolean backchannelLogoutSessionRequired() {
    return backchannelLogoutSessionRequired;
  }

  public String frontchannelLogoutUri() {
    return frontchannelLogoutUri;
  }

  public boolean hasFrontchannelLogoutUri() {
    return frontchannelLogoutUri != null && !frontchannelLogoutUri.isEmpty();
  }

  public boolean frontchannelLogoutSessionRequired() {
    return frontchannelLogoutSessionRequired;
  }

  public boolean supportsLogoutNotification() {
    return hasBackchannelLogoutUri() || hasFrontchannelLogoutUri();
  }

  public ClientAttributes clientAttributes() {
    return new ClientAttributes(
        clientId, clientIdAlias, clientName, clientUri, logoUri, contacts, tosUri, policyUri);
  }

  public boolean hasJwks() {
    return jwks != null && !jwks.isEmpty();
  }

  public boolean hasSecret() {
    return clientSecret != null && !clientSecret.isEmpty();
  }

  public boolean exists() {
    return clientId != null && !clientId.isEmpty();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public boolean hasCreatedAt() {
    return createdAt != null;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public boolean hasUpdatedAt() {
    return updatedAt != null;
  }

  public ClientConfiguration setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public ClientConfiguration setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public boolean hasAccessTokenDuration() {
    return extension.hasAccessTokenDuration();
  }

  public long accessTokenDuration() {
    return extension.accessTokenDuration();
  }

  public boolean hasRefreshTokenDuration() {
    return extension.hasRefreshTokenDuration();
  }

  public long refreshTokenDuration() {
    return extension.refreshTokenDuration();
  }

  public boolean hasAvailableFederations() {
    return extension.hasAvailableFederations();
  }

  public List<AvailableFederation> availableFederations() {
    return extension.availableFederations();
  }

  public List<Map<String, Object>> availableFederationsAsMapList() {
    return extension.availableFederationsAsMapList();
  }

  public AuthenticationInteractionType defaultCibaAuthenticationInteractionType() {
    return extension.defaultCibaAuthenticationInteractionType();
  }

  public boolean hasDefaultCibaAuthenticationInteractionType() {
    return extension.hasDefaultCibaAuthenticationInteractionType();
  }

  public boolean isCibaRequireRar() {
    return extension.isCibaRequireRar();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("client_id", clientId);
    map.put("client_id_alias", clientIdAlias);
    map.put("client_secret", clientSecret);
    map.put("redirect_uris", redirectUris);
    map.put("post_logout_redirect_uris", postLogoutRedirectUris);
    map.put("token_endpoint_auth_method", tokenEndpointAuthMethod);
    map.put("grant_types", grantTypes);
    map.put("response_types", responseTypes);
    map.put("client_name", clientName);
    map.put("client_uri", clientUri);
    map.put("logo_uri", logoUri);
    map.put("scope", scope);
    map.put("contacts", contacts);
    map.put("tos_uri", tosUri);
    map.put("policy_uri", policyUri);
    map.put("jwks_uri", jwksUri);
    map.put("jwks", jwks);
    map.put("software_id", softwareId);
    map.put("software_version", softwareVersion);
    map.put("request_uris", requestUris);
    map.put("backchannel_token_delivery_mode", backchannelTokenDeliveryMode);
    map.put("backchannel_client_notification_endpoint", backchannelClientNotificationEndpoint);
    map.put(
        "backchannel_authentication_request_signing_alg",
        backchannelAuthenticationRequestSigningAlg);
    map.put("backchannel_user_code_parameter", backchannelUserCodeParameter);
    map.put("application_type", applicationType);
    map.put("id_token_encrypted_response_alg", idTokenEncryptedResponseAlg);
    map.put("id_token_encrypted_response_enc", idTokenEncryptedResponseEnc);
    map.put("authorization_details_types", authorizationDetailsTypes);
    map.put("tls_client_auth_subject_dn", tlsClientAuthSubjectDn);
    map.put("tls_client_auth_san_dns", tlsClientAuthSanDns);
    map.put("tls_client_auth_san_uri", tlsClientAuthSanUri);
    map.put("tls_client_auth_san_ip", tlsClientAuthSanIp);
    map.put("tls_client_auth_san_email", tlsClientAuthSanEmail);
    map.put("tls_client_certificate_bound_access_tokens", tlsClientCertificateBoundAccessTokens);
    map.put("authorization_signed_response_alg", authorizationSignedResponseAlg);
    map.put("authorization_encrypted_response_alg", authorizationEncryptedResponseAlg);
    map.put("authorization_encrypted_response_enc", authorizationEncryptedResponseEnc);
    map.put("backchannel_logout_uri", backchannelLogoutUri);
    map.put("backchannel_logout_session_required", backchannelLogoutSessionRequired);
    map.put("frontchannel_logout_uri", frontchannelLogoutUri);
    map.put("frontchannel_logout_session_required", frontchannelLogoutSessionRequired);
    map.put("enabled", enabled);
    if (hasCreatedAt()) map.put("created_at", createdAt.toString());
    if (hasUpdatedAt()) map.put("updated_at", updatedAt.toString());
    map.put("extension", extension.toMap());
    return map;
  }
}
