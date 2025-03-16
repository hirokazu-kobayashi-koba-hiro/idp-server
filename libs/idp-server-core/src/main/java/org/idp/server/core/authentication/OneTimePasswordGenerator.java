package org.idp.server.core.authentication;

import java.util.Random;

public class OneTimePasswordGenerator {

  public static OneTimePassword generate() {
    Random random = new Random();
    int randomValue = random.nextInt(999999);
    String value = String.format("%06d", randomValue);
    return new OneTimePassword(value);
  }
}
