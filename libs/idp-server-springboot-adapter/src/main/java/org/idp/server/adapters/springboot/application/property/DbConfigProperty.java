/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.property;

import org.idp.server.platform.datasource.DbConfig;

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
