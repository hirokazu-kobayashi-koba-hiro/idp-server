package org.idp.server.core.oauth;

import java.util.List;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.OAuthRequestParameters;

/** AuthorizationProfileAnalyzable */
public interface AuthorizationProfileAnalyzable {

  default AuthorizationProfile analyze(
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    List<String> filteredScopes = filterScopes(parameters, joseContext, clientConfiguration);
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

  default List<String> filterScopes(
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ClientConfiguration clientConfiguration) {
    // FIXME
    String scope = parameters.getString(OAuthRequestKey.scope);
    JsonWebTokenClaims claims = joseContext.claims();
    String joseScope = claims.getValue("scope");
    String targetScope = clientConfiguration.isSupportedJar() ? joseScope : scope;
    return clientConfiguration.filteredScope(targetScope);
  }
}
