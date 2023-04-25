package org.idp.server.handler.userinfo;

import java.util.Map;
import org.idp.server.handler.userinfo.io.UserinfoRequest;
import org.idp.server.handler.userinfo.io.UserinfoRequestResponse;
import org.idp.server.handler.userinfo.io.UserinfoRequestStatus;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.repository.ClientConfigurationRepository;
import org.idp.server.oauth.repository.ServerConfigurationRepository;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.repository.OAuthTokenRepository;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.userinfo.UserinfoClaimsCreator;
import org.idp.server.userinfo.UserinfoResponse;
import org.idp.server.userinfo.verifier.UserinfoVerifier;

public class UserinfoHandler {

  OAuthTokenRepository oAuthTokenRepository;
  UserinfoVerifier verifier;
  ServerConfigurationRepository serverConfigurationRepository;
  ClientConfigurationRepository clientConfigurationRepository;

  public UserinfoHandler(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.verifier = new UserinfoVerifier();
    this.serverConfigurationRepository = serverConfigurationRepository;
    this.clientConfigurationRepository = clientConfigurationRepository;
  }

  public UserinfoRequestResponse handle(UserinfoRequest request, UserinfoDelegate delegate) {
    AccessToken accessToken = request.toAccessToken();
    TokenIssuer tokenIssuer = request.toTokenIssuer();
    serverConfigurationRepository.get(tokenIssuer);
    OAuthToken oAuthToken = oAuthTokenRepository.find(tokenIssuer, accessToken);
    verifier.verify(oAuthToken);
    User user = delegate.getUser(oAuthToken.subject());
    UserinfoClaimsCreator claimsCreator = new UserinfoClaimsCreator();
    Map<String, Object> claims = claimsCreator.createClaims(user, oAuthToken.authorizationGrant());
    UserinfoResponse userinfoResponse = new UserinfoResponse(user, claims);
    return new UserinfoRequestResponse(UserinfoRequestStatus.OK, userinfoResponse);
  }
}
