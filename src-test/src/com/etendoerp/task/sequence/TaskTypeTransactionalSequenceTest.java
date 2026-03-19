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
package com.etendoerp.task.sequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.sequences.parameters.SequenceParameterList;
import com.etendoerp.sequences.transactional.RequiredDimensionException;
import com.etendoerp.sequences.transactional.TransactionalSequenceUtils;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.utils.TaskConstants;

/**
 * Unit tests for {@link TaskTypeTransactionalSequence}.
 */
@ExtendWith(MockitoExtension.class)
class TaskTypeTransactionalSequenceTest {
  private static final String TASK_NO = "TASK-0001";
  private static final String TASK_NO_TRIMMED = "TASK-0001";
  private static final String PREVIEW_VALUE = "<Preview>";
  private static final String GENERATED_TASK_NO = "TASK-NEW";
  private static final String EMPTY_STRING = "";
  private static final String COLUMN_ID = "COL123";
  private static final String CLIENT_ID = "C1";
  private static final String ORG_ID = "O1";
  private static final String TASK_TYPE_ID = "TT1";
  private static final String CACHED_COLUMN_ID = "CACHED";
  private static final String RESOLVED_COLUMN_ID = "COL-RESOLVED";
  private static final String RUNTIME_ERROR = "boom";
  private static final String REQUIRED_DIMENSION = "organization";

  @Mock
  private Session mockSession;

  @Mock
  private Task mockTask;

  @Mock
  private TaskType mockTaskType;

  @Mock
  private Client mockClient;

  @Mock
  private Organization mockOrganization;

  @Mock
  private Sequence mockSequence;

  @Mock
  private OBDal mockOBDal;

  @Mock
  private OBCriteria<Table> mockTableCriteria;

  @Mock
  private OBCriteria<Column> mockColumnCriteria;

  @Mock
  private Table mockTable;

  @Mock
  private Column mockColumn;

  /**
   * Resets static state after each test execution.
   *
   * <p>This method clears the cached task number column identifier to ensure
   * test isolation and avoid side effects between tests.
   */
  @AfterEach
  void tearDown() {
    clearTaskNoColumnCache();
  }

  /**
   * Verifies that when the current task number is a non-preview value, the method returns the trimmed value
   * and does not attempt to generate a new one.
   */
  @Test
  void testGenerateValueWhenCurrentTaskNoIsNonPreviewThenReturnsTrimmedAndDoesNotRegenerate() {
    TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(TaskConstants.TASK_NO);

    when(mockTask.getTaskNo()).thenReturn("  " + TASK_NO + "  ");

    String result;
    try (MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
      result = seq.generateValue(mockSession, mockTask);
      tsu.verifyNoInteractions();
    }

    assertEquals(TASK_NO_TRIMMED, result);
  }

  /**
   * Verifies that when the current task number is a preview value, the task number is cleared and a new value
   * is generated using the transactional sequence.
   */
  @Test
  void testGenerateValueWhenCurrentTaskNoIsPreviewThenClearsAndGeneratesNewValue() {
    try (MockedStatic<TaskTypeTransactionalSequence> tts = mockStatic(
        TaskTypeTransactionalSequence.class); MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(
        TransactionalSequenceUtils.class)) {

      tts.when(TaskTypeTransactionalSequence::resolveTaskNoColumnId).thenReturn(COLUMN_ID);

      tsu.when(() -> TransactionalSequenceUtils.getSequenceFromParameters(any(SequenceParameterList.class))).thenReturn(
          mockSequence);
      tsu.when(() -> TransactionalSequenceUtils.getNextValueFromSequence(eq(mockSequence), eq(true))).thenReturn(
          GENERATED_TASK_NO);

      TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(TaskConstants.TASK_NO);

      when(mockTask.getTaskNo()).thenReturn(PREVIEW_VALUE);
      when(mockTask.getTaskType()).thenReturn(mockTaskType);
      when(mockTask.getClient()).thenReturn(mockClient);
      when(mockTask.getOrganization()).thenReturn(mockOrganization);
      when(mockClient.getId()).thenReturn(CLIENT_ID);
      when(mockOrganization.getId()).thenReturn(ORG_ID);
      when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID);

      String result = seq.generateValue(mockSession, mockTask);

      verify(mockTask, times(1)).setTaskNo(null);
      assertEquals(GENERATED_TASK_NO, result);
    }
  }

  /**
   * Verifies that when the task type is {@code null}, the method returns an empty string and does not interact
   * with sequence generation utilities.
   */
  @Test
  void testGenerateValueWhenTaskTypeIsNullThenReturnsEmptyString() {
    TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(TaskConstants.TASK_NO);

    when(mockTask.getTaskNo()).thenReturn(null);
    when(mockTask.getTaskType()).thenReturn(null);

    try (MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
      String result = seq.generateValue(mockSession, mockTask);
      assertEquals(EMPTY_STRING, result);
      tsu.verifyNoInteractions();
    }
  }

  /**
   * Verifies that when sequence resolution fails due to a missing required dimension, the method throws an
   * {@link OBException} that includes the missing dimension information.
   */
  @Test
  void testGenerateValueWhenRequiredDimensionMissingThenThrowsOBException() {
    try (MockedStatic<TaskTypeTransactionalSequence> tts = mockStatic(TaskTypeTransactionalSequence.class)) {

      tts.when(TaskTypeTransactionalSequence::resolveTaskNoColumnId).thenReturn(COLUMN_ID);
      TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(TaskConstants.TASK_NO);

      when(mockTask.getTaskNo()).thenReturn(null);
      when(mockTask.getTaskType()).thenReturn(mockTaskType);
      when(mockTask.getClient()).thenReturn(mockClient);
      when(mockTask.getOrganization()).thenReturn(mockOrganization);
      when(mockClient.getId()).thenReturn(CLIENT_ID);
      when(mockOrganization.getId()).thenReturn(ORG_ID);
      when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID);

      RequiredDimensionException ex = new RequiredDimensionException(REQUIRED_DIMENSION);

      try (MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
        tsu.when(
            () -> TransactionalSequenceUtils.getSequenceFromParameters(any(SequenceParameterList.class))).thenThrow(ex);

        OBException thrown = assertThrows(OBException.class, () -> seq.generateValue(mockSession, mockTask));
        assertTrue(thrown.getMessage().contains(REQUIRED_DIMENSION));
      }
    }
  }

  /**
   * Verifies that when an unexpected exception occurs during sequence resolution, the method throws an
   * {@link OBException} with the original error message.
   */
  @Test
  void testGenerateValueWhenUnexpectedExceptionThenThrowsOBException() {
    try (MockedStatic<TaskTypeTransactionalSequence> tts = mockStatic(TaskTypeTransactionalSequence.class)) {
      tts.when(TaskTypeTransactionalSequence::resolveTaskNoColumnId).thenReturn(COLUMN_ID);

      TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(TaskConstants.TASK_NO);

      when(mockTask.getTaskNo()).thenReturn(null);
      when(mockTask.getTaskType()).thenReturn(mockTaskType);
      when(mockTask.getClient()).thenReturn(mockClient);
      when(mockTask.getOrganization()).thenReturn(mockOrganization);
      when(mockClient.getId()).thenReturn(CLIENT_ID);
      when(mockOrganization.getId()).thenReturn(ORG_ID);
      when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID);

      try (MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
        tsu.when(
            () -> TransactionalSequenceUtils.getSequenceFromParameters(any(SequenceParameterList.class))).thenThrow(
            new RuntimeException(RUNTIME_ERROR));

        OBException thrown = assertThrows(OBException.class, () -> seq.generateValue(mockSession, mockTask));
        assertEquals(RUNTIME_ERROR, thrown.getMessage());
      }
    }
  }

  /**
   * Verifies that when the task number column identifier is cached, the cached value is returned and no database
   * access is performed.
   */
  @Test
  void testResolveTaskNoColumnIdWhenCachedThenReturnsCachedAndDoesNotHitDB() {
    try (MockedStatic<TaskTypeTransactionalSequence> tts = mockStatic(TaskTypeTransactionalSequence.class)) {
      tts.when(TaskTypeTransactionalSequence::resolveTaskNoColumnId).thenReturn(CACHED_COLUMN_ID);

      try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
        String result = TaskTypeTransactionalSequence.resolveTaskNoColumnId();
        assertEquals(CACHED_COLUMN_ID, result);
        obDalStatic.verifyNoInteractions();
      }
    }
  }

  /**
   * Verifies that when the task number column identifier is not cached, it is resolved via DAL queries, cached,
   * and returned.
   */
  @Test
  void testResolveTaskNoColumnIdWhenNotCachedThenQueriesDBAndCachesResult() {
    when(mockColumn.getId()).thenReturn(RESOLVED_COLUMN_ID);
    when(mockOBDal.createCriteria(Table.class)).thenReturn(mockTableCriteria);
    when(mockOBDal.createCriteria(Column.class)).thenReturn(mockColumnCriteria);
    when(mockTableCriteria.add(any())).thenReturn(mockTableCriteria);
    when(mockTableCriteria.setMaxResults(anyInt())).thenReturn(mockTableCriteria);
    when(mockTableCriteria.list()).thenReturn(List.of(mockTable));
    when(mockColumnCriteria.add(any())).thenReturn(mockColumnCriteria);
    when(mockColumnCriteria.setMaxResults(anyInt())).thenReturn(mockColumnCriteria);
    when(mockColumnCriteria.list()).thenReturn(List.of(mockColumn));

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockOBDal);

      String result = TaskTypeTransactionalSequence.resolveTaskNoColumnId();

      assertEquals(RESOLVED_COLUMN_ID, result);
      assertEquals(RESOLVED_COLUMN_ID, TaskTypeTransactionalSequence.taskNoAdColumnId);
    }
  }

  /**
   * Verifies that {@code isPreviewValue} correctly identifies preview task number formats.
   */
  @Test
  void testIsPreviewValue() {
    TaskTypeTransactionalSequence seq = new TaskTypeTransactionalSequence(Sequence.PROPERTY_ID);

    assertFalse(seq.isPreviewValue(null));
    assertFalse(seq.isPreviewValue(EMPTY_STRING));
    assertFalse(seq.isPreviewValue("  "));
    assertFalse(seq.isPreviewValue("TASK-1"));
    assertFalse(seq.isPreviewValue("<"));
    assertFalse(seq.isPreviewValue(">"));
    assertTrue(seq.isPreviewValue(PREVIEW_VALUE));
    assertTrue(seq.isPreviewValue("  <123>  "));
  }

  /**
   * Clears the cached task number column identifier.
   *
   * <p>This helper method resets the static cache used to store the resolved
   * task number column ID, ensuring a clean state for subsequent operations
   * or test executions.
   */
  private static void clearTaskNoColumnCache() {
    TaskTypeTransactionalSequence.taskNoAdColumnId = null;
  }
}
