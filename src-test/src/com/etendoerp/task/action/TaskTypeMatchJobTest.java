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

import static com.etendoerp.task.TaskTestsConstants.MISSING_VERB_MSG;
import static com.etendoerp.task.TaskTestsConstants.OPERATION_DELETE;
import static com.etendoerp.task.TaskTestsConstants.OUT_JSON;
import static com.etendoerp.task.TaskTestsConstants.STATE_ID;
import static com.etendoerp.task.TaskTestsConstants.TASK_ID;
import static com.etendoerp.task.TaskTestsConstants.TOPIC1;
import static com.etendoerp.task.TaskTestsConstants.TOPIC2;
import static com.etendoerp.task.TaskTestsConstants.TOPIC3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.utils.TaskConstants;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for {@link TaskTypeMatchJob}, verifying task type matching logic,
 * parameter validation, rule processing, and Kafka message publishing.
 */
@ExtendWith(MockitoExtension.class)
public class TaskTypeMatchJobTest {

  @Mock
  private OBDal mockDal;

  /**
   * Verifies that the action method skips processing and returns success when the operation is 'delete'.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionSkipsDeleteOperation() throws Exception {
    TaskTypeMatchJob job = new TaskTypeMatchJob();
    JSONObject parameters = new JSONObject();
    parameters.put(TaskConstants.OPERATION, OPERATION_DELETE);

    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBContext> obContext = mockStatic(OBContext.class)) {
      obContext.when(() -> OBContext.setAdminMode(true)).thenAnswer(inv -> null);
      obContext.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);

      ActionResult result = job.action(parameters, isStopped);

      assertEquals(Result.Type.SUCCESS, result.getType());
      JSONObject resultMessage = new JSONObject(result.getMessage());
      assertEquals(JSONObject.NULL, resultMessage.get(TaskConstants.NEXT));

      JSONObject messageContent = resultMessage.getJSONObject(TaskConstants.MESSAGE);
      assertEquals(OPERATION_DELETE, messageContent.getString(TaskConstants.OPERATION));

      assertEquals(JSONObject.NULL, resultMessage.get(TaskConstants.STATE));
    }
  }

  /**
   * Verifies that the action method throws an error and rolls back when the operation parameter is missing.
   *
   * @throws Exception if there is an error during the test execution
   */
  @Test
  public void testActionThrowsExceptionWhenOperationMissing() throws Exception {
    TaskTypeMatchJob job = new TaskTypeMatchJob();
    JSONObject parameters = new JSONObject();
    MutableBoolean isStopped = new MutableBoolean(false);

    try (MockedStatic<OBContext> obContext = mockStatic(OBContext.class);
         MockedStatic<OBDal> obDal = mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {

      obContext.when(() -> OBContext.setAdminMode(true)).thenAnswer(inv -> null);
      obContext.when(OBContext::restorePreviousMode).thenAnswer(inv -> null);

      obDal.when(OBDal::getInstance).thenReturn(mockDal);

      msgUtils.when(() -> OBMessageUtils.getI18NMessage("ETASK_MissingVerb")).thenReturn(MISSING_VERB_MSG);

      ActionResult result = job.action(parameters, isStopped);

      assertEquals(Result.Type.ERROR, result.getType());
      verify(mockDal).rollbackAndClose();

      JSONObject resultMessage = new JSONObject(result.getMessage());
      assertEquals(JSONObject.NULL, resultMessage.get(TaskConstants.NEXT));
      assertEquals(MISSING_VERB_MSG, resultMessage.get(TaskConstants.MESSAGE));
      assertEquals(JSONObject.NULL, resultMessage.get(TaskConstants.STATE));
    }
  }
  /**
   * Verifies that getInputClass returns JSONObject.class as the expected input type.
   */
  @Test
  public void testGetInputClassReturnsJSONObject() {
    TaskTypeMatchJob job = new TaskTypeMatchJob();
    assertEquals(JSONObject.class, job.getInputClass());
  }

  /**
   * Verifies that outJson correctly serializes multiple topics and task info into the result JSON.
   *
   * @throws Exception if there is an error during reflection or JSON processing
   */
  @Test
  public void testOutJsonWithMultipleTopics() throws Exception {
    TaskTypeMatchJob job = new TaskTypeMatchJob();
    List<String> topics = List.of(TOPIC1, TOPIC2, TOPIC3);
    String message = "test message";

    List<JSONObject> tasksInfo = new ArrayList<>();
    JSONObject taskInfo = new JSONObject();
    taskInfo.put(TaskConstants.TASK, TASK_ID);
    taskInfo.put(TaskConstants.STATE, STATE_ID);
    tasksInfo.add(taskInfo);

    java.lang.reflect.Method method = TaskTypeMatchJob.class.getDeclaredMethod(OUT_JSON, List.class, Object.class, List.class);
    method.setAccessible(true);
    JSONObject result = (JSONObject) method.invoke(job, topics, message, tasksInfo);

    assertNotNull(result);
    JSONArray nextArray = result.getJSONArray(TaskConstants.NEXT);
    assertEquals(3, nextArray.length());
    assertEquals(TOPIC1, nextArray.getString(0));
    assertEquals(TOPIC2, nextArray.getString(1));
    assertEquals(TOPIC3, nextArray.getString(2));

    assertEquals(message, result.getString(TaskConstants.MESSAGE));

    JSONArray stateArray = result.getJSONArray(TaskConstants.STATE);
    assertEquals(1, stateArray.length());
    JSONObject taskObj = stateArray.getJSONObject(0);
    assertEquals(TASK_ID, taskObj.getString(TaskConstants.TASK));
    assertEquals(STATE_ID, taskObj.getString(TaskConstants.STATE));
  }

  /**
   * Verifies that outJson correctly serializes a single topic and a JSON message into the result JSON.
   *
   * @throws Exception if there is an error during reflection or JSON processing
   */
  @Test
  public void testOutJsonWithSingleTopic() throws Exception {
    TaskTypeMatchJob job = new TaskTypeMatchJob();
    List<String> topics = List.of("single-topic");
    JSONObject message = new JSONObject();
    message.put("key", "value");

    java.lang.reflect.Method method = TaskTypeMatchJob.class.getDeclaredMethod(OUT_JSON, List.class, Object.class, List.class);
    method.setAccessible(true);
    JSONObject result = (JSONObject) method.invoke(job, topics, message, null);

    assertNotNull(result);
    assertEquals("single-topic", result.getString(TaskConstants.NEXT));
    assertEquals(message, result.getJSONObject(TaskConstants.MESSAGE));
    assertEquals(JSONObject.NULL, result.get(TaskConstants.STATE));
  }

  /**
   * Verifies that outJson returns a JSON object with null values when all arguments are null.
   *
   * @throws Exception if there is an error during reflection or JSON processing
   */
  @Test
  public void testOutJsonWithNullValues() throws Exception {
    TaskTypeMatchJob job = new TaskTypeMatchJob();

    java.lang.reflect.Method method = TaskTypeMatchJob.class.getDeclaredMethod(OUT_JSON, List.class, Object.class, List.class);
    method.setAccessible(true);
    JSONObject result = (JSONObject) method.invoke(job, null, null, null);

    assertNotNull(result);
    assertEquals(JSONObject.NULL, result.get(TaskConstants.NEXT));
    assertEquals(JSONObject.NULL, result.get(TaskConstants.MESSAGE));
    assertEquals(JSONObject.NULL, result.get(TaskConstants.STATE));
  }
}
