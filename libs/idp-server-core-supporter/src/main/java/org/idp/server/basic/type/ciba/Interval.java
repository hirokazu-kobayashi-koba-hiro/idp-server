package org.idp.server.basic.type.ciba;

public class Interval {
  int value;

  public Interval() {}

  public Interval(int value) {
    this.value = value;
  }

  public Interval(String value) {
    this.value = Integer.parseInt(value);
  }

  public int value() {
    return value;
  }

  public String toStringValue() {
    return String.valueOf(value);
  }
}
