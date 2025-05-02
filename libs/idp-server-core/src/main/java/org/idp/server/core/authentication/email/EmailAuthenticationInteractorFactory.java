package org.idp.server.core.authentication.email;

import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.factory.AuthenticationInteractorFactory;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.authentication.repository.AuthenticationInteractionQueryRepository;

public class EmailAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractionType type() {
    return StandardAuthenticationInteraction.EMAIL_VERIFICATION.toType();
  }

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {

    AuthenticationInteractionCommandRepository commandRepository =
        container.resolve(AuthenticationInteractionCommandRepository.class);
    AuthenticationInteractionQueryRepository queryRepository =
        container.resolve(AuthenticationInteractionQueryRepository.class);
    return new EmailAuthenticationInteractor(commandRepository, queryRepository);
  }
}
