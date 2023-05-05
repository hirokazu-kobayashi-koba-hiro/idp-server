package org.idp.server.ciba;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestKey;

/** AuthorizationProfileAnalyzable */
public interface CibaProfileAnalyzable {

  default CibaProfile analyze(Set<String> filteredScopes, ServerConfiguration serverConfiguration) {

    if (serverConfiguration.hasFapiAdvanceScope(filteredScopes)) {
      return CibaProfile.FAPI_CIBA;
    }
    if (serverConfiguration.hasFapiBaselineScope(filteredScopes)) {
      return CibaProfile.FAPI_CIBA;
    }
    return CibaProfile.CIBA;
  }

  default Set<String> filterScopes(
      CibaRequestPattern pattern,
      CibaRequestParameters parameters,
      JoseContext joseContext,
      ClientConfiguration clientConfiguration) {

    String scope = parameters.getString(OAuthRequestKey.scope);
    JsonWebTokenClaims claims = joseContext.claims();
    String joseScope = claims.getValue("scope");
    String targetScope =
        (pattern.isRequestParameter() || clientConfiguration.isSupportedJar()) ? joseScope : scope;

    return clientConfiguration.filteredScope(targetScope);
  }
}
