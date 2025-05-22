/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablecredential;

import java.util.ArrayList;
import java.util.List;

public class VerifiableCredentialContext {
  public static List<String> values = new ArrayList<>();

  static {
    values.add("https://www.w3.org/2018/credentials/v1");
    values.add("https://www.w3.org/2018/credentials/examples/v1");
  }
}
