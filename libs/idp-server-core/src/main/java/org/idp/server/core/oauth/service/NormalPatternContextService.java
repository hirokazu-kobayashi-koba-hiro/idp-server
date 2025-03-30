package org.idp.server.core.oauth.service;

import java.util.Set;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.factory.NormalRequestFactory;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.OAuthRequestParameters;
import org.idp.server.core.tenant.Tenant;

/** NormalPatternContextService */
public class NormalPatternContextService implements OAuthRequestContextService {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    JoseContext joseContext = new JoseContext();
    OAuthRequestPattern pattern = OAuthRequestPattern.NORMAL;
    Set<String> filteredScopes =
        filterScopes(pattern, parameters, joseContext, clientConfiguration);
    AuthorizationProfile profile = analyze(filteredScopes, serverConfiguration);

    AuthorizationRequest authorizationRequest =
        normalRequestFactory.create(
            profile,
            parameters,
            joseContext,
            filteredScopes,
            serverConfiguration,
            clientConfiguration);

    return new OAuthRequestContext(
        tenant,
        pattern,
        parameters,
        joseContext,
        authorizationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
