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
package com.etendoerp.task.helper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

/**
 * Unit tests for {@link RoundRobinHelper}, verifying user assignment strategies,
 * database persistence operations, and edge cases handling.
 */
@ExtendWith(MockitoExtension.class)
public class RoundRobinHelperTest {

  @Mock
  private OBDal mockDal;

  @Mock
  private Task mockTask1;

  @Mock
  private Task mockTask2;

  @Mock
  private Task mockTask3;

  @Mock
  private TaskType mockTaskType1;

  @Mock
  private TaskType mockTaskType2;

  @Mock
  private User mockUser1;

  @Mock
  private User mockUser2;

  @Mock
  private Function<TaskType, User> mockStrategyFunction;

  /**
   * Tests that users are correctly assigned to tasks using the strategy function
   * and tasks are saved to the database.
   */
  @Test
  public void testAssignUsersWithMultipleTasks() {
    List<Task> tasks = Arrays.asList(mockTask1, mockTask2, mockTask3);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);
    when(mockTask2.getTaskType()).thenReturn(mockTaskType2);
    when(mockTask3.getTaskType()).thenReturn(mockTaskType1);

    when(mockStrategyFunction.apply(mockTaskType1)).thenReturn(mockUser1);
    when(mockStrategyFunction.apply(mockTaskType2)).thenReturn(mockUser2);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockTask1).setAssignedUser(mockUser1);
      verify(mockTask2).setAssignedUser(mockUser2);
      verify(mockTask3).setAssignedUser(mockUser1);

      verify(mockDal, times(3)).save(any(Task.class));
      verify(mockDal).save(mockTask1);
      verify(mockDal).save(mockTask2);
      verify(mockDal).save(mockTask3);

      verify(mockStrategyFunction, times(2)).apply(mockTaskType1);
      verify(mockStrategyFunction, times(1)).apply(mockTaskType2);
    }
  }

  /**
   * Tests that assignment works correctly with a single task.
   */
  @Test
  public void testAssignUsersWithSingleTask() {
    List<Task> tasks = Collections.singletonList(mockTask1);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);
    when(mockStrategyFunction.apply(mockTaskType1)).thenReturn(mockUser1);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockTask1).setAssignedUser(mockUser1);
      verify(mockDal).save(mockTask1);
      verify(mockStrategyFunction).apply(mockTaskType1);
    }
  }

  /**
   * Tests that no operations are performed when the task list is empty.
   */
  @Test
  public void testAssignUsersWithEmptyTaskList() {
    List<Task> tasks = Collections.emptyList();

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockDal, never()).save(any(Task.class));
      verify(mockStrategyFunction, never()).apply(any(TaskType.class));
    }
  }

  /**
   * Tests that the strategy function can return null users and assignment still works.
   */
  @Test
  public void testAssignUsersWithNullUserFromStrategy() {
    List<Task> tasks = Arrays.asList(mockTask1, mockTask2);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);
    when(mockTask2.getTaskType()).thenReturn(mockTaskType2);

    when(mockStrategyFunction.apply(mockTaskType1)).thenReturn(null);
    when(mockStrategyFunction.apply(mockTaskType2)).thenReturn(mockUser2);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockTask1).setAssignedUser(null);
      verify(mockTask2).setAssignedUser(mockUser2);

      verify(mockDal).save(mockTask1);
      verify(mockDal).save(mockTask2);
    }
  }

  /**
   * Tests that the method handles tasks with null task types gracefully.
   */
  @Test
  public void testAssignUsersWithNullTaskType() {
    List<Task> tasks = Collections.singletonList(mockTask1);

    when(mockTask1.getTaskType()).thenReturn(null);
    when(mockStrategyFunction.apply(null)).thenReturn(mockUser1);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockTask1).setAssignedUser(mockUser1);
      verify(mockDal).save(mockTask1);
      verify(mockStrategyFunction).apply(null);
    }
  }

  /**
   * Tests that RuntimeException from strategy function is propagated correctly.
   */
  @Test
  public void testAssignUsersWithStrategyFunctionException() {
    List<Task> tasks = Collections.singletonList(mockTask1);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);
    when(mockStrategyFunction.apply(mockTaskType1)).thenThrow(new RuntimeException("Strategy failed"));

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      assertThrows(RuntimeException.class, () -> RoundRobinHelper.assignUsers(tasks, mockStrategyFunction));

      verify(mockTask1, never()).setAssignedUser(any());
      verify(mockDal, never()).save(any(Task.class));
    }
  }

  /**
   * Tests assignment with same task type but different strategy results.
   */
  @Test
  public void testAssignUsersWithSameTaskTypeDifferentResults() {
    List<Task> tasks = Arrays.asList(mockTask1, mockTask2);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);
    when(mockTask2.getTaskType()).thenReturn(mockTaskType1);

    when(mockStrategyFunction.apply(mockTaskType1))
        .thenReturn(mockUser1)
        .thenReturn(mockUser2);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      RoundRobinHelper.assignUsers(tasks, mockStrategyFunction);

      verify(mockTask1).setAssignedUser(mockUser1);
      verify(mockTask2).setAssignedUser(mockUser2);

      verify(mockDal).save(mockTask1);
      verify(mockDal).save(mockTask2);

      verify(mockStrategyFunction, times(2)).apply(mockTaskType1);
    }
  }

  /**
   * Tests that tasks with null values in the list are handled correctly.
   */
  @Test
  public void testAssignUsersWithNullTaskInList() {
    List<Task> tasks = Arrays.asList(mockTask1, null, mockTask2);

    when(mockTask1.getTaskType()).thenReturn(mockTaskType1);

    when(mockStrategyFunction.apply(mockTaskType1)).thenReturn(mockUser1);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(mockDal);

      assertThrows(NullPointerException.class, () -> RoundRobinHelper.assignUsers(tasks, mockStrategyFunction));
    }
  }
}
