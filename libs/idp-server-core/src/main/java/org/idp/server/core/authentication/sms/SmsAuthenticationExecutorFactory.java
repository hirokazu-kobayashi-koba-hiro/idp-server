package org.idp.server.core.authentication.sms;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;

public interface SmsAuthenticationExecutorFactory {

  SmsAuthenticationExecutor create(AuthenticationDependencyContainer container);
}
