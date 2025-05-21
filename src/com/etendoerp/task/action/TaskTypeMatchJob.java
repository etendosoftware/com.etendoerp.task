package com.etendoerp.task.action;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

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

      //Validation and normalization of parameters
      JSONObject norm = TaskUtil.validateAndNormalizeParameters(parameters);
      String table = norm.getString(TaskConstants.TABLE);
      String verb = norm.getString(TaskConstants.VERB);
      JSONObject after = norm.getJSONObject(TaskConstants.AFTER);

      //Logic for events in the ETASK_Task table
      if (StringUtils.equalsIgnoreCase(TaskConstants.TASK_TABLENAME, table)) {

        boolean isCreate = StringUtils.equalsIgnoreCase(TaskConstants.TABLE_CREATE, verb);
        boolean isUpdate = StringUtils.equalsIgnoreCase(TaskConstants.TABLE_UPDATE, verb);

        if (isCreate) {
          boolean auto = StringUtils.equalsIgnoreCase(after.optString(TaskConstants.CREATED_AUTOMATICALLY, "Y"), "Y");
          if (!auto) {
            State st = TaskUtil.findStateByStatusId(after.getString(TaskConstants.STATUS),
                after.getString(TaskConstants.TASK_TYPE_ID_PROPERTY));
            topics.addAll(TaskUtil.runStateEvents(st, norm));
          }
        } else if (isUpdate) {
          JSONObject before = norm.optJSONObject(TaskConstants.BEFORE);
          String oldSt = before == null ? null : before.optString(TaskConstants.STATUS, null);
          String newSt = after.getString(TaskConstants.STATUS);
          if (!StringUtils.equals(newSt, oldSt)) {
            State st = TaskUtil.findStateByStatusId(newSt,
                after.getString(TaskConstants.TASK_TYPE_ID_PROPERTY));
            topics.addAll(TaskUtil.runStateEvents(st, norm));
          }
        }
        actionResult.setType(Result.Type.SUCCESS);
        actionResult.setMessage(outJson(topics, parameters, null).toString());
        return actionResult;
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

        //Task creation
        Task newTask = TaskUtil.createTask(rule, init, after);
        OBDal.getInstance().save(newTask);
        OBDal.getInstance().flush();

        JSONObject taskInfo = new JSONObject();
        taskInfo.put(TaskConstants.TASK, newTask.getId());
        taskInfo.put(TaskConstants.STATE, newTask.getStatus().getId());
        created.add(taskInfo);
        topics.addAll(TaskUtil.runStateEvents(init, norm));

        commit = true;
      }

      if (commit) {
        OBDal.getInstance().commitAndClose();
      }
      actionResult.setType(Result.Type.SUCCESS);
      actionResult.setMessage(outJson(topics, parameters, created).toString());
      return actionResult;

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      log.error("TaskTypeMatchJob error", e);
      actionResult.setType(Result.Type.ERROR);
      actionResult.setMessage(outJson(null, null, null).toString());
      return actionResult;
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Converts the given input into a JSON object with three fields:
   * <ul>
   *   <li>next - a single string or an array of strings with the next topics to be published.</li>
   *   <li>message - the message associated with the task creation, or null.</li>
   *   <li>state - an array of objects with the task and state information, or null.</li>
   * </ul>
   * <p>
   * This method is used to create the output JSON object from the action. The method
   * takes care of converting the input into a JSON object and handling any exceptions
   * that may occur during the conversion.
   *
   * @param next
   *     the list of topics to be published, or null.
   * @param msg
   *     the message associated with the task creation, or null.
   * @param tasksInfo
   *     the list of objects with the task and state information, or null.
   * @return the JSON object with the information.
   */
  private JSONObject outJson(List<String> next, JSONObject msg, List<JSONObject> tasksInfo) {
    JSONObject resultJsonObj = new JSONObject();
    try {
      if (next == null || next.isEmpty()) {
        resultJsonObj.put(TaskConstants.NEXT, JSONObject.NULL);
      } else if (next.size() == 1) {
        resultJsonObj.put(TaskConstants.NEXT, next.get(0));
      } else {
        resultJsonObj.put(TaskConstants.NEXT, next);
      }
      resultJsonObj.put(TaskConstants.MESSAGE, msg == null ? JSONObject.NULL : msg);
      resultJsonObj.put(TaskConstants.STATE, tasksInfo == null || tasksInfo.isEmpty()
          ? JSONObject.NULL
          : new org.codehaus.jettison.json.JSONArray(tasksInfo));
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
