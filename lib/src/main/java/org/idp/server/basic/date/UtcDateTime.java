package org.idp.server.basic.date;

import java.time.Clock;
import java.time.LocalDateTime;

public class UtcDateTime {

  public static LocalDateTime now() {
    return LocalDateTime.now(Clock.systemUTC());
  }
}
