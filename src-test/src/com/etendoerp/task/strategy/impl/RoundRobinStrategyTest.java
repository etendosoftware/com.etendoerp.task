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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.access.User;

import com.etendoerp.task.data.TaskType;
import com.etendoerp.task.utils.TaskUtil;

/**
 * Unit tests for {@link RoundRobinStrategy}, verifying the round-robin task assignment
 * strategy behavior including user selection, index updates, and error handling.
 */
@ExtendWith(MockitoExtension.class)
public class RoundRobinStrategyTest {

  public static final String TASK_TYPE_ID_1 = "task-type-id-1";
  public static final String TASK_TYPE_ID_2 = "task-type-id-2";
  public static final String TASK_TYPE_ID_3 = "task-type-id-3";
  public static final String TASK_TYPE_ID_4 = "task-type-id-4";
  public static final String TASK_TYPE_ID_5 = "task-type-id-5";

  @Mock
  private TaskType mockTaskType;

  @Mock
  private User mockUser1;

  @Mock
  private User mockUser2;

  @Mock
  private User mockUser3;

  @Mock
  private JSONObject mockParameters;

  /**
   * Tests that the strategy selects the first user when round-robin index is null.
   */
  @Test
  public void testFindUserAccordingStrategyWhenIndexIsNull() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);
    when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID_1);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(null);
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_1, 1, 3)).then(invocation -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType, mockParameters);

      assertSame(mockUser1, result);
      taskUtilStatic.verify(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_1, 1, 3));
    }
  }

  /**
   * Tests that the strategy selects the correct user based on the current round-robin index.
   */
  @Test
  public void testFindUserAccordingStrategyWithExistingIndex() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);
    when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID_2);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(2L);
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_2, 3, 3)).then(invocation -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType, mockParameters);

      assertSame(mockUser3, result);
      taskUtilStatic.verify(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_2, 3, 3));
    }
  }

  /**
   * Tests that the strategy cycles back to the first user when reaching the end of the list.
   */
  @Test
  public void testFindUserAccordingStrategyWithCycling() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2);
    when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID_3);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(1L);
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_3, 2, 2)).then(invocation -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType, mockParameters);

      assertSame(mockUser2, result);
      taskUtilStatic.verify(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_3, 2, 2));
    }
  }

  /**
   * Tests that the strategy handles large round-robin index values correctly.
   */
  @Test
  public void testFindUserAccordingStrategyWithLargeIndex() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2);
    when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID_4);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(0L);
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_4, 1, 2)).then(invocation -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType, mockParameters);

      assertSame(mockUser1, result);
      taskUtilStatic.verify(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_4, 1, 2));
    }
  }

  /**
   * Tests that the strategy throws an exception when no users are available.
   */
  @Test
  public void testFindUserAccordingStrategyThrowsExceptionWhenNoUsers() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class);
         MockedStatic<OBMessageUtils> msgUtilStatic = mockStatic(OBMessageUtils.class)) {

      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(Collections.emptyList());
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(0L);
      msgUtilStatic.when(() -> OBMessageUtils.messageBD("ETASK_NoUsersFound")).thenReturn("No users found");

      assertThrows(OBException.class, () -> strategy.findUserAccordingStrategy(mockTaskType, mockParameters));
    }
  }

  /**
   * Tests that getUsersAvailable returns all active users regardless of task type.
   */
  @Test
  public void testGetUsersAvailableReturnsActiveUsers() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> expectedUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(expectedUsers);

      List<User> result = strategy.getUsersAvailable(mockTaskType, mockParameters);

      assertEquals(expectedUsers, result);
      taskUtilStatic.verify(TaskUtil::getActiveUsers);
    }
  }

  /**
   * Tests that getUsersAvailable returns empty list when no active users exist.
   */
  @Test
  public void testGetUsersAvailableReturnsEmptyWhenNoActiveUsers() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(Collections.emptyList());

      List<User> result = strategy.getUsersAvailable(mockTaskType, mockParameters);

      assertEquals(Collections.emptyList(), result);
      taskUtilStatic.verify(TaskUtil::getActiveUsers);
    }
  }

  /**
   * Tests that the strategy updates the index correctly when incrementing.
   */
  @Test
  public void testFindUserAccordingStrategyUpdatesIndexCorrectly() {
    RoundRobinStrategy strategy = new RoundRobinStrategy();
    List<User> availableUsers = Arrays.asList(mockUser1, mockUser2, mockUser3);
    when(mockTaskType.getId()).thenReturn(TASK_TYPE_ID_5);

    try (MockedStatic<TaskUtil> taskUtilStatic = mockStatic(TaskUtil.class)) {
      taskUtilStatic.when(TaskUtil::getActiveUsers).thenReturn(availableUsers);
      taskUtilStatic.when(() -> TaskUtil.getRoundRobinIndex(mockTaskType)).thenReturn(1L);
      taskUtilStatic.when(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_5, 2, 3)).then(invocation -> null);

      User result = strategy.findUserAccordingStrategy(mockTaskType, mockParameters);

      assertSame(mockUser2, result);
      taskUtilStatic.verify(() -> TaskUtil.updateRoundRobinIndex(TASK_TYPE_ID_5, 2, 3));
    }
  }
}
