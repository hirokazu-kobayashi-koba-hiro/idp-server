package org.idp.server.core.handler.userinfo.io;

import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.AccessTokenEntity;

public class UserinfoRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  String clientCert;

  public UserinfoRequest(Tenant tenant, String authorizationHeaders) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public String getClientCert() {
    return clientCert;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public UserinfoRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }
}
