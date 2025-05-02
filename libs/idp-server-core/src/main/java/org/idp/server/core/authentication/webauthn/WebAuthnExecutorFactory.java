package org.idp.server.core.authentication.webauthn;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public interface WebAuthnExecutorFactory {

  WebAuthnExecutor create(AuthenticationDependencyContainer container);
}
