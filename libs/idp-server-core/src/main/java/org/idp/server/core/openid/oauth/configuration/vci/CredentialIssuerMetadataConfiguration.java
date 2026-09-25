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

package org.idp.server.core.openid.oauth.configuration.vci;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Credential Issuer Metadata (OpenID4VCI 1.0 Section 12.2.4), held on the tenant's authorization
 * server configuration as {@code credential_issuer_metadata}.
 *
 * <p>The tenant's authorization server is also the Credential Issuer, so {@code credential_issuer}
 * defaults to the authorization server's {@code issuer} and {@code authorization_servers} is
 * omitted, which the specification reads as "the Credential Issuer is its own authorization
 * server".
 *
 * <p>Holds only what is published. How an issued credential is built from the user (which claims,
 * which are selectively disclosable, the signing key) is not metadata and does not belong here.
 */
public class CredentialIssuerMetadataConfiguration implements JsonReadable {

  String credentialIssuer;
  List<String> authorizationServers = new ArrayList<>();
  String credentialEndpoint;
  String nonceEndpoint;
  String deferredCredentialEndpoint;
  String notificationEndpoint;
  Map<String, Object> batchCredentialIssuance;
  List<Map<String, Object>> display = new ArrayList<>();
  Map<String, CredentialConfiguration> credentialConfigurationsSupported = new HashMap<>();

  public CredentialIssuerMetadataConfiguration() {}

  /** Configured only when the tenant issues at least one kind of credential. */
  public boolean exists() {
    return credentialConfigurationsSupported != null
        && !credentialConfigurationsSupported.isEmpty();
  }

  /**
   * The Credential Issuer Identifier, falling back to the authorization server's issuer.
   *
   * @param authorizationServerIssuer the tenant's {@code issuer}
   */
  public String credentialIssuer(String authorizationServerIssuer) {
    if (credentialIssuer == null || credentialIssuer.isEmpty()) {
      return authorizationServerIssuer;
    }
    return credentialIssuer;
  }

  public String credentialEndpoint() {
    return credentialEndpoint;
  }

  public String nonceEndpoint() {
    return nonceEndpoint;
  }

  public boolean hasNonceEndpoint() {
    return nonceEndpoint != null && !nonceEndpoint.isEmpty();
  }

  public String notificationEndpoint() {
    return notificationEndpoint;
  }

  public boolean hasNotificationEndpoint() {
    return notificationEndpoint != null && !notificationEndpoint.isEmpty();
  }

  public Map<String, CredentialConfiguration> credentialConfigurationsSupported() {
    return credentialConfigurationsSupported;
  }

  public boolean hasCredentialConfiguration(String credentialConfigurationId) {
    return credentialConfigurationId != null
        && credentialConfigurationsSupported.containsKey(credentialConfigurationId);
  }

  public CredentialConfiguration credentialConfiguration(String credentialConfigurationId) {
    CredentialConfiguration configuration =
        credentialConfigurationsSupported.get(credentialConfigurationId);
    if (configuration == null) {
      return new CredentialConfiguration();
    }
    return configuration;
  }

  /** The credential configuration requested by {@code scope}, or an empty one. */
  public CredentialConfiguration credentialConfigurationByScope(String scope) {
    return credentialConfigurationsSupported.values().stream()
        .filter(configuration -> configuration.hasScope() && configuration.scope().equals(scope))
        .findFirst()
        .orElse(new CredentialConfiguration());
  }

  /** The id under which {@code configuration} is registered, or {@code null}. */
  public String credentialConfigurationIdOf(CredentialConfiguration configuration) {
    return credentialConfigurationsSupported.entrySet().stream()
        .filter(entry -> entry.getValue() == configuration)
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }

  /**
   * The document served at {@code /.well-known/openid-credential-issuer}.
   *
   * @param authorizationServerIssuer the tenant's {@code issuer}, used when {@code
   *     credential_issuer} is not configured
   */
  public Map<String, Object> toMetadata(String authorizationServerIssuer) {
    Map<String, Object> map = new HashMap<>();
    map.put("credential_issuer", credentialIssuer(authorizationServerIssuer));
    if (!authorizationServers.isEmpty()) map.put("authorization_servers", authorizationServers);
    map.put("credential_endpoint", credentialEndpoint);
    if (hasNonceEndpoint()) map.put("nonce_endpoint", nonceEndpoint);
    if (deferredCredentialEndpoint != null) {
      map.put("deferred_credential_endpoint", deferredCredentialEndpoint);
    }
    if (hasNotificationEndpoint()) map.put("notification_endpoint", notificationEndpoint);
    if (batchCredentialIssuance != null) {
      map.put("batch_credential_issuance", batchCredentialIssuance);
    }
    if (!display.isEmpty()) map.put("display", display);
    Map<String, Object> configurations = new HashMap<>();
    credentialConfigurationsSupported.forEach(
        (id, configuration) -> configurations.put(id, configuration.toMap()));
    map.put("credential_configurations_supported", configurations);
    return map;
  }

  /** The stored form, for the management API: {@code credential_issuer} only when configured. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = toMetadata(null);
    if (credentialIssuer == null) map.remove("credential_issuer");
    return map;
  }
}
