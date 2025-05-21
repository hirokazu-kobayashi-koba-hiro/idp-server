package org.idp.server.authentication.interactors.sms;

import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;

public interface SmsAuthenticationExecutorFactory {

  SmsAuthenticationExecutor create(AuthenticationDependencyContainer container);
}
