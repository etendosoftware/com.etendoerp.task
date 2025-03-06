package com.etendoerp.task.strategy.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;

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

    List<Task> allTasks = preloadTasks(availableUsers);
    Map<User, Long> userOpenTasks = computeOpenTasksByUser(allTasks);
    long minLoad = userOpenTasks.values()
        .stream()
        .min(Comparator.naturalOrder())
        .orElse(0L);

    List<User> minimalLoadOps = availableUsers.stream()
        .filter(u -> userOpenTasks.getOrDefault(u, 0L) == minLoad)
        .collect(Collectors.toList());

    if (minimalLoadOps.isEmpty()) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_NoAvailableUsersByLoad"));
    }

    Long currentIndexBD = taskType.getRrindex();
    int currentIndex = currentIndexBD != null ? currentIndexBD.intValue() : 0;

    if (currentIndex >= minimalLoadOps.size()) {
      currentIndex = 0;
    }

    User selectedUser = minimalLoadOps.get(currentIndex);

    currentIndex++;
    if (currentIndex >= minimalLoadOps.size()) {
      currentIndex = 0;
    }

    taskType.setRrindex((long) currentIndex);
    OBDal.getInstance().save(taskType);
    OBDal.getInstance().flush();

    return selectedUser;
  }

  /**
   * Get all users that are available for the given task type. The method returns the list of all users
   * ordered by username.
   *
   * @param taskType
   *     the task type for which the users need to be found
   * @return the list of all users ordered by username
   */
  @Override
  public List<User> getUsersAvailable(TaskType taskType) {
    try {
      OBContext.setAdminMode(true);
      OBCriteria<User> criteria = OBDal.getInstance().createCriteria(User.class);
      criteria.addOrderBy(User.PROPERTY_USERNAME, true);

      return criteria.list();
    } catch (Exception e) {
      throw new OBException(e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Compute the number of open tasks for each user.
   *
   * @param allTasks
   *     the list of all tasks
   * @return a map with the user as key and the number of open tasks as value
   */
  private Map<User, Long> computeOpenTasksByUser(List<Task> allTasks) {
    Status pendingStatus = getStatus("PE");
    Status inProgressStatus = getStatus("IP");

    return allTasks.stream()
        .filter(wt -> {
          Status st = wt.getStatus();
          return (pendingStatus.equals(st) || inProgressStatus.equals(st));
        })
        .collect(Collectors.groupingBy(
            wt -> wt.getAssignedUser(),
            Collectors.counting()
        ));
  }

  /**
   * Retrieves the status object based on the provided status identifier.
   *
   * @param statusIdentifier
   *     the identifier of the status to be fetched
   * @return the Status object that matches the given identifier, or null if no matching status is found
   */
  private Status getStatus(String statusIdentifier) {
    OBCriteria<Status> criteria = OBDal.getInstance().createCriteria(Status.class);
    criteria.add(Restrictions.eq(Status.PROPERTY_IDENTIFIER, statusIdentifier));
    criteria.setMaxResults(1);
    return (Status) criteria.uniqueResult();
  }

  /**
   * Loads and retrieves tasks assigned to the specified users.
   * <p>
   * This method queries the database to fetch all tasks associated with the provided list
   * of users, specifically those that are currently assigned to them. It returns a list
   * of these tasks.
   * </p>
   *
   * @param users
   *     the list of users for whom tasks need to be preloaded
   * @return a list of tasks assigned to the given users
   */
  public static List<Task> preloadTasks(List<User> users) {
    OBCriteria<Task> warehouseTaskCriteria = OBDal.getInstance().createCriteria(Task.class);
    warehouseTaskCriteria.add(Restrictions.in(Task.PROPERTY_ASSIGNEDUSER, users));
    return warehouseTaskCriteria.list();
  }
}