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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.http.HttpRequestExecutionConfig;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class AvailableFederation implements JsonReadable {
  String id;
  String type;
  String ssoProvider;
  boolean autoSelected = false;
  String issuer;
  String providerId;
  boolean jwtBearerGrantEnabled = false;
  boolean tokenExchangeGrantEnabled = false;
  String tokenExchangeTokenVerificationMethod;
  String subjectClaimMapping;
  String jwksUri;
  String jwks;
  int jwksCacheTtlSeconds = 3600;
  String introspectionEndpoint;
  String introspectionAuthMethod;
  String introspectionClientId;
  String introspectionClientSecret;
  boolean jitProvisioningEnabled = false;
  List<MappingRule> userinfoMappingRules = new ArrayList<>();
  List<HttpRequestExecutionConfig> userinfoHttpRequests = new ArrayList<>();

  public AvailableFederation() {}

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  public String ssoProvider() {
    return ssoProvider;
  }

  public boolean autoSelected() {
    return autoSelected;
  }

  public String issuer() {
    return issuer;
  }

  public boolean hasIssuer() {
    return issuer != null && !issuer.isEmpty();
  }

  public String providerId() {
    return providerId;
  }

  public boolean hasProviderId() {
    return providerId != null && !providerId.isEmpty();
  }

  /**
   * Returns the provider ID for user lookup. If provider_id is not explicitly set, falls back to
   * issuer for backward compatibility.
   *
   * @return the provider ID or issuer as fallback
   */
  public String resolveProviderId() {
    if (hasProviderId()) {
      return providerId;
    }
    return issuer;
  }

  public boolean jwtBearerGrantEnabled() {
    return jwtBearerGrantEnabled;
  }

  public boolean tokenExchangeGrantEnabled() {
    return tokenExchangeGrantEnabled;
  }

  public String tokenExchangeTokenVerificationMethod() {
    return tokenExchangeTokenVerificationMethod;
  }

  public boolean isJwtVerification() {
    return "jwt".equals(tokenExchangeTokenVerificationMethod)
        || tokenExchangeTokenVerificationMethod == null
        || tokenExchangeTokenVerificationMethod.isEmpty();
  }

  public boolean isIntrospectionVerification() {
    return "introspection".equals(tokenExchangeTokenVerificationMethod);
  }

  public String subjectClaimMapping() {
    return subjectClaimMapping;
  }

  public boolean hasSubjectClaimMapping() {
    return subjectClaimMapping != null && !subjectClaimMapping.isEmpty();
  }

  public String resolveSubjectClaimMapping() {
    return hasSubjectClaimMapping() ? subjectClaimMapping : "sub";
  }

  public boolean isDeviceType() {
    return "device".equals(type);
  }

  public String jwksUri() {
    return jwksUri;
  }

  public boolean hasJwksUri() {
    return jwksUri != null && !jwksUri.isEmpty();
  }

  public String jwks() {
    return jwks;
  }

  public boolean hasJwks() {
    return jwks != null && !jwks.isEmpty();
  }

  public int jwksCacheTtlSeconds() {
    return jwksCacheTtlSeconds;
  }

  public boolean jitProvisioningEnabled() {
    return jitProvisioningEnabled;
  }

  public List<MappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }

  public boolean hasUserinfoMappingRules() {
    return userinfoMappingRules != null && !userinfoMappingRules.isEmpty();
  }

  public List<HttpRequestExecutionConfig> userinfoHttpRequests() {
    if (userinfoHttpRequests == null) {
      return new ArrayList<>();
    }
    return userinfoHttpRequests;
  }

  public boolean hasUserinfoHttpRequests() {
    return userinfoHttpRequests != null && !userinfoHttpRequests.isEmpty();
  }

  public String introspectionAuthMethod() {
    return introspectionAuthMethod;
  }

  public boolean isIntrospectionBasicAuth() {
    return introspectionAuthMethod == null
        || introspectionAuthMethod.isEmpty()
        || "client_secret_basic".equals(introspectionAuthMethod);
  }

  public String introspectionEndpoint() {
    return introspectionEndpoint;
  }

  public boolean hasIntrospectionEndpoint() {
    return introspectionEndpoint != null && !introspectionEndpoint.isEmpty();
  }

  public String introspectionClientId() {
    return introspectionClientId;
  }

  public String introspectionClientSecret() {
    return introspectionClientSecret;
  }

  public boolean matchesIssuer(String issuerToMatch) {
    if (!hasIssuer()) {
      return false;
    }
    return issuer.equals(issuerToMatch);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    map.put("sso_provider", ssoProvider);
    map.put("auto_selected", autoSelected);
    if (hasIssuer()) map.put("issuer", issuer);
    if (hasProviderId()) map.put("provider_id", providerId);
    map.put("jwt_bearer_grant_enabled", jwtBearerGrantEnabled);
    map.put("token_exchange_grant_enabled", tokenExchangeGrantEnabled);
    if (tokenExchangeTokenVerificationMethod != null)
      map.put("token_exchange_token_verification_method", tokenExchangeTokenVerificationMethod);
    if (hasSubjectClaimMapping()) map.put("subject_claim_mapping", subjectClaimMapping);
    if (hasJwksUri()) map.put("jwks_uri", jwksUri);
    if (hasJwks()) map.put("jwks", jwks);
    if (jwksCacheTtlSeconds > 0) map.put("jwks_cache_ttl_seconds", jwksCacheTtlSeconds);
    map.put("jit_provisioning_enabled", jitProvisioningEnabled);
    if (hasIntrospectionEndpoint()) map.put("introspection_endpoint", introspectionEndpoint);
    if (introspectionClientId != null) map.put("introspection_client_id", introspectionClientId);
    if (introspectionClientSecret != null)
      map.put("introspection_client_secret", introspectionClientSecret);
    if (hasUserinfoHttpRequests())
      map.put(
          "userinfo_http_requests",
          userinfoHttpRequests.stream().map(HttpRequestExecutionConfig::toMap).toList());
    return map;
  }

  public Map<String, Object> toViewMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    if (ssoProvider != null) map.put("sso_provider", ssoProvider);
    map.put("auto_selected", autoSelected);
    return map;
  }
}
