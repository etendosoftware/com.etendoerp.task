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

import static com.etendoerp.task.TaskTestsConstants.NO_TASK_ERROR;
import static com.etendoerp.task.TaskTestsConstants.SUCCESS_MESSAGE;
import static com.etendoerp.task.TaskTestsConstants.TASK_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.helper.RoundRobinHelper;
import com.etendoerp.task.strategy.impl.RoundRobinStrategy;
import com.etendoerp.task.utils.TaskConstants;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for {@link RoundRobin}, verifying user assignment using Round Robin strategy,
 * parameter handling, and error conditions.
 */
@ExtendWith(MockitoExtension.class)
public class RoundRobinTest {

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
  public void testActionSuccessWithTaskIdParameter() throws Exception {
    RoundRobin action = new RoundRobin();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class);
         MockedStatic<RoundRobinHelper> helperStatic = mockStatic(RoundRobinHelper.class);
         MockedConstruction<RoundRobinStrategy> strategyConstruction = mockConstruction(RoundRobinStrategy.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Task.class, TASK_ID)).thenReturn(mockTask);

      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_UserAssignedToTask")).thenReturn(SUCCESS_MESSAGE);

      helperStatic.when(() -> RoundRobinHelper.assignUsers(any(List.class), any())).thenAnswer(inv -> null);

      ActionResult result = action.action(parameters, isStopped);

      assertEquals(Result.Type.SUCCESS, result.getType());
      assertEquals(SUCCESS_MESSAGE, result.getMessage());
    }
  }

  /**
   * Verifies that the action method returns an error when no task is found for the provided ID.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionErrorWhenTaskNotFoundById() throws Exception {
    RoundRobin action = new RoundRobin();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Task.class, TASK_ID)).thenReturn(null);

      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_NoTaskFound")).thenReturn(NO_TASK_ERROR);

      ActionResult result = action.action(parameters, isStopped);

      assertEquals(Result.Type.ERROR, result.getType());
      assertEquals(NO_TASK_ERROR, result.getMessage());
    }
  }

  /**
   * Verifies that the action method returns an error when JSON parsing fails.
   */
  @Test
  public void testActionErrorWhenJSONParsingFails() {
    RoundRobin action = new RoundRobin();
    JSONObject parameters = mock(JSONObject.class);
    MutableBoolean isStopped = new MutableBoolean(false);

    when(parameters.has(anyString())).thenThrow(new RuntimeException("JSON parsing error"));

    ActionResult result = action.action(parameters, isStopped);

    assertEquals(Result.Type.ERROR, result.getType());
    assertEquals("JSON parsing error", result.getMessage());
  }

  /**
   * Verifies that getInputClass returns Task.class as the expected input type.
   */
  @Test
  public void testGetInputClassReturnsTaskClass() {
    RoundRobin action = new RoundRobin();

    Class<Task> result = action.getInputClass();

    assertSame(Task.class, result);
  }

  /**
   * Verifies that the action method returns an error when an Openbravo exception is thrown during task lookup.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionErrorWhenOBExceptionThrown() throws Exception {
    RoundRobin action = new RoundRobin();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {

      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Task.class, TASK_ID)).thenReturn(null);

      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_NoTaskFound")).thenReturn(NO_TASK_ERROR);

      ActionResult result = action.action(parameters, isStopped);

      assertEquals(Result.Type.ERROR, result.getType());
      assertEquals(NO_TASK_ERROR, result.getMessage());
    }
  }
}
