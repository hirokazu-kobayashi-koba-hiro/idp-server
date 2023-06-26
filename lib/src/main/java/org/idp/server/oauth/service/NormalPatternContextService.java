package org.idp.server.oauth.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.factory.NormalRequestFactory;
import org.idp.server.oauth.request.AuthorizationRequest;

/** NormalPatternContextService */
public class NormalPatternContextService implements OAuthRequestContextService {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();

  @Override
  public OAuthRequestContext create(
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
        pattern,
        parameters,
        joseContext,
        authorizationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
