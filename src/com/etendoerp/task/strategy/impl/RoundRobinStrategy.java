package com.etendoerp.task.strategy.impl;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;
import com.etendoerp.task.utils.TaskUtil;

/**
 * The RoundRobinStrategy class implements a **round-robin** task assignment strategy.
 * It extends the `UserAvailabilityStrategy` interface and assigns users to tasks
 * by cycling through available users in a sequential order.
 * <p>
 * ### Assignment Process:
 * 1. Retrieve the list of available users for the given task type.
 * 2. Get the current **round-robin index (`rrindex`)** stored in the `TaskType` entity.
 * 3. Select the next user in the list based on the index.
 * 4. Update the `rrindex` value to point to the next user for future assignments.
 * 5. Persist the updated index in the database.
 * <p>
 * If no users are available for the task type, the method throws an `OBException`.
 * <p>
 * ### Dependencies:
 * - `OBDal`: Handles database operations for retrieving users and updating task type data.
 * - `OBContext`: Ensures data access is performed in administrative mode.
 * - `TaskType`: Stores task type information and the round-robin index.
 * - `User`: Represents the user assigned to a task.
 */
public class RoundRobinStrategy implements UserAvailabilityStrategy {

  /**
   * Returns the next user to be assigned a task of the given type,
   * using a round-robin strategy based on the current index stored in the task type.
   *
   * <p>If no users are available, an {@link OBException} is thrown.</p>
   * <p>The current round-robin index is stored in the {@code rrindex} column
   * of the {@code AD_Task_Type} table and updated after each assignment.</p>
   *
   * @param taskType
   *     the task type to assign a user for
   * @param parameters
   *     additional input parameters (not used in this strategy)
   * @return the selected user according to the round-robin strategy
   * @throws OBException
   *     if no users are available
   */
  @Override
  public User findUserAccordingStrategy(TaskType taskType, JSONObject parameters) {
    Long roundRobinIndex = TaskUtil.getRoundRobinIndex(taskType);
    int currentIndex = (roundRobinIndex != null) ? roundRobinIndex.intValue() : 0;

    List<User> availableUsers = getUsersAvailable(taskType, parameters);
    if (availableUsers.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoUsersFound"));
    }

    User selectedUser = availableUsers.get(currentIndex);

    TaskUtil.updateRoundRobinIndex(taskType.getId(), currentIndex + 1, availableUsers.size());

    return selectedUser;
  }


  /**
   * Retrieves the list of users available for the given task type.
   *
   * <p>This implementation always returns all active users, regardless of task type or parameters.</p>
   *
   * @param taskType
   *     the task type (ignored)
   * @param parameters
   *     additional input parameters (ignored)
   * @return list of all active users
   */
  @Override
  public List<User> getUsersAvailable(TaskType taskType, JSONObject parameters) {
    return TaskUtil.getActiveUsers();
  }
}
