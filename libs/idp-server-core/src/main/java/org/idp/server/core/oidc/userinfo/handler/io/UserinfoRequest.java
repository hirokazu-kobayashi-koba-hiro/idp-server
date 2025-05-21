package org.idp.server.core.oidc.userinfo.handler.io;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.core.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

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
