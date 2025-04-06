package org.idp.server.core.authentication.email;

import org.idp.server.core.authentication.*;

public class EmailAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_VERIFICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationTransactionCommandRepository commandRepository =
        container.resolve(AuthenticationTransactionCommandRepository.class);
    AuthenticationTransactionQueryRepository queryRepository =
        container.resolve(AuthenticationTransactionQueryRepository.class);
    return new EmailAuthenticationInteractor(commandRepository, queryRepository);
  }
}
