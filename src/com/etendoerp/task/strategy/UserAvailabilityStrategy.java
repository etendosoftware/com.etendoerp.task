package com.etendoerp.task.strategy;

import java.util.List;

import org.codehaus.jettison.json.JSONObject;
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
   *
   * <p>The strategy may consider factors such as user availability, workload,
   * task type, section, or any custom rules. Input parameters may provide
   * additional context such as shipment, locator, or filters.</p>
   *
   * @param taskType
   *     the task type to find a user for
   * @param parameters
   *     additional input parameters that may influence the strategy
   * @return a user selected according to the strategy
   */
  User findUserAccordingStrategy(TaskType taskType, JSONObject parameters);

  /**
   * Returns a list of all users available for the given task type,
   * based on the implementing strategy.
   *
   * <p>The availability of users may be determined based on task type,
   * workload, roles, sections, or external input via parameters.</p>
   *
   * @param taskType
   *     the task type to get available users for
   * @param parameters
   *     additional input parameters that may influence the result
   * @return a list of users available for the task type
   */
  List<User> getUsersAvailable(TaskType taskType, JSONObject parameters);
}
