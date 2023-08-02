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
import jakarta.xml.bind.annotation.XmlTransient;

/**
 */
@XmlTransient
@XmlAccessorType(XmlAccessType.PROPERTY)
public class PlantModelElementTO {

  private String name = "";
  private List<PropertyTO> properties = new ArrayList<>();

  /**
   * Creates a new instance.
   */
  public PlantModelElementTO() {
  }

  @XmlAttribute(required = true)
  public String getName() {
    return name;
  }

  public PlantModelElementTO setName(@Nonnull String name) {
    requireNonNull(name, "name");
    this.name = name;
    return this;
  }

  @XmlElement(name = "property")
  public List<PropertyTO> getProperties() {
    return properties;
  }

  public PlantModelElementTO setProperties(@Nonnull List<PropertyTO> properties) {
    requireNonNull(properties, "properties");
    this.properties = properties;
    return this;
  }
}
