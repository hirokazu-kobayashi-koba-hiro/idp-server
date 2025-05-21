package org.idp.server.authentication.interactors.sms;

import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class SmsAuthenticationExecutors {

  Map<SmsAuthenticationType, SmsAuthenticationExecutor> executors;

  public SmsAuthenticationExecutors(
      Map<SmsAuthenticationType, SmsAuthenticationExecutor> executors) {
    this.executors = executors;
  }

  public SmsAuthenticationExecutor get(SmsAuthenticationType type) {
    SmsAuthenticationExecutor executor = executors.get(type);

    if (executor == null) {
      throw new UnSupportedException(
          "No sms authentication executor found for type: " + type.name());
    }

    return executor;
  }
}
