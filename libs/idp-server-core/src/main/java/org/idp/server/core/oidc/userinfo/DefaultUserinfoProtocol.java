package org.idp.server.core.oidc.userinfo;

import org.idp.server.basic.dependency.protocol.AuthorizationProvider;
import org.idp.server.basic.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.UserinfoHandler;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestStatus;
import org.idp.server.core.token.repository.OAuthTokenRepository;

public class DefaultUserinfoProtocol implements UserinfoProtocol {

  UserinfoHandler userinfoHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(UserinfoProtocol.class);

  public DefaultUserinfoProtocol(
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationRepository authorizationServerConfigurationRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.userinfoHandler =
        new UserinfoHandler(
            oAuthTokenRepository,
            authorizationServerConfigurationRepository,
            clientConfigurationQueryRepository);
  }

  @Override
  public AuthorizationProvider authorizationProtocolProvider() {
    return DefaultAuthorizationProvider.idp_server.toAuthorizationProtocolProvider();
  }

  public UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate) {
    try {
      return userinfoHandler.handle(request, delegate);
    } catch (Exception exception) {
      Error error = new Error("server_error");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      log.error(exception.getMessage(), exception);
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.SERVER_ERROR, new UserinfoErrorResponse(error, errorDescription));
    }
  }
}
