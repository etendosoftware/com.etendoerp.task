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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import jakarta.enterprise.inject.Vetoed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.model.Entity;
import org.openbravo.base.model.ModelProvider;
import org.openbravo.base.model.Property;
import org.openbravo.client.kernel.event.EntityNewEvent;
import org.openbravo.client.kernel.event.EntityPersistenceEvent;
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Task;

/**
 * Unit tests for {@link TaskDateValidation}.
 */
@ExtendWith(MockitoExtension.class)
class TaskDateValidationTest {

  private static final String ERROR_KEY = "ETASK_DueDateBeforeStartDate";
  private static final String ERROR_MESSAGE = "The Expiration Date must be the same as or later than the Start Date.";

  @Mock
  private Entity mockEntity;

  @Mock
  private Property mockStartProperty;

  @Mock
  private Property mockDueProperty;

  @Mock
  private EntityUpdateEvent mockUpdateEvent;

  @Mock
  private EntityNewEvent mockNewEvent;

  @Mock
  private Task mockTask;

  @Mock
  private ModelProvider mockProvider;

  private TestableTaskDateValidation observer;

  /**
   * Initializes the TaskDateValidation instance before each test.
   */
  @BeforeEach
  void setup() {
    observer = spy(createHandlerWithMockedModelProvider(true));
  }

  /**
   * Verifies that when the update event is not valid, the observer performs no actions.
   */
  @Test
  void testOnUpdateWhenEventIsInvalidThenDoesNothing() {
    TestableTaskDateValidation invalidObserver = spy(createHandlerWithMockedModelProvider(false));
    invalidObserver.onUpdate(mockUpdateEvent);
    verify(mockUpdateEvent, never()).getTargetInstance();
  }

  /**
   * Verifies that when the new-entity event is not valid, the observer performs no actions.
   */
  @Test
  void testOnSaveWhenEventIsInvalidThenDoesNothing() {
    TestableTaskDateValidation invalidObserver = spy(createHandlerWithMockedModelProvider(false));
    invalidObserver.onSave(mockNewEvent);
    verify(mockNewEvent, never()).getTargetInstance();
  }

  /**
   * Verifies that a due date earlier than the start date throws an exception on save.
   */
  @Test
  void testOnSaveWhenDueDateBeforeStartDateThenThrows() {
    Date startDate = new Date(1000L);
    Date dueDate = new Date(0L);
    mockDates(mockNewEvent, startDate, dueDate);

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.messageBD(ERROR_KEY)).thenReturn(ERROR_MESSAGE);

      OBException ex = assertThrows(OBException.class, () -> observer.onSave(mockNewEvent));
      assertEquals(ERROR_MESSAGE, ex.getMessage());
    }
  }

  /**
   * Verifies that a due date earlier than the start date throws an exception on update.
   */
  @Test
  void testOnUpdateWhenDueDateBeforeStartDateThenThrows() {
    Date startDate = new Date(1000L);
    Date dueDate = new Date(0L);
    mockDates(mockUpdateEvent, startDate, dueDate);

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.messageBD(ERROR_KEY)).thenReturn(ERROR_MESSAGE);

      OBException ex = assertThrows(OBException.class, () -> observer.onUpdate(mockUpdateEvent));
      assertEquals(ERROR_MESSAGE, ex.getMessage());
    }
  }

  /**
   * Verifies that equal dates are allowed.
   */
  @Test
  void testOnUpdateWhenDueDateEqualsStartDateThenOk() {
    Date startDate = new Date(1000L);
    Date dueDate = new Date(1000L);
    mockDates(mockUpdateEvent, startDate, dueDate);

    assertDoesNotThrow(() -> observer.onUpdate(mockUpdateEvent));
  }

  /**
   * Verifies that null values are allowed.
   */
  @Test
  void testOnSaveWhenAnyDateIsNullThenOk() {
    Date startDate = null;
    Date dueDate = new Date(1000L);
    mockDates(mockNewEvent, startDate, dueDate);
    assertDoesNotThrow(() -> observer.onSave(mockNewEvent));

    mockDates(mockNewEvent, new Date(1000L), null);
    assertDoesNotThrow(() -> observer.onSave(mockNewEvent));
  }

  private void mockDates(EntityPersistenceEvent event, Date startDate, Date dueDate) {
    when(event.getTargetInstance()).thenReturn(mockTask);
    when(mockTask.getEntity()).thenReturn(mockEntity);
    when(mockEntity.getProperty(Task.PROPERTY_STARTDATE)).thenReturn(mockStartProperty);
    when(mockEntity.getProperty(Task.PROPERTY_DUEDATE)).thenReturn(mockDueProperty);
    when(event.getCurrentState(mockStartProperty)).thenReturn(startDate);
    when(event.getCurrentState(mockDueProperty)).thenReturn(dueDate);
  }

  /**
   * Creates a {@link TaskDateValidation} instance with a mocked {@link ModelProvider}
   * and {@link Entity} metadata to avoid using real Openbravo infrastructure.
   *
   * @param valid
   *     whether the handler should consider events valid
   * @return a handler instance configured for testing
   */
  private TestableTaskDateValidation createHandlerWithMockedModelProvider(boolean valid) {
    try (MockedStatic<ModelProvider> model = mockStatic(ModelProvider.class)) {
      model.when(ModelProvider::getInstance).thenReturn(mockProvider);
      when(mockProvider.getEntity(Task.ENTITY_NAME)).thenReturn(mockEntity);
      return new TestableTaskDateValidation(valid);
    }
  }

  /**
   * Helper observer to control {@link TaskDateValidation#isValidEvent(EntityPersistenceEvent)}.
   */
  @Vetoed
  static class TestableTaskDateValidation extends TaskDateValidation {
    private final boolean valid;

    TestableTaskDateValidation(boolean valid) {
      this.valid = valid;
    }

    @Override
    protected boolean isValidEvent(EntityPersistenceEvent event) {
      return valid;
    }
  }
}
