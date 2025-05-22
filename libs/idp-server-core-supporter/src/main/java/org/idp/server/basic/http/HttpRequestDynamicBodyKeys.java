/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.http;

import java.util.Iterator;
import java.util.List;

public class HttpRequestDynamicBodyKeys implements Iterable<String> {

  List<String> values;

  public HttpRequestDynamicBodyKeys() {}

  public HttpRequestDynamicBodyKeys(List<String> values) {
    this.values = values;
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean shouldIncludeAll() {
    return !exists();
  }
}
