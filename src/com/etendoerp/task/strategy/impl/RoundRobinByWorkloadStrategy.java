package com.etendoerp.task.strategy.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;
import com.etendoerp.task.utils.TaskUtil;

/**
 * The RoundRobinByWorkloadStrategy class implements a workload-based task assignment strategy.
 * It extends the `UserAvailabilityStrategy` interface and assigns users to tasks by selecting
 * the user with the least number of open tasks, following a round-robin approach.
 * <p>
 * ### Assignment Process:
 * 1. Retrieve the list of users available for the given task type.
 * 2. Compute the workload of each user based on the number of open tasks assigned to them.
 * 3. Identify the users with the **lowest** workload.
 * 4. Select a user from this group in a **round-robin** manner.
 * 5. Update the task type's round-robin index (`rrindex`) to track the last assigned user.
 * 6. Save the assignment to the database.
 * <p>
 * ### Dependencies:
 * - `OBDal`: Handles database operations for user retrieval and task updates.
 * - `OBContext`: Ensures data access is performed in administrative mode.
 * - `Task`: Represents the tasks to be assigned.
 * - `TaskType`: Determines the type of the task and stores the round-robin index.
 * - `User`: Represents the user assigned to a task.
 * - `Status`: Helps identify open tasks.
 */
public class RoundRobinByWorkloadStrategy implements UserAvailabilityStrategy {

  /**
   * Finds the user with the lowest workload for the given task type, using round-robin among equally loaded users.
   *
   * <p>The workload is calculated as the number of open (pending or in-progress) tasks currently assigned to each user.
   * Among the users with the minimal number of open tasks, one is selected in round-robin order based on the
   * {@code rrindex} property of the {@link TaskType}.</p>
   *
   * @param taskType
   *     the task type for which to assign a user
   * @param parameters
   *     additional input parameters (not used in this strategy)
   * @return the selected user with the lowest workload
   * @throws OBException
   *     if task type is null or no available users are found
   */
  @Override
  public User findUserAccordingStrategy(TaskType taskType, JSONObject parameters) {
    if (taskType == null) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoTaskTypeFound"));
    }

    List<User> availableUsers = getUsersAvailable(taskType, parameters);
    if (availableUsers.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoUsersFound"));
    }

    List<Task> allTasks = TaskUtil.preloadTasks(availableUsers);
    Map<User, Long> userOpenTasks = computeOpenTasksByUser(allTasks);
    long minLoad = userOpenTasks.values().stream().min(Comparator.naturalOrder()).orElse(0L);

    List<User> minimalLoadOps = availableUsers.stream().filter(
        u -> userOpenTasks.getOrDefault(u, 0L) == minLoad).collect(Collectors.toList());

    if (minimalLoadOps.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoAvailableUsersByLoad"));
    }

    int currentIndex = (taskType.getRoundRobinIndex() != null) ? taskType.getRoundRobinIndex().intValue() : 0;
    User selectedUser = minimalLoadOps.get(currentIndex);

    TaskUtil.updateRoundRobinIndex(taskType.getId(), currentIndex + 1, minimalLoadOps.size());

    return selectedUser;
  }

  /**
   * Computes the number of open tasks assigned to each user.
   *
   * @param allTasks
   *     the list of tasks to be assigned
   * @return a map where the keys are the users and the values are the
   *     number of open tasks assigned to each user
   */
  private Map<User, Long> computeOpenTasksByUser(List<Task> allTasks) {
    Status pendingStatus = TaskUtil.getStatus("PE");
    Status inProgressStatus = TaskUtil.getStatus("IP");

    return allTasks.stream().filter(wt -> {
      Status st = wt.getStatus();
      return (pendingStatus.equals(st) || inProgressStatus.equals(st));
    }).collect(Collectors.groupingBy(Task::getAssignedUser, Collectors.counting()));
  }

  /**
   * Retrieves the list of users available for the given task type.
   *
   * <p>This implementation returns all active users, without filtering by task type or workload.</p>
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