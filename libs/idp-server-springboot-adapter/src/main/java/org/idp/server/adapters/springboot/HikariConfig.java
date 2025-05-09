package org.idp.server.adapters.springboot;

public class HikariConfig {
  int maximumPoolSize = 10;
  int minimumIdle = 2;
  long connectionTimeout = 3000;
  long idleTimeout = 600_000;
  long maxLifetime = 1_800_000;

  public int getMaximumPoolSize() {
    return maximumPoolSize;
  }

  public void setMaximumPoolSize(int maximumPoolSize) {
    this.maximumPoolSize = maximumPoolSize;
  }

  public int getMinimumIdle() {
    return minimumIdle;
  }

  public void setMinimumIdle(int minimumIdle) {
    this.minimumIdle = minimumIdle;
  }

  public long getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(long connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public long getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(long idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public long getMaxLifetime() {
    return maxLifetime;
  }

  public void setMaxLifetime(long maxLifetime) {
    this.maxLifetime = maxLifetime;
  }
}
