package com.etendoerp.task;

/**
 * Defines constant values used for task-related tests in the Etendo ERP system.
 * This utility class is not instantiable and provides string constants for table names,
 * verbs, status and type IDs, topics, messages, and error descriptions.
 */
public class TaskTestsConstants {
  /**
   * Private constructor to prevent instantiation of this utility class.
   * This class is meant to be used as a collection of constants only.
   */
  private TaskTestsConstants() {
    throw new AssertionError("Not instantiable");
  }

  public static final String OTHER_TABLE_NAME = "SomeOtherTable";
  public static final String CREATE_VERB = "create";
  public static final String DELETE_VERB = "delete";
  public static final String STATUS_ID = "status123";
  public static final String TASK_TYPE_ID = "taskType456";
  public static final String OLD_STATUS_ID = "oldStatus789";
  public static final String TOPIC1 = "topic1";
  public static final String TOPIC2 = "topic2";

  public static final String TASK_ID = "task123";
  public static final String NO_TASK_FOUND = "No task found";
  public static final String USER_ASSIGNED = "User assigned to task";

  public static final String SUCCESS_MESSAGE = "User assigned to task";
  public static final String NO_TASK_ERROR = "No task found";

  public static final String STATE_ID = "state456";
  public static final String OPERATION_DELETE = "d";
  public static final String MISSING_VERB_MSG = "Missing verb message";
  public static final String TOPIC3 = "topic3";

  public static final String NO_TASK_TYPE_ERROR = "No task type found";
  public static final String NO_USERS_ERROR = "No users found";
  public static final String OUT_JSON = "outJson";
  public static final String TEST_TABLE = "test_table";
  public static final String ORG_ID = "org123";


}
