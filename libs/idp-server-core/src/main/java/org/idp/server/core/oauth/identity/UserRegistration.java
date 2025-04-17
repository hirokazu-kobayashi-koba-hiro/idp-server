package org.idp.server.core.oauth.identity;

public class UserRegistration {

  String username;
  String email;
  String password;

  public UserRegistration(String username, String email, String password) {
    this.username = username;
    this.email = email;
    this.password = password;
  }

  public String username() {
    return username;
  }

  public String email() {
    return email;
  }

  public String password() {
    return password;
  }
}
