package org.idp.server.platform.date;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// FIXME consider
public class SystemDateTime {

  public static Clock clock = Clock.systemUTC();
  public static ZoneOffset zoneOffset = ZoneOffset.UTC;

  public static LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public static long toEpochSecond(LocalDateTime localDateTime) {
    return localDateTime.toEpochSecond(zoneOffset);
  }

  public static long epochMilliSecond() {
    return now().toEpochSecond(zoneOffset) * 1000;
  }
}
