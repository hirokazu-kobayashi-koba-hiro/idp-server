package org.idp.server.core.identity.verification.application;

import java.time.LocalDateTime;
import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.identity.verification.IdentityVerificationProcess;

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
