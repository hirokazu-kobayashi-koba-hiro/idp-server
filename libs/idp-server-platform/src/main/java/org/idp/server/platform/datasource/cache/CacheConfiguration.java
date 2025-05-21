package org.idp.server.platform.datasource.cache;

public class CacheConfiguration {
  String host;
  int port;
  int maxTotal;
  int maxIdle;
  int minIdle;
  int timeToLiveSeconds;

  public CacheConfiguration() {}

  public CacheConfiguration(
      String host, int port, int maxTotal, int maxIdle, int minIdle, int timeToLiveSeconds) {
    this.host = host;
    this.port = port;
    this.maxTotal = maxTotal;
    this.maxIdle = maxIdle;
    this.minIdle = minIdle;
    this.timeToLiveSeconds = timeToLiveSeconds;
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  public int maxTotal() {
    return maxTotal;
  }

  public int maxIdle() {
    return maxIdle;
  }

  public int minIdle() {
    return minIdle;
  }

  public int timeToLiveSeconds() {
    return timeToLiveSeconds;
  }
}
