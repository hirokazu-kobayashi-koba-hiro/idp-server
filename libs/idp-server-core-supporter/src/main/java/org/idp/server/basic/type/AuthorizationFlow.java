package org.idp.server.basic.type;

import org.idp.server.platform.exception.UnSupportedException;

public enum AuthorizationFlow {
  OAUTH("oauth"),
  CIBA("ciba");

  String value;

  AuthorizationFlow(String value) {
    this.value = value;
  }

  public static AuthorizationFlow of(String authorizationFlow) {
    for (AuthorizationFlow flow : AuthorizationFlow.values()) {
      if (flow.value.equals(authorizationFlow)) {
        return flow;
      }
    }
    throw new UnSupportedException(
        String.format("unsupported authorization flow (%s)", authorizationFlow));
  }

  public String value() {
    return value;
  }
  ;
}
