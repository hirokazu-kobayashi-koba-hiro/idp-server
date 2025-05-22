/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.security.event.hook.ssf;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventSubjectPayload {
  Map<String, String> values;

  public SecurityEventSubjectPayload() {
    values = new HashMap<>();
  }

  public SecurityEventSubjectPayload(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> toMap() {
    return values;
  }
}
