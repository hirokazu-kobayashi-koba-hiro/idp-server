/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.device;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AuthenticationDevices implements Iterable<AuthenticationDevice> {
  List<AuthenticationDevice> values;

  public AuthenticationDevices() {
    this.values = new ArrayList<>();
  }

  public AuthenticationDevices(List<AuthenticationDevice> values) {
    this.values = values;
  }

  @Override
  public Iterator<AuthenticationDevice> iterator() {
    return values.iterator();
  }
}
