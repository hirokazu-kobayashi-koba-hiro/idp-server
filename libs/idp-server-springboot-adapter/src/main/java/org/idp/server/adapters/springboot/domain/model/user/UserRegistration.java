package org.idp.server.adapters.springboot.domain.model.user;

public class UserRegistration {

  String username;
  String password;

  public UserRegistration(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }
}
