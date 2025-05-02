package org.idp.server.core.authentication.sms.external;

import org.idp.server.core.authentication.AuthenticationDependencyContainer;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutor;
import org.idp.server.core.authentication.sms.SmsAuthenticationExecutorFactory;

public class ExternalSmsAuthenticationExecutorFactory implements SmsAuthenticationExecutorFactory {

  @Override
  public SmsAuthenticationExecutor create(AuthenticationDependencyContainer container) {
    return new ExternalSmsAuthenticationExecutor();
  }
}
