/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.task.eventhandler;

import java.util.Date;

import jakarta.enterprise.event.Observes;

import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Task;

/**
 * Validates Task start and due dates on save and update.
 */
public class TaskDateValidation extends EntityPersistenceEventObserver {

  private final Entity[] entities = { ModelProvider.getInstance().getEntity(Task.ENTITY_NAME) };

  /**
   * Returns the entities observed by this handler.
   *
   * @return the array of observed entities
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Validates task dates on entity creation.
   *
   * @param event
   *     the entity new event being observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    validateDates(event);
  }

  /**
   * Validates task dates on entity update.
   *
   * @param event
   *     the entity update event being observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }
    validateDates(event);
  }

  /**
   * Ensures the due date is not earlier than the start date.
   *
   * @param event
   *     the persistence event providing current field values
   * @throws OBException
   *     if due date is before start date
   */
  protected void validateDates(EntityPersistenceEvent event) {
    Entity entity = event.getTargetInstance().getEntity();
    Property startProp = entity.getProperty(Task.PROPERTY_STARTDATE);
    Property dueProp = entity.getProperty(Task.PROPERTY_DUEDATE);
    Date startDate = (Date) event.getCurrentState(startProp);
    Date dueDate = (Date) event.getCurrentState(dueProp);
    if (startDate != null && dueDate != null && dueDate.before(startDate)) {
      throw new OBException(OBMessageUtils.messageBD("ETASK_DueDateBeforeStartDate"));
    }
  }
}
