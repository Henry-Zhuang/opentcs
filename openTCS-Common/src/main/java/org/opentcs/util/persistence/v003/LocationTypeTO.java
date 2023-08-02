/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.util.persistence.v003;

import java.util.ArrayList;
import java.util.List;
import static java.util.Objects.requireNonNull;
import javax.annotation.Nonnull;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 */
@XmlAccessorType(XmlAccessType.PROPERTY)
@XmlType(propOrder = {"name", "locationNamePrefix", "allowedOperations", "properties"})
public class LocationTypeTO
    extends PlantModelElementTO {

  private String locationNamePrefix;
  private List<AllowedOperationTO> allowedOperations = new ArrayList<>();

  /**
   * Creates a new instance.
   */
  public LocationTypeTO() {
  }

  @XmlAttribute
  public String getLocationNamePrefix() {
    return locationNamePrefix;
  }

  public LocationTypeTO setLocationNamePrefix(String locationNamePrefix) {
    this.locationNamePrefix = locationNamePrefix;
    return this;
  }

  @XmlElement(name = "allowedOperation")
  public List<AllowedOperationTO> getAllowedOperations() {
    return allowedOperations;
  }

  public LocationTypeTO setAllowedOperations(@Nonnull List<AllowedOperationTO> allowedOperations) {
    requireNonNull(allowedOperations, "allowedOperations");
    this.allowedOperations = allowedOperations;
    return this;
  }
}
