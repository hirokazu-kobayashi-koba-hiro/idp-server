package org.idp.server.core.security.hook;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurityEventHookConfigurations implements Iterable<SecurityEventHookConfiguration> {

  List<SecurityEventHookConfiguration> values;

  public SecurityEventHookConfigurations() {
    this.values = new ArrayList<>();
  }

  public SecurityEventHookConfigurations(List<SecurityEventHookConfiguration> values) {
    this.values = values;
  }

  @Override
  public Iterator<SecurityEventHookConfiguration> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

}
