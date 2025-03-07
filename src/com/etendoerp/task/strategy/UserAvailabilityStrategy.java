package com.etendoerp.task.strategy;

import java.util.List;

import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.TaskType;

/**
 * The UserAvailabilityStrategy interface defines the contract for strategies
 * that determine user assignment to tasks.
 * <p>
 * Implementations of this interface can define different user assignment strategies,
 * such as **round-robin** or **workload-based** selection.
 * <p>
 * ### Responsibilities:
 * - Find and return an available user based on a specific strategy.
 * - Retrieve a list of users that are eligible for task assignment.
 * <p>
 * ### Implementing Classes:
 * - `RoundRobinStrategy`: Implements a **round-robin** user assignment strategy.
 * - `RoundRobinByWorkloadStrategy`: Assigns users based on their current workload.
 */
public interface UserAvailabilityStrategy {

  /**
   * Finds a user according to the implementing strategy.
   * The strategy may take into account the availability of users,
   * and may also take into account the task type.
   *
   * @param taskType
   *     the task type to find a user for
   * @return a user that is available for the given task type
   */
  User findUserAccordingStrategy(TaskType taskType);

  /**
   * Get a list of all users available for a given task type.
   * Availability of a user is determined by the implementing strategy.
   *
   * @param taskType
   *     the task type to get users for
   * @return a list of users available for the given task type
   */
  List<User> getUsersAvailable(TaskType taskType);
}
