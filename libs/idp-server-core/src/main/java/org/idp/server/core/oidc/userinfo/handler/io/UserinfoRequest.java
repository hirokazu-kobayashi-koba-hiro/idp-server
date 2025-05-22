/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo.handler.io;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.core.oidc.token.AuthorizationHeaderHandlerable;
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
