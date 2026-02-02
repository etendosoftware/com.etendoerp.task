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

import javax.enterprise.event.Observes;

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

public class TaskSequenceObserver extends EntityPersistenceEventObserver {

  private static final Entity[] entities = { ModelProvider.getInstance().getEntity(Task.ENTITY_NAME) };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

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

  private boolean isPreviewValue(String v) {
    if (v == null) return false;
    String s = v.trim();
    return s.startsWith("<") && s.endsWith(">");
  }
}