/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.federation.sso.oidc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OidcTokenRequest {

  String endpoint;
  String code;
  String clientId;
  String clientSecret;
  String redirectUri;
  String grantType;
  String clientAuthenticationType;

  public OidcTokenRequest() {}

  public OidcTokenRequest(
      String endpoint,
      String code,
      String clientId,
      String clientSecret,
      String redirectUri,
      String grantType,
      String clientAuthenticationType) {
    this.endpoint = endpoint;
    this.code = code;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
    this.grantType = grantType;
    this.clientAuthenticationType = clientAuthenticationType;
  }

  public String endpoint() {
    return endpoint;
  }

  public boolean isClientSecretBasic() {
    return Objects.nonNull(clientAuthenticationType)
        && clientAuthenticationType.equals("client_secret_basic");
  }

  public String basicAuthenticationValue() {
    String auth = clientId + ":" + clientSecret;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encodedAuth;
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>();
    map.put("code", code);
    map.put("client_id", clientId);

    if (!isClientSecretBasic()) {
      map.put("client_secret", clientSecret);
    }

    map.put("redirect_uri", redirectUri);
    map.put("grant_type", grantType);
    return map;
  }
}
