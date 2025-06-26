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

import static com.etendoerp.task.TaskTestsConstants.CREATE_VERB;
import static com.etendoerp.task.TaskTestsConstants.DELETE_VERB;
import static com.etendoerp.task.TaskTestsConstants.OLD_STATUS_ID;
import static com.etendoerp.task.TaskTestsConstants.OTHER_TABLE_NAME;
import static com.etendoerp.task.TaskTestsConstants.STATUS_ID;
import static com.etendoerp.task.TaskTestsConstants.TASK_TYPE_ID;
import static com.etendoerp.task.TaskTestsConstants.TOPIC1;
import static com.etendoerp.task.TaskTestsConstants.TOPIC2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.etendoerp.task.data.State;
import com.etendoerp.task.utils.TaskConstants;
import com.etendoerp.task.utils.TaskUtil;
import com.smf.jobs.ActionResult;

/**
 * Unit tests for the handleTaskTableEvents method in {@link TaskTypeMatchJob}.
 * These tests verify the logic for handling task table events including create and update operations,
 * state transitions, and topic publishing.
 */
@ExtendWith(MockitoExtension.class)
public class HandleTaskTableEventsTest {

  @Mock
  private State mockState;

  private TaskTypeMatchJob job;
  private List<String> topics;
  private JSONObject parameters;
  private JSONObject norm;
  private JSONObject after;

  /**
   * Sets up the test environment before each test.
   * Initializes the TaskTypeMatchJob instance, topics list, parameters, norm, and after JSON objects.
   *
   * @throws Exception
   *     if there is an error during the setup
   */
  @BeforeEach
  void setUp() throws Exception {
    job = new TaskTypeMatchJob();
    topics = new ArrayList<>();
    parameters = new JSONObject();
    norm = new JSONObject();
    after = new JSONObject();

    parameters.put("test", "value");
    after.put(TaskConstants.STATUS, STATUS_ID);
    after.put(TaskConstants.TASK_TYPE_ID_PROPERTY, TASK_TYPE_ID);
  }

  /**
   * Verifies that handleTaskTableEvents returns null when the table name does not match the task table.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsReturnsNullForNonTaskTable() throws Exception {
    ActionResult result = job.handleTaskTableEvents(
        OTHER_TABLE_NAME, CREATE_VERB, norm, after, topics, parameters
    );

    assertNull(result);
  }

  /**
   * Verifies that when a task is created automatically, no state events are triggered and a result is returned.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsCreateOperationWithAutomatic() throws Exception {
    after.put(TaskConstants.CREATED_AUTOMATICALLY, "Y");

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, CREATE_VERB, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertNotNull(result.getMessage());

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(anyString(), anyString()), never());
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), never());
    }
  }

  /**
   * Verifies that when a task is created manually, state events are triggered and topics are published.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsCreateOperationWithManual() throws Exception {
    after.put(TaskConstants.CREATED_AUTOMATICALLY, "N");
    List<String> stateTopics = Arrays.asList(TOPIC1, TOPIC2);

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      taskUtil.when(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID))
          .thenReturn(mockState);
      taskUtil.when(() -> TaskUtil.runStateEvents(mockState))
          .thenReturn(stateTopics);

      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_CREATE, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertNotNull(result.getMessage());
      assertEquals(2, topics.size());
      assertEquals(TOPIC1, topics.get(0));
      assertEquals(TOPIC2, topics.get(1));

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID), times(1));
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), times(1));
    }
  }

  /**
   * Verifies that if the CREATED_AUTOMATICALLY field is missing, no state events are triggered and a result is returned.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsCreateOperationMissingAutoField() throws Exception {

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_CREATE, norm, after, topics, parameters
      );

      assertNotNull(result);

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(anyString(), anyString()), never());
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), never());
    }
  }

  /**
   * Tests update operation with status change.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsUpdateOperationWithStatusChange() throws Exception {
    JSONObject before = new JSONObject();
    before.put(TaskConstants.STATUS, OLD_STATUS_ID);
    norm.put(TaskConstants.BEFORE, before);

    List<String> stateTopics = Arrays.asList("updateTopic1", "updateTopic2");

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      taskUtil.when(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID))
          .thenReturn(mockState);
      taskUtil.when(() -> TaskUtil.runStateEvents(mockState))
          .thenReturn(stateTopics);

      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_UPDATE, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertNotNull(result.getMessage());
      assertEquals(2, topics.size());
      assertEquals("updateTopic1", topics.get(0));
      assertEquals("updateTopic2", topics.get(1));

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID), times(1));
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), times(1));
    }
  }

  /**
   * Tests update operation without status change.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsUpdateOperationWithoutStatusChange() throws Exception {
    JSONObject before = new JSONObject();
    before.put(TaskConstants.STATUS, STATUS_ID);
    norm.put(TaskConstants.BEFORE, before);

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_UPDATE, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertEquals(0, topics.size());

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(anyString(), anyString()), never());
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), never());
    }
  }

  /**
   * Tests update operation when before object is null.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsUpdateOperationWithNullBefore() throws Exception {
    List<String> stateTopics = List.of("nullBeforeTopic");

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      taskUtil.when(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID))
          .thenReturn(mockState);
      taskUtil.when(() -> TaskUtil.runStateEvents(mockState))
          .thenReturn(stateTopics);

      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_UPDATE, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertEquals(1, topics.size());
      assertEquals("nullBeforeTopic", topics.get(0));

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID), times(1));
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), times(1));
    }
  }

  /**
   * Tests update operation when before object exists but doesn't have status field.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsUpdateOperationWithBeforeMissingStatus() throws Exception {
    JSONObject before = new JSONObject();
    norm.put(TaskConstants.BEFORE, before);

    List<String> stateTopics = List.of("missingStatusTopic");

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      taskUtil.when(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID))
          .thenReturn(mockState);
      taskUtil.when(() -> TaskUtil.runStateEvents(mockState))
          .thenReturn(stateTopics);

      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_UPDATE, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertEquals(1, topics.size());
      assertEquals("missingStatusTopic", topics.get(0));

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID), times(1));
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), times(1));
    }
  }

  /**
   * Verifies that for operations other than create or update, no state events are triggered and a result is returned.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsOtherOperation() throws Exception {
    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      ActionResult result = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, DELETE_VERB, norm, after, topics, parameters
      );

      assertNotNull(result);
      assertEquals(0, topics.size());

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(anyString(), anyString()), never());
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), never());
    }
  }

  /**
   * Verifies that the result message returned by handleTaskTableEvents contains the expected structure and fields.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsResultMessageStructure() throws Exception {
    ActionResult result = job.handleTaskTableEvents(
        TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_CREATE, norm, after, topics, parameters
    );

    assertNotNull(result);
    String message = result.getMessage();
    assertNotNull(message);

    JSONObject messageJson = new JSONObject(message);
    assertNotNull(messageJson);

    assertTrue(messageJson.has(TaskConstants.NEXT));
    assertTrue(messageJson.has(TaskConstants.MESSAGE));
    assertTrue(messageJson.has(TaskConstants.STATE));
  }

  /**
   * Verifies that handleTaskTableEvents works correctly regardless of the case of the table name.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEvents_CaseInsensitiveTableName() throws Exception {
    ActionResult result1 = job.handleTaskTableEvents(
        TaskConstants.TASK_TABLENAME.toLowerCase(), TaskConstants.TABLE_CREATE, norm, after, topics, parameters
    );

    ActionResult result2 = job.handleTaskTableEvents(
        TaskConstants.TASK_TABLENAME.toUpperCase(), TaskConstants.TABLE_CREATE, norm, after, topics, parameters
    );

    assertNotNull(result1);
    assertNotNull(result2);
  }

  /**
   * Verifies that handleTaskTableEvents works correctly regardless of the case of the operation verb.
   *
   * @throws Exception
   *     if there is an error during the test execution
   */
  @Test
  void testHandleTaskTableEventsCaseInsensitiveVerb() throws Exception {
    after.put(TaskConstants.CREATED_AUTOMATICALLY, "N");
    List<String> stateTopics = List.of(TOPIC1);

    try (MockedStatic<TaskUtil> taskUtil = mockStatic(TaskUtil.class)) {
      taskUtil.when(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID))
          .thenReturn(mockState);
      taskUtil.when(() -> TaskUtil.runStateEvents(mockState))
          .thenReturn(stateTopics);

      ActionResult result1 = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_CREATE.toUpperCase(), norm, after, topics, parameters
      );

      topics.clear();

      ActionResult result2 = job.handleTaskTableEvents(
          TaskConstants.TASK_TABLENAME, TaskConstants.TABLE_CREATE.toLowerCase(), norm, after, topics, parameters
      );

      assertNotNull(result1);
      assertNotNull(result2);

      taskUtil.verify(() -> TaskUtil.findStateByStatusId(STATUS_ID, TASK_TYPE_ID), times(2));
      taskUtil.verify(() -> TaskUtil.runStateEvents(mockState), times(2));
    }
  }
}
