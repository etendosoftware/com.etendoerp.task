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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * Unit tests for {@link TaskUtil}.
 * <p>
 * Estos tests cubren la validación de filtros, lógica avanzada, obtención y normalización de parámetros,
 * creación de tareas, manejo de estados, reglas de matching y utilidades relacionadas con la gestión de tareas.
 * <p>
 * Los métodos de test pueden lanzar {@link Exception} o {@link org.openbravo.base.exception.OBException}
 * en caso de errores de lógica, acceso a datos o validación de parámetros.
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

  /**
   * Tests the validation of a filter with a valid expression that evaluates to true.
   * This should return true when the filter condition is met by the provided data.
   *
   * @throws Exception if there's an error during filter validation
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
   * Tests the execution of advanced logic with a valid action.
   * This should return true if the action is executed successfully.
   */
  @Test
  public void testGetActiveUsers() {
    List<User> expectedUsers = List.of(mockUser);

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<OBContext> contextStatic = mockStatic(OBContext.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(User.class)).thenReturn(mockUserCriteria);
      when(mockUserCriteria.addOrderBy(User.PROPERTY_USERNAME, true)).thenReturn(mockUserCriteria);
      when(mockUserCriteria.list()).thenReturn(expectedUsers);

      List<User> result = TaskUtil.getActiveUsers();

      assertEquals(expectedUsers, result);
      contextStatic.verify(() -> OBContext.setAdminMode(true));
      contextStatic.verify(OBContext::restorePreviousMode);
    }
  }

  /**
   * Tests the retrieval of active users when no users are found.
   * This should return an empty list.
   */
  @Test
  public void testPreloadTasks() {
    List<User> users = List.of(mockUser);
    List<Task> expectedTasks = List.of(mockTask);

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<Restrictions> restrictionsStatic = mockStatic(Restrictions.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(Task.class)).thenReturn(mockTaskCriteria);
      when(mockTaskCriteria.list()).thenReturn(expectedTasks);

      List<Task> result = TaskUtil.preloadTasks(users);

      assertEquals(expectedTasks, result);
      restrictionsStatic.verify(() -> Restrictions.in(Task.PROPERTY_ASSIGNEDUSER, users));
    }
  }

  /**
   * Tests the retrieval of tasks when no tasks are found.
   * This should return an empty list.
   */
  @Test
  public void testGetStatusFound() {
    String identifier = "OPEN";

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<Restrictions> restrictionsStatic = mockStatic(Restrictions.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(Status.class)).thenReturn(mockStatusCriteria);
      when(mockStatusCriteria.setMaxResults(1)).thenReturn(mockStatusCriteria);
      when(mockStatusCriteria.uniqueResult()).thenReturn(mockStatus);

      Status result = TaskUtil.getStatus(identifier);

      assertEquals(mockStatus, result);
      restrictionsStatic.verify(() -> Restrictions.eq(Status.PROPERTY_IDENTIFIER, identifier));
    }
  }

  /**
   * Tests the retrieval of a status by its identifier when the status is not found.
   * This should return null if no status matches the identifier.
   */
  @Test
  public void testGetStatusNotFound() {
    String identifier = "UNKNOWN";

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(Status.class)).thenReturn(mockStatusCriteria);
      when(mockStatusCriteria.setMaxResults(1)).thenReturn(mockStatusCriteria);
      when(mockStatusCriteria.uniqueResult()).thenReturn(null);

      Status result = TaskUtil.getStatus(identifier);

      assertNull(result);
    }
  }
/**
   * Tests the update of the round robin index when the index is within bounds.
   * This simulates a scenario where the index is less than the size.
   */
  @Test
  public void testUpdateRoundRobinIndexNormal() {
    int idx = 2;
    int size = 5;

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      TaskUtil.updateRoundRobinIndex(mockTaskType, idx, size);

      verify(mockTaskType).setRoundRobinIndex(2L);
      verify(mockDal).save(mockTaskType);
      verify(mockDal).flush();
    }
  }

  /**
   * Tests the reset of the round robin index when the index exceeds the size.
   * This simulates a scenario where the index is greater than or equal to the size.
   */
  @Test
  public void testUpdateRoundRobinIndexReset() {
    int idx = 5;
    int size = 5;

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      TaskUtil.updateRoundRobinIndex(mockTaskType, idx, size);

      verify(mockTaskType).setRoundRobinIndex(0L);
      verify(mockDal).save(mockTaskType);
      verify(mockDal).flush();
    }
  }

  @Test
  public void testValidateAndNormalizeParametersValid() throws Exception {
    JSONObject parameters = new JSONObject();
    JSONObject source = new JSONObject().put(TaskConstants.TABLE, "test_table");
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

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.getI18NMessage("ETASK_MissingSource")).thenReturn("Missing source");

      OBException exception = assertThrows(OBException.class,
          () -> TaskUtil.validateAndNormalizeParameters(parameters));
      assertEquals("Missing source", exception.getMessage());
    }
  }

  @Test
  public void testValidateAndNormalizeParametersInvalidOperation() throws Exception {
    JSONObject parameters = new JSONObject();
    JSONObject source = new JSONObject().put(TaskConstants.TABLE, "test_table");
    parameters.put(TaskConstants.SOURCE, source);
    parameters.put(TaskConstants.OPERATION, "x");
    parameters.put(TaskConstants.AFTER, new JSONObject());

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_InvalidDatabaseOperation")).thenReturn(
          "Invalid operation: %s");

      OBException exception = assertThrows(OBException.class,
          () -> TaskUtil.validateAndNormalizeParameters(parameters));
      assertTrue(exception.getMessage().contains("x"));
    }
  }

  @Test
  public void testCreateTask() throws Exception {
    JSONObject data = new JSONObject();
    data.put(TaskConstants.AD_CLIENT_ATTR, "client123");
    data.put(TaskConstants.AD_ORG_ATTR, "org123");
    JSONObject rawEvent = new JSONObject().put("event", "test");

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<OBProvider> providerStatic = mockStatic(OBProvider.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      providerStatic.when(OBProvider::getInstance).thenReturn(mockProvider);

      when(mockProvider.get(Task.class)).thenReturn(mockTask);
      when(mockDal.get(Client.class, "client123")).thenReturn(mockClient);
      when(mockDal.get(Organization.class, "org123")).thenReturn(mockOrganization);
      when(mockDal.get(User.class, TaskConstants.ADMIN_USER)).thenReturn(mockUser);

      when(mockTable.getTaskType()).thenReturn(mockTaskType);
      when(mockState.getTaskStatus()).thenReturn(mockStatus);

      Task result = TaskUtil.createTask(mockTable, mockState, data, rawEvent, true);

      assertEquals(mockTask, result);
      verify(mockTask).setTaskType(mockTaskType);
      verify(mockTask).setStatus(mockStatus);
      verify(mockTask).setCreatedAutomatically(true);
      verify(mockTask).setEventJsoninfo(rawEvent.toString());
      verify(mockTask).setClient(mockClient);
      verify(mockTask).setOrganization(mockOrganization);
    }
  }

  @Test
  public void testCreateTaskMissingClient() throws Exception {
    JSONObject data = new JSONObject();
    data.put(TaskConstants.AD_ORG_ATTR, "org123");

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_MissingAttributeInEventData")).thenReturn(
          "Missing attribute: %s");

      OBException exception = assertThrows(OBException.class,
          () -> TaskUtil.createTask(mockTable, mockState, data, null, false));
      assertTrue(exception.getMessage().contains(TaskConstants.AD_CLIENT_ATTR));
    }
  }

  /**
   * Tests the creation of a task with a missing organization.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testGetInitialStateFound() {
    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<Restrictions> restrictionsStatic = mockStatic(Restrictions.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.addOrderBy(State.PROPERTY_SEQUENCENO, true)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.setMaxResults(1)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.uniqueResult()).thenReturn(mockState);

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
    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {

      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.addOrderBy(State.PROPERTY_SEQUENCENO, true)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.setMaxResults(1)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.uniqueResult()).thenReturn(null);
      msgUtils.when(() -> OBMessageUtils.getI18NMessage("ETASK_NoInitialState")).thenReturn("No initial state");

      OBException exception = assertThrows(OBException.class,
          () -> TaskUtil.getInitialState(mockTaskType));
      assertEquals("No initial state", exception.getMessage());
    }
  }

  /**
   * Tests the retrieval of an AD Table by its name.
   * This should return the AD Table if found.
   */
  @Test
  public void testGetADTableFound() {
    String tableName = "test_table";

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(org.openbravo.model.ad.datamodel.Table.class)).thenReturn(mockADTableCriteria);
      when(mockADTableCriteria.setMaxResults(1)).thenReturn(mockADTableCriteria);
      when(mockADTableCriteria.uniqueResult()).thenReturn(mockADTable);

      org.openbravo.model.ad.datamodel.Table result = TaskUtil.getADTable(tableName);

      assertEquals(mockADTable, result);
    }
  }

  /**
   * Tests the retrieval of an AD Table by its name when the table is not found.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testGetEventValueFound() {
    String verb = "CREATE";

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(Reference.class, TaskConstants.TABLE_EVENTS_REF)).thenReturn(mockReference);
      when(mockDal.createCriteria(org.openbravo.model.ad.domain.List.class)).thenReturn(mockListCriteria);
      when(mockListCriteria.setMaxResults(1)).thenReturn(mockListCriteria);
      when(mockListCriteria.uniqueResult()).thenReturn(mockList);
      when(mockList.getSearchKey()).thenReturn("C");

      String result = TaskUtil.getEventValue(verb);

      assertEquals("C", result);
    }
  }

  /**
   * Tests the retrieval of an event value when the verb is not found.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testGetMatchingRules() {
    String eventIdentifier = "CREATE";
    List<Table> expectedRules = List.of(mockTable);

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.createCriteria(Table.class)).thenReturn(mockTableCriteria);
      when(mockTableCriteria.list()).thenReturn(expectedRules);

      List<Table> result = TaskUtil.getMatchingRules(mockADTable, eventIdentifier);

      assertEquals(expectedRules, result);
    }
  }

  /**
   * Tests the retrieval of an event value when the verb is not found.
   * This should throw an OBException with a specific error message.
   */
  @Test
  public void testFindStateByStatusId() {
    String statusId = "status123";
    String taskTypeId = "tasktype123";

    try (MockedStatic<OBDal> dalStatic = mockStatic(OBDal.class)) {
      dalStatic.when(OBDal::getInstance).thenReturn(mockDal);
      when(mockDal.get(TaskType.class, taskTypeId)).thenReturn(mockTaskType);
      when(mockDal.createCriteria(State.class)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.createAlias(State.PROPERTY_TASKSTATUS, "st")).thenReturn(mockStateCriteria);
      when(mockStateCriteria.setMaxResults(1)).thenReturn(mockStateCriteria);
      when(mockStateCriteria.uniqueResult()).thenReturn(mockState);

      State result = TaskUtil.findStateByStatusId(statusId, taskTypeId);

      assertEquals(mockState, result);
    }
  }

}
