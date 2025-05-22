/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authentication.interactors.email;

import java.util.Random;

public class OneTimePasswordGenerator {

  public static OneTimePassword generate() {
    Random random = new Random();
    int randomValue = random.nextInt(999999);
    String value = String.format("%06d", randomValue);
    return new OneTimePassword(value);
  }
}
