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

package org.idp.server.adapters.springboot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idp.async")
public class AsyncProperties {

  private ExecutorProperties securityEvent = new ExecutorProperties(5, 30, 5000);
  private ExecutorProperties userLifecycleEvent = new ExecutorProperties(5, 10, 1000);
  private ExecutorProperties auditLog = new ExecutorProperties(5, 30, 5000);

  public ExecutorProperties getSecurityEvent() {
    return securityEvent;
  }

  public void setSecurityEvent(ExecutorProperties securityEvent) {
    this.securityEvent = securityEvent;
  }

  public ExecutorProperties getUserLifecycleEvent() {
    return userLifecycleEvent;
  }

  public void setUserLifecycleEvent(ExecutorProperties userLifecycleEvent) {
    this.userLifecycleEvent = userLifecycleEvent;
  }

  public ExecutorProperties getAuditLog() {
    return auditLog;
  }

  public void setAuditLog(ExecutorProperties auditLog) {
    this.auditLog = auditLog;
  }

  public static class ExecutorProperties {

    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
    private int retryQueueCapacity = 5000;

    public ExecutorProperties() {}

    public ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {
      this.corePoolSize = corePoolSize;
      this.maxPoolSize = maxPoolSize;
      this.queueCapacity = queueCapacity;
    }

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
      return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public int getRetryQueueCapacity() {
      return retryQueueCapacity;
    }

    public void setRetryQueueCapacity(int retryQueueCapacity) {
      this.retryQueueCapacity = retryQueueCapacity;
    }
  }
}
