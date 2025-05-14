package org.idp.server.core.oidc.userinfo.handler;

import java.util.Map;
import org.idp.server.basic.type.oauth.AccessTokenEntity;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.userinfo.UserinfoClaimsCreator;
import org.idp.server.core.oidc.userinfo.UserinfoResponse;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestStatus;
import org.idp.server.core.oidc.userinfo.verifier.UserinfoVerifier;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.tokenintrospection.exception.TokenInvalidException;

public class UserinfoHandler {

  OAuthTokenRepository oAuthTokenRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public UserinfoHandler(
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.oAuthTokenRepository = oAuthTokenRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
  }

  public UserinfoRequestResponse handle(UserinfoRequest request, UserinfoDelegate delegate) {
    AccessTokenEntity accessTokenEntity = request.toAccessToken();
    Tenant tenant = request.tenant();

    OAuthToken oAuthToken = oAuthTokenRepository.find(tenant, accessTokenEntity);

    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }

    User user = delegate.findUser(tenant, oAuthToken.subject());
    UserinfoVerifier verifier = new UserinfoVerifier(oAuthToken, request.toClientCert(), user);
    verifier.verify();

    UserinfoClaimsCreator claimsCreator =
        new UserinfoClaimsCreator(user, oAuthToken.authorizationGrant());
    Map<String, Object> claims = claimsCreator.createClaims();
    UserinfoResponse userinfoResponse = new UserinfoResponse(user, claims);
    return new UserinfoRequestResponse(UserinfoRequestStatus.OK, oAuthToken, userinfoResponse);
  }
}
