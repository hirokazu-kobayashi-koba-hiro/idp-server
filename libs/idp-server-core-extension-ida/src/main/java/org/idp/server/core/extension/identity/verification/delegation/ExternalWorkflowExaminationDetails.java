/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.delegation;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;

public class ExternalWorkflowExaminationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowExaminationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowExaminationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
