package org.idp.server.core.authentication.email;

import org.idp.server.core.authentication.*;
import org.idp.server.core.notification.EmailSenders;

public class EmailAuthenticationChallengeInteractorFactory
    implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_VERIFICATION_CHALLENGE.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(AuthenticationConfigurationQueryRepository.class);
    AuthenticationTransactionCommandRepository transactionCommandRepository =
        container.resolve(AuthenticationTransactionCommandRepository.class);
    EmailSenders emailSenders = container.resolve(EmailSenders.class);
    return new EmailAuthenticationChallengeInteractor(
        configurationQueryRepository, transactionCommandRepository, emailSenders);
  }
}
