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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.oauth.client.OutboundClientCredentials;

/** The authorization code exchange with the external IdP's token endpoint. */
public class OidcTokenRequest {

  String endpoint;
  String code;
  String redirectUri;
  String grantType;
  OutboundClientCredentials credentials;

  public OidcTokenRequest() {}

  public OidcTokenRequest(
      String endpoint,
      String code,
      String redirectUri,
      String grantType,
      OutboundClientCredentials credentials) {
    this.endpoint = endpoint;
    this.code = code;
    this.redirectUri = redirectUri;
    this.grantType = grantType;
    this.credentials = credentials;
  }

  public String endpoint() {
    return endpoint;
  }

  /** How this server authenticates to the external IdP's token endpoint. */
  public OutboundClientCredentials credentials() {
    return credentials;
  }

  /** The grant's own parameters. The client authentication adds its own to them. */
  public Map<String, String> toGrantValues() {
    Map<String, String> map = new HashMap<>();
    map.put("code", code);
    map.put("redirect_uri", redirectUri);
    map.put("grant_type", grantType);
    return map;
  }
}
