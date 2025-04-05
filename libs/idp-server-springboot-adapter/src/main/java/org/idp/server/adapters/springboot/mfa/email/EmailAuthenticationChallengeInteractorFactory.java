package org.idp.server.adapters.springboot.mfa.email;

import org.idp.server.core.mfa.*;
import org.idp.server.core.notification.EmailSender;

public class EmailAuthenticationChallengeInteractorFactory implements MfaInteractorFactory {

  @Override
  public MfaInteractionType type() {
    return StandardMfaInteraction.EMAIL_VERIFICATION_CHALLENGE.toType();
  }

  @Override
  public MfaInteractor create(MfaDependencyContainer container) {

    MfaConfigurationQueryRepository configurationQueryRepository =
        container.resolve(MfaConfigurationQueryRepository.class);
    MfaTransactionCommandRepository transactionCommandRepository =
        container.resolve(MfaTransactionCommandRepository.class);
    EmailSender emailSender = container.resolve(EmailSender.class);
    return new EmailAuthenticationChallengeInteractor(
        configurationQueryRepository, transactionCommandRepository, emailSender);
  }
}
