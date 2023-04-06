package org.idp.server.core.type;

public class ExpiresIn {
  long value;

  public ExpiresIn() {}

  public ExpiresIn(int value) {
    this.value = value;
  }

  public ExpiresIn(long value) {
    this.value = value;
  }

  public long value() {
    return value;
  }
}
