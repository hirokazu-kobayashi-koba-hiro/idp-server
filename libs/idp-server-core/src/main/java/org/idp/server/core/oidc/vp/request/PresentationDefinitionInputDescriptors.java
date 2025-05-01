package org.idp.server.core.oidc.vp.request;

import java.util.Iterator;
import java.util.List;

/**
 * Input Descriptor
 *
 * <p>Input Descriptors are objects used to describe the information a Verifier requires of a
 * Holder. All Input Descriptors MUST be satisfied, unless otherwise specified by a Feature.
 *
 * <p>Input Descriptor Objects contain an identifier and may contain constraints on data values, and
 * an explanation why a certain item or set of data is being requested.
 *
 * @see <a
 *     href="https://identity.foundation/presentation-exchange/spec/v2.0.0/#input-descriptor">input-descriptor</a>
 */
public class PresentationDefinitionInputDescriptors implements Iterable<InputDescriptor> {

  List<InputDescriptor> values;

  public PresentationDefinitionInputDescriptors() {
    this.values = List.of();
  }

  public PresentationDefinitionInputDescriptors(List<InputDescriptor> values) {
    this.values = values;
  }

  @Override
  public Iterator<InputDescriptor> iterator() {
    return values.iterator();
  }
}
