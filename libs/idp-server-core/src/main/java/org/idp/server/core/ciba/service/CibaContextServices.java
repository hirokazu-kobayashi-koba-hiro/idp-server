package org.idp.server.core.ciba.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.ciba.CibaRequestPattern;
import org.idp.server.core.type.exception.UnSupportedException;

public class CibaContextServices {

  Map<CibaRequestPattern, CibaRequestContextService> values;

  public CibaContextServices() {
    this.values = new HashMap<>();
    this.values.put(CibaRequestPattern.NORMAL, new NormalPatternContextService());
    this.values.put(CibaRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextService());
  }

  public CibaRequestContextService get(CibaRequestPattern pattern) {
    CibaRequestContextService cibaRequestContextService = values.get(pattern);
    if (Objects.isNull(cibaRequestContextService)) {
      throw new UnSupportedException(
          String.format("unsupported ciba request pattern (%s)", pattern.name()));
    }
    return cibaRequestContextService;
  }
}
