package org.idp.server.authentication.interactors.sms.external;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutor;
import org.idp.server.authentication.interactors.sms.SmsAuthenticationExecutorFactory;

public class ExternalSmsAuthenticationExecutorFactory implements SmsAuthenticationExecutorFactory {

  @Override
  public SmsAuthenticationExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository interactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository interactionQueryRepository =
        container.resolve(AuthenticationInteractionQueryRepository.class);
    return new ExternalSmsAuthenticationExecutor(
        interactionCommandRepository, interactionQueryRepository);
  }
}
