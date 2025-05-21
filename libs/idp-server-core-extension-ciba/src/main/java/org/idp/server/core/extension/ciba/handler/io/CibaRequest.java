package org.idp.server.core.extension.ciba.handler.io;

import java.util.Map;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;

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
