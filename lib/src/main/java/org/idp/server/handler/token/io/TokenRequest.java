package org.idp.server.handler.token.io;

import java.util.Map;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.token.AuthorizationHeaderHandlerable;
import org.idp.server.token.TokenRequestParameters;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecretBasic;
import org.idp.server.type.oauth.TokenIssuer;

public class TokenRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  Map<String, String[]> params;
  String issuer;

  public TokenRequest() {
    this.authorizationHeaders = "";
    this.params = Map.of();
    this.issuer = "";
  }

  public TokenRequest(String authorizationHeaders, Map<String, String[]> params, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
    this.issuer = issuer;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public ClientId clientId() {
    TokenRequestParameters parameters = toParameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new ClientId(basicAuth.username());
    }
    return new ClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public TokenRequestParameters toParameters() {
    return new TokenRequestParameters(params);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }
}
