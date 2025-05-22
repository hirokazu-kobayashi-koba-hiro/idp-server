/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.id_token;

import java.util.List;
import org.idp.server.basic.json.JsonReadable;

public class ClaimsObject implements JsonReadable {
  boolean essential;
  String value;
  List<String> values;

  public ClaimsObject() {}

  public boolean essential() {
    return essential;
  }

  public String value() {
    return value;
  }

  public List<String> values() {
    return values;
  }
}
