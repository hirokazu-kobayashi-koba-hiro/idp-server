package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.factory.NormalRequestFactory;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.OAuthRequestParameters;

/** NormalPatternContextService */
public class NormalPatternContextCreator implements OAuthRequestContextCreator {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    JoseContext joseContext = new JoseContext();
    OAuthRequestPattern pattern = OAuthRequestPattern.NORMAL;
    Set<String> filteredScopes =
        filterScopes(pattern, parameters, joseContext, clientConfiguration);
    AuthorizationProfile profile = analyze(filteredScopes, authorizationServerConfiguration);

    AuthorizationRequest authorizationRequest =
        normalRequestFactory.create(
            profile,
            parameters,
            joseContext,
            filteredScopes,
            authorizationServerConfiguration,
            clientConfiguration);

    return new OAuthRequestContext(
        tenant,
        pattern,
        parameters,
        joseContext,
        authorizationRequest,
        authorizationServerConfiguration,
        clientConfiguration);
  }
}
