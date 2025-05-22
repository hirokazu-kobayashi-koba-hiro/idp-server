/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.control_plane.model;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class OperatorPrincipal extends AbstractAuthenticationToken {

  User user;
  OAuthToken oauthToken;

  public OperatorPrincipal(
      User user, OAuthToken oAuthToken, List<IdpControlPlaneAuthority> idpControlPlaneAuthorities) {
    super(idpControlPlaneAuthorities);
    this.user = user;
    this.oauthToken = oAuthToken;
  }

  @Override
  public Object getCredentials() {
    return oauthToken;
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  public User getUser() {
    return user;
  }

  public OAuthToken getOAuthToken() {
    return oauthToken;
  }

  public RequestedClientId getRequestedClientId() {
    return oauthToken.requestedClientId();
  }
}
