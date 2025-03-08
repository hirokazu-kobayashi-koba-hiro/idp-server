package org.idp.server.core.federation;

import java.util.HashMap;
import java.util.Map;

public class FederationTokenRequest {

  String endpoint;
  String code;
  String clientId;
  String clientSecret;
  String redirectUri;
  String grantType;

  public FederationTokenRequest() {}

  public FederationTokenRequest(
      String endpoint,
      String code,
      String clientId,
      String clientSecret,
      String redirectUri,
      String grantType) {
    this.endpoint = endpoint;
    this.code = code;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
    this.grantType = grantType;
  }

  public String endpoint() {
    return endpoint;
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>();
    map.put("code", code);
    map.put("client_id", clientId);
    map.put("client_secret", clientSecret);
    map.put("redirect_uri", redirectUri);
    map.put("grant_type", grantType);
    return map;
  }
}
