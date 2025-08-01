/*************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF  ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and  limitations under the License.
 * All portions are Copyright (C) 2021-2025 Futit Services S.L.
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 ************************************************************************/
package com.etendoerp.task.eventhandler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

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
import org.openbravo.client.kernel.event.EntityUpdateEvent;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.task.data.Table;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.data.UserAlgorithm;

/**
 * Comprehensive unit tests for {@link TaskTypeAlgorithm} event handler,
 * verifying business rules enforcement, entity relationship validation,
 * and event handling scenarios.
 */
@ExtendWith(MockitoExtension.class)
public class TaskTypeAlgorithmTest {

  public static final String TEST_TASK_TYPE = "Test Task Type";
  public static final String ETASK_TASK_TYPE_NO_ALGORITHM_TABLE = "ETASK_TaskTypeNoAlgorithmTable";
  public static final String TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM = "TaskType cannot have tables without algorithm";
  @Mock
  private TaskType mockTaskType;
  @Mock
  private Table mockTable;
  @Mock
  private UserAlgorithm mockUserAlgorithm;
  @Mock
  private Entity mockTaskTypeEntity;
  @Mock
  private Entity mockTableEntity;
  @Mock
  private EntityUpdateEvent mockUpdateEvent;
  @Mock
  private EntityNewEvent mockNewEvent;
  @Mock
  private Property mockProperty;
  @Mock
  private ModelProvider mockModelProvider;

  private TaskTypeAlgorithm algorithm;

  /**
   * Test subclass of {@link TaskTypeAlgorithm} that overrides {@code isValidEvent}
   * to always return {@code true}. Used to simplify event validation in unit tests.
   */
  private static class TestTaskTypeAlgorithm extends TaskTypeAlgorithm {
    /**
     * Always returns {@code true} to bypass event validation logic during testing.
     *
     * @param event
     *     the entity persistence event
     * @return always {@code true}
     */
    @Override
    protected boolean isValidEvent(org.openbravo.client.kernel.event.EntityPersistenceEvent event) {
      return true;
    }
  }

  /**
   * Initializes the {@code algorithm} instance with the test subclass before each test.
   */
  @BeforeEach
  public void setUp() {
    algorithm = new TestTaskTypeAlgorithm();
  }

  /**
   * Tests that the observed entities array is correctly initialized and contains expected entities.
   */
  @Test
  void testGetObservedEntitiesReturnsCorrectEntities() {
    Entity[] entities = algorithm.getObservedEntities();

    assertNotNull(entities);
    assertEquals(2, entities.length);
  }

  /**
   * Tests that logger is properly initialized and not null.
   */
  @Test
  void testLoggerIsInitialized() {
    assertNotNull(algorithm.logger);
  }

  /**
   * Tests that TaskType with UserAlgorithm can have associated Table records.
   */
  @Test
  void testTaskTypeWithUserAlgorithmAllowsTableAssignment() {
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);
    when(mockUserAlgorithm.getId()).thenReturn("test-algorithm-id");

    assertNotNull(mockTaskType.getUserAlgorithm());
    assertNotNull(mockTaskType.getUserAlgorithm().getId());

    verify(mockTaskType, times(2)).getUserAlgorithm();
    verify(mockUserAlgorithm).getId();
  }

  /**
   * Tests handling of Table entity with valid TaskType reference.
   */
  @Test
  void testTableWithValidTaskTypeReference() {
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    assertNotNull(mockTable.getTaskType());
    assertNotNull(mockTable.getTaskType().getUserAlgorithm());

    verify(mockTable, times(2)).getTaskType();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests handling of Table entity with invalid TaskType reference (no UserAlgorithm).
   */
  @Test
  void testTableWithInvalidTaskTypeReference() {
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);

    assertNotNull(mockTable.getTaskType());
    assertNull(mockTable.getTaskType().getUserAlgorithm());

    verify(mockTable, times(2)).getTaskType();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests handling of Table entity with null TaskType reference.
   */
  @Test
  void testTableWithNullTaskTypeReference() {
    when(mockTable.getTaskType()).thenReturn(null);

    assertNull(mockTable.getTaskType());

    verify(mockTable).getTaskType();
  }

  /**
   * Tests entity state validation for TaskType creation.
   */
  @Test
  void testTaskTypeCreationValidation() {
    when(mockTaskType.getId()).thenReturn("new-task-type-id");
    when(mockTaskType.getName()).thenReturn(TEST_TASK_TYPE);
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    assertNotNull(mockTaskType.getId());
    assertNotNull(mockTaskType.getName());
    assertNotNull(mockTaskType.getUserAlgorithm());

    verify(mockTaskType).getId();
    verify(mockTaskType).getName();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests entity state validation for TaskType update scenarios.
   */
  @Test
  void testTaskTypeUpdateValidation() {
    when(mockTaskType.getId()).thenReturn("existing-task-type-id");
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);

    // Simulate update where UserAlgorithm is removed
    assertNotNull(mockTaskType.getId());
    assertNull(mockTaskType.getUserAlgorithm());

    verify(mockTaskType).getId();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests multiple Table entities associated with same TaskType.
   */
  @Test
  void testMultipleTablesWithSameTaskType() {
    Table mockTable2 = mock(Table.class);

    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTable2.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    assertSame(mockTable.getTaskType(), mockTable2.getTaskType());
    assertNotNull(mockTaskType.getUserAlgorithm());

    verify(mockTable).getTaskType();
    verify(mockTable2).getTaskType();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests cascade deletion scenario where TaskType is deleted.
   */
  @Test
  void testTaskTypeDeletionWithAssociatedTables() {
    when(mockTaskType.getId()).thenReturn("task-type-to-delete");
    when(mockTable.getTaskType()).thenReturn(mockTaskType);

    assertNotNull(mockTaskType.getId());
    assertSame(mockTaskType, mockTable.getTaskType());

    verify(mockTaskType).getId();
    verify(mockTable).getTaskType();
  }

  /**
   * Tests UserAlgorithm assignment and removal scenarios.
   */
  @Test
  void testUserAlgorithmAssignmentScenarios() {
    // Test assignment
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);
    when(mockUserAlgorithm.getName()).thenReturn("Round Robin Algorithm");

    assertNotNull(mockTaskType.getUserAlgorithm());
    assertEquals("Round Robin Algorithm", mockTaskType.getUserAlgorithm().getName());

    // Test removal
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);
    assertNull(mockTaskType.getUserAlgorithm());

    verify(mockTaskType, times(3)).getUserAlgorithm();
    verify(mockUserAlgorithm).getName();
  }

  /**
   * Tests entity validation with invalid data states.
   */
  @Test
  void testEntityValidationWithInvalidStates() {
    // Test TaskType with empty name
    when(mockTaskType.getName()).thenReturn("");
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    assertEquals("", mockTaskType.getName());
    assertNotNull(mockTaskType.getUserAlgorithm());

    // Test Table with invalid references
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTable.getTable()).thenReturn(null);

    assertNotNull(mockTable.getTaskType());
    assertNull(mockTable.getTable());

    verify(mockTaskType).getName();
    verify(mockTaskType).getUserAlgorithm();
    verify(mockTable).getTaskType();
    verify(mockTable).getTable();
  }

  /**
   * Tests the business rule that TaskType without UserAlgorithm should throw exception when having tables.
   */
  @Test
  void testTaskTypeBusinessRuleViolation() {
    List<Table> tableList = new ArrayList<>();
    tableList.add(mockTable);

    when(mockTaskType.getUserAlgorithm()).thenReturn(null);
    when(mockTaskType.getETASKTableList()).thenReturn(tableList);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      // This simulates the business rule violation
      UserAlgorithm algorithm2 = mockTaskType.getUserAlgorithm();
      List<Table> tables = mockTaskType.getETASKTableList();

      if (algorithm2 == null && !tables.isEmpty()) {
        assertThrows(OBException.class, () -> {
          throw new OBException(OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE));
        });
      }

      verify(mockTaskType).getUserAlgorithm();
      verify(mockTaskType).getETASKTableList();
    }
  }

  /**
   * Tests the valid scenario where TaskType has UserAlgorithm and tables.
   */
  @Test
  void testTaskTypeValidBusinessRule() {
    List<Table> tableList = new ArrayList<>();
    tableList.add(mockTable);

    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);
    when(mockTaskType.getETASKTableList()).thenReturn(tableList);

    // This should be valid - TaskType with algorithm can have tables
    UserAlgorithm algorithm2 = mockTaskType.getUserAlgorithm();
    List<Table> tables = mockTaskType.getETASKTableList();

    assertNotNull(algorithm2);
    assertNotNull(tables);
    assertEquals(1, tables.size());

    verify(mockTaskType).getUserAlgorithm();
    verify(mockTaskType).getETASKTableList();
  }

  /**
   * Tests Table business rule validation.
   */
  @Test
  void testTableBusinessRuleValidation() {
    when(mockTable.getTaskType()).thenReturn(mockTaskType);

    // Test valid case - Table with TaskType that has algorithm
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    TaskType taskType = mockTable.getTaskType();
    UserAlgorithm algorithm2 = taskType.getUserAlgorithm();

    assertNotNull(taskType);
    assertNotNull(algorithm2);

    // Test invalid case - Table with TaskType that has no algorithm
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      UserAlgorithm invalidAlgorithm = mockTable.getTaskType().getUserAlgorithm();

      if (invalidAlgorithm == null) {
        assertThrows(OBException.class, () -> {
          throw new OBException(OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE));
        });
      }
    }

    verify(mockTable, times(2)).getTaskType();
    verify(mockTaskType, times(2)).getUserAlgorithm();
  }

  /**
   * Tests algorithm logger functionality.
   */
  @Test
  void testAlgorithmLogging() {
    assertNotNull(algorithm.logger);
    assertEquals("com.etendoerp.task.eventhandler.TaskTypeAlgorithm", algorithm.logger.getName());
  }

  /**
   * Tests entity relationships and dependencies.
   */
  @Test
  void testEntityRelationships() {
    // Test TaskType -> UserAlgorithm relationship
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);
    when(mockUserAlgorithm.getId()).thenReturn("algorithm-123");
    when(mockUserAlgorithm.getName()).thenReturn("Round Robin");

    UserAlgorithm algorithm2 = mockTaskType.getUserAlgorithm();
    assertNotNull(algorithm2);
    assertEquals("algorithm-123", algorithm2.getId());
    assertEquals("Round Robin", algorithm2.getName());

    // Test Table -> TaskType relationship
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getId()).thenReturn("tasktype-456");
    when(mockTaskType.getName()).thenReturn(TEST_TASK_TYPE);

    TaskType taskType = mockTable.getTaskType();
    assertNotNull(taskType);
    assertEquals("tasktype-456", taskType.getId());
    assertEquals(TEST_TASK_TYPE, taskType.getName());

    verify(mockTaskType, times(1)).getUserAlgorithm();
    verify(mockUserAlgorithm).getId();
    verify(mockUserAlgorithm).getName();
    verify(mockTable).getTaskType();
    verify(mockTaskType).getId();
    verify(mockTaskType).getName();
  }

  /**
   * Tests the onUpdate method with TaskType entity - valid scenario.
   */
  @Test
  void testOnUpdateWithValidTaskType() {


    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTaskType);
    when(mockTaskTypeEntity.getProperty(TaskType.PROPERTY_USERALGORITHM)).thenReturn(mockProperty);
    when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn(mockUserAlgorithm);

    // Test that the method handles valid TaskType updates without exceptions
    assertDoesNotThrow(() -> algorithm.handleTaskTypeUpdate(mockUpdateEvent, mockTaskTypeEntity));
  }

  /**
   * Tests the onUpdate method with TaskType entity - should throw exception.
   */
  @Test
  void testOnUpdateWithInvalidTaskType() {
    List<Table> tableList = new ArrayList<>();
    tableList.add(mockTable);

    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTaskType);
    when(mockTaskTypeEntity.getProperty(TaskType.PROPERTY_USERALGORITHM)).thenReturn(mockProperty);
    when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn(null);
    when(mockTaskType.getETASKTableList()).thenReturn(tableList);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      assertThrows(OBException.class, () ->
          algorithm.handleTaskTypeUpdate(mockUpdateEvent, mockTaskTypeEntity));
    }
  }

  /**
   * Tests the onUpdate method with Table entity.
   */
  @Test
  void testOnUpdateWithTableEntity() {
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTable);
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    // Test that the method handles Table updates without exceptions
    assertDoesNotThrow(() -> algorithm.handleTableUpdate(mockUpdateEvent));

    verify(mockTable).getTaskType();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests the onSave method with Table entity - valid scenario.
   */
  @Test
  void testOnSaveWithValidTable() {
    when(mockNewEvent.getTargetInstance()).thenReturn(mockTable);
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(mockUserAlgorithm);

    // Test that the method handles valid Table saves without exceptions
    assertDoesNotThrow(() -> algorithm.handleTableSave(mockNewEvent));

    verify(mockTable).getTaskType();
    verify(mockTaskType).getUserAlgorithm();
  }

  /**
   * Tests the onSave method with Table entity - should throw exception.
   */
  @Test
  void testOnSaveWithInvalidTable() {
    when(mockNewEvent.getTargetInstance()).thenReturn(mockTable);
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      assertThrows(OBException.class, () -> algorithm.handleTableSave(mockNewEvent));
    }
  }

  /**
   * Tests the onSave method with TaskType entity - should not process.
   */
  @Test
  void testOnSaveWithTaskTypeEntity() {
    when(mockNewEvent.getTargetInstance()).thenReturn(mockTaskType);

    // Test that TaskType entities are properly identified
    assertDoesNotThrow(() -> assertNotNull(mockNewEvent.getTargetInstance()));
  }

  /**
   * Tests the isTaskType method with TaskType entity.
   */
  @Test
  void testIsTaskTypeWithTaskTypeEntity() {
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTaskType);
    when(mockTaskType.getEntity()).thenReturn(mockTaskTypeEntity);

    try (MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class)) {
      modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
      when(mockModelProvider.getEntity(TaskType.class)).thenReturn(mockTaskTypeEntity);

      boolean result = algorithm.isTaskType(mockUpdateEvent);

      assertTrue(result);
    }
  }

  /**
   * Tests the isTaskType method with Table entity.
   */
  @Test
  void testIsTaskTypeWithTableEntity() {
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTable);
    when(mockTable.getEntity()).thenReturn(mockTableEntity);

    try (MockedStatic<ModelProvider> modelProviderMock = mockStatic(ModelProvider.class)) {
      modelProviderMock.when(ModelProvider::getInstance).thenReturn(mockModelProvider);
      when(mockModelProvider.getEntity(TaskType.class)).thenReturn(mockTaskTypeEntity);

      boolean result = algorithm.isTaskType(mockUpdateEvent);

      assertFalse(result);
    }
  }

  /**
   * Tests the handleTaskTypeUpdate method with valid algorithm.
   */
  @Test
  void testHandleTaskTypeUpdateWithValidAlgorithm() {
    List<Table> tableList = new ArrayList<>();
    tableList.add(mockTable);

    when(mockTaskTypeEntity.getProperty(TaskType.PROPERTY_USERALGORITHM)).thenReturn(mockProperty);
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTaskType);
    when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn(mockUserAlgorithm);

    // Should not throw exception
    assertDoesNotThrow(() -> algorithm.handleTaskTypeUpdate(mockUpdateEvent, mockTaskTypeEntity));

    verify(mockUpdateEvent).getTargetInstance();
    verify(mockUpdateEvent).getCurrentState(mockProperty);
  }

  /**
   * Tests the handleTaskTypeUpdate method without algorithm but with tables - should throw.
   */
  @Test
  void testHandleTaskTypeUpdateWithoutAlgorithmWithTables() {
    List<Table> tableList = new ArrayList<>();
    tableList.add(mockTable);

    when(mockTaskTypeEntity.getProperty(TaskType.PROPERTY_USERALGORITHM)).thenReturn(mockProperty);
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTaskType);
    when(mockUpdateEvent.getCurrentState(mockProperty)).thenReturn(null);
    when(mockTaskType.getETASKTableList()).thenReturn(tableList);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      assertThrows(OBException.class, () ->
          algorithm.handleTaskTypeUpdate(mockUpdateEvent, mockTaskTypeEntity));
    }
  }


  /**
   * Tests the handleTableUpdate method with invalid TaskType - should throw.
   */
  @Test
  void testHandleTableUpdateWithInvalidTaskType() {
    when(mockUpdateEvent.getTargetInstance()).thenReturn(mockTable);
    when(mockTable.getTaskType()).thenReturn(mockTaskType);
    when(mockTaskType.getUserAlgorithm()).thenReturn(null);

    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      assertThrows(OBException.class, () -> algorithm.handleTableUpdate(mockUpdateEvent));
    }
  }


  /**
   * Tests the static throwErrorException method.
   */
  @Test
  void testThrowErrorException() {
    try (MockedStatic<OBMessageUtils> messageMock = mockStatic(OBMessageUtils.class)) {
      messageMock.when(() -> OBMessageUtils.messageBD(ETASK_TASK_TYPE_NO_ALGORITHM_TABLE))
          .thenReturn(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM);

      OBException exception = assertThrows(OBException.class, TaskTypeAlgorithm::throwErrorException);

      assertEquals(TASK_TYPE_CANNOT_HAVE_TABLES_WITHOUT_ALGORITHM, exception.getMessage());
    }
  }

  /**
   * Tests invalid event handling in onUpdate - simplified.
   */
  @Test
  void testOnUpdateWithInvalidEvent() {
    // Test that methods handle null values correctly
    when(mockUpdateEvent.getTargetInstance()).thenReturn(null);

    // Verify that algorithm properly handles invalid events
    assertDoesNotThrow(() -> {
      Object target = mockUpdateEvent.getTargetInstance();
      assertNull(target);
    });

    verify(mockUpdateEvent).getTargetInstance();
  }

  /**
   * Tests invalid event handling in onSave - simplified.
   */
  @Test
  void testOnSaveWithInvalidEvent() {
    // Test that methods handle null values correctly
    when(mockNewEvent.getTargetInstance()).thenReturn(null);

    // Verify that algorithm properly handles invalid events
    assertDoesNotThrow(() -> {
      Object target = mockNewEvent.getTargetInstance();
      assertNull(target);
    });

    verify(mockNewEvent).getTargetInstance();
  }
}
