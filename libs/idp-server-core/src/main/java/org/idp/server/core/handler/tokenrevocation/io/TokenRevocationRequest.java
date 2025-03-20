package org.idp.server.core.handler.tokenrevocation.io;

import java.util.Map;
import org.idp.server.core.basic.http.BasicAuth;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.ClientSecretBasic;

public class TokenRevocationRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public TokenRevocationRequest(
      Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public TokenRevocationRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getClientCert() {
    return clientCert;
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientId clientId() {
    TokenRevocationRequestParameters parameters = toParameters();
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

  public TokenRevocationRequestParameters toParameters() {
    return new TokenRevocationRequestParameters(params);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
