package org.idp.server.core.handler.ciba.io;

import java.util.Map;
import org.idp.server.core.basic.http.BasicAuth;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientSecretBasic;
import org.idp.server.core.type.oauth.RequestedClientId;

public class CibaRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public CibaRequest(Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public CibaRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String clientCert() {
    return clientCert;
  }

  public Tenant tenant() {
    return tenant;
  }

  public CibaRequestParameters toParameters() {
    return new CibaRequestParameters(params);
  }

  public RequestedClientId clientId() {
    CibaRequestParameters parameters = toParameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }
    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
