package org.idp.server.authentication.interactors.email;

import org.idp.server.authentication.interactors.notification.EmailSenders;
import org.idp.server.core.oidc.authentication.AuthenticationInteractionType;
import org.idp.server.core.oidc.authentication.AuthenticationInteractor;
import org.idp.server.core.oidc.authentication.StandardAuthenticationInteraction;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationInteractorFactory;
import org.idp.server.core.oidc.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;

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
