package org.idp.server.core.authentication;

public interface AuthenticationInteractorFactory {

  AuthenticationInteractionType type();

  AuthenticationInteractor create(AuthenticationDependencyContainer container);
}
