/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.factory.RequestObjectFactoryType;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** OAuthRequestContextService */
public interface OAuthRequestContextCreator {

  OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  default AuthorizationProfile analyze(
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration) {

    if (authorizationServerConfiguration.hasFapiAdvanceScope(filteredScopes)) {
      return AuthorizationProfile.FAPI_ADVANCE;
    }
    if (authorizationServerConfiguration.hasFapiBaselineScope(filteredScopes)) {
      return AuthorizationProfile.FAPI_BASELINE;
    }
    if (filteredScopes.contains("openid")) {
      return AuthorizationProfile.OIDC;
    }
    return AuthorizationProfile.OAUTH2;
  }

  default Set<String> filterScopes(
      OAuthRequestPattern pattern,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ClientConfiguration clientConfiguration) {

    String scope = parameters.getValueOrEmpty(OAuthRequestKey.scope);
    JsonWebTokenClaims claims = joseContext.claims();
    String joseScope = claims.getValue("scope");
    String targetScope =
        (pattern.isRequestParameter() || clientConfiguration.isSupportedJar()) ? joseScope : scope;

    return clientConfiguration.filteredScope(targetScope);
  }

  default RequestObjectFactoryType selectRequestObjectType(
      AuthorizationProfile profile,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    if (profile.isFapiAdvance()) {
      return RequestObjectFactoryType.FAPI;
    }
    return RequestObjectFactoryType.DEFAULT;
  }
}
