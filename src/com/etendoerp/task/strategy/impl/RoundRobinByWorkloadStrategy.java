package com.etendoerp.task.strategy.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
   * Find the user with the minimal current workload assigned to the given task type. The user is chosen
   * in a round-robin fashion from the ones with the minimal load. The user's workload is calculated as the number
   * of open tasks assigned to the user. The index of the chosen user is stored in the task type's
   * {@code rrindex} property.
   *
   * @param taskType
   *     the task type for which the user needs to be found
   * @return the user with the minimal workload assigned to the given task type
   * @throws OBException
   *     if no task type is given or if no users are found that are available for the given task type
   */
  @Override
  public User findUserAccordingStrategy(TaskType taskType) {
    if (taskType == null) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoTaskTypeFound"));
    }

    List<User> availableUsers = getUsersAvailable(taskType);
    if (availableUsers.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoUsersFound"));
    }

    List<Task> allTasks = TaskUtil.preloadTasks(availableUsers);
    Map<User, Long> userOpenTasks = computeOpenTasksByUser(allTasks);
    long minLoad = userOpenTasks.values().stream().min(Comparator.naturalOrder()).orElse(0L);

    List<User> minimalLoadOps = availableUsers.stream()
        .filter(u -> userOpenTasks.getOrDefault(u, 0L) == minLoad)
        .collect(Collectors.toList());

    if (minimalLoadOps.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoAvailableUsersByLoad"));
    }

    int currentIndex = (taskType.getRoundRobinIndex() != null) ? taskType.getRoundRobinIndex().intValue() : 0;
    User selectedUser = minimalLoadOps.get(currentIndex);

    TaskUtil.updateRoundRobinIndex(taskType, currentIndex + 1, minimalLoadOps.size());

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

    return allTasks.stream()
        .filter(wt -> {
          Status st = wt.getStatus();
          return (pendingStatus.equals(st) || inProgressStatus.equals(st));
        })
        .collect(Collectors.groupingBy(Task::getAssignedUser, Collectors.counting()));
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