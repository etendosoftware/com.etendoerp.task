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
import org.junit.jupiter.api.BeforeEach;
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

  @Mock private Task mockTask;
  @Mock private OBDal mockDal;

  private RoundRobin action;
  private JSONObject parameters;
  private MutableBoolean isStopped;

  /**
   * Sets up the test environment before each test case.
   * Initializes the RoundRobin action, parameters, and mutable boolean for stopping condition.
   */
  @BeforeEach
  void setUp() {
    action = new RoundRobin();
    parameters = new JSONObject();
    isStopped = new MutableBoolean(false);
  }

  /**
   * Verifies that the action method assigns a user to a task when a valid task ID is provided.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionSuccessWithTaskIdParameter() throws Exception {
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);

    ActionResult result = executeActionWithTaskLookup(mockTask, SUCCESS_MESSAGE, "ETASK_UserAssignedToTask");

    assertEquals(Result.Type.SUCCESS, result.getType());
    assertEquals(SUCCESS_MESSAGE, result.getMessage());
  }

  /**
   * Verifies that the action method returns an error when no task is found for the provided ID.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionErrorWhenTaskNotFoundById() throws Exception {
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);

    ActionResult result = executeActionWithTaskLookup(null, NO_TASK_ERROR, "ETASK_NoTaskFound");

    assertEquals(Result.Type.ERROR, result.getType());
    assertEquals(NO_TASK_ERROR, result.getMessage());
  }

  /**
   * Verifies that the action method returns an error when JSON parsing fails.
   */
  @Test
  public void testActionErrorWhenJSONParsingFails() {
    JSONObject faultyParameters = mock(JSONObject.class);
    when(faultyParameters.has(anyString())).thenThrow(new RuntimeException("JSON parsing error"));

    ActionResult result = action.action(faultyParameters, isStopped);

    assertEquals(Result.Type.ERROR, result.getType());
    assertEquals("JSON parsing error", result.getMessage());
  }

  /**
   * Verifies that getInputClass returns Task.class as the expected input type.
   */
  @Test
  public void testGetInputClassReturnsTaskClass() {
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
    parameters.put(TaskConstants.TASK_ID_PROPERTY, TASK_ID);

    ActionResult result = executeActionWithTaskLookup(null, NO_TASK_ERROR, "ETASK_NoTaskFound");

    assertEquals(Result.Type.ERROR, result.getType());
    assertEquals(NO_TASK_ERROR, result.getMessage());
  }

  /**
   * Executes the action with mocked task lookup and message utilities.
   *
   * @param taskToReturn The task object to return from DAL lookup (null for not found scenarios)
   * @param expectedMessage The message to return from OBMessageUtils
   * @param messageKey The message key to use for OBMessageUtils lookup
   * @return The ActionResult from executing the action
   */
  private ActionResult executeActionWithTaskLookup(Task taskToReturn, String expectedMessage, String messageKey) {

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class);
         MockedStatic<RoundRobinHelper> helperStatic = mockStatic(RoundRobinHelper.class);
         MockedConstruction<RoundRobinStrategy> strategyConstruction = mockConstruction(RoundRobinStrategy.class)) {

      setupBasicMocks(obDalStatic, msgUtils, taskToReturn, expectedMessage, messageKey);

      if (taskToReturn != null) {
        helperStatic.when(() -> RoundRobinHelper.assignUsers(any(List.class), any())).thenAnswer(inv -> null);
      }

      return action.action(parameters, isStopped);
    }
  }

  /**
   * Sets up the basic mocks for OBDal and OBMessageUtils.
   */
  private void setupBasicMocks(MockedStatic<OBDal> obDalStatic, MockedStatic<OBMessageUtils> msgUtils,
      Task taskToReturn, String expectedMessage, String messageKey) {
    obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);
    when(mockDal.get(Task.class, TASK_ID)).thenReturn(taskToReturn);
    msgUtils.when(() -> OBMessageUtils.messageBD(messageKey)).thenReturn(expectedMessage);
  }
}
