package com.etendoerp.task.action;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.helper.RoundRobinHelper;
import com.etendoerp.task.strategy.impl.RoundRobinByWorkloadStrategy;
import com.etendoerp.task.utils.TaskConstants;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Assigns users to tasks based on workload distribution using a Round Robin strategy.
 */
public class RoundRobinByWorkload extends Action {
  private static final Logger log = LogManager.getLogger(RoundRobinByWorkload.class);

  /**
   * Assigns a user to tasks based on workload, using RoundRobinByWorkloadStrategy.
   *
   * @param parameters
   *     JSON object containing input parameters.
   * @param isStopped
   *     Flag indicating if the action was stopped.
   * @return ActionResult indicating success or failure.
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    ActionResult result = new ActionResult();

    try {
      result.setType(Result.Type.SUCCESS);
      List<Task> taskList = getTasksFromParameters(parameters);

      if (taskList.isEmpty()) {
        throw new OBException(OBMessageUtils.messageBD("ETASK_NoTaskFound"));
      }

      RoundRobinHelper.assignUsers(
          taskList,
          taskType -> new RoundRobinByWorkloadStrategy().findUserAccordingStrategy(taskType)
      );

      result.setMessage(OBMessageUtils.getI18NMessage("ETASK_UserAssignedToTask"));

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      result.setType(Result.Type.ERROR);
      result.setMessage(e.getMessage());
    }
    return result;
  }

  /**
   * Retrieves a list of tasks based on parameters.
   *
   * @param parameters
   *     JSON object containing task parameters.
   * @return List of tasks to be assigned.
   * @throws JSONException
   *     if there's an issue parsing JSON parameters.
   */
  private List<Task> getTasksFromParameters(JSONObject parameters) throws JSONException {
    if (!parameters.has(TaskConstants.TASK_ID_PROPERTY)) {
      return getInputContents(getInputClass());
    }

    String taskId = parameters.getString(TaskConstants.TASK_ID_PROPERTY);
    return Optional.ofNullable(OBDal.getInstance().get(Task.class, taskId))
        .map(Collections::singletonList)
        .orElseThrow(() -> new OBException(OBMessageUtils.messageBD("ETASK_NoTaskFound")));
  }

  /**
   * Returns the class type of the input list elements.
   *
   * @return Class type of Task.
   */
  @Override
  protected Class<Task> getInputClass() {
    return Task.class;
  }
}