package org.idp.server.core.authentication.sms.external;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutor;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutorFactory;

public class ExternalSmsAuthenticationExecutorFactory implements SmsAuthenticationExecutorFactory {

  @Override
  public SmsAuthenticationExecutor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository interactionCommandRepository = container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository interactionQueryRepository = container.resolve(AuthenticationInteractionQueryRepository.class);
    return new ExternalSmsAuthenticationExecutor(interactionCommandRepository, interactionQueryRepository);
  }
}
