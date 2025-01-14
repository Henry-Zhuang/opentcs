/**
 * Copyright (c) The openTCS Authors.
 *
 * This program is free software and subject to the MIT license. (For details,
 * see the licensing information (LICENSE.txt) you should have received with
 * this copy of the software.)
 */
package org.opentcs.guing.base.model.elements;

import java.util.Arrays;
import static java.util.Objects.requireNonNull;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import org.opentcs.data.model.visualization.ElementPropKeys;
import static org.opentcs.guing.base.I18nPlantOverviewBase.BUNDLE_PATH;
import org.opentcs.guing.base.components.layer.NullLayerWrapper;
import org.opentcs.guing.base.components.properties.type.AngleProperty;
import org.opentcs.guing.base.components.properties.type.CoordinateProperty;
import org.opentcs.guing.base.components.properties.type.KeyValueSetProperty;
import org.opentcs.guing.base.components.properties.type.LayerWrapperProperty;
import org.opentcs.guing.base.components.properties.type.LengthProperty;
import org.opentcs.guing.base.components.properties.type.PointTypeProperty;
import org.opentcs.guing.base.components.properties.type.SelectionProperty;
import org.opentcs.guing.base.components.properties.type.StringProperty;
import org.opentcs.guing.base.model.AbstractConnectableModelComponent;
import org.opentcs.guing.base.model.AbstractModelComponent;
import org.opentcs.guing.base.model.FigureDecorationDetails;
import org.opentcs.guing.base.model.PositionableModelComponent;

/**
 * Basic implementation of a point.
 */
public class PointModel
    extends AbstractConnectableModelComponent
    implements PositionableModelComponent,
               FigureDecorationDetails {

  /**
   * Key for the prefered angle of a vehicle on this point.
   */
  public static final String VEHICLE_ORIENTATION_ANGLE = "vehicleOrientationAngle";
  /**
   * Key for the type.
   */
  public static final String TYPE = "Type";
  /**
   * The point's default position for both axes.
   */
  private static final int DEFAULT_XY_POSITION = 0;
  /**
   * This class's resource bundle.
   */
  private final ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_PATH);
  /**
   * The set of vehicle models for which this model component's figure is to be decorted to
   * indicate that it is part of the route of the respective vehicles.
   */
  private Set<VehicleModel> vehicles
      = new TreeSet<>((v1, v2) -> v1.getName().compareTo(v2.getName()));
  /**
   * The set of block models for which this model component's figure is to be decorated to indicate
   * that it is part of the respective block.
   */
  private Set<BlockModel> blocks
      = new TreeSet<>((b1, b2) -> b1.getName().compareTo(b2.getName()));

  /**
   * Creates a new instance.
   */
  public PointModel() {
    createProperties();
  }

  @Override // AbstractModelComponent
  public String getDescription() {
    return bundle.getString("pointModel.description");
  }

  public CoordinateProperty getPropertyModelPositionX() {
    return (CoordinateProperty) getProperty(MODEL_X_POSITION);
  }

  public CoordinateProperty getPropertyModelPositionY() {
    return (CoordinateProperty) getProperty(MODEL_Y_POSITION);
  }

  public AngleProperty getPropertyVehicleOrientationAngle() {
    return (AngleProperty) getProperty(VEHICLE_ORIENTATION_ANGLE);
  }

  @SuppressWarnings("unchecked")
  public SelectionProperty<Type> getPropertyType() {
    return (SelectionProperty<Type>) getProperty(TYPE);
  }

  public KeyValueSetProperty getPropertyMiscellaneous() {
    return (KeyValueSetProperty) getProperty(MISCELLANEOUS);
  }

  public StringProperty getPropertyLayoutPosX() {
    return (StringProperty) getProperty(ElementPropKeys.POINT_POS_X);
  }

  public StringProperty getPropertyLayoutPosY() {
    return (StringProperty) getProperty(ElementPropKeys.POINT_POS_Y);
  }

  public StringProperty getPropertyPointLabelOffsetX() {
    return (StringProperty) getProperty(ElementPropKeys.POINT_LABEL_OFFSET_X);
  }

  public StringProperty getPropertyPointLabelOffsetY() {
    return (StringProperty) getProperty(ElementPropKeys.POINT_LABEL_OFFSET_Y);
  }

  public StringProperty getPropertyPointLabelOrientationAngle() {
    return (StringProperty) getProperty(ElementPropKeys.POINT_LABEL_ORIENTATION_ANGLE);
  }

  @Override
  public void addVehicleModel(VehicleModel model) {
    vehicles.add(model);
  }

  @Override
  public void removeVehicleModel(VehicleModel model) {
    vehicles.remove(model);
  }

  @Override
  public Set<VehicleModel> getVehicleModels() {
    return vehicles;
  }

  @Override
  public void addBlockModel(BlockModel model) {
    blocks.add(model);
  }

  @Override
  public void removeBlockModel(BlockModel model) {
    blocks.remove(model);
  }

  @Override
  public Set<BlockModel> getBlockModels() {
    return blocks;
  }

  @Override
  @SuppressWarnings("unchecked")
  public AbstractModelComponent clone()
      throws CloneNotSupportedException {
    PointModel clone = (PointModel) super.clone();
    clone.setVehicleModels((Set<VehicleModel>) ((TreeSet<VehicleModel>) vehicles).clone());
    clone.setBlockModels((Set<BlockModel>) ((TreeSet<BlockModel>) blocks).clone());

    return clone;
  }

  private void createProperties() {
    StringProperty pName = new StringProperty(this);
    pName.setDescription(bundle.getString("pointModel.property_name.description"));
    pName.setHelptext(bundle.getString("pointModel.property_name.helptext"));
    setProperty(NAME, pName);

    CoordinateProperty pPosX = new CoordinateProperty(this,
                                                      DEFAULT_XY_POSITION,
                                                      LengthProperty.Unit.MM);
    pPosX.setDescription(bundle.getString("pointModel.property_modelPositionX.description"));
    pPosX.setHelptext(bundle.getString("pointModel.property_modelPositionX.helptext"));
    setProperty(MODEL_X_POSITION, pPosX);

    CoordinateProperty pPosY = new CoordinateProperty(this,
                                                      DEFAULT_XY_POSITION,
                                                      LengthProperty.Unit.MM);
    pPosY.setDescription(bundle.getString("pointModel.property_modelPositionY.description"));
    pPosY.setHelptext(bundle.getString("pointModel.property_modelPositionY.helptext"));
    setProperty(MODEL_Y_POSITION, pPosY);

    AngleProperty pPhi = new AngleProperty(this);
    pPhi.setDescription(bundle.getString("pointModel.property_angle.description"));
    pPhi.setHelptext(bundle.getString("pointModel.property_angle.helptext"));
    setProperty(VEHICLE_ORIENTATION_ANGLE, pPhi);

    PointTypeProperty pType = new PointTypeProperty(this,
                                                    Arrays.asList(Type.values()),
                                                    Type.values()[0]);
    pType.setDescription(bundle.getString("pointModel.property_type.description"));
    pType.setHelptext(bundle.getString("pointModel.property_type.helptext"));
    pType.setCollectiveEditable(true);
    setProperty(TYPE, pType);

    KeyValueSetProperty pMiscellaneous = new KeyValueSetProperty(this);
    pMiscellaneous.setDescription(
        bundle.getString("pointModel.property_miscellaneous.description")
    );
    pMiscellaneous.setHelptext(bundle.getString("pointModel.property_miscellaneous.helptext"));
    pMiscellaneous.setOperatingEditable(true);
    setProperty(MISCELLANEOUS, pMiscellaneous);

    StringProperty pPointPosX = new StringProperty(this, String.valueOf(DEFAULT_XY_POSITION));
    pPointPosX.setDescription(bundle.getString("pointModel.property_positionX.description"));
    pPointPosX.setHelptext(bundle.getString("pointModel.property_positionX.helptext"));
    // The position can only be changed in the drawing.
    pPointPosX.setModellingEditable(false);
    setProperty(ElementPropKeys.POINT_POS_X, pPointPosX);

    StringProperty pPointPosY = new StringProperty(this, String.valueOf(DEFAULT_XY_POSITION));
    pPointPosY.setDescription(bundle.getString("pointModel.property_positionY.description"));
    pPointPosY.setHelptext(bundle.getString("pointModel.property_positionY.helptext"));
    // The position can only be changed in the drawing.
    pPointPosY.setModellingEditable(false);
    setProperty(ElementPropKeys.POINT_POS_Y, pPointPosY);

    StringProperty pPointLabelOffsetX = new StringProperty(this);
    pPointLabelOffsetX.setDescription(
        bundle.getString("pointModel.property_labelOffsetX.description")
    );
    pPointLabelOffsetX.setHelptext(bundle.getString("pointModel.property_labelOffsetX.helptext"));
    pPointLabelOffsetX.setModellingEditable(false);
    setProperty(ElementPropKeys.POINT_LABEL_OFFSET_X, pPointLabelOffsetX);

    StringProperty pPointLabelOffsetY = new StringProperty(this);
    pPointLabelOffsetY.setDescription(
        bundle.getString("pointModel.property_labelOffsetY.description")
    );
    pPointLabelOffsetY.setHelptext(bundle.getString("pointModel.property_labelOffsetY.helptext"));
    pPointLabelOffsetY.setModellingEditable(false);
    setProperty(ElementPropKeys.POINT_LABEL_OFFSET_Y, pPointLabelOffsetY);

    StringProperty pPointLabelOrientationAngle = new StringProperty(this);
    pPointLabelOrientationAngle.setDescription(
        bundle.getString("pointModel.property_labelOrientationAngle.description"));
    pPointLabelOrientationAngle.setHelptext(
        bundle.getString("pointModel.property_labelOrientationAngle.helptext"));
    pPointLabelOrientationAngle.setModellingEditable(false);
    setProperty(ElementPropKeys.POINT_LABEL_ORIENTATION_ANGLE, pPointLabelOrientationAngle);

    LayerWrapperProperty pLayerWrapper = new LayerWrapperProperty(this, new NullLayerWrapper());
    pLayerWrapper.setDescription(bundle.getString("pointModel.property_layerWrapper.description"));
    pLayerWrapper.setHelptext(bundle.getString("pointModel.property_layerWrapper.helptext"));
    pLayerWrapper.setModellingEditable(false);
    setProperty(LAYER_WRAPPER, pLayerWrapper);
  }

  private void setVehicleModels(Set<VehicleModel> vehicles) {
    this.vehicles = vehicles;
  }

  private void setBlockModels(Set<BlockModel> blocks) {
    this.blocks = blocks;
  }

  /**
   * The supported point types.
   */
  public enum Type {

    /**
     * A halting position.
     */
    HALT(ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.halt.description"),
         ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.halt.helptext")),
    /**
     * A reporting position.
     */
    REPORT(ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.report.description"),
           ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.report.helptext")),
    /**
     * A parking position.
     */
    PARK(ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.park.description"),
         ResourceBundle.getBundle(BUNDLE_PATH).getString("pointModel.type.park.helptext"));

    private final String description;
    private final String helptext;

    Type(String description, String helptext) {
      this.description = requireNonNull(description, "description");
      this.helptext = requireNonNull(helptext, "helptext");
    }

    public String getDescription() {
      return description;
    }

    public String getHelptext() {
      return helptext;
    }
  }
}
