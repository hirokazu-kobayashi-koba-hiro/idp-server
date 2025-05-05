package org.idp.server.core.identity.deletion;

import java.util.Map;

public class UserDeletionResult {
  UserDeletionResultStatus status;
  Map<String, Object> data;

  public static UserDeletionResult success(Map<String, Object> data) {
    return new UserDeletionResult(UserDeletionResultStatus.SUCCESS, data);
  }

  public static UserDeletionResult failure(Map<String, Object> data) {
    return new UserDeletionResult(UserDeletionResultStatus.FAILURE, data);
  }

  private UserDeletionResult(UserDeletionResultStatus status, Map<String, Object> data) {
    this.status = status;
    this.data = data;
  }

  public UserDeletionResultStatus status() {
    return status;
  }

  public Map<String, Object> data() {
    return data;
  }
}
