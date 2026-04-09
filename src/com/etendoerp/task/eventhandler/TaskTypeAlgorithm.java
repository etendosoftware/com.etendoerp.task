package com.etendoerp.task.eventhandler;

import jakarta.enterprise.event.Observes;

import org.apache.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.data.UserAlgorithm;

/**
 * Event handler for TaskType and Table entities.
 * <p>
 * This class enforces business rules regarding the assignment of user algorithms to TaskType entities and their relationship with Table records.
 * <ul>
 *   <li>If a TaskType does not have a user assignment algorithm, it cannot have associated Table records.</li>
 *   <li>Handles create, update, and delete events for TaskType and Table entities.</li>
 * </ul>
 * </p>
 */
public class TaskTypeAlgorithm extends EntityPersistenceEventObserver {

  private static Entity[] entities = {
      ModelProvider.getInstance().getEntity(TaskType.class),
      ModelProvider.getInstance().getEntity(Table.class)
  };

  protected Logger logger = Logger.getLogger(TaskTypeAlgorithm.class);

  /**
   * Returns the entities that this observer listens to.
   *
   * @return an array of entities observed by this handler
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles the update event for observed entities. Validates and processes TaskType and Table updates.
   *
   * @param event
   *     the entity update event to be observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    boolean isTT = isTaskType(event);
    Entity entity = event.getTargetInstance().getEntity();
    if (isTT) {
      handleTaskTypeUpdate(event, entity);
    } else {
      handleTableUpdate(event);
    }
  }

  /**
   * Handles the save event for observed entities. Validates and processes Table saves.
   *
   * @param event
   *     the entity save event to be observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    boolean isTT = isTaskType(event);
    if (!isTT) {
      handleTableSave(event);
    }
  }


  /**
   * Determines if the event's target entity is a TaskType.
   *
   * @param event
   *     the entity persistence event
   * @return true if the entity is TaskType, false otherwise
   */
  public boolean isTaskType(EntityPersistenceEvent event) {
    Entity targetEntity = event.getTargetInstance().getEntity();
    Entity taskTypeEnt = ModelProvider.getInstance().getEntity(TaskType.class);
    return targetEntity.equals(taskTypeEnt);
  }

  /**
   * Handles logic for TaskType update events, validating user algorithm and table records.
   *
   * @param event
   *     the entity update event
   * @param entity
   *     the entity being updated
   */
  public void handleTaskTypeUpdate(EntityUpdateEvent event, Entity entity) {
    TaskType taskType = (TaskType) event.getTargetInstance();
    Property propertyAlg = entity.getProperty(TaskType.PROPERTY_USERALGORITHM);
    UserAlgorithm alg = (UserAlgorithm) event.getCurrentState(propertyAlg);
    if (alg == null && !taskType.getETASKTableList().isEmpty()) {
      throwErrorException();
    }
  }

  /**
   * Throws an exception indicating that a TaskType cannot have associated Table records
   * without a user assignment algorithm.
   *
   * <p>This method retrieves a localized error message using the key
   * "ETASK_TaskTypeNoAlgorithmTable" and throws an `OBException` with the message.
   *
   * @throws OBException
   *     Always thrown with a localized error message.
   */
  public static void throwErrorException() {
    throw new OBException(OBMessageUtils.messageBD("ETASK_TaskTypeNoAlgorithmTable"));
  }

  /**
   * Handles logic for Table update events, validating user algorithm assignment.
   *
   * @param event
   *     the entity update event
   */
  public void handleTableUpdate(EntityUpdateEvent event) {
    Table table = (Table) event.getTargetInstance();
    TaskType taskType = table.getTaskType();
    if (taskType.getUserAlgorithm() == null) {
      throwErrorException();
    }
  }

  /**
   * Handles logic for Table save events, validating user algorithm assignment.
   *
   * @param event
   *     the entity new event
   */
  public void handleTableSave(EntityNewEvent event) {
    Table table = (Table) event.getTargetInstance();
    TaskType taskType = table.getTaskType();
    if (taskType.getUserAlgorithm() == null) {
      throwErrorException();
    }
  }
}
