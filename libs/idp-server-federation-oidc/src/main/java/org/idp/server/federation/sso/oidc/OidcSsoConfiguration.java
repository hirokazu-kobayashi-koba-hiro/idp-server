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

package org.idp.server.federation.sso.oidc;

import java.util.List;
import java.util.Objects;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.mapper.MappingRule;

public class OidcSsoConfiguration implements JsonReadable {

  String type;
  String provider;
  String issuer;
  String issuerName;
  String description;
  String clientId;
  String clientSecret;
  String clientAuthenticationType;
  String redirectUri;
  List<String> scopesSupported;
  String authorizationEndpoint;
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwksUri;
  String privateKeys = "";
  List<MappingRule> userinfoMappingRules;
  String paramsDelimiter;

  public OidcSsoConfiguration() {}

  public OidcSsoConfiguration(
      String type,
      String issuer,
      String issuerName,
      String description,
      String clientId,
      String clientSecret,
      String clientAuthenticationType,
      String redirectUri,
      List<String> scopesSupported,
      String authorizationEndpoint,
      String tokenEndpoint,
      String userinfoEndpoint,
      String jwksUri,
      String privateKeys,
      String paramsDelimiter) {
    this.type = type;
    this.issuer = issuer;
    this.issuerName = issuerName;
    this.description = description;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clientAuthenticationType = clientAuthenticationType;
    this.redirectUri = redirectUri;
    this.scopesSupported = scopesSupported;
    this.authorizationEndpoint = authorizationEndpoint;
    this.tokenEndpoint = tokenEndpoint;
    this.userinfoEndpoint = userinfoEndpoint;
    this.jwksUri = jwksUri;
    this.privateKeys = privateKeys;
    this.paramsDelimiter = paramsDelimiter;
  }

  public String type() {
    return type;
  }

  public String issuer() {
    return issuer;
  }

  public String issuerName() {
    return issuerName;
  }

  public String description() {
    return description;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public List<String> scopesSupported() {
    return scopesSupported;
  }

  public String scopeAsString() {

    if (Objects.nonNull(paramsDelimiter) && Objects.equals(paramsDelimiter, ",")) {
      return String.join(",", scopesSupported);
    }

    return String.join(" ", scopesSupported);
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

  public String jwksUri() {
    return jwksUri;
  }

  public String privateKeys() {
    return privateKeys;
  }

  public SsoProvider ssoProvider() {
    return new SsoProvider(provider);
  }

  public List<MappingRule> userinfoMappingRules() {
    return userinfoMappingRules;
  }
}
