package org.idp.server.basic.random;

import org.idp.server.basic.random.RandomStringGenerator;
import org.junit.jupiter.api.Test;

public class RandomStringGeneratorTest {

  @Test
  void can_create_random_string() {
    RandomStringGenerator randomStringGenerator = new RandomStringGenerator(20);
    System.out.println(randomStringGenerator.generate());
  }
}
