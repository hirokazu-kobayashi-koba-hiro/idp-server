package org.idp.server.oauth.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.factory.AuthorizationRequestFactory;
import org.idp.server.oauth.factory.FapiAdvanceRequestObjectPatternFactory;
import org.idp.server.oauth.factory.RequestObjectPatternFactory;
import org.idp.server.type.OAuthRequestKey;

/** OAuthRequestContextService */
public interface OAuthRequestContextService {

  OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);

  default AuthorizationProfile analyze(
      Set<String> filteredScopes, ServerConfiguration serverConfiguration) {

    if (serverConfiguration.hasFapiAdvanceScope(filteredScopes)) {
      return AuthorizationProfile.FAPI_ADVANCE;
    }
    if (serverConfiguration.hasFapiBaselineScope(filteredScopes)) {
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

    String scope = parameters.getOrEmpty(OAuthRequestKey.scope);
    JsonWebTokenClaims claims = joseContext.claims();
    String joseScope = claims.getValue("scope");
    String targetScope =
        (pattern.isRequestParameter() || clientConfiguration.isSupportedJar()) ? joseScope : scope;

    return clientConfiguration.filteredScope(targetScope);
  }

  default AuthorizationRequestFactory selectAuthorizationRequestFactory(
      AuthorizationProfile profile,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    if (profile.isFapiAdvance()) {
      return new FapiAdvanceRequestObjectPatternFactory();
    }
    return new RequestObjectPatternFactory();
  }
}
