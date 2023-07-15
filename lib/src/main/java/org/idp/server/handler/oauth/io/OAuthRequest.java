package org.idp.server.handler.oauth.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.oauth.request.OAuthRequestParameters;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthRequest */
public class OAuthRequest {

  Map<String, String[]> params;
  String issuer;
  String sessionId;

  public OAuthRequest() {
    this.params = Map.of();
    this.issuer = "";
    this.sessionId = "";
  }

  public OAuthRequest(Map<String, String[]> params, String issuer) {
    this.params = params;
    this.issuer = issuer;
  }

  public static OAuthRequest singleMap(Map<String, String> params, String issuer) {
    HashMap<String, String[]> map = new HashMap<>();
    params.forEach(
        (key, value) -> {
          map.put(key, new String[] {value});
        });
    return new OAuthRequest(map, issuer);
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public OAuthRequestParameters toParameters() {
    return new OAuthRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}
