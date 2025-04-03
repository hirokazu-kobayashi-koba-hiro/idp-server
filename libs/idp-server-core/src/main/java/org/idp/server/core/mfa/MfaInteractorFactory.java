package org.idp.server.core.mfa;

public interface MfaInteractorFactory {

  MfaInteractionType type();

  MfaInteractor create(MfaDependencyContainer container);
}
