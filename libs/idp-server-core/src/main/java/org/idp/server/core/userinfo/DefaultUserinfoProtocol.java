package org.idp.server.core.userinfo;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;
import org.idp.server.core.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.userinfo.handler.UserinfoHandler;
import org.idp.server.core.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.userinfo.handler.io.UserinfoRequestStatus;

public class DefaultUserinfoProtocol implements UserinfoProtocol {

  UserinfoHandler userinfoHandler;
  Logger log = Logger.getLogger(UserinfoProtocol.class.getName());

  public DefaultUserinfoProtocol(
      OAuthTokenRepository oAuthTokenRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.userinfoHandler =
        new UserinfoHandler(
            oAuthTokenRepository, serverConfigurationRepository, clientConfigurationRepository);
    ;
  }

  public UserinfoRequestResponse request(UserinfoRequest request, UserinfoDelegate delegate) {
    try {
      return userinfoHandler.handle(request, delegate);
    } catch (Exception exception) {
      Error error = new Error("server_error");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      log.log(Level.SEVERE, exception.getMessage(), exception);
      return new UserinfoRequestResponse(
          UserinfoRequestStatus.SERVER_ERROR, new UserinfoErrorResponse(error, errorDescription));
    }
  }
}
