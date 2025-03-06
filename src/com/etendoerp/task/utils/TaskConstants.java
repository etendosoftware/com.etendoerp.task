package com.etendoerp.task.utils;

/**
 * The TaskConstants class defines constant values used across the task management system.
 * <p>
 * This class serves as a centralized location for defining **static** and **final** constants
 * related to tasks, ensuring consistency and avoiding hardcoded string values in the codebase.
 * <p>
 * ### Design:
 * - The constructor is **private** to prevent instantiation since this is a utility class.
 * - The class throws `UnsupportedOperationException` if instantiation is attempted.
 * - Contains only **public static final** constants.
 */
public class TaskConstants {

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * @throws UnsupportedOperationException
   *     if an attempt is made to instantiate the class.
   */
  private TaskConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /* Generic constants */
  public static final String TASK_ID_PROPERTY = "task_id";
}
