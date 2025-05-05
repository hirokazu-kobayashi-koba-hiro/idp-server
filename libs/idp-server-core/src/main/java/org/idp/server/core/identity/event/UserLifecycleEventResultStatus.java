package org.idp.server.core.identity.event;

public enum UserLifecycleEventResultStatus {
  SUCCESS,
  FAILURE;

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isFailure() {
    return this == FAILURE;
  }
}
