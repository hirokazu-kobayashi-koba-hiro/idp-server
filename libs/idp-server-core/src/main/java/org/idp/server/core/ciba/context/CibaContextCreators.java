package org.idp.server.core.ciba.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.core.ciba.CibaRequestPattern;

public class CibaContextCreators {

  Map<CibaRequestPattern, CibaRequestContextCreator> values;

  public CibaContextCreators() {
    this.values = new HashMap<>();
    this.values.put(CibaRequestPattern.NORMAL, new NormalPatternContextCreator());
    this.values.put(CibaRequestPattern.REQUEST_OBJECT, new RequestObjectPatternContextCreator());
  }

  public CibaRequestContextCreator get(CibaRequestPattern pattern) {
    CibaRequestContextCreator cibaRequestContextCreator = values.get(pattern);
    if (Objects.isNull(cibaRequestContextCreator)) {
      throw new UnSupportedException(String.format("unsupported ciba request pattern (%s)", pattern.name()));
    }
    return cibaRequestContextCreator;
  }
}
