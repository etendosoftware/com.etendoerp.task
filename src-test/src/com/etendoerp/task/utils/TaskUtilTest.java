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

import static com.etendoerp.task.TaskTestsConstants.CLIENT_123;
import static com.etendoerp.task.TaskTestsConstants.ORG_ID;
import static com.etendoerp.task.TaskTestsConstants.TASK_TYPE_123;
import static com.etendoerp.task.TaskTestsConstants.TEST_PROCESS;
import static com.etendoerp.task.TaskTestsConstants.TEST_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.task.data.Events;
import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.data.TaskTypeInfo;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;
import com.smf.jobs.model.Job;

/**
 * Unit tests for {@link TaskUtil}.
 * <p>
 * These tests cover filter validation, advanced logic, parameter retrieval and normalization,
 * task creation, state handling, matching rules, and utilities related to task management.
 * <p>
 * The test methods may throw {@link Exception} or {@link org.openbravo.base.exception.OBException}
 * in case of logic errors, data access issues, or parameter validation failures.
 */
@ExtendWith(MockitoExtension.class)
public class TaskUtilTest {

  @Mock
  private OBDal mockDal;

  @Mock
  private OBCriteria<User> mockUserCriteria;

  @Mock
  private OBCriteria<Task> mockTaskCriteria;

  @Mock
  private OBCriteria<Status> mockStatusCriteria;

  @Mock
  private OBCriteria<State> mockStateCriteria;

  @Mock
  private OBCriteria<Table> mockTableCriteria;

  @Mock
  private OBCriteria<org.openbravo.model.ad.datamodel.Table> mockADTableCriteria;

  @Mock
  private OBCriteria<org.openbravo.model.ad.domain.List> mockListCriteria;

  @Mock
  private User mockUser;

  @Mock
  private Task mockTask;

  @Mock
  private Status mockStatus;

  @Mock
  private State mockState;

  @Mock
  private Table mockTable;

  @Mock
  private TaskType mockTaskType;

  @Mock
  private TaskTypeInfo mockTaskTypeInfo;

  @Mock
  private org.openbravo.model.ad.datamodel.Table mockADTable;

  @Mock
  private Reference mockReference;

  @Mock
  private org.openbravo.model.ad.domain.List mockList;

  @Mock
  private Client mockClient;

  @Mock
  private Organization mockOrganization;

  @Mock
  private OBProvider mockProvider;

  @Mock
  private Process mockProcess;

  @Mock
  private OBCriteria<Events> mockEventsCriteria;

  @Mock
  private OBCriteria<TaskTypeInfo> mockTaskTypeInfoCriteria;

  /**
   * Helper method to setup common OBDal mock behavior.
   */
  private MockedStatic<OBDal> setupOBDalMock() {
    MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
    dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
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
   * Helper method to test round-robin index update with expected index value.
   *
   * @param initialIndex
   *     the input index value
   * @param size
   *     the size of the round-robin list
   * @param expectedFinalIndex
   *     the expected final index value after normalization
   */
  private void testRoundRobinIndexUpdate(int initialIndex, int size, long expectedFinalIndex) {
    String taskTypeId = TASK_TYPE_123;

    try (MockedStatic<OBDal> dalStatic = setupOBDalMock(); MockedStatic<OBContext> contextStatic = mockStatic(
        OBContext.class); MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {

      OBContext mockCurrentContext = mock(OBContext.class);
      contextStatic.when(OBContext::getOBContext).thenReturn(mockCurrentContext);
      when(mockDal.get(TaskType.class, taskTypeId)).thenReturn(mockTaskType);

      // Mock getTaskTypeInfo to return existing TaskTypeInfo
      taskUtilStatic.when(() -> TaskUtil.getTaskTypeInfo(mockTaskType)).thenReturn(mockTaskTypeInfo);

      // Call the real method for updateRoundRobinIndex
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(taskTypeId, initialIndex, size)).thenCallRealMethod();

      TaskUtil.updateRoundRobinIndex(taskTypeId, initialIndex, size);

      verify(mockTaskTypeInfo).setRoundRobinIndex(expectedFinalIndex);
      verify(mockDal).save(mockTaskTypeInfo);
      verify(mockDal).flush();
    }
  }

  /**
   * Helper method to test task creation with configurable auto-assignment.
   *
   * @param assignOperatorAutomatically
   *     whether to assign operator automatically
   * @throws Exception
   *     if there's an error during task creation
   */
  private void testTaskCreationWithAssignment(boolean assignOperatorAutomatically) throws Exception {
    JSONObject parameters = createBasicTaskData();
    OBContext mockEntityContext = mock(OBContext.class);

    try (MockedStatic<OBContext> contextStatic = mockStatic(
        OBContext.class); MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<OBProvider> providerStatic = mockStatic(
        OBProvider.class); MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {

      providerStatic.when(OBProvider::getInstance).thenReturn(mockProvider);
      contextStatic.when(OBContext::getOBContext).thenReturn(mockEntityContext);
      when(mockEntityContext.getUser()).thenReturn(mockUser);

      setupTaskCreationMocks();

      taskUtilStatic.when(
          () -> TaskUtil.createTask(any(TaskType.class), any(Status.class), anyBoolean(), any(JSONObject.class),
              any(OBContext.class))).thenCallRealMethod();

      if (assignOperatorAutomatically) {
        taskUtilStatic.when(() -> TaskUtil.setTaskUser(any(Task.class))).then(invocation -> null);
      }

      Task result = TaskUtil.createTask(mockTaskType, mockStatus, assignOperatorAutomatically, parameters,
          mockEntityContext);

      assertEquals(mockTask, result);
      verifyTaskCreation();
      verify(mockDal).save(mockTask);
      verify(mockDal).flush();

      if (assignOperatorAutomatically) {
        taskUtilStatic.verify(() -> TaskUtil.setTaskUser(mockTask));
      } else {
        taskUtilStatic.verify(() -> TaskUtil.setTaskUser(any(Task.class)), never());
      }
    }
  }

  /**
   * Helper method to setup criteria mock with common behavior.
   */
  private <T extends org.openbravo.base.structure.BaseOBObject> void setupCriteriaMock(OBCriteria<T> criteria,
      T result) {
    when(criteria.setMaxResults(1)).thenReturn(criteria);
    when(criteria.uniqueResult()).thenReturn(result);
  }

  /**
   * Helper method to setup criteria mock with list result.
   */
  private <T extends org.openbravo.base.structure.BaseOBObject> void setupCriteriaListMock(OBCriteria<T> criteria,
      List<T> result) {
    when(criteria.list()).thenReturn(result);
  }

  /**
   * Helper method to setup criteria mock with ordering.
   */
  private <T extends org.openbravo.base.structure.BaseOBObject> void setupCriteriaWithOrdering(OBCriteria<T> criteria,
      String property, boolean ascending) {
    when(criteria.addOrderBy(property, ascending)).thenReturn(criteria);
  }

  /**
   * Helper method to create a JSONObject with client and org data.
   */
  private JSONObject createBasicTaskData() throws JSONException {
    JSONObject data = new JSONObject();
    data.put(TaskConstants.AD_CLIENT_ATTR, CLIENT_123);
    data.put(TaskConstants.AD_ORG_ATTR, ORG_ID);
    return data;
  }

  /**
   * Helper method to setup common mocks for task creation.
   */
  private void setupTaskCreationMocks() {
    when(mockProvider.get(Task.class)).thenReturn(mockTask);
    when(mockDal.get(Client.class, CLIENT_123)).thenReturn(mockClient);
    when(mockDal.get(Organization.class, ORG_ID)).thenReturn(mockOrganization);
  }

  /**
   * Helper method to verify common task creation behavior.
   */
  private void verifyTaskCreation() {
    verify(mockTask).setTaskType(mockTaskType);
    verify(mockTask).setStatus(mockStatus);
    verify(mockTask).setClient(mockClient);
    verify(mockTask).setOrganization(mockOrganization);
  }

  /**
   * Helper method to assert OBException with message content.
   */
  private void assertOBExceptionContains(String expectedContent, Runnable action) {
    OBException exception = assertThrows(OBException.class, action::run);
    assertTrue(exception.getMessage().contains(expectedContent));
  }

  /**
   * Tests the validation of a filter with a valid expression that evaluates to true.
   * This should return true when the filter condition is met by the provided data.
   *
   * @throws Exception
   *     if there's an error during filter validation
   */
  @Test
  public void testValidateFilterWithValidFilter() throws Exception {
    String filter = "age > 18";
    JSONObject data = new JSONObject().put("age", 25);

    boolean result = TaskUtil.validateFilter(filter, data);

    assertTrue(result);
  }

  /**
   * Tests the validation of a filter that results in false.
   * This should return false if the filter evaluates to false.
   */
  @Test
  public void testValidateFilterWithInvalidFilter() {
    String filter = "age >>";
    JSONObject data = new JSONObject();

    boolean result = TaskUtil.validateFilter(filter, data);

    assertFalse(result);
  }

  /**
   * Tests the validation of a filter that returns a non-boolean result.
   * This should return false when the filter expression evaluates to a non-boolean value.
   *
   * @throws Exception
   *     if there's an error during filter evaluation
   */
  @Test
  public void testValidateFilterWithNonBooleanResult() throws Exception {
    String filter = "age + 5";
    JSONObject data = new JSONObject().put("age", 25);

    boolean result = TaskUtil.validateFilter(filter, data);

    assertFalse(result);
  }

  /**
   * Tests the execution of advanced logic with a valid action.
   * This should return true if the action is executed successfully.
   */
  @Test
  public void testExecuteAdvancedLogicWithNullAction() {
    when(mockTable.getAction()).thenReturn(null);

    boolean result = TaskUtil.executeAdvancedLogic(mockTable, new JSONObject());

    assertTrue(result);
  }

  /**
   * Tests the retrieval of active users.
   * This should return a list of active users.
   */
  @Test
  public void testGetActiveUsers() {
    List<User> expectedUsers = List.of(mockUser);
    String orgId = "org-id";
    String clientId = "client-id";

    OBContext mockContext = mock(OBContext.class);
    org.openbravo.dal.security.OrganizationStructureProvider ospMock = mock(
        org.openbravo.dal.security.OrganizationStructureProvider.class);

    try (MockedStatic<OBDal> ignoredDal = setupOBDalMock(); MockedStatic<OBContext> contextStatic = mockStatic(
        OBContext.class)) {

      contextStatic.when(OBContext::getOBContext).thenReturn(mockContext);

      when(mockContext.getCurrentOrganization()).thenReturn(mockOrganization);
      when(mockContext.getCurrentClient()).thenReturn(mockClient);
      when(mockOrganization.getId()).thenReturn(orgId);
      when(mockClient.getId()).thenReturn(clientId);
      when(mockContext.getOrganizationStructureProvider(clientId)).thenReturn(ospMock);
      when(ospMock.getNaturalTree(orgId)).thenReturn(Set.of(orgId));

      when(mockDal.createCriteria(User.class)).thenReturn(mockUserCriteria);
      setupCriteriaWithOrdering(mockUserCriteria, User.PROPERTY_USERNAME, true);
      setupCriteriaListMock(mockUserCriteria, expectedUsers);

      List<User> result = TaskUtil.getActiveUsers();

      assertEquals(expectedUsers, result);
      contextStatic.verify(OBContext::getOBContext);
    }
  }

  /**
   * Tests the preloading of tasks for given users.
   * This should return a list of tasks assigned to the users.
   */
  @Test
  public void testPreloadTasks() {
    List<User> users = List.of(mockUser);
    List<Task> expectedTasks = List.of(mockTask);

    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<Restrictions> restrictionsStatic = mockStatic(
        Restrictions.class)) {

      when(mockDal.createCriteria(Task.class)).thenReturn(mockTaskCriteria);
      setupCriteriaListMock(mockTaskCriteria, expectedTasks);

      List<Task> result = TaskUtil.preloadTasks(users);

      assertEquals(expectedTasks, result);
      restrictionsStatic.verify(() -> Restrictions.in(Task.PROPERTY_ASSIGNEDUSER, users));
    }
  }

  /**
   * Tests the retrieval of a status by identifier when found.
   * This should return the status if it exists.
   */
  @Test
  public void testGetStatusFound() {
    String identifier = "OPEN";

    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<Restrictions> restrictionsStatic = mockStatic(
        Restrictions.class)) {

      when(mockDal.createCriteria(Status.class)).thenReturn(mockStatusCriteria);
      setupCriteriaMock(mockStatusCriteria, mockStatus);

      Status result = TaskUtil.getStatus(identifier);

      assertEquals(mockStatus, result);
      restrictionsStatic.verify(() -> Restrictions.eq(Status.PROPERTY_SEARCHKEY, identifier));
    }
  }

  /**
   * Tests the retrieval of a status by its identifier when the status is not found.
   * This should return null if no status matches the identifier.
   */
  @Test
  public void testGetStatusNotFound() {
    String identifier = "UNKNOWN";

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.createCriteria(Status.class)).thenReturn(mockStatusCriteria);
      setupCriteriaMock(mockStatusCriteria, null);

      Status result = TaskUtil.getStatus(identifier);

      assertNull(result);
    }
  }

  /**
   * Tests the validation and normalization of parameters with valid input data.
   * This should successfully validate and normalize the parameters, setting the
   * appropriate table name and verb based on the operation.
   *
   * @throws Exception
   *     if there's an error during parameter validation or normalization
   */
  @Test
  public void testValidateAndNormalizeParametersValid() throws Exception {
    JSONObject parameters = new JSONObject();
    JSONObject source = new JSONObject().put(TaskConstants.TABLE, TEST_TABLE);
    parameters.put(TaskConstants.SOURCE, source);
    parameters.put(TaskConstants.OPERATION, "c");
    parameters.put(TaskConstants.AFTER, new JSONObject().put("id", "123"));

    JSONObject result = TaskUtil.validateAndNormalizeParameters(parameters);

    assertEquals("test_table", result.getString(TaskConstants.TABLE));
    assertEquals(TaskConstants.TABLE_CREATE, result.getString(TaskConstants.VERB));
    assertTrue(result.has(TaskConstants.AFTER));
  }

  /**
   * Tests the validation and normalization of parameters when the source is missing.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testValidateAndNormalizeParametersMissingSource() {
    JSONObject parameters = new JSONObject();

    try (MockedStatic<OBMessageUtils> ignored = setupMessageUtilsMock("ETASK_MissingSource", "Missing source")) {
      assertOBExceptionContains("Missing source", () -> {
        try {
          TaskUtil.validateAndNormalizeParameters(parameters);
        } catch (JSONException e) {
          throw new TestException(e);
        }
      });
    }
  }

  /**
   * Tests the validation and normalization of parameters with an invalid operation.
   * This should throw an OBException when an unsupported database operation is provided.
   *
   * @throws Exception
   *     if there's an error during parameter validation
   * @throws OBException
   *     when an invalid operation is specified
   */
  @Test
  public void testValidateAndNormalizeParametersInvalidOperation() throws Exception {
    JSONObject parameters = new JSONObject();
    JSONObject source = new JSONObject().put(TaskConstants.TABLE, TEST_TABLE);
    parameters.put(TaskConstants.SOURCE, source);
    parameters.put(TaskConstants.OPERATION, "x");
    parameters.put(TaskConstants.AFTER, new JSONObject());

    try (MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETASK_InvalidDatabaseOperation",
        "Invalid operation: %s")) {
      assertOBExceptionContains("x", () -> {
        try {
          TaskUtil.validateAndNormalizeParameters(parameters);
        } catch (JSONException e) {
          throw new TestException(e);
        }
      });
    }
  }

  /**
   * Tests the creation of a task with valid input parameters.
   * This should successfully create a new Task object with the provided data,
   * setting all required fields including client, organization, and user references.
   *
   * @throws Exception
   *     if there's an error during task creation or data access
   */
  @Test
  public void testCreateTask() throws Exception {
    JSONObject data = createBasicTaskData();
    JSONObject rawEvent = new JSONObject().put("event", "test");
    when(mockDal.get(User.class, TaskConstants.ADMIN_USER)).thenReturn(mockUser);
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockState.getTaskStatus()).thenReturn(mockStatus);

    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<OBProvider> providerStatic = mockStatic(
        OBProvider.class)) {

      providerStatic.when(OBProvider::getInstance).thenReturn(mockProvider);
      setupTaskCreationMocks();

      Task result = TaskUtil.createTask(mockTable, mockState, data, rawEvent, true);

      assertEquals(mockTask, result);
      verifyTaskCreation();
      verify(mockTask).setCreatedAutomatically(true);
      verify(mockTask).setEventJsoninfo(rawEvent.toString());
    }
  }

  /**
   * Tests the creation of a task when the client attribute is missing from the data.
   * This should throw an OBException indicating that the required client attribute
   * is missing from the event data.
   *
   * @throws Exception
   *     if there's an error during task creation validation
   * @throws OBException
   *     when the required client attribute is missing
   */
  @Test
  public void testCreateTaskMissingClient() throws Exception {
    JSONObject data = new JSONObject();
    data.put(TaskConstants.AD_ORG_ATTR, ORG_ID);

    try (MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETASK_MissingAttributeInEventData",
        "Missing attribute: %s")) {
      assertOBExceptionContains(TaskConstants.AD_CLIENT_ATTR,
          () -> TaskUtil.createTask(mockTable, mockState, data, null, false));
    }
  }

  /**
   * Tests the retrieval of initial state when found.
   * This should return the initial state for the task type.
   */
  @Test
  public void testGetInitialStateFound() {
    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<Restrictions> restrictionsStatic = mockStatic(
        Restrictions.class)) {

      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      setupCriteriaWithOrdering(mockStateCriteria, State.PROPERTY_SEQUENCENO, true);
      setupCriteriaMock(mockStateCriteria, mockState);

      State result = TaskUtil.getInitialState(mockTaskType);

      assertEquals(mockState, result);
      restrictionsStatic.verify(() -> Restrictions.eq(State.PROPERTY_TASKTYPE, mockTaskType));
    }
  }

  /**
   * Tests the retrieval of the initial state when no initial state is found.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testGetInitialStateNotFound() {
    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<OBMessageUtils> ignored1 = setupMessageUtilsMock(
        "ETASK_NoInitialState", "No initial state")) {

      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      setupCriteriaWithOrdering(mockStateCriteria, State.PROPERTY_SEQUENCENO, true);
      setupCriteriaMock(mockStateCriteria, null);

      assertOBExceptionContains("No initial state", () -> TaskUtil.getInitialState(mockTaskType));
    }
  }

  /**
   * Tests the retrieval of an AD Table by its name.
   * This should return the AD Table if found.
   */
  @Test
  public void testGetADTableFound() {
    String tableName = TEST_TABLE;

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.createCriteria(org.openbravo.model.ad.datamodel.Table.class)).thenReturn(mockADTableCriteria);
      setupCriteriaMock(mockADTableCriteria, mockADTable);

      org.openbravo.model.ad.datamodel.Table result = TaskUtil.getADTable(tableName);

      assertEquals(mockADTable, result);
    }
  }

  /**
   * Tests the retrieval of event value when found.
   * This should return the event value for the verb.
   */
  @Test
  public void testGetEventValueFound() {
    String verb = "CREATE";

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.get(Reference.class, TaskConstants.TABLE_EVENTS_REF)).thenReturn(mockReference);
      when(mockDal.createCriteria(org.openbravo.model.ad.domain.List.class)).thenReturn(mockListCriteria);
      setupCriteriaMock(mockListCriteria, mockList);
      when(mockList.getSearchKey()).thenReturn("C");

      String result = TaskUtil.getEventValue(verb);

      assertEquals("C", result);
    }
  }

  /**
   * Tests the retrieval of matching rules.
   * This should return a list of matching table rules.
   */
  @Test
  public void testGetMatchingRules() {
    String eventIdentifier = "CREATE";
    List<Table> expectedRules = List.of(mockTable);

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.createCriteria(Table.class)).thenReturn(mockTableCriteria);
      setupCriteriaListMock(mockTableCriteria, expectedRules);

      List<Table> result = TaskUtil.getMatchingRules(mockADTable, eventIdentifier);

      assertEquals(expectedRules, result);
    }
  }

  /**
   * Tests finding state by status ID.
   * This should return the state for the given status and task type.
   */
  @Test
  public void testFindStateByStatusId() {
    String statusId = "status123";
    String taskTypeId = TASK_TYPE_123;

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.get(TaskType.class, taskTypeId)).thenReturn(mockTaskType);
      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.createAlias(State.PROPERTY_TASKSTATUS, "st")).thenReturn(mockStateCriteria);
      setupCriteriaMock(mockStateCriteria, mockState);

      State result = TaskUtil.findStateByStatusId(statusId, taskTypeId);

      assertEquals(mockState, result);
    }
  }

  /**
   * Tests the execution of an action process when the class name is blank.
   * This should throw an OBException.
   */
  @Test
  public void testRunActionBlankClassName() {
    when(mockProcess.getJavaClassName()).thenReturn("");
    when(mockProcess.getName()).thenReturn(TEST_PROCESS);

    try (MockedStatic<OBMessageUtils> ignored = setupMessageUtilsMock("ETASK_ProcessWithoutClassName",
        "Process %s has no class name")) {
      assertOBExceptionContains(TEST_PROCESS, () -> TaskUtil.runAction(mockProcess, new JSONObject()));
    }
  }

  /**
   * Tests the update of round-robin index with a normal index value.
   * This should successfully update the task type's round-robin index.
   */
  @Test
  public void testUpdateRoundRobinIndexNormal() {
    testRoundRobinIndexUpdate(2, 5, 2L);
  }

  /**
   * Tests the update of round-robin index when the index exceeds the size.
   * This should reset the index to 0.
   */
  @Test
  public void testUpdateRoundRobinIndexOversize() {
    testRoundRobinIndexUpdate(5, 3, 0L);
  }

  /**
   * Tests the creation of a task with automatic operator assignment.
   * This should successfully create a task and assign an operator automatically.
   *
   * @throws Exception
   *     if there's an error during task creation
   */
  @Test
  public void testCreateTaskWithAutoAssignment() throws Exception {
    testTaskCreationWithAssignment(true);
  }

  /**
   * Tests the creation of a task without automatic operator assignment.
   * This should successfully create a task without assigning an operator.
   *
   * @throws Exception
   *     if there's an error during task creation
   */
  @Test
  public void testCreateTaskWithoutAutoAssignment() throws Exception {
    testTaskCreationWithAssignment(false);
  }

  /**
   * Tests the execution of state events with multiple events.
   * This should return a list of initial topics from the jobs associated with the events.
   */
  @Test
  public void testRunStateEventsWithTopics() {
    Events mockEvent1 = mock(Events.class);
    Events mockEvent2 = mock(Events.class);
    Events mockEvent3 = mock(Events.class);

    Job mockJob1 = mock(Job.class);
    Job mockJob2 = mock(Job.class);

    List<Events> eventsList = List.of(mockEvent1, mockEvent2, mockEvent3);

    when(mockEvent1.getJob()).thenReturn(mockJob1);
    when(mockEvent2.getJob()).thenReturn(mockJob2);
    when(mockEvent3.getJob()).thenReturn(null);

    when(mockJob1.getEtapInitialTopic()).thenReturn("topic1");
    when(mockJob2.getEtapInitialTopic()).thenReturn("topic2");

    try (MockedStatic<OBDal> ignored = setupOBDalMock(); MockedStatic<Restrictions> restrictionsStatic = mockStatic(
        Restrictions.class)) {

      when(mockDal.createCriteria(Events.class)).thenReturn(mockEventsCriteria);
      setupCriteriaWithOrdering(mockEventsCriteria, Events.PROPERTY_SEQUENCENO, true);
      setupCriteriaListMock(mockEventsCriteria, eventsList);

      List<String> result = TaskUtil.runStateEvents(mockState);

      assertEquals(2, result.size());
      assertTrue(result.contains("topic1"));
      assertTrue(result.contains("topic2"));
      restrictionsStatic.verify(() -> Restrictions.eq(Events.PROPERTY_STATE, mockState));
    }
  }

  /**
   * Tests the execution of state events when no events have jobs with topics.
   * This should return an empty list.
   */
  @Test
  public void testRunStateEventsEmptyTopics() {
    Events mockEvent = mock(Events.class);
    List<Events> eventsList = List.of(mockEvent);

    when(mockEvent.getJob()).thenReturn(null);

    try (MockedStatic<OBDal> ignored = setupOBDalMock()) {
      when(mockDal.createCriteria(Events.class)).thenReturn(mockEventsCriteria);
      setupCriteriaWithOrdering(mockEventsCriteria, Events.PROPERTY_SEQUENCENO, true);
      setupCriteriaListMock(mockEventsCriteria, eventsList);

      List<String> result = TaskUtil.runStateEvents(mockState);

      assertTrue(result.isEmpty());
    }
  }

  /**
   * Tests the execution of an action process when the class name is null.
   * This should throw an OBException.
   */
  @Test
  public void testRunActionNullClassName() {
    when(mockProcess.getJavaClassName()).thenReturn(null);
    when(mockProcess.getName()).thenReturn(TEST_PROCESS);

    try (MockedStatic<OBMessageUtils> msgUtils = setupMessageUtilsMock("ETASK_ProcessWithoutClassName",
        "Process %s has no class name")) {
      assertOBExceptionContains(TEST_PROCESS, () -> TaskUtil.runAction(mockProcess, new JSONObject()));
    }
  }

  /**
   * Tests setting a task user successfully.
   * This should retrieve the user strategy and assign a user to the task.
   *
   * @throws Exception
   *     if there's an error during user assignment
   */
  @Test
  public void testSetTaskUserSuccess() throws Exception {
    String eventJsonInfo = "{\"param1\":\"value1\"}";

    UserAvailabilityStrategy mockStrategy = mock(UserAvailabilityStrategy.class);
    User mockAssignedUser = mock(User.class);

    when(mockTask.getEventJsoninfo()).thenReturn(eventJsonInfo);
    when(mockTask.getTaskType()).thenReturn(mockTaskType);
    when(mockStrategy.findUserAccordingStrategy(eq(mockTaskType), any(JSONObject.class))).thenReturn(mockAssignedUser);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(
        TaskUtil.class); MockedStatic<OBDal> ignored = setupOBDalMock()) {

      taskUtilStatic.when(() -> TaskUtil.getUserStrategyClass(mockTaskType)).thenReturn(mockStrategy);
      taskUtilStatic.when(() -> TaskUtil.setTaskUser(mockTask)).thenCallRealMethod();

      TaskUtil.setTaskUser(mockTask);

      verify(mockTask).setAssignedUser(mockAssignedUser);
      verify(mockDal).save(mockTask);
      taskUtilStatic.verify(() -> TaskUtil.getUserStrategyClass(mockTaskType));
    }
  }

  /**
   * Tests the validation of a blank (null/empty) filter.
   * A blank filter should be treated as true so the rule
   * always matches.
   *
   * @throws Exception
   *     if there's an error during filter validation
   */
  @Test
  void testValidateFilterWithBlankFilterReturnsTrue() throws Exception {
    String filter = null;
    JSONObject data = new JSONObject().put("anyField", "anyValue");

    boolean result = TaskUtil.validateFilter(filter, data);

    assertTrue(result);
  }

  /**
   * Custom test exception for wrapping JSON exceptions in tests.
   */
  private static class TestException extends RuntimeException {
    public TestException(Throwable cause) {
      super(cause);
    }
  }
}
