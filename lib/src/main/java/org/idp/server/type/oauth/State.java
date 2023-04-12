package org.idp.server.type.oauth;

import java.util.Objects;

/** State */
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
