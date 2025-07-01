package com.etendoerp.task.utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Hibernate;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.task.data.Events;
import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.strategy.UserAvailabilityStrategy;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Data;
import com.smf.jobs.Result;

/**
 * Utility class for managing task-related operations in Etendo ERP.
 *
 * <p>Provides static methods to support:
 * <ul>
 *   <li>Creating tasks based on matching rules and initial states.</li>
 *   <li>Validating filters and executing advanced logic using JEXL or scheduled actions.</li>
 *   <li>Accessing and manipulating related entities such as users, states, tables, events, and task types.</li>
 *   <li>Validating and normalizing event parameters for task processing.</li>
 * </ul>
 *
 * <p>This class is marked as {@code final} and has a private constructor, preventing instantiation
 * and inheritance. It is designed to offer reusable logic across jobs and processes in the task module.
 *
 * <p>Key dependencies:
 * <ul>
 *   <li>{@link OBDal} for data access and persistence operations.</li>
 *   <li>{@link JexlEngine} for dynamic expression evaluation.</li>
 *   <li>{@link Action} for executing custom user-defined logic.</li>
 * </ul>
 */
public final class TaskUtil {

  private static final Logger log = LogManager.getLogger(TaskUtil.class);

  private TaskUtil() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Represents the outcome of executing a scheduled {@link Action}.
   *
   * <p>This class is used to encapsulate the result of an action execution,
   * indicating whether it was successful and holding the resulting message, if any.
   * The message is typically returned as a {@link JSONObject}, or {@code null} if no message was produced.
   *
   * <p>Instances of this class are immutable and are returned from utility methods such as
   * {@link TaskUtil#runAction(Process, JSONObject)}.
   *
   * @see Action
   * @see TaskUtil#runAction(Process, JSONObject)
   */
  public static class ActionOutcome {
    /**
     * Indicates whether the action was successfully executed.
     *
     * <p>If {@code true}, the action completed without errors.
     * If {@code false}, the action failed or threw an exception.
     */
    public final boolean success;

    /**
     * Optional message returned by the action.
     *
     * <p>This is typically a JSON object containing additional information
     * about the action's result. It may be {@code null} if the action
     * did not produce a structured message.
     */
    public final JSONObject message;

    ActionOutcome(boolean success, JSONObject message) {
      this.success = success;
      this.message = message;
    }
  }

  /**
   * Executes the given {@link Process} as an {@link Action} and returns the outcome of the execution.
   * <p>
   * The process is expected to be an {@link Action} implementation, and the provided JSON payload is
   * passed to the action as its parameters.
   * <p>
   * The method returns an {@link ActionOutcome} that contains the success status and the message
   * returned by the action. If the action does not return a JSON message, the message is null.
   * <p>
   * If any error occurs during the execution, the method throws an {@link OBException}.
   *
   * @param proc
   *     the process to execute
   * @param payload
   *     the JSON payload to pass as parameters to the action
   * @return the outcome of the action execution
   */
  public static ActionOutcome runAction(Process proc, JSONObject payload) {

    String className = proc.getJavaClassName();
    if (StringUtils.isBlank(className)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETASK_ProcessWithoutClassName"), proc.getName()));
    }

    try {
      Class<?> clazz = Class.forName(className);
      if (!Action.class.isAssignableFrom(clazz)) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETASK_ProcessIsNotAction"), proc.getName()));
      }

      Action act = (Action) clazz.getDeclaredConstructor().newInstance();

      Method setParams = Action.class.getDeclaredMethod("setParameters", JSONObject.class);
      setParams.setAccessible(true);
      setParams.invoke(act, payload);

      ActionResult ar = act.run(new Data(), new MutableBoolean(false));

      JSONObject msg = null;
      String raw = ar.getMessage();
      if (raw != null && !raw.isBlank()) {
        msg = safeParseJson(raw, className);
      }
      return new ActionOutcome(Result.Type.SUCCESS.equals(ar.getType()), msg);

    } catch (Exception e) {
      log.error("Error running Action {}", className, e);
      throw new OBException(e);
    }
  }

  /**
   * Safely parses a raw string into a JSON object.
   *
   * <p>This method attempts to convert the given raw string into a
   * {@link JSONObject}. If the string cannot be parsed as JSON, it logs
   * a debug message and returns null.
   *
   * @param raw
   *     the raw string to parse
   * @param className
   *     the name of the class invoking this method, used for logging purposes
   * @return the parsed JSON object, or null if parsing fails
   */
  private static JSONObject safeParseJson(String raw, String className) {
    try {
      return new JSONObject(raw);
    } catch (JSONException je) {
      log.debug("Action {} returned plain text message: {}", className, raw);
      return null;
    }
  }

  /**
   * Validates a JEXL filter against the given data.
   *
   * <p>This method will return {@code false} if the filter is invalid or does not return a boolean
   * value.
   *
   * @param filter
   *     JEXL filter string
   * @param data
   *     JSON object containing data to evaluate against
   * @return whether the filter validated against the data
   */
  public static boolean validateFilter(String filter, JSONObject data) {
    try {
      JexlEngine eng = new JexlBuilder().create();
      JexlContext ctx = new MapContext();
      Iterator<?> it = data.keys();
      while (it.hasNext()) {
        String k = (String) it.next();
        ctx.set(k, data.get(k));
      }
      JexlExpression expr = eng.createExpression(filter);
      Object res = expr.evaluate(ctx);
      if (!(res instanceof Boolean)) {
        log.warn("Filter '{}' did not return boolean, got {}", filter, res);
        return false;
      }
      return (Boolean) res;

    } catch (Exception e) {
      log.error("Filter evaluation failed", e);
      return false;
    }
  }

  /**
   * Executes the advanced logic (an {@link Action}) associated with the given {@link Table} rule.
   * <p>
   * If the rule has no advanced logic, this method simply returns true.
   * <p>
   * The action is executed with the given data as context. The method returns true if the action
   * succeeds, false otherwise.
   *
   * @param rule
   *     The {@link Table} rule to execute.
   * @param data
   *     Data to use as context for the advanced logic action.
   * @return true if the action succeeds, false otherwise.
   */
  public static boolean executeAdvancedLogic(Table rule, JSONObject data) {
    if (rule.getAction() == null) {
      return true;
    }
    ActionOutcome out = runAction(rule.getAction(), data);
    return out.success;
  }

  /**
   * Retrieves a list of active users.
   * <p>
   * This method runs in <strong>admin mode</strong>, so it is not affected by the
   * current user's permissions. The returned list is ordered alphabetically by
   * username.
   *
   * @return a list of active User instances
   * @see OBContext#setAdminMode(boolean)
   */
  public static List<User> getActiveUsers() {
    try {
      OBContext.setAdminMode(true);
      OBCriteria<User> userOBCriteria = OBDal.getInstance().createCriteria(User.class);
      userOBCriteria.add(Restrictions.ne(User.PROPERTY_ID, "0"));
      userOBCriteria.addOrderBy(User.PROPERTY_USERNAME, true);
      return userOBCriteria.list();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Retrieves a list of Task entities assigned to the given list of users.
   * <p>
   * This method creates a criteria query to search for Task entities that have
   * an assigned user matching one of the users in the given list. The query is
   * executed and the resulting list of Task entities is returned.
   * <p>
   * Dependencies:
   * <ul>
   *   <li>OBDal: Used for creating a criteria query to search for the Task entities.</li>
   *   <li>Restrictions: Applies a condition to filter the Task entities by their assigned user.</li>
   * </ul>
   *
   * @param users
   *     The list of users to find assigned tasks for.
   * @return A list of Task entities assigned to the users in the given list.
   */
  public static List<Task> preloadTasks(List<User> users) {
    OBCriteria<Task> taskOBCriteria = OBDal.getInstance().createCriteria(Task.class);
    taskOBCriteria.add(Restrictions.in(Task.PROPERTY_ASSIGNEDUSER, users));
    return taskOBCriteria.list();
  }

  /**
   * Retrieves a Status entity based on its identifier.
   *
   * <p>This method queries the database to find a single Status entity that matches
   * the provided identifier. If no matching entity is found, it returns null.
   *
   * <p>Dependencies:
   * <ul>
   *   <li>OBDal: Used for creating a criteria query to search for the Status entity.</li>
   *   <li>Restrictions: Applies a condition to filter the Status entity by its identifier.</li>
   * </ul>
   *
   * @param identifier
   *     The unique identifier of the Status to retrieve.
   * @return The matching Status entity, or null if no match is found.
   */
  public static Status getStatus(String identifier) {
    OBCriteria<Status> statusOBCriteria = OBDal.getInstance().createCriteria(Status.class);
    statusOBCriteria.add(Restrictions.eq(Status.PROPERTY_IDENTIFIER, identifier));
    statusOBCriteria.setMaxResults(1);
    return (Status) statusOBCriteria.uniqueResult();
  }

  /**
   * Updates the round-robin index for the given task type.
   * <p>
   * This method updates the round-robin index for the given task type by setting it to
   * the given index value. If the given index value is out of range, it is normalized
   * to the range [0, size) by resetting it to 0.
   * <p>
   * ### Dependencies:
   * - `OBDal`: Handles database operations for updating task type data.
   * - `TaskType`: Stores task type information and the round-robin index.
   *
   * @param taskTypeId
   *     the task type to update
   * @param idx
   *     the new index value
   * @param size
   *     the size of the round-robin list
   */
  public static void updateRoundRobinIndex(String taskTypeId, int idx, int size) {
    if (idx >= size) {
      idx = 0;
    }

    Long newIndex = (long) idx;

    OBContext currentContext = OBContext.getOBContext();
    try {
      OBContext.setOBContext("100", "0", "0", "0");

      TaskType taskType = OBDal.getInstance().get(TaskType.class, taskTypeId);
      taskType.setRoundRobinIndex(newIndex);
      OBDal.getInstance().save(taskType);
      OBDal.getInstance().flush();
    } finally {
      OBContext.setOBContext(currentContext);
    }
  }

  /**
   * Creates and saves a new {@link Task} with the given type, status, and parameters.
   * <p>
   * Uses the provided {@link OBContext} to set client, organization, and user context.
   * Optionally assigns an operator automatically.
   * </p>
   *
   * @param taskType
   *     the task type to assign
   * @param status
   *     the initial status of the task
   * @param assignOperatorAutomatically
   *     whether to assign the operator automatically
   * @param parameters
   *     JSON with additional task data (must include client and org IDs)
   * @param entityContex
   *     the OBContext to use during task creation
   * @return the created and persisted {@link Task}
   * @throws OBException
   *     if an error occurs during task creation
   */
  public static Task createTask(TaskType taskType, Status status, boolean assignOperatorAutomatically,
      JSONObject parameters, OBContext entityContex) {
    OBContext.setAdminMode(true);
    try {
      OBContext.setOBContext(entityContex);
      Task task = OBProvider.getInstance().get(Task.class);

      String clientId = parameters.optString("ad_client_id", "");
      Client client = OBDal.getInstance().get(Client.class, clientId);

      String orgId = parameters.optString("ad_org_id", "");
      Organization org = OBDal.getInstance().get(Organization.class, orgId);
      User user = OBContext.getOBContext().getUser();

      task.setTaskType(taskType);
      task.setStatus(status);
      task.setClient(client);
      task.setOrganization(org);
      task.setCreatedBy(user);
      task.setUpdatedBy(user);
      task.setEventJsoninfo(parameters.toString());

      if (assignOperatorAutomatically) {
        setTaskUser(task);
      }

      OBDal.getInstance().save(task);
      OBDal.getInstance().flush();
      return task;
    } catch (Exception e) {
      throw new OBException("Error creating Task: " + e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Sets the operator for the given task based on availability and configuration.
   *
   * @param warehouseTask
   *     The task for which to set the operator.
   */
  public static void setTaskUser(Task warehouseTask) throws JSONException {
    String paramsStr = warehouseTask.getEventJsoninfo();
    JSONObject params = new JSONObject(paramsStr);
    TaskType taskType = warehouseTask.getTaskType();
    UserAvailabilityStrategy userStrategy = getUserStrategyClass(taskType);
    User assignedUser = userStrategy.findUserAccordingStrategy(taskType, params);
    warehouseTask.setAssignedUser(assignedUser);
    OBDal.getInstance().save(warehouseTask);
  }

  /**
   * Returns an instance of {@link UserAvailabilityStrategy} based on the Java implementation
   * defined in the user algorithm associated with the given {@link TaskType}.
   *
   * @param taskType
   *     the task type containing the user assignment strategy
   * @return an instance of the user availability strategy
   * @throws OBException
   *     if the algorithm is missing or the class cannot be loaded or instantiated
   */
  public static UserAvailabilityStrategy getUserStrategyClass(TaskType taskType) throws OBException {
    OBContext currentContext = OBContext.getOBContext();
    try {
      Hibernate.initialize(taskType.getUserAlgorithm());
      OBContext.setOBContext("100", "0", "0", "0");

      if (taskType.getUserAlgorithm() == null) {
        throw new OBException(OBMessageUtils.messageBD("ETAWIM_UserAlgorithmNotFound"));
      }

      String javaImpl = taskType.getUserAlgorithm().getJavaImplementation();
      Class<?> clz = OBClassLoader.getInstance().loadClass(javaImpl);
      return (UserAvailabilityStrategy) clz.getDeclaredConstructor().newInstance();

    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new OBException(e.getMessage());
    } finally {
      OBContext.setOBContext(currentContext);
    }
  }

  /**
   * Validates and normalizes the input JSON parameters for task processing.
   *
   * <p>This method ensures that the required fields are present and correctly formatted
   * in the provided JSON object, throwing an exception if any validation fails. It
   * extracts the relevant fields and constructs a new JSON object with normalized data.
   *
   * <p>Validation checks include:
   * <ul>
   *   <li>Presence of a non-null "source" field with a valid "table" value.</li>
   *   <li>Presence of an "operation" field, mapping its value to a corresponding
   *   verb ("create", "update", or "delete").</li>
   *   <li>Optional inclusion of a "before" field, if present in the parameters.</li>
   *   <li>Mandatory inclusion of a non-null "after" field.</li>
   * </ul>
   *
   * @param parameters
   *     JSON object containing the input parameters to validate and normalize.
   * @return A new JSON object with the validated and normalized parameters.
   * @throws JSONException
   *     If any JSON parsing errors occur.
   * @throws OBException
   *     If any required field is missing or has an invalid value.
   */
  public static JSONObject validateAndNormalizeParameters(JSONObject parameters) throws JSONException {

    JSONObject out = new JSONObject();

    if (!parameters.has(TaskConstants.SOURCE) || parameters.isNull(TaskConstants.SOURCE)) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingSource"));
    }
    JSONObject src = parameters.getJSONObject(TaskConstants.SOURCE);
    if (!src.has(TaskConstants.TABLE) || src.getString(TaskConstants.TABLE).isEmpty()) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingTable"));
    }
    out.put(TaskConstants.TABLE, src.getString(TaskConstants.TABLE));
    String operation = parameters.getString(TaskConstants.OPERATION);
    String verb;

    switch (operation) {
      case "c":
        verb = TaskConstants.TABLE_CREATE;
        break;
      case "u":
        verb = TaskConstants.TABLE_UPDATE;
        break;
      case "d":
        verb = TaskConstants.TABLE_DELETE;
        break;
      default:
        throw new OBException(String.format(
            OBMessageUtils.messageBD("ETASK_InvalidDatabaseOperation"), operation));
    }

    out.put(TaskConstants.VERB, verb);

    if (parameters.has(TaskConstants.BEFORE) && !parameters.isNull(TaskConstants.BEFORE)) {
      out.put(TaskConstants.BEFORE, parameters.getJSONObject(TaskConstants.BEFORE));
    }
    if (!parameters.has(TaskConstants.AFTER) || parameters.isNull(TaskConstants.AFTER)) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingData"));
    }
    out.put(TaskConstants.AFTER, parameters.getJSONObject(TaskConstants.AFTER));

    return out;
  }


  /**
   * Creates a new task with the given task type, initial state, and additional data.
   * <p>
   * The task is created with the given task type, initial status, and the information
   * provided in the data map. The task is also marked as created automatically if
   * the given flag is set to true. The raw event JSON is stored in the task's
   * {@link Task#getEventJsoninfo()} field.
   * <p>
   * The method ensures that the client and organization are set by looking up the
   * corresponding entities in the database. If the entities are not found, an
   * exception is thrown.
   * <p>
   * The method returns the newly created task.
   *
   * @param rule
   *     the rule to create the task for
   * @param initialState
   *     the initial state of the task
   * @param data
   *     the data used to create the task
   * @param rawEvent
   *     the raw event JSON
   * @param createdAutomatically
   *     whether the task was created automatically
   * @return the newly created task
   * @throws OBException
   *     if any error occurs during the execution of this method
   */
  public static Task createTask(Table rule, State initialState, JSONObject data, JSONObject rawEvent,
      boolean createdAutomatically) {
    try {
      OBDal obd = OBDal.getInstance();

      Task newTask = OBProvider.getInstance().get(Task.class);
      newTask.setTaskType(rule.getTaskType());
      newTask.setStatus(initialState.getTaskStatus());
      newTask.setCreatedAutomatically(createdAutomatically);
      if (rawEvent != null) {
        newTask.setEventJsoninfo(rawEvent.toString());
      }

      String clientId = getRequiredString(data, TaskConstants.AD_CLIENT_ATTR);
      newTask.setClient(getRequiredEntity(Client.class, clientId, TaskConstants.AD_CLIENT_ATTR));

      String orgId = getRequiredString(data, TaskConstants.AD_ORG_ATTR);
      newTask.setOrganization(getRequiredEntity(Organization.class, orgId, TaskConstants.AD_ORG_ATTR));

      User admin = obd.get(User.class, TaskConstants.ADMIN_USER);
      newTask.setCreatedBy(admin);
      newTask.setUpdatedBy(admin);

      return newTask;
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Retrieves a required string attribute from the given JSON object.
   * If the attribute is not present or has a null value, an exception is thrown.
   *
   * @param data
   *     the JSON object containing the attribute
   * @param key
   *     the key of the attribute to retrieve
   * @return the value associated with the given key
   * @throws JSONException
   *     if the attribute is not present or has a null value
   */
  private static String getRequiredString(JSONObject data, String key) throws JSONException {
    if (!data.has(key) || data.isNull(key)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETASK_MissingAttributeInEventData"), key));
    }
    return data.getString(key);
  }

  /**
   * Retrieves a required entity from the database by its id.
   * If the entity is not found, an exception is thrown.
   *
   * @param clazz
   *     the class of the entity to retrieve
   * @param id
   *     the id of the entity to retrieve
   * @param field
   *     the field name of the entity used in the error message
   * @return the required entity
   * @throws OBException
   *     if the entity is not found
   */
  private static <T> T getRequiredEntity(Class<T> clazz, String id, String field) {
    T genericEntity = OBDal.getInstance().get(clazz, id);
    if (genericEntity == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETASK_EntityNotFound"), id, field));
    }
    return genericEntity;
  }

  /**
   * Retrieves the initial state for a given task type.
   * The initial state is the state with the lowest sequence number.
   * If no state is found for the given task type, an exception is thrown.
   *
   * @param taskType
   *     the task type to get the initial state for
   * @return the initial state associated with the given task type
   * @throws OBException
   *     if no state is found for the given task type
   */
  public static State getInitialState(TaskType taskType) {
    OBCriteria<State> stateOBCriteria = OBDal.getInstance().createCriteria(State.class);
    stateOBCriteria.add(Restrictions.eq(State.PROPERTY_TASKTYPE, taskType));
    stateOBCriteria.addOrderBy(State.PROPERTY_SEQUENCENO, true);
    stateOBCriteria.setMaxResults(1);
    State state = (State) stateOBCriteria.uniqueResult();
    if (state == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_NoInitialState"));
    }
    return state;
  }

  /**
   * Retrieves the {@link org.openbravo.model.ad.datamodel.Table} corresponding to the specified
   * table name.
   *
   * <p>This method searches for a table in the database using a case-insensitive match with the
   * provided table name. It returns the first matching table, or null if no match is found.
   *
   * @param tableName
   *     The name of the table to retrieve.
   * @return The matching table, or null if no match is found.
   */
  public static org.openbravo.model.ad.datamodel.Table getADTable(String tableName) {
    OBCriteria<org.openbravo.model.ad.datamodel.Table> tableOBCriteria =
        OBDal.getInstance().createCriteria(org.openbravo.model.ad.datamodel.Table.class);
    tableOBCriteria.add(
        Restrictions.ilike(org.openbravo.model.ad.datamodel.Table.PROPERTY_DBTABLENAME, tableName, MatchMode.EXACT));
    tableOBCriteria.setMaxResults(1);
    return (org.openbravo.model.ad.datamodel.Table) tableOBCriteria.uniqueResult();
  }

  /**
   * Retrieves the search key of an event list entry based on the provided verb.
   *
   * <p>This method searches for an entry in the list associated with the reference
   * defined by {@link TaskConstants#TABLE_EVENTS_REF} where the name matches the
   * specified verb, ignoring case. It returns the search key of the first matching
   * entry found, or null if no match is found.
   *
   * @param verb
   *     The verb to match against the list entry names.
   * @return The search key of the matching list entry, or null if no match is found.
   */
  public static String getEventValue(String verb) {
    Reference ref = OBDal.getInstance().get(Reference.class, TaskConstants.TABLE_EVENTS_REF);
    OBCriteria<org.openbravo.model.ad.domain.List> listOBCriteria =
        OBDal.getInstance().createCriteria(org.openbravo.model.ad.domain.List.class);
    listOBCriteria.add(Restrictions.eq(org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE, ref));
    listOBCriteria.add(Restrictions.ilike(org.openbravo.model.ad.domain.List.PROPERTY_NAME, verb));
    listOBCriteria.setMaxResults(1);
    org.openbravo.model.ad.domain.List event = (org.openbravo.model.ad.domain.List) listOBCriteria.uniqueResult();
    return event != null ? event.getSearchKey() : null;
  }

  /**
   * Finds all matching {@link Table} rules based on the given
   * {@link org.openbravo.model.ad.datamodel.Table} and event identifier.
   *
   * @param table
   *     the table for which to find matching rules
   * @param eventIdentifier
   *     the event identifier to match
   * @return a list of matching {@link Table} instances
   */
  public static List<Table> getMatchingRules(org.openbravo.model.ad.datamodel.Table table, String eventIdentifier) {
    OBCriteria<Table> tableOBCriteria = OBDal.getInstance().createCriteria(Table.class);
    tableOBCriteria.add(Restrictions.eq(Table.PROPERTY_TABLE, table));
    tableOBCriteria.add(Restrictions.eq(Table.PROPERTY_TABLEEVENTS, eventIdentifier));
    return tableOBCriteria.list();
  }

  /**
   * Finds a state with the given task type ID and status identifier.
   *
   * @param statusId
   *     the status identifier
   * @param taskTypeId
   *     the task type ID
   * @return the matching state, or null if no match is found
   */
  public static State findStateByStatusId(String statusId, String taskTypeId) {
    OBCriteria<State> stateOBCriteria = OBDal.getInstance().createCriteria(State.class);
    stateOBCriteria.add(Restrictions.eq(State.PROPERTY_TASKTYPE,
        OBDal.getInstance().get(TaskType.class, taskTypeId)));
    stateOBCriteria.createAlias(State.PROPERTY_TASKSTATUS, "st");
    stateOBCriteria.add(Restrictions.eq("st.id", statusId));
    stateOBCriteria.setMaxResults(1);
    return (State) stateOBCriteria.uniqueResult();
  }

  /**
   * Executes state events associated with the given state and payload, collecting
   * any initial Kafka topics configured for the jobs linked to these events.
   *
   * <p>This method retrieves all events associated with the provided state,
   * ordered by sequence number. For each event, if a job is defined and has
   * a non-blank initial topic, that topic is added to the list of topics.
   *
   * @param state
   *     The {@link State} for which events should be executed.
   * @return A list of initial topics associated with the jobs of the executed events.
   */
  public static List<String> runStateEvents(State state) {
    List<String> topics = new ArrayList<>();
    OBCriteria<Events> eventsOBCriteria = OBDal.getInstance().createCriteria(Events.class);
    eventsOBCriteria.add(Restrictions.eq(Events.PROPERTY_STATE, state));
    eventsOBCriteria.addOrderBy(Events.PROPERTY_SEQUENCENO, true);

    eventsOBCriteria.list().forEach(e -> {
      var job = e.getJob();
      if (job != null && StringUtils.isNotBlank(job.getEtapInitialTopic())) {
        topics.add(job.getEtapInitialTopic());
      }
    });
    return topics;
  }
}