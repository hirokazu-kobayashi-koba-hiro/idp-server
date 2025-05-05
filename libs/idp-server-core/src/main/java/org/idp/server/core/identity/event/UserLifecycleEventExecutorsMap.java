package org.idp.server.core.identity.event;

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
