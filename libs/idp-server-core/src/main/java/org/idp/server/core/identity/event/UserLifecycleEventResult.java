package org.idp.server.core.identity.event;

import java.util.Map;
import java.util.UUID;

public class UserLifecycleEventResult {
  UserLifecycleEventResultIdentifier identifier;
  UserLifecycleEventResultStatus status;
  String executorName;
  Map<String, Object> data;

  public static UserLifecycleEventResult success(String executorName, Map<String, Object> data) {
    UserLifecycleEventResultIdentifier identifier =
        new UserLifecycleEventResultIdentifier(UUID.randomUUID().toString());
    return new UserLifecycleEventResult(
        identifier, UserLifecycleEventResultStatus.SUCCESS, executorName, data);
  }

  public static UserLifecycleEventResult failure(String executorName, Map<String, Object> data) {
    UserLifecycleEventResultIdentifier identifier =
        new UserLifecycleEventResultIdentifier(UUID.randomUUID().toString());
    return new UserLifecycleEventResult(
        identifier, UserLifecycleEventResultStatus.FAILURE, executorName, data);
  }

  private UserLifecycleEventResult(
      UserLifecycleEventResultIdentifier identifier,
      UserLifecycleEventResultStatus status,
      String executorName,
      Map<String, Object> data) {
    this.identifier = identifier;
    this.status = status;
    this.executorName = executorName;
    this.data = data;
  }

  public UserLifecycleEventResultIdentifier identifier() {
    return identifier;
  }

  public UserLifecycleEventResultStatus status() {
    return status;
  }

  public String executorName() {
    return executorName;
  }

  public Map<String, Object> data() {
    return data;
  }
}
