package org.idp.server.handler.oauth.io;

import java.util.Map;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.type.extension.SessionIdentifier;
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

  public OAuthRequest setSessionId(String sessionId) {
    this.sessionId = sessionId;
    return this;
  }

  public SessionIdentifier toSessionIdentifier() {
    return new SessionIdentifier(sessionId);
  }
}
