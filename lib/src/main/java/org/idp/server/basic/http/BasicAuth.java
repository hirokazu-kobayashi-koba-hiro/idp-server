package org.idp.server.basic.http;

import java.util.Objects;

public class BasicAuth {
  String username;
  String password;

  public BasicAuth() {}

  public BasicAuth(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public boolean exists() {
    return exists(username) && exists(password);
  }

  boolean exists(String value) {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    BasicAuth basicAuth = (BasicAuth) o;
    return Objects.equals(username, basicAuth.username)
        && Objects.equals(password, basicAuth.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }
}
