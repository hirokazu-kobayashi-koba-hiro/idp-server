package org.idp.server.platform.security.hook;

public enum SecurityEventHookStatus {
  SUCCESS,
  FAILURE;

  public boolean isSuccess() {
    return this == SUCCESS;
  }

  public boolean isFailure() {
    return this == FAILURE;
  }
}
