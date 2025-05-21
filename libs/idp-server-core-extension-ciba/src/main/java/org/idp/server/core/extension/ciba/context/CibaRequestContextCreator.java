package org.idp.server.core.extension.ciba.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientSecretBasic;
import org.idp.server.core.extension.ciba.CibaProfile;
import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.extension.ciba.CibaRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface CibaRequestContextCreator {

  CibaRequestContext create(
      Tenant tenant,
      ClientSecretBasic clientSecretBasic,
      ClientCert clientCert,
      CibaRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  default CibaProfile analyze(
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration) {

    if (authorizationServerConfiguration.hasFapiAdvanceScope(filteredScopes)) {
      return CibaProfile.FAPI_CIBA;
    }
    if (authorizationServerConfiguration.hasFapiBaselineScope(filteredScopes)) {
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
