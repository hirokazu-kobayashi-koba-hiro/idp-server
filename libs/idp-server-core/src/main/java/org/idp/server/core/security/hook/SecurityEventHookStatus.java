package org.idp.server.core.security.hook;

public enum SecurityEventHookStatus {
  SUCCESS, FAILURE;

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isFailure() {
    return this == FAILURE;
  }
}
