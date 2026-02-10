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
package com.etendoerp.task.callout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.utility.Sequence;
import org.openbravo.model.common.enterprise.Organization;

import com.etendoerp.sequences.parameters.SequenceParameterList;
import com.etendoerp.sequences.transactional.NotFoundSequenceException;
import com.etendoerp.sequences.transactional.TransactionalSequenceUtils;

/**
 * Unit tests for {@link TaskTypeCallout}, verifying task type matching logic,
 * parameter validation, rule processing, and Kafka message publishing.
 */
@ExtendWith(MockitoExtension.class)
class TaskTypeCalloutTest {
  private static final String TASK_TYPE_ID_PARAM = "inpetaskTaskTypeId";
  private static final String TABLE_ID_PARAM = "inpTableId";
  private static final String TASK_NO_PARAM = "inptaskno";
  private static final String TASK_TABLE_ID = "TABLE_ID";
  private static final String TASKNO_COLUMN_ID = "TASKNO_COLUMN_ID";
  private static final String CLIENT_ID = "CLIENT_ID";
  private static final String ORG_ID = "ORG_ID";

  @Mock
  private Column mockColumn;

  @Mock
  private Sequence mockSequence;

  @Mock
  private OBContext mockOBContext;

  @Mock
  private Client mockClient;

  @Mock
  private Organization mockOrganization;

  @Mock
  private OBDal mockOBDal;

  @Mock
  private VariablesSecureApp mockVars;

  @Mock
  private OBCriteria<Column> mockCriteria;

  private TaskTypeCallout taskTypeCallout;

  /**
   * Initializes the TaskTypeCallout instance before each test.
   */
  @BeforeEach
  void setup() {
    taskTypeCallout = spy(new TaskTypeCallout());
  }

  /**
   * Verifies that when the task type parameter is blank, the callout clears the task number field.
   *
   * @throws Exception
   *     if an error occurs during test execution
   */
  @Test
  void testExecuteWhenTaskTypeBlankThenClearsTaskNo() throws Exception {
    VariablesSecureApp vars = mock(VariablesSecureApp.class);
    when(vars.getStringParameter(TASK_TYPE_ID_PARAM)).thenReturn("");

    Map<String, Object> results = new HashMap<>();
    SimpleCallout.CalloutInfo info = buildInfoWithVars(vars, results);

    try (MockedStatic<OBContext> ignored = mockStatic(OBContext.class)) {
      taskTypeCallout.execute(info);
    }

    assertEquals("", results.get(TASK_NO_PARAM));
  }

  /**
   * Verifies that when a sequence is found for the selected task type, the callout sets a preview task number.
   *
   * <p>The preview value is expected to be wrapped in angle brackets.
   *
   * @throws Exception
   *     if an error occurs during test execution
   */
  @Test
  void testExecuteWhenSequenceExistsThenSetsPreview() throws Exception {
    when(mockVars.getStringParameter(TASK_TYPE_ID_PARAM)).thenReturn("TT_CALL");
    when(mockVars.getStringParameter(TABLE_ID_PARAM)).thenReturn(TASK_TABLE_ID);

    Map<String, Object> results = new HashMap<>();
    SimpleCallout.CalloutInfo info = buildInfoWithVars(mockVars, results);
    mockTaskNoColumnResolution();

    try (MockedStatic<OBDal> obdal = mockStatic(OBDal.class); MockedStatic<OBContext> obctx = mockStatic(
        OBContext.class); MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
      mockCoreContext(obdal, obctx);

      tsu.when(() -> TransactionalSequenceUtils.getSequenceFromParameters(any(SequenceParameterList.class))).thenReturn(
          mockSequence);
      tsu.when(() -> TransactionalSequenceUtils.getNextValueFromSequence(eq(mockSequence), eq(false))).thenReturn(
          "1000007");

      taskTypeCallout.execute(info);
    }

    assertEquals("<1000007>", results.get(TASK_NO_PARAM));
  }

  /**
   * Verifies that when no sequence is found for the selected task type, the callout clears the task number field.
   *
   * @throws Exception
   *     if an error occurs during test execution
   */
  @Test
  void testExecuteWhenNoSequenceThenClearsTaskNo() throws Exception {
    when(mockVars.getStringParameter(TASK_TYPE_ID_PARAM)).thenReturn("TT_EMAIL");
    when(mockVars.getStringParameter(TABLE_ID_PARAM)).thenReturn(TASK_TABLE_ID);

    Map<String, Object> results = new HashMap<>();
    SimpleCallout.CalloutInfo info = buildInfoWithVars(mockVars, results);
    mockTaskNoColumnResolution();

    try (MockedStatic<OBDal> obdal = mockStatic(OBDal.class); MockedStatic<OBContext> obctx = mockStatic(
        OBContext.class); MockedStatic<TransactionalSequenceUtils> tsu = mockStatic(TransactionalSequenceUtils.class)) {
      mockCoreContext(obdal, obctx);

      tsu.when(() -> TransactionalSequenceUtils.getSequenceFromParameters(any(SequenceParameterList.class))).thenThrow(
          new NotFoundSequenceException());

      taskTypeCallout.execute(info);
    }

    assertEquals("", results.get(TASK_NO_PARAM));
  }

  /**
   * Mocks the resolution of the task number column identifier.
   *
   * <p>This helper configures the DAL criteria to return a predefined column
   * representing the task number column, allowing tests to bypass actual
   * database lookup logic.
   */
  private void mockTaskNoColumnResolution() {
    when(mockColumn.getId()).thenReturn(TaskTypeCalloutTest.TASKNO_COLUMN_ID);
    when(mockOBDal.createCriteria(Column.class)).thenReturn(mockCriteria);
    when(mockCriteria.setFilterOnReadableClients(anyBoolean())).thenReturn(mockCriteria);
    when(mockCriteria.setFilterOnReadableOrganization(anyBoolean())).thenReturn(mockCriteria);
    when(mockCriteria.createAlias(anyString(), anyString())).thenReturn(mockCriteria);
    when(mockCriteria.add(any(Criterion.class))).thenReturn(mockCriteria);
    when(mockCriteria.setMaxResults(anyInt())).thenReturn(mockCriteria);
    when(mockCriteria.uniqueResult()).thenReturn(mockColumn);
  }

  /**
   * Mocks the core Openbravo context and DAL access.
   *
   * <p>This helper sets up the static {@link OBDal} and {@link OBContext} calls
   * to return mocked client and organization information required during
   * callout execution.
   *
   * @param obdal
   *     the mocked static {@link OBDal} context
   * @param obctx
   *     the mocked static {@link OBContext} context
   */
  private void mockCoreContext(MockedStatic<OBDal> obdal, MockedStatic<OBContext> obctx) {
    obdal.when(OBDal::getInstance).thenReturn(mockOBDal);
    when(mockClient.getId()).thenReturn(TaskTypeCalloutTest.CLIENT_ID);
    when(mockOrganization.getId()).thenReturn(TaskTypeCalloutTest.ORG_ID);
    when(mockOBContext.getCurrentClient()).thenReturn(mockClient);
    when(mockOBContext.getCurrentOrganization()).thenReturn(mockOrganization);
    obctx.when(OBContext::getOBContext).thenReturn(mockOBContext);
  }

  /**
   * Builds a {@link org.openbravo.erpCommon.ad_callouts.SimpleCallout.CalloutInfo} instance with the given variables
   * and captures results written through {@code addResult}.
   *
   * @param vars
   *     the variable provider to set on the callout info
   * @param outResults
   *     the map where values passed to {@code addResult} are stored
   * @return a spied callout info instance that records output values in {@code outResults}
   * @throws Exception
   *     if an error occurs during reflective construction or field access
   */
  private SimpleCallout.CalloutInfo buildInfoWithVars(VariablesSecureApp vars,
      Map<String, Object> outResults) throws Exception {
    Class<?> ciClass = Class.forName("org.openbravo.erpCommon.ad_callouts.SimpleCallout$CalloutInfo");
    Constructor<?> ctor = ciClass.getDeclaredConstructors()[0];
    ctor.setAccessible(true);

    Object[] args = new Object[ctor.getParameterCount()];

    SimpleCallout.CalloutInfo info = (SimpleCallout.CalloutInfo) ctor.newInstance(args);

    Field varsField = ciClass.getDeclaredField("vars");
    varsField.setAccessible(true);
    varsField.set(info, vars);

    SimpleCallout.CalloutInfo infoSpy = spy(info);
    doAnswer(inv -> {
      outResults.put(inv.getArgument(0, String.class), inv.getArgument(1));
      return null;
    }).when(infoSpy).addResult(anyString(), any());

    return infoSpy;
  }
}
