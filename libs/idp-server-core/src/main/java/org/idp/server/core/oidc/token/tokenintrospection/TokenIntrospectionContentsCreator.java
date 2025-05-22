/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.tokenintrospection;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.oidc.token.OAuthToken;

public class TokenIntrospectionContentsCreator {

  public static Map<String, Object> createSuccessContents(OAuthToken oAuthToken) {
    Map<String, Object> contents = new HashMap<>();
    AccessToken accessToken = oAuthToken.accessToken();
    contents.put("active", true);
    contents.put("iss", accessToken.tokenIssuer().value());
    if (accessToken.hasSubject()) {
      contents.put("sub", accessToken.subject().value());
    }
    contents.put("client_id", accessToken.clientIdentifier().value());
    contents.put("scope", accessToken.scopes().toStringValues());
    if (accessToken.hasCustomProperties()) {
      contents.putAll(accessToken.customProperties().values());
    }
    if (accessToken.hasAuthorizationDetails()) {
      contents.put("authorization_details", accessToken.authorizationDetails().toMapValues());
    }
    if (accessToken.hasClientCertification()) {
      contents.put("cnf", Map.of("x5t#S256", accessToken.clientCertificationThumbprint().value()));
    }
    contents.put("iat", accessToken.createdAt().toEpochSecondWithUtc());
    contents.put("exp", accessToken.expiredAt().toEpochSecondWithUtc());
    return contents;
  }

  public static Map<String, Object> createFailureContents() {
    return Map.of("active", false);
  }
}
