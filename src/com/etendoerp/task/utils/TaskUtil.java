package com.etendoerp.task.utils;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * Utility class for task-related operations.
 * <p>
 * The {@code TaskUtil} class provides various helper methods related to task management,
 * including retrieving active users, preloading tasks assigned to users, fetching task statuses,
 * and updating the round-robin index for task assignment strategies.
 * <p>
 * ### Design:
 * - The constructor is **private** to prevent instantiation since this is a utility class.
 * - The class throws `UnsupportedOperationException` if instantiation is attempted.
 */
public class TaskUtil {

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException
   *     if an attempt is made to instantiate the class.
   */
  private TaskUtil() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Return a list of all available users sorted by username, in ascending order.
   * <p>
   * This method is used to retrieve the list of available users for task assignment.
   * The list is sorted by username in ascending order, and the result is cached.
   * </p>
   *
   * @return a list of available users sorted by username.
   */
  public static List<User> getActiveUsers() {
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
   * Loads and retrieves tasks assigned to the specified users.
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

  /**
   * Retrieves the status object based on the provided status identifier.
   *
   * @param statusIdentifier
   *     the identifier of the status to be fetched
   * @return the Status object that matches the given identifier, or null if no matching status is found
   */
  public static Status getStatus(String statusIdentifier) {
    OBCriteria<Status> criteria = OBDal.getInstance().createCriteria(Status.class);
    criteria.add(Restrictions.eq(Status.PROPERTY_IDENTIFIER, statusIdentifier));
    criteria.setMaxResults(1);
    return (Status) criteria.uniqueResult();
  }

  /**
   * Updates the round-robin index in the task type, ensuring it wraps around correctly.
   *
   * @param taskType
   *     the task type being updated
   * @param currentIndex
   *     the current index to update
   * @param size
   *     the total number of users available
   */
  public static void updateRoundRobinIndex(TaskType taskType, int currentIndex, int size) {
    if (currentIndex >= size) {
      currentIndex = 0;
    }
    taskType.setRoundRobinIndex((long) currentIndex);
    OBDal.getInstance().save(taskType);
    OBDal.getInstance().flush();
  }
}
