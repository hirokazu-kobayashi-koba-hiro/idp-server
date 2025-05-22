/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.factory;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.type.extension.ExpiredAt;
import org.idp.server.basic.type.oauth.ExpiresIn;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.MaxAge;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestBuilder;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** NormalRequestFactory */
public class NormalRequestFactory implements AuthorizationRequestFactory {

  @Override
  public AuthorizationRequest create(
      Tenant tenant,
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(tenant.identifier());
    builder.add(profile);
    builder.add(new Scopes(filteredScopes));
    builder.add(parameters.responseType());
    builder.add(parameters.clientId());
    builder.add(clientConfiguration.client());
    builder.add(parameters.redirectUri());
    builder.add(parameters.state());
    builder.add(parameters.responseMode());
    builder.add(parameters.nonce());
    builder.add(parameters.display());
    builder.add(parameters.prompts());
    if (parameters.hasMaxAge()) {
      builder.add(parameters.maxAge());
    } else {
      builder.add(new MaxAge(authorizationServerConfiguration.defaultMaxAge()));
    }
    builder.add(parameters.uiLocales());
    builder.add(parameters.idTokenHint());
    builder.add(parameters.loginHint());
    builder.add(parameters.acrValues());
    builder.add(parameters.claims());
    builder.add(parameters.request());
    builder.add(parameters.requestUri());
    builder.add(convertClaimsPayload(parameters.claims()));
    builder.add(parameters.codeChallenge());
    builder.add(parameters.codeChallengeMethod());
    builder.add(convertAuthorizationDetails(parameters.authorizationDetailsValue()));
    builder.add(parameters.customParams());
    builder.add(
        new ExpiresIn(authorizationServerConfiguration.oauthAuthorizationRequestExpiresIn()));
    builder.add(
        new ExpiredAt(
            SystemDateTime.now()
                .plusSeconds(
                    authorizationServerConfiguration.oauthAuthorizationRequestExpiresIn())));
    return builder.build();
  }
}
