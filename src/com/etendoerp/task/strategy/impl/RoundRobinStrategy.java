package com.etendoerp.task.strategy.impl;

import java.util.List;

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
   * Returns the next user that should be assigned a task of the given type,
   * following a round-robin strategy.
   * <p>
   * If no user is available to take tasks of the given type, an exception is
   * thrown.
   * <p>
   * The index of the selected user is stored in the {@code rrindex} column of
   * the {@code AD_Task_Type} table, and is updated after each call to this
   * method.
   *
   * @param taskType
   *     the type of task to assign
   * @return the user that should take the task
   * @throws OBException
   *     if no user is available
   */
  @Override
  public User findUserAccordingStrategy(TaskType taskType) {
    int currentIndex = (taskType.getRoundRobinIndex() != null) ? taskType.getRoundRobinIndex().intValue() : 0;

    List<User> availableUsers = getUsersAvailable(taskType);
    if (availableUsers.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoUsersFound"));
    }

    User selectedUser = availableUsers.get(currentIndex);

    TaskUtil.updateRoundRobinIndex(taskType, currentIndex + 1, availableUsers.size());

    return selectedUser;
  }

  /**
   * Retrieves a list of users available for the given task type.
   * The result is always the list of all active users, regardless of the task type.
   * <p>
   *
   * @param taskType
   *     ignored
   * @return a list of all active users
   */
  @Override
  public List<User> getUsersAvailable(TaskType taskType) {
    return TaskUtil.getActiveUsers();
  }
}
