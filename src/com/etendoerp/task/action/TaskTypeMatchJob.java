/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.task.action;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserRoles;

import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.utils.TaskConstants;
import com.etendoerp.task.utils.TaskUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Job responsible for processing task type matching events in Etendo ERP.
 *
 * <p>This class implements a scheduled {@link com.smf.jobs.Action} that listens to events
 * related to the ETASK_Task table and other business tables. Its main purpose is to
 * interpret these events (create, update), apply configured rules, and generate
 * tasks accordingly.
 *
 * <p>Main responsibilities include:
 * <ul>
 *   <li>Validating and normalizing incoming parameters received as JSON.</li>
 *   <li>Applying matching rules for other tables and validating user-defined filters.</li>
 *   <li>Creating tasks based on rule matches and triggering corresponding state transitions.</li>
 *   <li>Publishing Kafka messages for processed events.</li>
 * </ul>
 *
 * <p>Returns an {@link ActionResult} describing the outcome of execution, including
 * triggered Kafka topics and the final state of any created or updated tasks.
 *
 * <p>This job extends {@link com.smf.jobs.Action}, allowing it to be executed
 * via the SMF Jobs scheduler framework.
 */
public class TaskTypeMatchJob extends Action {

  private static final Logger log = LogManager.getLogger(TaskTypeMatchJob.class);

  /**
   * Executes the task type matching logic for the given JSON parameters and whether the job is stopped.
   * <p>
   * The method validates and normalizes the input parameters, applies configured rules, creates tasks,
   * triggers state transitions, and publishes Kafka messages for the resulting events.
   * <p>
   * On successful processing, an {@link ActionResult} is returned with details about the outcome,
   * including triggered topics and the final task state.
   *
   * @param parameters
   *     JSON object containing event data, including the table name, and before/after statuses.
   * @param isStopped
   *     whether the job is stopped
   * @return an {@link ActionResult} with information about the outcome
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {

    ActionResult actionResult = new ActionResult();
    List<String> topics = new ArrayList<>();
    boolean commit = false;

    try {
      OBContext.setAdminMode(true);
      actionResult.setType(Result.Type.SUCCESS);

      if (!parameters.has(TaskConstants.OPERATION) || parameters.getString(TaskConstants.OPERATION).isEmpty()) {
        throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingVerb"));
      }
      String operation = parameters.getString(TaskConstants.OPERATION);

      //If it is a deleted event, we skip the topic search, as there is no data structure yet available to support this event.
      if (StringUtils.equals(operation, "d")) {
        actionResult.setMessage(outJson(null, parameters, null).toString());
        return actionResult;
      }
      //Validation and normalization of parameters
      JSONObject norm = TaskUtil.validateAndNormalizeParameters(parameters);
      String table = norm.getString(TaskConstants.TABLE);
      String verb = norm.getString(TaskConstants.VERB);
      JSONObject after = norm.getJSONObject(TaskConstants.AFTER);

      //Logic for events in the ETASK_Task table
      ActionResult taskTableResult = handleTaskTableEvents(table, verb, norm, after, topics, parameters);
      if (taskTableResult != null) {
        return taskTableResult;
      }

      // Section of code in charge of managing the other tables
      var adTable = TaskUtil.getADTable(table);
      String evKey = TaskUtil.getEventValue(verb);
      List<Table> rules = TaskUtil.getMatchingRules(adTable, evKey);
      List<JSONObject> created = new ArrayList<>();

      for (Table rule : rules) {
        if (!TaskUtil.validateFilter(rule.getFilter(), after)) {
          continue;
        }
        if (!TaskUtil.executeAdvancedLogic(rule, after)) {
          continue;
        }

        //The initial state is obtained
        State init = TaskUtil.getInitialState(rule.getTaskType());
        JSONObject taskParams = new JSONObject(norm.toString());

        taskParams.put("ad_client_id", after.getString(TaskConstants.AD_CLIENT_ATTR));
        taskParams.put("ad_org_id", after.getString(TaskConstants.AD_ORG_ATTR));

        //Task creation
        Task newTask = TaskUtil.createTask(rule.getTaskType(), init.getTaskStatus(), true, taskParams,
            OBContext.getOBContext());
        OBDal.getInstance().save(newTask);
        OBDal.getInstance().flush();

        JSONObject taskInfo = new JSONObject();
        taskInfo.put(TaskConstants.TASK, newTask.getId());
        taskInfo.put(TaskConstants.STATE, newTask.getStatus().getId());
        created.add(taskInfo);
        topics.addAll(TaskUtil.runStateEvents(init));

        commit = true;
      }

      if (commit) {
        OBDal.getInstance().commitAndClose();
      }

      actionResult.setMessage(outJson(topics, parameters, created).toString());
      return actionResult;

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log.error("TaskTypeMatchJob error", e);
      actionResult.setType(Result.Type.ERROR);
      actionResult.setMessage(outJson(null, e.getMessage(), null).toString());
      return actionResult;

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Handles events in the ETASK_Task table.
   *
   * @param table
   *     The table name of the event.
   * @param verb
   *     The operation (create, update, delete) of the event.
   * @param norm
   *     The normalized parameters of the event.
   * @param after
   *     The JSON object representing the after state of the event.
   * @param topics
   *     The list of Kafka topics to be published.
   * @param parameters
   *     The JSON object containing the input parameters of the job.
   * @return an ActionResult indicating success or failure.
   * @throws Exception
   *     If any error occurs during the execution of this method.
   */
  protected ActionResult handleTaskTableEvents(String table, String verb, JSONObject norm, JSONObject after,
      List<String> topics, JSONObject parameters) throws Exception {
    if (!StringUtils.equalsIgnoreCase(TaskConstants.TASK_TABLENAME, table)) {
      return null;
    }

    ActionResult result = new ActionResult();
    boolean isCreate = StringUtils.equalsIgnoreCase(TaskConstants.TABLE_CREATE, verb);
    boolean isUpdate = StringUtils.equalsIgnoreCase(TaskConstants.TABLE_UPDATE, verb);

    if (isCreate) {
      boolean auto = StringUtils.equalsIgnoreCase(after.optString(TaskConstants.CREATED_AUTOMATICALLY, "Y"), "Y");
      if (!auto) {
        State st = TaskUtil.findStateByStatusId(after.getString(TaskConstants.STATUS),
            after.getString(TaskConstants.TASK_TYPE_ID_PROPERTY));
        topics.addAll(TaskUtil.runStateEvents(st));
      }
    } else if (isUpdate) {
      JSONObject before = norm.optJSONObject(TaskConstants.BEFORE);
      String oldSt = before == null ? null : before.optString(TaskConstants.STATUS, null);
      String newSt = after.getString(TaskConstants.STATUS);
      if (!StringUtils.equals(newSt, oldSt)) {
        State st = TaskUtil.findStateByStatusId(newSt, after.getString(TaskConstants.TASK_TYPE_ID_PROPERTY));
        topics.addAll(TaskUtil.runStateEvents(st));
      }
    }

    var contextJson = new JSONObject();
    JSONObject afterBlock = after;
    if (afterBlock == null) {
      afterBlock = parameters.has("after") ? parameters.getJSONObject("after") : parameters;
    }
    String userId = getUser(afterBlock);
    contextJson.put("user", userId);
    contextJson.put("client", afterBlock.getString(TaskConstants.AD_CLIENT_ID));
    contextJson.put("role", getRole(afterBlock, userId));
    contextJson.put("organization", afterBlock.getString(TaskConstants.AD_ORG_ID));
    parameters.put("context", contextJson);
    result.setMessage(outJson(topics, parameters, null).toString());
    return result;
  }


  /**
   * Retrieves the role ID for a user based on the provided parameters.
   *
   * <p>If the `parameters` object contains the `ad_role_id` field, its value is returned.
   * Otherwise, the method fetches the user from the database using the provided `userId`
   * and retrieves the default role ID associated with that user. If the user does not have
   * a default role, the first role from the user's role list is returned.
   *
   * @param taskData
   *     A JSON object containing input parameters, which may include `ad_role_id`.
   * @param userId
   *     The ID of the user whose role is to be retrieved.
   * @return The role ID as a string.
   */
  public String getRole(JSONObject taskData, String userId) {
    String assignedRole = taskData.optString(TaskConstants.ASSIGNED_ROLE, null);
    if (StringUtils.isNotBlank(assignedRole) && !StringUtils.equals(assignedRole, "null")) {
      return assignedRole;
    }
    User usr = OBDal.getReadOnlyInstance().get(User.class, userId);
    if (usr.getDefaultRole() != null) {
      return usr.getDefaultRole().getId();
    }
    List<UserRoles> adUserRolesList = usr.getADUserRolesList();
    if (adUserRolesList.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_UserHasNoRole"));
    }
    return adUserRolesList.get(0).getRole().getId();
  }

  /**
   * Retrieves the user ID from the provided parameters.
   *
   * <p>The method first checks if the `after` object in the `parameters` contains an `assigned_user` field.
   * If found, its value is returned. If not, the method checks for the `createdby` field in the same object.
   * If neither field is present, a default user ID of "100" is returned.
   *
   * @param taskData
   *     A JSON object containing input parameters, which may include user-related fields.
   * @return The user ID as a string.
   */
  public String getUser(JSONObject taskData) {
    if (taskData == null) {
      // Default user ID if not specified
      return "100";
    }
    if (taskData.has(TaskConstants.ASSIGNED_USER) && taskData.optString(TaskConstants.ASSIGNED_USER,
        null) != null && !StringUtils.equals(taskData.optString(TaskConstants.ASSIGNED_USER, null), "null")) {
      return taskData.optString(TaskConstants.ASSIGNED_USER);
    }
    return taskData.optString("createdby");
  }

  /**
   * Converts the given input into a JSON object with three fields:
   * <ul>
   *   <li>next - a single string or an array of strings with the next topics to be published.</li>
   *   <li>message - a message string or JSON object associated with the task creation, or null.</li>
   *   <li>state - an array of objects with the task and state information, or null.</li>
   * </ul>
   * <p>
   * This method is used to create the output JSON object from the action. It supports both
   * raw strings and JSON objects as input for the message field.
   *
   * @param next
   *     the list of topics to be published, or null.
   * @param msg
   *     the message associated with the task creation (can be a JSONObject, String, or null).
   * @param tasksInfo
   *     the list of task and state objects, or null.
   * @return the resulting JSON object.
   */
  private JSONObject outJson(List<String> next, Object msg, List<JSONObject> tasksInfo) {
    JSONObject resultJsonObj = new JSONObject();
    try {
      if (next == null || next.isEmpty()) {
        resultJsonObj.put(TaskConstants.NEXT, JSONObject.NULL);
      } else if (next.size() == 1) {
        resultJsonObj.put(TaskConstants.NEXT, next.get(0));
      } else {
        resultJsonObj.put(TaskConstants.NEXT, next);
      }

      if (msg == null) {
        resultJsonObj.put(TaskConstants.MESSAGE, JSONObject.NULL);
      } else if (msg instanceof JSONObject) {
        resultJsonObj.put(TaskConstants.MESSAGE, msg);
      } else if (msg instanceof String) {
        resultJsonObj.put(TaskConstants.MESSAGE, msg);
      } else {
        resultJsonObj.put(TaskConstants.MESSAGE, msg.toString());
      }

      resultJsonObj.put(TaskConstants.STATE,
          tasksInfo == null || tasksInfo.isEmpty() ? JSONObject.NULL : new org.codehaus.jettison.json.JSONArray(
              tasksInfo));

    } catch (Exception e) {
      throw new OBException(e);
    }

    return resultJsonObj;
  }

  /**
   * Returns the class type of the input list elements.
   *
   * @return Class type of JSONObject.
   */
  @Override
  protected Class<?> getInputClass() {
    return JSONObject.class;
  }
}
