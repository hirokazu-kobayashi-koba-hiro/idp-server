/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.event;

import java.util.Map;

public class UserLifecycleEventExecutorsMap {

  Map<UserLifecycleType, UserLifecycleEventExecutors> map;

  public UserLifecycleEventExecutorsMap(Map<UserLifecycleType, UserLifecycleEventExecutors> map) {
    this.map = map;
  }

  public UserLifecycleEventExecutors find(UserLifecycleType type) {
    UserLifecycleEventExecutors userLifecycleEventExecutors = map.get(type);
    if (userLifecycleEventExecutors == null) {
      return new UserLifecycleEventExecutors();
    }
    return userLifecycleEventExecutors;
  }
}
