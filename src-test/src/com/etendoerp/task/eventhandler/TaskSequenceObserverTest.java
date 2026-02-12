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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.enterprise.inject.Vetoed;

import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.dal.core.SessionHandler;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.sequence.TaskTypeTransactionalSequence;

/**
 * Unit tests for {@link TaskSequenceObserver}.
 */
@ExtendWith(MockitoExtension.class)
class TaskSequenceObserverTest {
  private static final String PREVIEW_VALUE = "<Preview>";
  private static final String TT_CALL_ID = "TT_CALL";
  private static final String TASK_NO_SAMPLE = "TASK-0001";

  @Mock
  private Entity mockEntity;

  @Mock
  private Property mockTaskTypeProperty;

  @Mock
  private Property mockTaskNoProperty;

  @Mock
  private TaskType mockOldTaskType;

  @Mock
  private TaskType mockNewTaskType;

  @Mock
  private SessionHandler mockSessionHandler;

  @Mock
  private Session mockSession;

  @Mock
  private EntityUpdateEvent mockEntityUpdateEvent;

  @Mock
  private EntityNewEvent mockEntityNewEvent;

  @Mock
  private Task mockTask;

  private TestableTaskSequenceObserver observer;

  /**
   * Initializes the TaskSequenceObserver instance before each test.
   */
  @BeforeEach
  void setup() {
    observer = spy(new TestableTaskSequenceObserver(true));
  }

  /**
   * Verifies that when the update event is not valid, the observer performs no actions.
   */
  @Test
  void testOnUpdateWhenEventIsInvalidThenDoesNothing() {
    TestableTaskSequenceObserver invalidObserver = spy(new TestableTaskSequenceObserver(false));
    invalidObserver.onUpdate(mockEntityUpdateEvent);
    verify(mockEntityUpdateEvent, never()).getTargetInstance();
  }

  /**
   * Verifies that when the new-entity event is not valid, the observer performs no actions.
   */
  @Test
  void testOnSaveWhenEventIsInvalidThenDoesNothing() {
    TestableTaskSequenceObserver invalidObserver = spy(new TestableTaskSequenceObserver(false));
    invalidObserver.onSave(mockEntityNewEvent);
    verify(mockEntityNewEvent, never()).getTargetInstance();
  }

  /**
   * Verifies that when the task type does not change and the task number is not a preview value,
   * the observer does not regenerate the task number.
   */
  @Test
  void testOnUpdateWhenTaskTypeDoesNotChangeAndTaskNoIsNotPreviewThenNothingHappens() {
    when(mockEntityUpdateEvent.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_TASKTYPE)).thenReturn(mockTaskTypeProperty);
    when(mockEntity.getProperty(Task.PROPERTY_TASKNO)).thenReturn(mockTaskNoProperty);
    when(mockEntityUpdateEvent.getPreviousState(mockTaskTypeProperty)).thenReturn(mockOldTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskTypeProperty)).thenReturn(mockNewTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskNoProperty)).thenReturn(TASK_NO_SAMPLE);
    when(mockOldTaskType.getId()).thenReturn(TT_CALL_ID);
    when(mockNewTaskType.getId()).thenReturn(TT_CALL_ID);

    observer.onUpdate(mockEntityUpdateEvent);

    verify(mockEntityUpdateEvent, never()).setCurrentState(eq(mockTaskNoProperty), any());
  }

  /**
   * Verifies that when the task type changes, the observer regenerates and sets a new task number.
   */
  @Test
  void testOnUpdateWhenTaskTypeChangesThenTaskNoIsRegenerated() {
    when(mockEntityUpdateEvent.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_TASKTYPE)).thenReturn(mockTaskTypeProperty);
    when(mockEntity.getProperty(Task.PROPERTY_TASKNO)).thenReturn(mockTaskNoProperty);
    when(mockEntityUpdateEvent.getPreviousState(mockTaskTypeProperty)).thenReturn(mockOldTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskTypeProperty)).thenReturn(mockNewTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskNoProperty)).thenReturn(TASK_NO_SAMPLE);
    when(mockOldTaskType.getId()).thenReturn("OLD");
    when(mockNewTaskType.getId()).thenReturn("NEW");

    try (MockedStatic<SessionHandler> sessionHandlerMock = mockStatic(SessionHandler.class)) {
      sessionHandlerMock.when(SessionHandler::getInstance).thenReturn(mockSessionHandler);
      when(mockSessionHandler.getSession()).thenReturn(mockSession);

      try (var mocked = mockConstruction(TaskTypeTransactionalSequence.class,
          (seq, context) -> when(seq.generateValue(any(), any())).thenReturn("TEST_TASK_NO"))) {

        observer.onUpdate(mockEntityUpdateEvent);

        verify(mockEntityUpdateEvent).setCurrentState(mockTaskNoProperty, "TEST_TASK_NO");
      }
    }
  }

  /**
   * Verifies that when the current task number is a preview value, the observer regenerates and sets
   * a new task number even if the task type does not change.
   */
  @Test
  void testOnUpdateWhenTaskNoIsPreviewThenTaskNoIsRegenerated() {
    when(mockEntityUpdateEvent.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_TASKTYPE)).thenReturn(mockTaskTypeProperty);
    when(mockEntity.getProperty(Task.PROPERTY_TASKNO)).thenReturn(mockTaskNoProperty);
    when(mockEntityUpdateEvent.getPreviousState(mockTaskTypeProperty)).thenReturn(mockOldTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskTypeProperty)).thenReturn(mockNewTaskType);
    when(mockEntityUpdateEvent.getCurrentState(mockTaskNoProperty)).thenReturn(PREVIEW_VALUE);
    when(mockOldTaskType.getId()).thenReturn(TT_CALL_ID);
    when(mockNewTaskType.getId()).thenReturn(TT_CALL_ID);

    try (MockedStatic<SessionHandler> sessionHandlerMock = mockStatic(SessionHandler.class)) {
      sessionHandlerMock.when(SessionHandler::getInstance).thenReturn(mockSessionHandler);
      when(mockSessionHandler.getSession()).thenReturn(mockSession);

      try (var mocked = mockConstruction(TaskTypeTransactionalSequence.class,
          (seq, context) -> when(seq.generateValue(any(), any())).thenReturn("GEN_TASK_NO"))) {

        observer.onUpdate(mockEntityUpdateEvent);

        verify(mockEntityUpdateEvent).setCurrentState(mockTaskNoProperty, "GEN_TASK_NO");
      }
    }
  }

  /**
   * Verifies that when a new task is saved with a preview task number, the observer generates and sets
   * the final task number.
   */
  @Test
  void testOnSaveWhenTaskNoIsPreviewThenTaskNoIsGenerated() {
    when(mockEntityNewEvent.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_TASKNO)).thenReturn(mockTaskNoProperty);
    when(mockEntityNewEvent.getCurrentState(mockTaskNoProperty)).thenReturn(PREVIEW_VALUE);

    try (MockedStatic<SessionHandler> sessionHandlerMock = mockStatic(SessionHandler.class)) {
      sessionHandlerMock.when(SessionHandler::getInstance).thenReturn(mockSessionHandler);
      when(mockSessionHandler.getSession()).thenReturn(mockSession);

      try (var mocked = mockConstruction(TaskTypeTransactionalSequence.class,
          (seq, context) -> when(seq.generateValue(any(), any())).thenReturn("NEW_TASK_NO"))) {

        observer.onSave(mockEntityNewEvent);

        verify(mockEntityNewEvent).setCurrentState(mockTaskNoProperty, "NEW_TASK_NO");
      }
    }
  }

  /**
   * Verifies that when a new task is saved with a non-preview task number, the observer does not modify it.
   */
  @Test
  void testOnSaveWhenTaskNoIsNotPreviewThenDoesNothing() {
    when(mockEntityNewEvent.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_TASKNO)).thenReturn(mockTaskNoProperty);
    when(mockEntityNewEvent.getCurrentState(mockTaskNoProperty)).thenReturn(TASK_NO_SAMPLE);

    observer.onSave(mockEntityNewEvent);

    verify(mockEntityNewEvent, never()).setCurrentState(eq(mockTaskNoProperty), any());
  }

  /**
   * Verifies that {@code isPreviewValue} correctly identifies preview task number formats.
   */
  @Test
  void testIsPreviewValue() {
    assertFalse(observer.isPreviewValue(null));
    assertFalse(observer.isPreviewValue(""));
    assertFalse(observer.isPreviewValue("  "));
    assertFalse(observer.isPreviewValue(TASK_NO_SAMPLE));
    assertTrue(observer.isPreviewValue(PREVIEW_VALUE));
    assertTrue(observer.isPreviewValue("  <Preview>  "));
    assertTrue(observer.isPreviewValue("<123>"));
  }

  /**
   * Helper observer to control {@link TaskSequenceObserver#isValidEvent(EntityPersistenceEvent)}.
   */
  @Vetoed
  static class TestableTaskSequenceObserver extends TaskSequenceObserver {
    private final boolean valid;

    TestableTaskSequenceObserver(boolean valid) {
      this.valid = valid;
    }

    @Override
    protected boolean isValidEvent(EntityPersistenceEvent event) {
      return valid;
    }
  }
}