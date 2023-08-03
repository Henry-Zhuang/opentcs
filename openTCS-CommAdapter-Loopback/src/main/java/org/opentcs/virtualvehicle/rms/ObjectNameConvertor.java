package org.opentcs.virtualvehicle.rms;

import org.opentcs.data.TCSObject;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObjectNameConvertor {
  private static final String NAME_REGEXP_FORMAT = "%s-([0-9]+)";
  private static final String NAME_FORMAT = "%s-%05d";

  public static <T extends TCSObject<T>> Integer toObjectId(@Nonnull Class<T> objectClass, String objectName) {
    if (objectName != null) {
      String regexp = String.format(NAME_REGEXP_FORMAT, objectClass.getSimpleName());
      Pattern pattern = Pattern.compile(regexp);
      Matcher matcher = pattern.matcher(objectName);
      if (matcher.find()) {
        return Integer.parseInt(matcher.group(1));
      }
    }
    return null;
  }

  public static <T extends TCSObject<T>> String toObjectName(@Nonnull Class<T> objectClass, int objectId) {
    return String.format(NAME_FORMAT, objectClass.getSimpleName(), objectId);
  }

}
