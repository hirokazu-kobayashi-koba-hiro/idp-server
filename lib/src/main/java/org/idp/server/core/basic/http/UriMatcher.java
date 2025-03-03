package org.idp.server.core.basic.http;

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
