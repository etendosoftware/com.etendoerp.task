package com.etendoerp.task.action;

import java.util.List;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.utils.TaskUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Processes database events from Kafka topic default.public.*, validates them against rules in
 * etask_table using JEXL Engine for filter evaluation, applies advanced logic via OBUIAPP_Process,
 * creates tasks in etask_task, and returns a JSON response. Implements the validation chain:
 * event → filter → advanced logic → task creation, without depending on the validation hook (ETP-1530).
 * <p>
 * Requires the commons-jexl3 library (org.apache.commons:commons-jexl3:3.3) for filter evaluation.
 */
public class TaskTypeMatchJob extends Action {
  private static final Logger log = LogManager.getLogger(TaskTypeMatchJob.class);


  /**
   * Executes the TaskTypeMatchJob to process a Debezium Kafka event, validate rules, apply advanced
   * logic, and create tasks if applicable.
   *
   * @param parameters
   *     JSONObject containing the Debezium event (source, op, after)
   * @param isStopped
   *     MutableBoolean flag to indicate if the action should be stopped
   * @return An ActionResult indicating the result of the action (SUCCESS or ERROR)
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    ActionResult result = new ActionResult();
    boolean success = false;

    try {
      OBContext.setAdminMode(true);
      result.setType(Result.Type.SUCCESS);

      JSONObject normalizedParams = TaskUtil.validateAndNormalizeParameters(parameters);
      String tableName = normalizedParams.getString("table");
      String verb = normalizedParams.getString("verb");
      JSONObject data = normalizedParams.getJSONObject("data");

      org.openbravo.model.ad.datamodel.Table table = TaskUtil.getADTable(tableName);
      if (!validateTable(table, tableName, result)) {
        return result;
      }

      String eventValue = TaskUtil.getEventValue(verb);
      if (!validateEvent(eventValue, verb, result)) {
        return result;
      }

      List<Table> tableEventRules = TaskUtil.getMatchingRules(table, eventValue);
      if (tableEventRules.isEmpty()) {
        result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoMatchingRules"));
        return result;
      }

      boolean taskCreated = processRules(tableEventRules, data, tableName, result);

      if (taskCreated) {
        OBDal.getInstance().commitAndClose();
      }

      success = true;

    } catch (JSONException e) {
      log.error("Error parsing JSON parameters: {}", e.getMessage(), e);
      result.setType(Result.Type.ERROR);
      result.setMessage("Invalid event data: " + e.getMessage());
    } catch (Exception e) {
      log.error("Unexpected error in TaskTypeMatchJob: {}", e.getMessage(), e);
      result.setType(Result.Type.ERROR);
      result.setMessage(e.getMessage());
    } finally {
      if (!success) {
        OBDal.getInstance().rollbackAndClose();
      }
      OBContext.restorePreviousMode();
    }

    return result;
  }

  /**
   * Verifies that the given AD_Table record matches the provided table name.
   * If no matching table is found, logs a warning and sets the result message.
   *
   * @param table
   *     the AD_Table record to be validated
   * @param tableName
   *     the physical database table name to be matched
   * @param result
   *     the ActionResult to store the result message
   * @return {@code true} if the table matches, {@code false} otherwise
   */
  private boolean validateTable(org.openbravo.model.ad.datamodel.Table table, String tableName, ActionResult result) {
    if (table == null) {
      log.warn("No matching table event for table={}", tableName);
      result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoTableMatching"));
      return false;
    }
    log.debug("Table found: id={}, dbTableName={}", table.getId(), table.getDBTableName());
    return true;
  }

  /**
   * Verifies that the given event value (search key) matches the provided verb.
   * If no matching event is found, logs a warning and sets the result message.
   *
   * @param eventValue
   *     internal search key (event value) from the reference list.
   * @param verb
   *     the operation type (e.g., insert, update, delete) from the event.
   * @param result
   *     ActionResult instance to fill with the result and message.
   * @return {@code true} if the event value matches the verb; {@code false} otherwise.
   */
  private boolean validateEvent(String eventValue, String verb, ActionResult result) {
    if (eventValue == null) {
      log.warn("No matching table event for verb={}", verb);
      result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoTableEventMatching"));
      return false;
    }
    log.debug("Event validated: verb={}, searchKey={}", verb, eventValue);
    return true;
  }

  /**
   * Iterates over the rules matching the given table and event, applies JEXL filter evaluation, executes
   * advanced logic actions, creates tasks in etask_task, and appends a JSON response message.
   * A task is created for each rule that passes filter evaluation and advanced logic validation.
   * If any rule throws an error, the error message is appended to the response.
   *
   * @param tableEventRules
   *     List of Table rules matching the given table and event.
   * @param data
   *     full event payload to be passed to the advanced action.
   * @param tableName
   *     physical database table name of the event.
   * @param result
   *     ActionResult instance to fill with the result and message.
   * @return {@code true} if at least one task was created; {@code false} otherwise.
   */
  private boolean processRules(List<Table> tableEventRules, JSONObject data, String tableName, ActionResult result) {
    boolean taskCreated = false;
    StringBuilder msg = new StringBuilder();

    for (Table rule : tableEventRules) {
      try {
        log.debug("Processing rule: table={}, event={}, filter={}, action={}", rule.getTable().getDBTableName(),
            rule.getTableEvents(), rule.getFilter(), rule.getAction() != null ? rule.getAction().getId() : "none");

        if (!filterPasses(rule, data)) {
          log.debug("Rule skipped: filter validation failed");
          continue;
        }

        if (!TaskUtil.executeAdvancedLogic(rule, data)) {
          log.debug("Rule skipped: advanced logic validation failed");
          continue;
        }

        State initialState = TaskUtil.getInitialState(rule.getTaskType());
        Task task = TaskUtil.createTask(rule, initialState, data);
        log.debug("Task created: client={}, org={}, taskType={}", task.getClient().getId(),
            task.getOrganization().getId(), task.getTaskType().getId());

        OBDal.getInstance().save(task);
        OBDal.getInstance().flush();
        taskCreated = true;

        msg.append(
            String.format(OBMessageUtils.messageBD("ETASK_TaskCreatedSuccessfully"), rule.getTaskType().getIdentifier(),
                tableName)).append("\n");
      } catch (Exception ruleEx) {
        log.error("Error processing rule [{}]: {}", rule.getIdentifier(), ruleEx.getMessage(), ruleEx);
        msg.append(String.format(OBMessageUtils.messageBD("ETASK_ErrorProcessingRule"), rule.getIdentifier(),
            ruleEx.getMessage())).append("\n");
      }
    }

    result.setMessage(msg.toString());
    return taskCreated;
  }

  /**
   * Evaluates the JEXL filter expression in the given rule against the provided JSON data.
   * If the filter is empty or null, this method returns {@code true}.
   *
   * @param rule
   *     the rule containing the filter expression
   * @param data
   *     the JSON object to be evaluated
   * @return {@code true} if the filter expression evaluates to {@code true}, {@code false} otherwise.
   */
  private boolean filterPasses(Table rule, JSONObject data) {
    if (rule.getFilter() != null && !rule.getFilter().isEmpty()) {
      boolean valid = TaskUtil.validateFilter(rule.getFilter(), data);
      log.debug("Filter result: {}", valid);
      return valid;
    }
    return true;
  }

  /**
   * Specifies the expected input class for the action.
   *
   * @return The Class of the expected input (JSONObject)
   */
  @Override
  protected Class<?> getInputClass() {
    return JSONObject.class;
  }
}
