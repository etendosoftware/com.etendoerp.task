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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;
import org.openbravo.model.ad.access.User;
import org.openbravo.client.application.Process;

/**
 * Split-out tests for action-related behavior in TaskUtil.
 */
@ExtendWith(MockitoExtension.class)
public class TaskUtilActionTest {

  @Mock
  private Process mockProcess;

  @Mock
  private org.openbravo.dal.service.OBDal mockDal;

  @Mock
  private com.etendoerp.task.data.Task mockTask;

  @Mock
  private com.etendoerp.task.data.TaskType mockTaskType;

  @Mock
  private org.openbravo.base.provider.OBProvider mockProvider;

  @Mock
  private User mockUser;

  // Common test constants to avoid duplicated literals flagged by static analysis
  private static final String TEST_PROCESS_NAME = "TestProcess";
  private static final String TASK_TYPE_ID = "TASK_TYPE_123";

  /**
   * Helper method to setup common OBDal mock behavior.
   */
  private MockedStatic<org.openbravo.dal.service.OBDal> setupOBDalMock() {
    MockedStatic<org.openbravo.dal.service.OBDal> dalStatic = mockStatic(org.openbravo.dal.service.OBDal.class);
    dalStatic.when(org.openbravo.dal.service.OBDal::getInstance).thenReturn(mockDal);
    return dalStatic;
  }

  /**
   * Helper method to setup common OBMessageUtils mock behavior for exceptions.
   */
  private MockedStatic<OBMessageUtils> setupMessageUtilsMock(String messageKey, String messageValue) {
    MockedStatic<OBMessageUtils> mockStatic = mockStatic(OBMessageUtils.class);
    mockStatic.when(() -> OBMessageUtils.messageBD(messageKey)).thenReturn(messageValue);
    mockStatic.when(() -> OBMessageUtils.getI18NMessage(messageKey)).thenReturn(messageValue);
    return mockStatic;
  }

  /**
   * Tests that running an action with a blank Java class name for the process
   * throws an OBException containing the process name.
   */
  @Test
  public void testRunActionBlankClassName() {
    when(mockProcess.getJavaClassName()).thenReturn("");
  when(mockProcess.getName()).thenReturn(TEST_PROCESS_NAME);

    try (MockedStatic<OBMessageUtils> ignored = setupMessageUtilsMock("ETASK_ProcessWithoutClassName",
        "Process %s has no class name")) {
      org.openbravo.base.exception.OBException ex = org.junit.jupiter.api.Assertions.assertThrows(
          org.openbravo.base.exception.OBException.class,
          () -> TaskUtil.runAction(mockProcess, new JSONObject())
      );
  org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains(TEST_PROCESS_NAME));
    }
  }

  /**
   * Tests that running an action with a null Java class name for the process
   * throws an OBException containing the process name.
   */
  @Test
  public void testRunActionNullClassName() {
    when(mockProcess.getJavaClassName()).thenReturn(null);
  when(mockProcess.getName()).thenReturn(TEST_PROCESS_NAME);

    try (MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETASK_ProcessWithoutClassName",
        "Process %s has no class name")) {
      org.openbravo.base.exception.OBException ex = org.junit.jupiter.api.Assertions.assertThrows(
          org.openbravo.base.exception.OBException.class,
          () -> TaskUtil.runAction(mockProcess, new JSONObject())
      );
  org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains(TEST_PROCESS_NAME));
    }
  }

  /**
   * Tests that an action class that returns a JSON message is handled and
   * reported as a successful outcome with a JSON message present.
   *
   * @throws Exception when the action execution fails unexpectedly
   */
  @Test
  public void testRunActionReturnsJsonMessage() throws Exception {
    when(mockProcess.getJavaClassName()).thenReturn("com.smf.jobs.TestActionJson");

    TaskUtil.ActionOutcome outcome = TaskUtil.runAction(mockProcess, new JSONObject("{}"));

    assertTrue(outcome.success);
    assertTrue(outcome.message != null && outcome.message.has("ok"));
  }

  /**
   * Tests that an action class that returns a plain text result is handled as
   * successful and that no JSON message is attached to the outcome.
   *
   * @throws Exception when the action execution fails unexpectedly
   */
  @Test
  public void testRunActionReturnsPlainTextMessage() throws Exception {
    when(mockProcess.getJavaClassName()).thenReturn("com.smf.jobs.TestActionPlain");

    TaskUtil.ActionOutcome outcome = TaskUtil.runAction(mockProcess, new JSONObject("{}"));

    assertTrue(outcome.success);
    assertNull(outcome.message);
  }

  /**
   * Tests that when the configured process class is not an Action implementation
   * an OBException is thrown and the exception message contains the process name.
   */
  @Test
  public void testRunActionClassIsNotActionThrows() {
    when(mockProcess.getJavaClassName()).thenReturn("com.smf.jobs.NotAnAction");
    when(mockProcess.getName()).thenReturn("NotAnActionProcess");

    try (MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETASK_ProcessIsNotAction",
        "Process %s is not an Action")) {
      org.openbravo.base.exception.OBException ex = org.junit.jupiter.api.Assertions.assertThrows(
          org.openbravo.base.exception.OBException.class,
          () -> TaskUtil.runAction(mockProcess, new JSONObject())
      );
      org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("NotAnActionProcess"));
    }
  }

  /**
   * Tests that TaskUtil.setTaskUser successfully assigns a user according to
   * the configured strategy and persists the assigned user on the task.
   *
   * @throws Exception when the underlying strategy or persistence fails
   */
  @Test
  public void testSetTaskUserSuccess() throws Exception {
    String eventJsonInfo = "{\"param1\":\"value1\"}";

    UserAvailabilityStrategy mockStrategy = mock(UserAvailabilityStrategy.class);
    User mockAssignedUser = mock(User.class);

    when(mockTask.getEventJsoninfo()).thenReturn(eventJsonInfo);
    when(mockTask.getTaskType()).thenReturn(mockTaskType);
    when(mockStrategy.findUserAccordingStrategy(org.mockito.ArgumentMatchers.eq(mockTaskType), org.mockito.ArgumentMatchers.any(JSONObject.class)))
        .thenReturn(mockAssignedUser);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class);
         MockedStatic<org.openbravo.dal.service.OBDal> ignored = setupOBDalMock()) {

      taskUtilStatic.when(() -> TaskUtil.getUserStrategyClass(mockTaskType)).thenReturn(mockStrategy);
      taskUtilStatic.when(() -> TaskUtil.setTaskUser(mockTask)).thenCallRealMethod();

      TaskUtil.setTaskUser(mockTask);

      org.mockito.Mockito.verify(mockTask).setAssignedUser(mockAssignedUser);
      org.mockito.Mockito.verify(mockDal).save(mockTask);
      taskUtilStatic.verify(() -> TaskUtil.getUserStrategyClass(mockTaskType));
    }
  }

  /**
   * Tests that requesting the user strategy class for a task type without a
   * configured UserAlgorithm throws an OBException with an appropriate message.
   */
  @Test
  public void testGetUserStrategyClassNoAlgorithm() {
  when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID);

    try (MockedStatic<org.openbravo.dal.service.OBDal> dalStatic = setupOBDalMock();
         MockedStatic<org.openbravo.dal.core.OBContext> contextStatic = mockStatic(org.openbravo.dal.core.OBContext.class);
         MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETAWIM_UserAlgorithmNotFound",
             "User algorithm not found")) {

      org.openbravo.dal.core.OBContext mockCurrentContext = mock(org.openbravo.dal.core.OBContext.class);
      contextStatic.when(org.openbravo.dal.core.OBContext::getOBContext).thenReturn(mockCurrentContext);

  when(mockDal.get(com.etendoerp.task.data.TaskType.class, TASK_TYPE_ID)).thenReturn(mockTaskType);
      when(mockTaskType.getUserAlgorithm()).thenReturn(null);

      org.openbravo.base.exception.OBException ex = org.junit.jupiter.api.Assertions.assertThrows(
          org.openbravo.base.exception.OBException.class,
          () -> TaskUtil.getUserStrategyClass(mockTaskType)
      );
      org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("User algorithm not found"));
    }
  }

  /**
   * Tests that the configured UserAvailabilityStrategy implementation is
   * correctly loaded and instantiated via OBClassLoader when present.
   *
   * @throws Exception if class loading or instantiation fails
   */
  @Test
  public void testGetUserStrategyClassSuccess() throws Exception {
  when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID);

    try (MockedStatic<org.openbravo.dal.service.OBDal> dalStatic = setupOBDalMock();
         MockedStatic<org.openbravo.dal.core.OBContext> contextStatic = mockStatic(org.openbravo.dal.core.OBContext.class);
         MockedStatic<OBClassLoader> classLoaderStatic = mockStatic(OBClassLoader.class)) {

      org.openbravo.dal.core.OBContext mockCurrentContext = mock(org.openbravo.dal.core.OBContext.class);
      contextStatic.when(org.openbravo.dal.core.OBContext::getOBContext).thenReturn(mockCurrentContext);

  when(mockDal.get(com.etendoerp.task.data.TaskType.class, TASK_TYPE_ID)).thenReturn(mockTaskType);
      when(mockTaskType.getUserAlgorithm()).thenReturn(mock(com.etendoerp.task.data.UserAlgorithm.class));

      String impl = TaskUtilActionTest.DummyStrategy.class.getName();
      when(mockTaskType.getUserAlgorithm().getJavaImplementation()).thenReturn(impl);

      OBClassLoader mockLoader = mock(OBClassLoader.class);
      classLoaderStatic.when(OBClassLoader::getInstance).thenReturn(mockLoader);
      when(mockLoader.loadClass(impl)).thenReturn((Class) TaskUtilActionTest.DummyStrategy.class);

      UserAvailabilityStrategy result = TaskUtil.getUserStrategyClass(mockTaskType);

      org.junit.jupiter.api.Assertions.assertEquals(TaskUtilActionTest.DummyStrategy.class, result.getClass());
    }
  }

  /**
   * Dummy strategy used to test dynamic loading and instantiation via OBClassLoader.
   */
  public static class DummyStrategy implements UserAvailabilityStrategy {

    @Override
    public User findUserAccordingStrategy(com.etendoerp.task.data.TaskType taskType, JSONObject parameters) {
      return null;
    }

    @Override
    public java.util.List<User> getUsersAvailable(com.etendoerp.task.data.TaskType taskType, JSONObject parameters) {
      return java.util.List.of();
    }
  }

}
