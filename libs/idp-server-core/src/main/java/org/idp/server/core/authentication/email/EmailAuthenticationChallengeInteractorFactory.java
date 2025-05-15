package org.idp.server.core.authentication.email;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.notification.EmailSenders;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;

public class EmailAuthenticationChallengeInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_AUTHENTICATION_CHALLENGE.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    AuthenticationInteractionCommandRepository transactionCommandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    EmailSenders emailSenders = container.resolve(EmailSenders.class);
    return new EmailAuthenticationChallengeInteractor(
        configurationQueryRepository, transactionCommandRepository, emailSenders);
  }
}
