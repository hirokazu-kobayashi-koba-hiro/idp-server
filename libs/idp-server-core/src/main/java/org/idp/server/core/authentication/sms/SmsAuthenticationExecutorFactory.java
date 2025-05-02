package org.idp.server.core.authentication.sms;

import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public interface SmsAuthenticationExecutorFactory {

  SmsAuthenticationExecutor create(AuthenticationDependencyContainer container);
}
