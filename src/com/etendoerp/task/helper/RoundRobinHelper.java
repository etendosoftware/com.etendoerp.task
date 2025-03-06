package com.etendoerp.task.helper;

import java.util.List;
import java.util.function.Function;

import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * The RoundRobinHelper class provides utility methods for assigning users to tasks
 * using a specified strategy function.
 * <p>
 * This class is designed to support different user assignment strategies, such as
 * Round Robin or Workload-based assignment. It determines the appropriate user for
 * each task by applying a given function to the task type.
 * <p>
 * ### Functionality:
 * - Iterates through a list of tasks.
 * - Uses the provided `strategyFunction` to determine which user should be assigned to each task.
 * - Assigns the selected user to the task and saves the task in the database.
 * <p>
 * ### Dependencies:
 * - `OBDal`: Handles database operations for persisting task assignments.
 * - `Task`: Represents the task to which a user is assigned.
 * - `TaskType`: Used to determine the appropriate user selection strategy.
 * - `User`: Represents the assigned user.
 */
public class RoundRobinHelper {

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException
   *     if an attempt is made to instantiate the class.
   */
  private RoundRobinHelper() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Assigns users to a list of tasks, using the given strategy function
   * to determine the user for each task.
   *
   * @param tasks
   *     the list of tasks to assign users to
   * @param strategyFunction
   *     the function to determine the user for each task
   */
  public static void assignUsers(List<Task> tasks, Function<TaskType, User> strategyFunction) {
    for (Task task : tasks) {
      User assignedUser = strategyFunction.apply(task.getTaskType());
      task.setAssignedUser(assignedUser);
      OBDal.getInstance().save(task);
    }
  }
}
