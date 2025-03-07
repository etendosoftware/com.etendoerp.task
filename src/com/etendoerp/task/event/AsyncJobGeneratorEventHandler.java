package com.etendoerp.task.event;

import javax.enterprise.event.Observes;

import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.application.Process;
import org.openbravo.client.kernel.event.EntityDeleteEvent;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEventObserver;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.task.data.Events;
import com.etendoerp.task.data.State;
import com.smf.jobs.model.Job;
import com.smf.jobs.model.JobLine;

/**
 * This event handler manages asynchronous job creation and updates
 * based on CRUD operations on the {@code etask_events} table (mapped by
 * the {@link Events} entity).
 * <p>
 * When a new {@link Events} record is inserted, it checks if the parent {@link State}
 * has an associated {@link Job}. If not, it creates one and marks it as an async job.
 * It then creates a {@link JobLine} referencing the newly created or existing {@link Job}.
 * <p>
 * On update, if the {@code Action} field changes, this handler updates the associated
 * {@link JobLine}'s action accordingly.
 * <p>
 * On delete, it removes the corresponding {@link JobLine} from the database.
 */
public class AsyncJobGeneratorEventHandler extends EntityPersistenceEventObserver {

  /** Observed entity: the {@code Events} class mapping the etask_events table. */
  private static final Entity[] entities = {
      ModelProvider.getInstance().getEntity(Events.ENTITY_NAME)
  };

  @Override
  protected Entity[] getObservedEntities() {
    return entities;
  }

  /**
   * Handles INSERT operations on {@link Events} records.
   * <ul>
   *   <li>Checks if the parent {@link State} has an associated {@link Job}. If it does not, creates one.</li>
   *   <li>Creates a {@link JobLine} referencing that {@link Job} and sets the line number and action.</li>
   *   <li>Links the newly created {@link JobLine} back to the {@link Events} record.</li>
   * </ul>
   *
   * @param event the {@link EntityNewEvent} containing the new {@link Events} record
   */
  public void onSave(@Observes EntityNewEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Events etaskEvents = (Events) event.getTargetInstance();
    State eTaskState = etaskEvents.getState();

    // Check if the parent State already has a Job
    Job stateJob = eTaskState.getJob();
    if (stateJob == null) {
      stateJob = OBProvider.getInstance().get(Job.class);
      stateJob.setClient(eTaskState.getClient());
      stateJob.setOrganization(eTaskState.getOrganization());
      stateJob.setActive(true);
      stateJob.setEtapIsAsync(true);
      stateJob.setName("Async Job for ETaskState " + eTaskState.getId());
      OBDal.getInstance().save(stateJob);

      eTaskState.setJob(stateJob);
      OBDal.getInstance().save(eTaskState);
    }

    // Create a new JobLine referencing the existing or newly created Job
    JobLine jobLine = OBProvider.getInstance().get(JobLine.class);
    jobLine.setClient(etaskEvents.getClient());
    jobLine.setOrganization(etaskEvents.getOrganization());
    jobLine.setJobsJob(stateJob);
    jobLine.setAction(etaskEvents.getAction());
    jobLine.setLineNo(etaskEvents.getSequenceNo());
    OBDal.getInstance().save(jobLine);

    // Link the newly created JobLine to the Events record
    final Entity evEntity = ModelProvider.getInstance().getEntity(Events.ENTITY_NAME);
    final Property eventJobLineProperty = evEntity.getProperty(Events.PROPERTY_JOBLINE);
    event.setCurrentState(eventJobLineProperty, jobLine);
  }

  /**
   * Handles UPDATE operations on {@link Events} records.
   * <ul>
   *   <li>If the {@code Action} field changes, the associated {@link JobLine}'s action is updated.</li>
   * </ul>
   *
   * @param event the {@link EntityUpdateEvent} containing the updated {@link Events} record
   */
  public void onUpdate(@Observes EntityUpdateEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Events etaskEvents = (Events) event.getTargetInstance();
    Entity entity = event.getTargetInstance().getEntity();

    Property actionProperty = entity.getProperty(Events.PROPERTY_ACTION);
    Process oldAction = (Process) event.getPreviousState(actionProperty);
    Process newAction = (Process) event.getCurrentState(actionProperty);

    if (oldAction != null && !oldAction.equals(newAction)) {
      JobLine jobLine = etaskEvents.getJobLine();
      if (jobLine != null) {
        jobLine.setAction(newAction);
        OBDal.getInstance().save(jobLine);
      }
    }
  }

  /**
   * Handles DELETE operations on {@link Events} records.
   * <ul>
   *   <li>Removes the associated {@link JobLine} from the database.</li>
   * </ul>
   *
   * @param event the {@link EntityDeleteEvent} containing the {@link Events} record to delete
   */
  public void onDelete(@Observes EntityDeleteEvent event) {
    if (!isValidEvent(event)) {
      return;
    }

    Events etaskEvents = (Events) event.getTargetInstance();
    JobLine jobLine = etaskEvents.getJobLine();

    if (jobLine != null) {
      OBDal.getInstance().remove(jobLine);
    }
  }
}