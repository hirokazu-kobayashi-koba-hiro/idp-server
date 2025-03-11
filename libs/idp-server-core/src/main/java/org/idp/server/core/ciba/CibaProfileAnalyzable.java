package org.idp.server.core.ciba;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestKey;

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

    String scope = parameters.getValueOrEmpty(OAuthRequestKey.scope);
    JsonWebTokenClaims claims = joseContext.claims();
    String joseScope = claims.getValue("scope");
    String targetScope =
        (pattern.isRequestParameter() || clientConfiguration.isSupportedJar()) ? joseScope : scope;

    return clientConfiguration.filteredScope(targetScope);
  }
}
