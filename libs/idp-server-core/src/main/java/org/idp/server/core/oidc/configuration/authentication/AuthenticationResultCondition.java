/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.authentication;

import org.idp.server.basic.json.JsonReadable;

public class AuthenticationResultCondition implements JsonReadable {
  String type;
  int successCount;
  int failureCount;

  public AuthenticationResultCondition() {}

  public AuthenticationResultCondition(String type, int successCount, int failureCount) {
    this.type = type;
    this.successCount = successCount;
    this.failureCount = failureCount;
  }

  public String type() {
    return type;
  }

  public int successCount() {
    return successCount;
  }

  public int failureCount() {
    return failureCount;
  }

  public boolean isSatisfiedSuccess(int successCount) {
    return successCount >= this.successCount;
  }

  public boolean isSatisfiedFailure(int failureCount) {
    return failureCount >= this.failureCount;
  }
}
