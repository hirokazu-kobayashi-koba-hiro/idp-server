/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.delegation;

public class ExternalWorkflowExaminationStatus {
  String name;
  boolean completed;

  public ExternalWorkflowExaminationStatus() {}

  public ExternalWorkflowExaminationStatus(String name, boolean completed) {
    this.name = name;
    this.completed = completed;
  }

  public String name() {
    return name;
  }

  public boolean completed() {
    return completed;
  }
}
