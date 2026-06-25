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

import jakarta.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.openbravo.dal.service.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.ad_callouts.SimpleCallout;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Sequence;

import com.etendoerp.sequences.SequenceDatabaseUtils;
import com.etendoerp.sequences.parameters.SequenceParameterList;
import com.etendoerp.sequences.parameters.SequenceParametersUtils;
import com.etendoerp.sequences.transactional.MaskValueGenerationException;
import com.etendoerp.sequences.transactional.NotFoundSequenceException;
import com.etendoerp.sequences.transactional.RequiredDimensionException;
import com.etendoerp.sequences.transactional.TransactionalSequenceUtils;
import com.etendoerp.task.utils.TaskConstants;

/**
 * Callout that generates a preview task number based on the selected task type.
 *
 * <p>This callout resolves the appropriate sequence for the task type and returns
 * a preview value wrapped in angle brackets, or an empty value when the sequence
 * cannot be resolved.
 */
public class TaskTypeCallout extends SimpleCallout {
  private static final String PARAM_TASK_TYPE_ID = "inpetaskTaskTypeId";
  private static final String PARAM_TASK_NO = "inptaskno";

  /**
   * Executes the callout logic to update the task number field.
   *
   * <p>If no task type is provided, the task number is cleared. Otherwise, a preview
   * value is generated using transactional sequence parameters derived from the
   * current client, organization, table, and task type.
   *
   * @param info
   *     the callout context containing request parameters and response values
   * @throws ServletException
   *     if an error occurs during callout execution
   */
  @Override
  protected void execute(CalloutInfo info) throws ServletException {

    final String taskTypeId = info.vars.getStringParameter(PARAM_TASK_TYPE_ID);
    if (StringUtils.isBlank(taskTypeId)) {
      info.addResult(PARAM_TASK_NO, "");
      return;
    }

    OBContext.setAdminMode(true);
    try {
      final String tableId = info.vars.getStringParameter("inpTableId");
      final String columnId = resolveTaskNoColumnId(tableId);

      final SequenceParameterList params = new SequenceParameterList();
      params.setParameter(SequenceDatabaseUtils.PROPERTY_COLUMN,
          SequenceParametersUtils.generateTableDirParameter(columnId));
      params.setParameter(SequenceDatabaseUtils.PROPERTY_CLIENT,
          SequenceParametersUtils.generateTableDirParameter(OBContext.getOBContext().getCurrentClient().getId()));
      params.setParameter(SequenceDatabaseUtils.PROPERTY_ORGANIZATION,
          SequenceParametersUtils.generateTableDirParameter(OBContext.getOBContext().getCurrentOrganization().getId()));

      params.setParameter(Sequence.PROPERTY_ETASKTASKTYPEID,
          SequenceParametersUtils.generateTableDirParameter(taskTypeId));

      try {
        final Sequence seq = TransactionalSequenceUtils.getSequenceFromParameters(params);
        final String preview = TransactionalSequenceUtils.getNextValueFromSequence(seq, false);

        info.addResult(PARAM_TASK_NO, StringUtils.isBlank(preview) ? "" : "<" + preview + ">");
      } catch (NotFoundSequenceException e) {
        info.addResult(PARAM_TASK_NO, "");
      } catch (RequiredDimensionException e) {
        throw new OBException("Missing required dimension: " + e.getRequiredDimension(), e);
      } catch (MaskValueGenerationException e) {
        throw new OBException(e.getMessage(), e);
      }

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Resolves the identifier of the task number column for the given table.
   *
   * <p>The method searches for a column named {@code Taskno} in the specified table.
   * If no matching column is found, an exception is raised.
   *
   * @param tableId
   *     the identifier of the table to search
   * @return the identifier of the task number column
   * @throws OBException
   *     if the column cannot be found for the given table
   */
  protected String resolveTaskNoColumnId(String tableId) {
    OBCriteria<Column> cCrit = OBDal.getInstance().createCriteria(Column.class);
    cCrit.setFilterOnReadableClients(false);
    cCrit.setFilterOnReadableOrganization(false);
    cCrit.createAlias(Column.PROPERTY_TABLE, "t");
    cCrit.add(Restrictions.eq("t." + Table.PROPERTY_ID, tableId));
    cCrit.add(Restrictions.eq(Column.PROPERTY_DBCOLUMNNAME, TaskConstants.TASK_NO));
    cCrit.setMaxResults(1);

    final Column col = (Column) cCrit.uniqueResult();
    if (col == null) {
      throw new OBException(
          "Couldn't find AD_Column for tableId=" + tableId + " and dbColumnName=" + TaskConstants.TASK_NO);
    }
    return col.getId();
  }
}