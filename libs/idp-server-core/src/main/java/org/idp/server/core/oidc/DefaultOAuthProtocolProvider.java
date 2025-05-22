/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.oidc.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.oidc.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oidc.repository.AuthorizationRequestRepository;
import org.idp.server.core.oidc.token.repository.OAuthTokenRepository;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.dependency.protocol.ProtocolProvider;

public class DefaultOAuthProtocolProvider implements ProtocolProvider<OAuthProtocol> {

  @Override
  public Class<OAuthProtocol> type() {
    return OAuthProtocol.class;
  }

  @Override
  public OAuthProtocol provide(ApplicationComponentContainer container) {
    AuthorizationRequestRepository authorizationRequestRepository =
        container.resolve(AuthorizationRequestRepository.class);
    AuthorizationServerConfigurationQueryRepository
        authorizationServerConfigurationQueryRepository =
            container.resolve(AuthorizationServerConfigurationQueryRepository.class);
    ClientConfigurationQueryRepository clientConfigurationQueryRepository =
        container.resolve(ClientConfigurationQueryRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    AuthorizationCodeGrantRepository authorizationCodeGrantRepository =
        container.resolve(AuthorizationCodeGrantRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);
    OAuthSessionDelegate oAuthSessionDelegate = container.resolve(OAuthSessionDelegate.class);
    return new DefaultOAuthProtocol(
        authorizationRequestRepository,
        authorizationServerConfigurationQueryRepository,
        clientConfigurationQueryRepository,
        authorizationGrantedRepository,
        authorizationCodeGrantRepository,
        oAuthTokenRepository,
        oAuthSessionDelegate);
  }
}
