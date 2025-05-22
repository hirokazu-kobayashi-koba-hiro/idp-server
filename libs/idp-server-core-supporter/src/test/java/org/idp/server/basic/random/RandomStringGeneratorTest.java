/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.random;

import org.junit.jupiter.api.Test;

public class RandomStringGeneratorTest {

  @Test
  void can_create_random_string() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);
    System.out.println(randomStringGenerator.generate());
  }
}
