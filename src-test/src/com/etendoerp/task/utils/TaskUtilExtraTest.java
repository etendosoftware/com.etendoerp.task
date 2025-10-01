/*************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF  ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and  limitations under the License.
 * All portions are Copyright (C) 2021-2025 Futit Services S.L.
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 ************************************************************************/
package com.etendoerp.task.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Small split-out tests moved from TaskUtilTest to reduce class size for Sonar.
 */
@ExtendWith(MockitoExtension.class)
public class TaskUtilExtraTest {

  /**
   * Tests the validation of a filter that results in false.
   * This should return false if the filter evaluates to false.
   */
  @Test
  void testValidateFilterWithInvalidFilter() {
    String filter = "age >>";
    JSONObject data = new JSONObject();

    boolean result = TaskUtil.validateFilter(filter, data);

    assertFalse(result);
  }

}
