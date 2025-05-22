/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oauth;

import java.util.Objects;

/**
 * state
 *
 * <p>An opaque value used by the client to maintain state between the request and callback. The
 * authorization server includes this value when redirecting the user-agent back to the client. The
 * parameter SHOULD be used for preventing cross-site request forgery as described in Section 10.12.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.1">4.1.1. Authorization
 *     Request</a>
 */
public class State {
  String value;

  public State() {}

  public State(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    State state = (State) o;
    return Objects.equals(value, state.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
