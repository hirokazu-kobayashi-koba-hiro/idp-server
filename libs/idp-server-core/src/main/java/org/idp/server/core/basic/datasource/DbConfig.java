package org.idp.server.core.basic.datasource;

public record DbConfig(
    String url,
    String username,
    String password,
    int maximumPoolSize,
    int minimumIdle,
    long connectionTimeout,
    long idleTimeout,
    long maxLifetime) {

  public static DbConfig defaultConfig(String url, String username, String password) {
    return new DbConfig(
        url,
        username,
        password,
        10, // maximumPoolSize
        2, // minimumIdle
        3000, // connectionTimeout
        600000, // idleTimeout
        1800000 // maxLifetime
        );
  }
}
