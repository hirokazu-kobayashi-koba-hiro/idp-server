package org.idp.server.core.identity.event;

import java.util.Map;

public class UserLifecycleEventResult {
  UserLifecycleEventResultStatus status;
  Map<String, Object> data;

  public static UserLifecycleEventResult success(Map<String, Object> data) {
    return new UserLifecycleEventResult(UserLifecycleEventResultStatus.SUCCESS, data);
  }

  public static UserLifecycleEventResult failure(Map<String, Object> data) {
    return new UserLifecycleEventResult(UserLifecycleEventResultStatus.FAILURE, data);
  }

  private UserLifecycleEventResult(
      UserLifecycleEventResultStatus status, Map<String, Object> data) {
    this.status = status;
    this.data = data;
  }

  public UserLifecycleEventResultStatus status() {
    return status;
  }

  public Map<String, Object> data() {
    return data;
  }
}
