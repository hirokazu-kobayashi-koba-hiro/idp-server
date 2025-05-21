package org.idp.server.platform.security;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurityEvents implements Iterable<SecurityEvent> {

  List<SecurityEvent> values;

  public SecurityEvents() {
    values = new ArrayList<>();
  }

  public SecurityEvents(List<SecurityEvent> values) {
    this.values = values;
  }

  @Override
  public Iterator<SecurityEvent> iterator() {
    return values.iterator();
  }
}
