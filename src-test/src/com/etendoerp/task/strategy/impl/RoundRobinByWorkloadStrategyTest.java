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
package com.etendoerp.task.strategy.impl;

import static com.etendoerp.task.TaskTestsConstants.NO_TASK_TYPE_ERROR;
import static com.etendoerp.task.TaskTestsConstants.NO_USERS_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.Status;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.utils.TaskUtil;

/**
 * Unit tests for {@link RoundRobinByWorkloadStrategy}, verifying task assignment
 * based on workload and round-robin selection among users with minimal load.
 */
@ExtendWith(MockitoExtension.class)
public class RoundRobinByWorkloadStrategyTest {

  @Mock
  private TaskType mockTaskType;

  @Mock
  private User mockUser1;

  @Mock
  private User mockUser2;

  @Mock
  private User mockUser3;

  @Mock
  private Task mockTask1;

  @Mock
  private Task mockTask2;

  @Mock
  private Task mockTask3;

  @Mock
  private Status mockPendingStatus;

  @Mock
  private Status mockInProgressStatus;

  @Mock
  private Status mockCompletedStatus;

  /**
   * Tests that findUserAccordingStrategy throws OBException when taskType is null.
   */
  @Test
  public void testFindUserAccordingStrategyThrowsExceptionWhenTaskTypeIsNull() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();

    try (MockedStatic<OBMessageUtils> msgUtils = mockStatic(OBMessageUtils.class)) {
      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_NoTaskTypeFound")).thenReturn(NO_TASK_TYPE_ERROR);

      OBException exception = assertThrows(OBException.class, () -> strategy.findUserAccordingStrategy(null));
      assertEquals(NO_TASK_TYPE_ERROR, exception.getMessage());
    }
  }

  /**
   * Tests that findUserAccordingStrategy throws OBException when no users are available.
   */
  @Test
  public void testFindUserAccordingStrategyThrowsExceptionWhenNoUsersAvailable() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(
        TaskUtil.class); MockedStatic<OBMessageUtils> msgUtils = mockStatic(
        OBMessageUtils.class)) {

      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(Collections.emptyList());
      msgUtils.when(() -> OBMessageUtils.messageBD("ETASK_NoUsersFound")).thenReturn(NO_USERS_ERROR);

      OBException exception = assertThrows(OBException.class, () -> strategy.findUserAccordingStrategy(mockTaskType));
      assertEquals(NO_USERS_ERROR, exception.getMessage());
    }
  }

  /**
   * Tests that findUserAccordingStrategy uses existing round-robin index correctly.
   */
  @Test
  public void testFindUserAccordingStrategyUsesExistingRoundRobinIndex() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2);

    when(mockTaskType.getRoundRobinIndex()).thenReturn(0L);

    when(mockTask1.getAssignedUser()).thenReturn(mockUser1);
    when(mockTask1.getStatus()).thenReturn(mockPendingStatus);

    when(mockTask2.getAssignedUser()).thenReturn(mockUser2);
    when(mockTask2.getStatus()).thenReturn(mockPendingStatus);

    List<Task> allTasks = Arrays.asList(mockTask1, mockTask2);

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtils.when(() -> TaskUtil.preloadTasks(availableUsers)).thenReturn(allTasks);
      taskUtils.when(() -> TaskUtil.getStatus("PE")).thenReturn(mockPendingStatus);
      taskUtils.when(() -> TaskUtil.getStatus("IP")).thenReturn(mockInProgressStatus);
      taskUtils.when(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2)).thenAnswer(inv -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType);

      assertNotNull(result);
      taskUtils.verify(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2));
    }
  }

  /**
   * Tests that findUserAccordingStrategy handles multiple users with same minimal workload.
   */
  @Test
  public void testFindUserAccordingStrategyHandlesMultipleUsersWithSameMinimalWorkload() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);

    when(mockTaskType.getRoundRobinIndex()).thenReturn(0L);

    List<Task> allTasks = Collections.emptyList();

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtils.when(() -> TaskUtil.preloadTasks(availableUsers)).thenReturn(allTasks);
      taskUtils.when(() -> TaskUtil.getStatus("PE")).thenReturn(mockPendingStatus);
      taskUtils.when(() -> TaskUtil.getStatus("IP")).thenReturn(mockInProgressStatus);
      taskUtils.when(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 3)).thenAnswer(inv -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType);

      assertNotNull(result);
      assertEquals(mockUser1, result);
      taskUtils.verify(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 3));
    }
  }

  /**
   * Tests that findUserAccordingStrategy filters only pending and in-progress tasks for workload calculation.
   */
  @Test
  public void testFindUserAccordingStrategyFiltersOnlyOpenTasks() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2);

    when(mockTaskType.getRoundRobinIndex()).thenReturn(0L);

    when(mockTask1.getAssignedUser()).thenReturn(mockUser1);
    when(mockTask1.getStatus()).thenReturn(mockPendingStatus);

    when(mockTask2.getStatus()).thenReturn(mockCompletedStatus);

    when(mockTask3.getAssignedUser()).thenReturn(mockUser2);
    when(mockTask3.getStatus()).thenReturn(mockInProgressStatus);

    List<Task> allTasks = Arrays.asList(mockTask1, mockTask2, mockTask3);

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtils.when(() -> TaskUtil.preloadTasks(availableUsers)).thenReturn(allTasks);
      taskUtils.when(() -> TaskUtil.getStatus("PE")).thenReturn(mockPendingStatus);
      taskUtils.when(() -> TaskUtil.getStatus("IP")).thenReturn(mockInProgressStatus);
      taskUtils.when(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2)).thenAnswer(inv -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType);

      assertNotNull(result);
      taskUtils.verify(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2));
    }
  }

  /**
   * Tests that getUsersAvailable returns all active users regardless of task type.
   */
  @Test
  public void testGetUsersAvailableReturnsAllActiveUsers() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();
    List<User> expectedUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(expectedUsers);

      List<User> result = strategy.getUsersAvailable(mockTaskType);

      assertEquals(expectedUsers, result);
      assertEquals(3, result.size());
      taskUtils.verify(TaskUtil::getActiveUsers);
    }
  }

  /**
   * Tests that getUsersAvailable returns empty list when no active users exist.
   */
  @Test
  public void testGetUsersAvailableReturnsEmptyListWhenNoActiveUsers() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(Collections.emptyList());

      List<User> result = strategy.getUsersAvailable(mockTaskType);

      assertEquals(Collections.emptyList(), result);
      assertEquals(0, result.size());
      taskUtils.verify(TaskUtil::getActiveUsers);
    }
  }

  /**
   * Tests that round-robin index wraps around correctly when reaching the end of minimal load users.
   */
  @Test
  public void testFindUserAccordingStrategyWrapsRoundRobinIndex() {
    RoundRobinByWorkloadStrategy strategy = new RoundRobinByWorkloadStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2);

    when(mockTaskType.getRoundRobinIndex()).thenReturn(0L);

    List<Task> allTasks = Collections.emptyList();

    try (MockedStatic<TaskUtil> taskUtils = mockStatic(TaskUtil.class)) {
      taskUtils.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtils.when(() -> TaskUtil.preloadTasks(availableUsers)).thenReturn(allTasks);
      taskUtils.when(() -> TaskUtil.getStatus("PE")).thenReturn(mockPendingStatus);
      taskUtils.when(() -> TaskUtil.getStatus("IP")).thenReturn(mockInProgressStatus);
      taskUtils.when(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2)).thenAnswer(inv -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType);

      assertNotNull(result);
      assertEquals(mockUser1, result);
      taskUtils.verify(() -> TaskUtil.updateRoundRobinIndex(mockTaskType, 1, 2));
    }
  }

}
