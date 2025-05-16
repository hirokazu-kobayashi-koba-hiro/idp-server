package org.idp.server.core.oidc.io;

import java.util.Map;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.request.OAuthPushedRequestParameters;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;

public class OAuthPushedRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public OAuthPushedRequest(
      Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public OAuthPushedRequest setClientCert(String clientCert) {
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

  public OAuthPushedRequestParameters toBackchannelParameters() {
    return new OAuthPushedRequestParameters(params);
  }

  public OAuthRequestParameters toOAuthRequestParameters() {
    return new OAuthRequestParameters(params);
  }

  public RequestedClientId clientId() {
    OAuthPushedRequestParameters parameters = toBackchannelParameters();
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
