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

import jakarta.enterprise.event.Observes;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.SessionHandler;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.sequence.TaskTypeTransactionalSequence;

/**
 * Persistence event observer that manages automatic task number generation.
 *
 * <p>This observer reacts to task creation and updates to ensure that the task
 * number is generated or regenerated when a preview value is present or when
 * the task type changes.
 */
public class TaskSequenceObserver extends EntityPersistenceEventObserver {
  private static final Entity[] entities = { ModelProvider.getInstance().getEntity(Task.ENTITY_NAME) };

  /**
   * Returns the entities observed by this event observer.
   *
   * @return the array of observed entities
   */
  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles update events for tasks to regenerate the task number when needed.
   *
   * <p>The task number is regenerated if it contains a preview value or if the
   * task type has changed between the previous and current state.
   *
   * @param event
   *     the entity update event being observed
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Entity entity = event.getTargetInstance().getEntity();
    Property taskTypeProp = entity.getProperty(Task.PROPERTY_TASKTYPE);
    Property taskNoProp = entity.getProperty(Task.PROPERTY_TASKNO);
    TaskType oldType = (TaskType) event.getPreviousState(taskTypeProp);
    TaskType newType = (TaskType) event.getCurrentState(taskTypeProp);
    String currentTaskNo = (String) event.getCurrentState(taskNoProp);

    boolean isPreview = isPreviewValue(currentTaskNo);
    boolean typeChanged = oldType != null && !oldType.getId().equals(newType.getId());

    if (!isPreview && !typeChanged) {
      return;
    }

    TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(Task.PROPERTY_TASKNO);
    String newValue = seq.generateValue(SessionHandler.getInstance().getSession(), event.getTargetInstance());
    event.setCurrentState(taskNoProp, newValue);
  }

  /**
   * Handles save events for new tasks to replace preview task numbers.
   *
   * <p>If the task number contains a preview value at creation time, a final
   * value is generated and assigned.
   *
   * @param event
   *     the entity new event being observed
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Entity entity = event.getTargetInstance().getEntity();
    Property taskNoProp = entity.getProperty(Task.PROPERTY_TASKNO);
    String current = (String) event.getCurrentState(taskNoProp);

    if (!isPreviewValue(current)) {
      return;
    }

    TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(Task.PROPERTY_TASKNO);
    String newValue = seq.generateValue(SessionHandler.getInstance().getSession(), event.getTargetInstance());
    event.setCurrentState(taskNoProp, newValue);
  }

  /**
   * Determines whether the given value represents a preview task number.
   *
   * <p>A value is considered a preview if it is non-null, trimmed, and enclosed
   * in angle brackets.
   *
   * @param v
   *     the value to evaluate
   * @return {@code true} if the value is a preview value, {@code false} otherwise
   */
  protected boolean isPreviewValue(String v) {
    if (v == null) return false;
    String s = v.trim();
    return s.startsWith("<") && s.endsWith(">");
  }
}