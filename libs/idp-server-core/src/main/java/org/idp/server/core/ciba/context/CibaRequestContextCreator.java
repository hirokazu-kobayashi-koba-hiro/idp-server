package org.idp.server.core.ciba.context;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.ciba.CibaProfile;
import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.CibaRequestParameters;
import org.idp.server.core.ciba.CibaRequestPattern;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.type.oauth.ClientSecretBasic;

public interface CibaRequestContextCreator {

  CibaRequestContext create(
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);

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
