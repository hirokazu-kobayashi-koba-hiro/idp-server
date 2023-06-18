package org.idp.server.handler.config;

public class DatabaseConfig {
  String url;
  String username;
  String password;

  public DatabaseConfig(String url, String username, String password) {
    this.url = url;
    this.username = username;
    this.password = password;
  }

  public String url() {
    return url;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }
}
