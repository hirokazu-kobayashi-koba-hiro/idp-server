/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.userinfo;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.core.oidc.userinfo.handler.UserinfoDelegate;
import org.idp.server.core.oidc.userinfo.handler.UserinfoHandler;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequest;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestResponse;
import org.idp.server.core.oidc.userinfo.handler.io.UserinfoRequestStatus;
import org.idp.server.platform.dependency.protocol.AuthorizationProvider;
import org.idp.server.platform.dependency.protocol.DefaultAuthorizationProvider;
import org.idp.server.platform.log.LoggerWrapper;

public class DefaultUserinfoProtocol implements UserinfoProtocol {

  UserinfoHandler userinfoHandler;
  LoggerWrapper log = LoggerWrapper.getLogger(UserinfoProtocol.class);

  public DefaultUserinfoProtocol(
      OAuthTokenRepository oAuthTokenRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.userinfoHandler =
        new UserinfoHandler(
            oAuthTokenRepository,
            authorizationServerConfigurationQueryRepository,
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
