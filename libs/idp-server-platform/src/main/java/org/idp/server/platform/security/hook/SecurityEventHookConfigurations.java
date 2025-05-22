/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security.hook;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurityEventHookConfigurations implements Iterable<SecurityEventHookConfiguration> {

  List<SecurityEventHookConfiguration> values;

  public SecurityEventHookConfigurations() {
    this.values = new ArrayList<>();
  }

  public SecurityEventHookConfigurations(List<SecurityEventHookConfiguration> values) {
    this.values = values;
  }

  @Override
  public Iterator<SecurityEventHookConfiguration> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
