package org.idp.server.type.oauth;

public class ExpiresIn {
  long value;

  public ExpiresIn() {}

  public ExpiresIn(int value) {
    this.value = value;
  }

  public ExpiresIn(long value) {
    this.value = value;
  }

  public ExpiresIn(String value) {
    this.value = Long.parseLong(value);
  }

  public long value() {
    return value;
  }

  public String toStringValue() {
    return String.valueOf(value);
  }
}
