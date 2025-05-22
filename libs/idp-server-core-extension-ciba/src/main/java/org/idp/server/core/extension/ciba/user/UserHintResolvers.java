/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.ciba.user;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class UserHintResolvers {

  Map<UserHintType, UserHintResolver> resolvers;

  public UserHintResolvers() {
    this.resolvers = new HashMap<>();
    resolvers.put(UserHintType.LOGIN_HINT, new LoginHintResolver());
    resolvers.put(UserHintType.ID_TOKEN_HINT, new IdTokenHintResolver());
  }

  public UserHintResolver get(UserHintType type) {
    UserHintResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported user hint type: " + type.name());
    }

    return resolver;
  }
}
