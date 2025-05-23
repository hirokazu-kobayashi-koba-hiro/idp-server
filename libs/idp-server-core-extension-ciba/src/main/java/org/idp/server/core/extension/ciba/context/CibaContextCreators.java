/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.extension.ciba.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.ciba.CibaRequestPattern;
import org.idp.server.platform.exception.UnSupportedException;

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
      throw new UnSupportedException(
          String.format("unsupported ciba request pattern (%s)", pattern.name()));
    }
    return cibaRequestContextCreator;
  }
}
