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
package org.idp.server.platform.oauth.client;

/**
 * Everything a client authentication needs for one token request: who the client is, its secrets,
 * and where the request goes.
 */
public class OutboundClientCredentials {

  OutboundClientAuthenticationType type;
  String clientId;
  String clientSecret;
  String tokenEndpoint;
  String issuer;
  OutboundClientAuthenticationConfig config;

  /**
   * @param issuer the authorization server's issuer, for an assertion addressed to it; {@code null}
   *     when the configuration does not carry one, and then {@code config}'s is used
   */
  public OutboundClientCredentials(
      OutboundClientAuthenticationType type,
      String clientId,
      String clientSecret,
      String tokenEndpoint,
      String issuer,
      OutboundClientAuthenticationConfig config) {
    this.type = type;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenEndpoint = tokenEndpoint;
    this.issuer = issuer;
    this.config = config == null ? new OutboundClientAuthenticationConfig() : config;
  }

  public OutboundClientAuthenticationType type() {
    return type;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public OutboundClientAuthenticationConfig config() {
    return config;
  }

  /**
   * Whom a client assertion is addressed to: the token endpoint, or the issuer when the
   * configuration asks for it.
   *
   * @return the audience, or {@code null} when the issuer is asked for and none is known
   */
  public String assertionAudience() {
    if (!config.addressesIssuer()) {
      return tokenEndpoint;
    }
    if (issuer != null && !issuer.isEmpty()) {
      return issuer;
    }
    return config.hasIssuer() ? config.issuer() : null;
  }
}
