package org.idp.server.basic.date;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class UtcDateTime {

  public static LocalDateTime now() {
    return LocalDateTime.now(Clock.systemUTC());
  }

  public static long toEpochSecond(LocalDateTime localDateTime) {
    return localDateTime.toEpochSecond(ZoneOffset.UTC);
  }
}
