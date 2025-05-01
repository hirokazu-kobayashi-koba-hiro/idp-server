package org.idp.server.basic.type.verifiablepresentation;

import java.util.Objects;

public class PresentationSubmission {
  String value;

  public PresentationSubmission() {}

  public PresentationSubmission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
