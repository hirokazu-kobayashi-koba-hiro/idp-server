/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.http;

import java.net.URI;

public class UriMatcher {

  public static boolean matchWithNormalizationAndComparison(String target, String other) {
    if (equalsSimpleComparison(target, other)) {
      return true;
    }
    return matchSyntaxBasedNormalization(target, other);
  }

  static boolean equalsSimpleComparison(String target, String other) {
    return target.equals(other);
  }

  static boolean matchSyntaxBasedNormalization(String target, String other) {
    try {
      UriWrapper targetUri = new UriWrapper(new URI(target));
      UriWrapper otherUri = new UriWrapper(new URI(other));
      if (!targetUri.equalsUserinfo(otherUri)) {
        return false;
      }
      if (!targetUri.equalsPath(otherUri)) {
        return false;
      }
      if (!targetUri.equalsHost(otherUri)) {
        return false;
      }
      return targetUri.equalsPort(otherUri);
    } catch (Exception e) {
      return false;
    }
  }
}
