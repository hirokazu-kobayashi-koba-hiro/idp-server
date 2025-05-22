/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
