/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurityEvents implements Iterable<SecurityEvent> {

  List<SecurityEvent> values;

  public SecurityEvents() {
    values = new ArrayList<>();
  }

  public SecurityEvents(List<SecurityEvent> values) {
    this.values = values;
  }

  @Override
  public Iterator<SecurityEvent> iterator() {
    return values.iterator();
  }
}
