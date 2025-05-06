package org.idp.server.core.authentication.sms;

import java.util.Objects;

public class SmsAuthenticationType {
  String name;

  public SmsAuthenticationType() {}

  public SmsAuthenticationType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    SmsAuthenticationType that = (SmsAuthenticationType) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}
