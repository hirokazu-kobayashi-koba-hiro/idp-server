/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.restapi.model;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class ResourceOwnerPrincipal extends AbstractAuthenticationToken {

  User user;
  OAuthToken oAuthToken;

  public ResourceOwnerPrincipal(
      User user, OAuthToken oAuthToken, List<IdPApplicationScope> idPApplicationScopes) {
    super(idPApplicationScopes);
    this.user = user;
    this.oAuthToken = oAuthToken;
  }

  @Override
  public Object getCredentials() {
    return oAuthToken;
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  public User getUser() {
    return user;
  }

  public OAuthToken getOAuthToken() {
    return oAuthToken;
  }

  public RequestedClientId getRequestedClientId() {
    return oAuthToken.requestedClientId();
  }
}
