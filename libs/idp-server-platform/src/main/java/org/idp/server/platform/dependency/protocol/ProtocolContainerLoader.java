/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.dependency.protocol;

import java.util.ServiceLoader;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class ProtocolContainerLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ProtocolContainerLoader.class);

  public static ProtocolContainer load(
      ApplicationComponentContainer applicationComponentContainer) {
    ProtocolContainer container = new ProtocolContainer();
    ServiceLoader<ProtocolProvider> loader = ServiceLoader.load(ProtocolProvider.class);

    for (ProtocolProvider<?> provider : loader) {
      container.register(provider.type(), provider.provide(applicationComponentContainer));
      log.info("Dynamic Registered Protocol provider " + provider.type());
    }

    return container;
  }
}
