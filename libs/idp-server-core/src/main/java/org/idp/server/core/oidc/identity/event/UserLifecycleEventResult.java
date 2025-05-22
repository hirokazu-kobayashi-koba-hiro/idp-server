/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.event;

import java.util.Map;
import java.util.UUID;

public class UserLifecycleEventResult {
  UserLifecycleEventResultIdentifier identifier;
  UserLifecycleEventResultStatus status;
  String executorName;
  Map<String, Object> data;

  public static UserLifecycleEventResult success(String executorName, Map<String, Object> data) {
    UserLifecycleEventResultIdentifier identifier =
        new UserLifecycleEventResultIdentifier(UUID.randomUUID().toString());
    return new UserLifecycleEventResult(
        identifier, UserLifecycleEventResultStatus.SUCCESS, executorName, data);
  }

  public static UserLifecycleEventResult failure(String executorName, Map<String, Object> data) {
    UserLifecycleEventResultIdentifier identifier =
        new UserLifecycleEventResultIdentifier(UUID.randomUUID().toString());
    return new UserLifecycleEventResult(
        identifier, UserLifecycleEventResultStatus.FAILURE, executorName, data);
  }

  private UserLifecycleEventResult(
      UserLifecycleEventResultIdentifier identifier,
      UserLifecycleEventResultStatus status,
      String executorName,
      Map<String, Object> data) {
    this.identifier = identifier;
    this.status = status;
    this.executorName = executorName;
    this.data = data;
  }

  public UserLifecycleEventResultIdentifier identifier() {
    return identifier;
  }

  public UserLifecycleEventResultStatus status() {
    return status;
  }

  public String executorName() {
    return executorName;
  }

  public Map<String, Object> data() {
    return data;
  }
}
