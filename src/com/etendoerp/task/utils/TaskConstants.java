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
  public static final String TASK_TYPE_ID_PROPERTY = "etask_task_type_id";
  public static final String ADMIN_USER = "100";
  public static final String AD_CLIENT_ATTR = "ad_client_id";
  public static final String AD_ORG_ATTR = "ad_org_id";
  public static final String SOURCE = "source";
  public static final String TABLE = "table";
  public static final String VERB = "verb";
  public static final String OPERATION = "op";
  public static final String BEFORE = "before";
  public static final String AFTER = "after";
  public static final String TABLE_EVENTS_REF = "687091A6C1A1406EA9942575D671EBE8";
  public static final String NEXT = "next";
  public static final String MESSAGE = "message";
  public static final String STATE = "state";
  public static final String STATUS = "status";
  public static final String ERROR = "error";
  public static final String TOPIC = "topic";
  public static final String CREATED_AUTOMATICALLY = "created_automatically";
  public static final String TASK = "task";
  public static final String ASSIGNED_ROLE = "assigned_role";
  public static final String ASSIGNED_USER = "assigned_user";
  public static final String AD_CLIENT_ID = "ad_client_id";
  public static final String AD_ORG_ID = "ad_org_id";

  /* Task constants */
  public static final String TASK_TABLENAME = "etask_task";
  public static final String TASK_NO = "Taskno";

  /* Table Events constants */
  public static final String TABLE_CREATE = "insert";
  public static final String TABLE_UPDATE = "update";
  public static final String TABLE_DELETE = "delete";

}
