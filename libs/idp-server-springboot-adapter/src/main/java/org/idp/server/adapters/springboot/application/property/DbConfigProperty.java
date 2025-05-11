package org.idp.server.adapters.springboot.application.property;

import org.idp.server.basic.datasource.DbConfig;

public class DbConfigProperty {
  String url;
  String username;
  String password;
  HikariConfig hikari = new HikariConfig();

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public HikariConfig getHikari() {
    return hikari;
  }

  public void setHikari(HikariConfig hikari) {
    this.hikari = hikari;
  }

  public DbConfig toDbConfig() {
    return new DbConfig(
        url,
        username,
        password,
        hikari.getMaximumPoolSize(),
        hikari.getMinimumIdle(),
        hikari.getConnectionTimeout(),
        hikari.getIdleTimeout(),
        hikari.getMaxLifetime());
  }
}
