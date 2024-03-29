package org.idp.server.handler.ciba.io;

import java.util.Map;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.ciba.CibaRequestParameters;
import org.idp.server.token.AuthorizationHeaderHandlerable;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecretBasic;
import org.idp.server.type.oauth.TokenIssuer;

public class CibaRequest implements AuthorizationHeaderHandlerable {

  String authorizationHeaders;
  Map<String, String[]> params;
  String issuer;
  String clientCert;

  public CibaRequest() {
    this.authorizationHeaders = "";
    this.params = Map.of();
    this.issuer = "";
    this.clientCert = "";
  }

  public CibaRequest(String authorizationHeaders, Map<String, String[]> params, String issuer) {
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
    this.issuer = issuer;
  }

  public CibaRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getIssuer() {
    return issuer;
  }

  public String clientCert() {
    return clientCert;
  }

  public CibaRequestParameters toParameters() {
    return new CibaRequestParameters(params);
  }

  public ClientId clientId() {
    CibaRequestParameters parameters = toParameters();
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

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
