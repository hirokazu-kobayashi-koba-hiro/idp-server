package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.factory.AuthorizationRequestFactory;
import org.idp.server.core.oidc.factory.FapiAdvanceRequestObjectPatternFactory;
import org.idp.server.core.oidc.factory.RequestObjectPatternFactory;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.basic.type.OAuthRequestKey;

/** OAuthRequestContextService */
public interface OAuthRequestContextCreator {

  OAuthRequestContext create(
      Tenant tenant,
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

    String scope = parameters.getValueOrEmpty(OAuthRequestKey.scope);
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
