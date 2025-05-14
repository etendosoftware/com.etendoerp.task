package com.etendoerp.task.utils;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Process;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.domain.Reference;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.task.data.State;
import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Data;
import com.smf.jobs.Result;

/**
 * <h2>TaskUtil</h2>
 *
 * <p>Centralised helper methods for task-related operations <em>and</em>
 * shared logic used by Etendo task {@code Action}s.</p>
 *
 * <p>Two categories of utilities coexist:</p>
 * <ol>
 *   <li><strong>Business helpers</strong> – original methods dealing with users,
 *       task pre-loading and round-robin indices.</li>
 *   <li><strong>Action helpers</strong> – logic formerly embedded in
 *       {@code TaskTypeMatchJob}: Debezium event normalisation, JEXL
 *       filter evaluation and execution of advanced validations
 *       (extra {@link Action}s declared in {@code etask_table}).</li>
 * </ol>
 *
 * <p>All methods are {@code static}. The constructor is private and
 * throws {@link IllegalStateException} to prevent instantiation.</p>
 *
 * <p><b>Thread-safety:</b> Methods that access Openbravo DAL must be
 * executed with care regarding {@link OBContext} and session handling.
 * Each helper opens admin mode when required and restores the previous
 * mode in a {@code finally} block.</p>
 */
public class TaskUtil {

  private static final Logger log = LogManager.getLogger(TaskUtil.class);

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

  /**
   * Validates and normalises a Debezium event so that downstream
   * {@code Action}s can consume it consistently.
   *
   * <ul>
   *   <li>{@code table} – exact table name.</li>
   *   <li>{@code verb}  – {@code create|update|delete}.</li>
   *   <li>{@code data}  – original <b>after</b> JSON object.</li>
   * </ul>
   *
   * @param parameters
   *     raw Debezium-style JSON received from Kafka.
   * @return a new {@link JSONObject} with the three keys above.
   * @throws JSONException
   *     for malformed JSON.
   * @throws OBException
   *     for missing required fields.
   */
  public static JSONObject validateAndNormalizeParameters(JSONObject parameters) throws JSONException {

    JSONObject normalized = new JSONObject();

    if (!parameters.has("source") || parameters.isNull("source")) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingSource"));
    }
    JSONObject source = parameters.getJSONObject("source");
    if (!source.has("table") || source.getString("table").isEmpty()) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingTable"));
    }
    normalized.put("table", source.getString("table"));

    if (!parameters.has("op") || parameters.getString("op").isEmpty()) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingVerb"));
    }
    String verb;
    switch (parameters.getString("op")) {
      case "c":
        verb = "create";
        break;
      case "u":
        verb = "update";
        break;
      case "d":
        verb = "delete";
        break;
      default:
        throw new OBException("Invalid operation: " + parameters.getString("op"));
    }
    normalized.put("verb", verb);

    if (!parameters.has("after") || parameters.isNull("after")) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_MissingData"));
    }
    JSONObject data = parameters.getJSONObject("after");
    normalized.put("data", data);

    return normalized;
  }

  /**
   * Evaluates a JEXL expression against the JSON payload of the event.
   * Any checked/unchecked exception is logged and treated as
   * <i>filter not passed</i>.
   *
   * @param filter
   *     filter expression stored in {@code etask_table.filter}.
   * @param data
   *     {@code after} section of the Debezium event.
   * @return {@code true} if the expression returns <i>true</i>; {@code false} otherwise.
   */
  public static boolean validateFilter(String filter, JSONObject data) {
    try {
      JexlEngine engine = new JexlBuilder().create();
      JexlContext ctx = new MapContext();
      Iterator<String> it = data.keys();
      while (it.hasNext()) {
        String key = it.next();
        ctx.set(key, data.get(key));
      }
      JexlExpression expr = engine.createExpression(filter);
      Object result = expr.evaluate(ctx);

      if (!(result instanceof Boolean)) {
        log.warn("Filter '{}' did not return boolean: {}", filter, result);
        return false;
      }
      return (Boolean) result;

    } catch (Exception e) {
      log.error("Error evaluating filter '{}'", filter, e);
      return false;
    }
  }

  /**
   * Executes the <i>advanced logic</i> {@link Action} referenced in
   * {@code etask_table.advanced_logic_action_id}. Reflection is used
   * only to access {@code setParameters(JSONObject)} (protected in
   * the base class). The invoked action runs synchronously
   * <strong>in the same transaction and thread</strong>.
   *
   * @param rule
   *     configuration row from {@code etask_table}.
   * @param data
   *     full event payload to be passed to the advanced action.
   * @return {@code true} if the advanced action finishes with
   *     {@link Result.Type#SUCCESS}; {@code false} otherwise.
   * @throws OBException
   *     wraps any error thrown while executing the action.
   */
  @SuppressWarnings("java:S3011") // accessing protected member via reflection
  public static boolean executeAdvancedLogic(Table rule, JSONObject data) {

    if (rule.getAction() == null) {
      log.debug("Rule without advanced action – automatically passed");
      return true;
    }
    try {
      Process proc = rule.getAction();
      String className = proc.getJavaClassName();

      if (className == null || className.isBlank()) {
        log.warn("Process {} without javaClassName – rule skipped", proc.getName());
        return false;
      }

      Class<?> clazz = Class.forName(className);
      if (!Action.class.isAssignableFrom(clazz)) {
        throw new OBException("Process " + proc.getName() + " does not extend Action");
      }
      Action advAction = (Action) clazz.getDeclaredConstructor().newInstance();

      /* inject parameters */
      Method setParams = Action.class.getDeclaredMethod("setParameters", JSONObject.class);
      setParams.setAccessible(true);
      setParams.invoke(advAction, data);

      ActionResult result = advAction.run(new Data(), new MutableBoolean(false));
      return Result.Type.SUCCESS.equals(result.getType());

    } catch (Exception e) {
      log.error("Advanced logic failed", e);
      throw new OBException("Advanced logic validation failed: " + e.getMessage(), e);
    }
  }

  /**
   * Creates a new {@link Task} instance based on the given rule and initial state,
   * using context data provided in a JSON object.
   *
   * @param rule
   *     The table rule defining the task type.
   * @param initialState
   *     The initial state to assign to the task.
   * @param data
   *     A JSON object containing required context fields, including:
   *     <ul>
   *       <li><b>ad_client_id</b>: ID of the client</li>
   *       <li><b>ad_org_id</b>: ID of the organization</li>
   *     </ul>
   * @return A newly created and initialized {@link Task}.
   * @throws OBException
   *     If required fields are missing or contain invalid values.
   */
  public static Task createTask(Table rule, State initialState, JSONObject data) {
    try {
      OBDal obdal = OBDal.getInstance();

      Task task = OBProvider.getInstance().get(Task.class);
      task.setTaskType(rule.getTaskType());
      task.setStatus(initialState.getTaskStatus());
      task.setCreatedAutomatically(true);

      String clientId = getRequiredString(data, TaskConstants.AD_CLIENT_ATTR);
      Client client = getRequiredEntity(Client.class, clientId, TaskConstants.AD_CLIENT_ATTR);
      task.setClient(client);

      String orgId = getRequiredString(data, TaskConstants.AD_ORG_ATTR);
      Organization organization = getRequiredEntity(Organization.class, orgId, TaskConstants.AD_ORG_ATTR);
      task.setOrganization(organization);

      task.setCreatedBy(obdal.get(User.class, TaskConstants.ADMIN_USER));
      task.setUpdatedBy(obdal.get(User.class, TaskConstants.ADMIN_USER));
      task.setCreationDate(new Date());
      task.setUpdated(new Date());

      return task;
    } catch (Exception e) {
      log.error("Error creating task: {}", e.getMessage(), e);
      throw new OBException(e.getMessage());
    }
  }

  /**
   * Retrieves a required string value from a {@link JSONObject}, ensuring the key is present and not null.
   *
   * @param data
   *     The JSON object containing the data.
   * @param key
   *     The key to retrieve from the JSON.
   * @return The string value associated with the given key.
   * @throws OBException
   *     If the key is missing or has a null value.
   */
  private static String getRequiredString(JSONObject data, String key) throws JSONException {
    if (!data.has(key) || data.isNull(key)) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETASK_MissingAttributeInEventData"), key));
    }
    return data.getString(key);
  }

  /**
   * Retrieves an entity from the database and ensures it is not null.
   *
   * @param <T>
   *     The type of the expected entity.
   * @param entityClass
   *     The entity class.
   * @param id
   *     The ID of the entity to retrieve.
   * @param fieldName
   *     The name of the field being validated (used in error messages).
   * @return The entity instance if found.
   * @throws OBException
   *     If no entity is found with the given ID.
   */
  private static <T> T getRequiredEntity(Class<T> entityClass, String id, String fieldName) {
    T entity = OBDal.getInstance().get(entityClass, id);
    if (entity == null) {
      throw new OBException(String.format("Invalid %s: %s", fieldName, id));
    }
    return entity;
  }

  /**
   * Retrieves the initial state for a task type by selecting the state with the lowest sequence_no.
   *
   * @param taskType
   *     The TaskType entity
   * @return The initial State entity
   * @throws OBException
   *     if no initial state is found
   */
  public static State getInitialState(TaskType taskType) {
    OBCriteria<State> statusCriteria = OBDal.getInstance().createCriteria(State.class);
    statusCriteria.add(Restrictions.eq(State.PROPERTY_TASKTYPE, taskType));
    statusCriteria.addOrderBy(State.PROPERTY_SEQUENCENO, true);
    statusCriteria.setMaxResults(1);
    State state = (State) statusCriteria.uniqueResult();
    if (state == null) {
      throw new OBException(OBMessageUtils.getI18NMessage("ETASK_NoInitialState"));
    }
    return state;
  }

  /**
   * Retrieves the Etendo AD_Table record that matches the given physical database table name.
   *
   * @param tableName
   *     The physical database table name from the event.
   * @return The matching AD_Table object, or null if not found.
   */
  public static org.openbravo.model.ad.datamodel.Table getADTable(String tableName) {
    OBCriteria<org.openbravo.model.ad.datamodel.Table> tableCriteria =
        OBDal.getInstance().createCriteria(org.openbravo.model.ad.datamodel.Table.class);
    tableCriteria.add(Restrictions.ilike(org.openbravo.model.ad.datamodel.Table.PROPERTY_DBTABLENAME, tableName));
    tableCriteria.setMaxResults(1);

    return (org.openbravo.model.ad.datamodel.Table) tableCriteria.uniqueResult();
  }

  /**
   * Retrieves the internal search key (event value) for a given operation verb, based on
   * the "Table Events" reference list.
   *
   * @param verb
   *     The operation type (e.g., insert, update, delete) from the event.
   * @return The search key corresponding to the verb, or null if not found.
   */
  public static String getEventValue(String verb) {
    Reference tableEventsRef = OBDal.getInstance().get(Reference.class, TaskConstants.TABLE_EVENTS_REF);

    OBCriteria<org.openbravo.model.ad.domain.List> eventCriteria =
        OBDal.getInstance().createCriteria(org.openbravo.model.ad.domain.List.class);
    eventCriteria.add(Restrictions.eq(org.openbravo.model.ad.domain.List.PROPERTY_REFERENCE, tableEventsRef));
    eventCriteria.add(Restrictions.ilike(org.openbravo.model.ad.domain.List.PROPERTY_NAME, verb));
    eventCriteria.setMaxResults(1);

    org.openbravo.model.ad.domain.List event = (org.openbravo.model.ad.domain.List) eventCriteria.uniqueResult();
    return event != null ? event.getSearchKey() : null;
  }

  /**
   * Retrieves all etask_table rules that match the specified table and event.
   *
   * @param table
   *     The AD_Table record the rules should apply to.
   * @param eventValue
   *     The internal event key (e.g., I, U, D) from the reference list.
   * @return A list of matching Table rules, or an empty list if none found.
   */
  public static List<Table> getMatchingRules(org.openbravo.model.ad.datamodel.Table table, String eventValue) {
    OBCriteria<Table> criteria = OBDal.getInstance().createCriteria(Table.class);
    criteria.add(Restrictions.eq(Table.PROPERTY_TABLE, table));
    criteria.add(Restrictions.eq(Table.PROPERTY_TABLEEVENTS, eventValue));
    return criteria.list();
  }

}
