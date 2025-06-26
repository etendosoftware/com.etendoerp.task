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
package com.etendoerp.task.action;

import static com.etendoerp.task.TaskTestsConstants.NO_TASK_FOUND;
import static com.etendoerp.task.TaskTestsConstants.TASK_ID;
import static com.etendoerp.task.TaskTestsConstants.USER_ASSIGNED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.helper.RoundRobinHelper;
import com.etendoerp.task.utils.TaskConstants;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for {@link RoundRobinByWorkload}, verifying task assignment behavior,
 * parameter handling, and error scenarios.
 */
@ExtendWith(MockitoExtension.class)
public class RoundRobinByWorkloadTest {

  @Mock
  private Task mockTask;

  @Mock
  private OBDal mockDal;


  /**
   * Verifies that the action method assigns a user to a task when a valid task ID is provided.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionSuccessWithTaskId() throws Exception {
    RoundRobinByWorkload action = new RoundRobinByWorkload();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class);
         MockedStatic<RoundRobinHelper> helperStatic = mockStatic(RoundRobinHelper.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Task.class, TASK_ID)).thenReturn(mockTask);

      msgUtils.when(() -> OBMessageUtils.getI18NMessage("ETASK_UserAssignedToTask")).thenReturn(USER_ASSIGNED);

      ActionResult result = action.action(parameters, isStopped);

      assertEquals(Result.Type.SUCCESS, result.getType());
      assertEquals(USER_ASSIGNED, result.getMessage());
      helperStatic.verify(() -> RoundRobinHelper.assignUsers(eq(Collections.singletonList(mockTask)), any()), times(1));
    }
  }

  /**
   * Verifies that the action method returns an error when no task is found for the provided ID.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionErrorWhenTaskNotFound() throws Exception {
    RoundRobinByWorkload action = new RoundRobinByWorkload();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Task.class, TASK_ID)).thenReturn(null);

      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_NoTaskFound")).thenReturn(NO_TASK_FOUND);

      ActionResult result = action.action(parameters, isStopped);

      assertEquals(Result.Type.ERROR, result.getType());
      assertEquals(NO_TASK_FOUND, result.getMessage());
    }
  }

  /**
   * Verifies that the action method returns an error when JSON parsing fails.
   */
  @Test
  public void testActionErrorWhenJsonParsingFails() {
    RoundRobinByWorkload action = new RoundRobinByWorkload();
    JSONObject parameters = mock(JSONObject.class);
    MutableBoolean isStopped = new MutableBoolean(false);

    when(parameters.has(anyString())).thenThrow(new RuntimeException("JSON error"));

    ActionResult result = action.action(parameters, isStopped);

    assertEquals(Result.Type.ERROR, result.getType());
    assertEquals("JSON error", result.getMessage());
  }

  /**
   * Verifies that getInputClass returns Task.class as the expected input type.
   */
  @Test
  public void testGetInputClassReturnsTask() {
    RoundRobinByWorkload action = new RoundRobinByWorkload();
    assertEquals(Task.class, action.getInputClass());
  }

}
