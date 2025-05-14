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

      // Validate and extract parameters from Debezium format
      JSONObject normalizedParams = TaskUtil.validateAndNormalizeParameters(parameters);
      String tableName = normalizedParams.getString("table");
      String verb = normalizedParams.getString("verb");
      JSONObject data = normalizedParams.getJSONObject("data");

      org.openbravo.model.ad.datamodel.Table table = TaskUtil.getADTable(tableName);
      if (table == null) {
        log.warn("No matching table event for table={}", tableName);
        result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoTableMatching"));
        return result;
      }
      log.debug("Table found: id={}, dbTableName={}", table.getId(), table.getDBTableName());

      String eventValue = TaskUtil.getEventValue(verb);
      if (eventValue == null) {
        log.warn("No matching table event for verb={}", verb);
        result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoTableEventMatching"));
        return result;
      }
      log.debug("Event validated: verb={}, searchKey={}", verb, eventValue);

      List<Table> tableEventRules = TaskUtil.getMatchingRules(table, eventValue);
      log.debug("Found {} rules for table={} event={}", tableEventRules.size(), tableName, eventValue);
      if (tableEventRules.isEmpty()) {
        result.setMessage(OBMessageUtils.getI18NMessage("ETASK_NoMatchingRules"));
        return result;
      }

      boolean taskCreated = false;

      // Process each rule until a task is created
      StringBuilder msg = new StringBuilder();
      for (Table rule : tableEventRules) {
        try {
          log.debug("Processing rule: table={}, event={}, filter={}, action={}",
              rule.getTable().getDBTableName(),
              rule.getTableEvents(),
              rule.getFilter(),
              rule.getAction() != null ? rule.getAction().getId() : "none");
          if (rule.getFilter() != null && !rule.getFilter().isEmpty()) {
            boolean filterValid = TaskUtil.validateFilter(rule.getFilter(), data);
            log.debug("Filter result: {}", filterValid);
            if (!filterValid) {
              continue; // Skip rule if filter fails
            }
          }

          // Apply advanced logic (OBUIAPP_Process) if defined
          if (!TaskUtil.executeAdvancedLogic(rule, data)) {
            log.debug("Rule skipped: advanced logic validation failed");
            continue;
          }
          State initialState = TaskUtil.getInitialState(rule.getTaskType());
          // Create task in etask_task
          Task task = TaskUtil.createTask(rule, initialState, data);
          log.debug("Task created: client={}, org={}, taskType={}",
              task.getClient().getId(),
              task.getOrganization().getId(),
              task.getTaskType().getId());
          OBDal.getInstance().save(task);
          OBDal.getInstance().flush();

          taskCreated = true;

          msg.append(String.format(OBMessageUtils.messageBD("ETASK_TaskCreatedSuccessfully"),
              rule.getTaskType().getIdentifier(), tableName)).append("\n");
          log.debug("Rule processed successfully, task created.");
        } catch (Exception ruleEx) {
          log.error("Error processing rule [{}]: {}", rule.getIdentifier(), ruleEx.getMessage(), ruleEx);
          msg.append(String.format(OBMessageUtils.messageBD("ETASK_ErrorProcessingRule"),
              rule.getIdentifier(), ruleEx.getMessage())).append("\n");
        }
      }

      if (taskCreated) {
        OBDal.getInstance().commitAndClose();
      }

      success = true;
      result.setMessage(msg.toString());

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
   * Specifies the expected input class for the action.
   *
   * @return The Class of the expected input (JSONObject)
   */
  @Override
  protected Class<?> getInputClass() {
    return JSONObject.class;
  }
}
