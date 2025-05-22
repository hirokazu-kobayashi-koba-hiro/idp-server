/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

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
