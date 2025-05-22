/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.legacy;

import org.idp.server.basic.json.JsonReadable;

public class UserInfoMappingRule implements JsonReadable {
  String from;
  String to;
  String type;

  public UserInfoMappingRule() {}

  public UserInfoMappingRule(String from, String to, String type) {
    this.from = from;
    this.to = to;
    this.type = type;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public String getType() {
    return type;
  }
}
