/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.application;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;

public class IdentityVerificationApplicationProcess implements JsonReadable {

  String process;
  String requestedAt;

  public IdentityVerificationApplicationProcess() {}

  public IdentityVerificationApplicationProcess(
      IdentityVerificationProcess process, LocalDateTime requestedAt) {
    this.process = process.name();
    this.requestedAt = requestedAt.toString();
  }

  public IdentityVerificationApplicationProcess(String process, String requestedAt) {
    this.process = process;
    this.requestedAt = requestedAt;
  }

  public String process() {
    return process;
  }

  public String requestedAt() {
    return requestedAt;
  }

  public Map<String, Object> toMap() {
    return Map.of("process", process, "requestedAt", requestedAt);
  }
}
