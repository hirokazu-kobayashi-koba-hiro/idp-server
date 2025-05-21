package org.idp.server.core.extension.ciba.token;

import org.idp.server.core.extension.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.extension.ciba.repository.CibaGrantRepository;
import org.idp.server.core.grant_management.AuthorizationGrantedRepository;
import org.idp.server.core.token.plugin.OAuthTokenCreationServiceFactory;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.token.service.OAuthTokenCreationService;
import org.idp.server.platform.dependency.ApplicationComponentContainer;

public class CibaGrantServiceFactory implements OAuthTokenCreationServiceFactory {

  @Override
  public OAuthTokenCreationService create(ApplicationComponentContainer container) {
    BackchannelAuthenticationRequestRepository backchannelAuthenticationRequestRepository =
        container.resolve(BackchannelAuthenticationRequestRepository.class);
    CibaGrantRepository cibaGrantRepository = container.resolve(CibaGrantRepository.class);
    OAuthTokenRepository oAuthTokenRepository = container.resolve(OAuthTokenRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        container.resolve(AuthorizationGrantedRepository.class);
    return new CibaGrantService(
        backchannelAuthenticationRequestRepository,
        cibaGrantRepository,
        oAuthTokenRepository,
        authorizationGrantedRepository);
  }
}
