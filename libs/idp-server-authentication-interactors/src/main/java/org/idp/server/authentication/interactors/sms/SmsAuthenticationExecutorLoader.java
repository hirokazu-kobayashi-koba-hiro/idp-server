/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class SmsAuthenticationExecutorLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SmsAuthenticationExecutorLoader.class);

  public static SmsAuthenticationExecutors load(AuthenticationDependencyContainer container) {
    Map<SmsAuthenticationType, SmsAuthenticationExecutor> executors = new HashMap<>();
    ServiceLoader<SmsAuthenticationExecutorFactory> loader =
        ServiceLoader.load(SmsAuthenticationExecutorFactory.class);

    for (SmsAuthenticationExecutorFactory factory : loader) {
      SmsAuthenticationExecutor executor = factory.create(container);
      executors.put(executor.type(), executor);
      log.info("Dynamic Registered SmsAuthenticationExecutor " + executor.type().name());
    }

    return new SmsAuthenticationExecutors(executors);
  }
}
