package org.idp.server.handler.userinfo;

import java.util.Map;
import org.idp.server.configuration.ClientConfigurationRepository;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.configuration.ServerConfigurationRepository;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.handler.userinfo.io.UserinfoRequestStatus;
import org.idp.server.oauth.identity.User;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessTokenEntity;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.userinfo.UserinfoClaimsCreator;
import org.idp.server.userinfo.UserinfoResponse;
import org.idp.server.userinfo.verifier.UserinfoVerifier;

public class UserinfoHandler {

  OAuthTokenRepository oAuthTokenRepository;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public UserinfoHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public UserinfoRequestResponse handle(UserinfoRequest request, UserinfoDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    ServerConfiguration serverConfiguration = serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessTokenEntity);

    UserinfoVerifier verifier = new UserinfoVerifier(oAuthToken);
    verifier.verify();

    User user = delegate.getUser(oAuthToken.subject());
    UserinfoClaimsCreator claimsCreator =
        new UserinfoClaimsCreator(
            user, oAuthToken.authorizationGrant(), serverConfiguration.claimsSupported());
    Map<String, Object> claims = claimsCreator.createClaims();
    UserinfoResponse userinfoResponse = new UserinfoResponse(user, claims);
    return new UserinfoRequestResponse(UserinfoRequestStatus.OK, userinfoResponse);
  }
}
