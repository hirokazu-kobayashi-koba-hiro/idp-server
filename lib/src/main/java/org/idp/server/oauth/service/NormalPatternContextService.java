package org.idp.server.oauth.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.AuthorizationProfileAnalyzable;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.factory.NormalRequestFactory;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.OAuthRequestContextService;

/** NormalPatternContextService */
public class NormalPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  NormalRequestFactory normalRequestFactory = new NormalRequestFactory();
  JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    JoseContext joseContext = new JoseContext();
    Set<String> filteredScopes = filterScopes(parameters, joseContext, clientConfiguration);
    AuthorizationProfile profile = analyze(filteredScopes, serverConfiguration);
    AuthorizationRequest authorizationRequest =
        normalRequestFactory.create(
            profile,
            parameters,
            joseContext,
            filteredScopes,
            serverConfiguration,
            clientConfiguration);
    ClaimsPayload claimsPayload =
        parameters.hasClaims()
            ? jsonParser.read(parameters.claims().value(), ClaimsPayload.class)
            : new ClaimsPayload();
    return new OAuthRequestContext(
        OAuthRequestPattern.NORMAL,
        parameters,
        joseContext,
        authorizationRequest,
        serverConfiguration,
        clientConfiguration);
  }
}
