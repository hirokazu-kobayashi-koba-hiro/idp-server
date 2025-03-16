package org.idp.server.core.handler.token.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.http.BasicAuth;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.token.TokenRequestParameters;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecretBasic;
import org.idp.server.core.type.oauth.TokenIssuer;

public class TokenRequest implements AuthorizationHeaderHandlerable {
  String authorizationHeaders;
  Map<String, String[]> params;
  String issuer;
  String clientCert;
  Map<String, Object> customProperties = new HashMap<>();

  public TokenRequest(String authorizationHeaders, Map<String, String[]> params, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
    this.issuer = issuer;
  }

  public TokenRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
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

  public String getClientCert() {
    return clientCert;
  }

  public Map<String, Object> customProperties() {
    return customProperties;
  }

  public TokenRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
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

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
