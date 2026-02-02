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

import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBCriteria;
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

public class TaskTypeCallout extends SimpleCallout {
  private static final String PARAM_TASK_TYPE_ID = "inpetaskTaskTypeId";
  private static final String PARAM_TASK_NO = "inptaskno";

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

  private String resolveTaskNoColumnId(String tableId) {
    OBCriteria<Column> cCrit = OBDal.getInstance().createCriteria(Column.class);
    cCrit.setFilterOnReadableClients(false);
    cCrit.setFilterOnReadableOrganization(false);
    cCrit.createAlias(Column.PROPERTY_TABLE, "t");
    cCrit.add(Restrictions.eq("t." + Table.PROPERTY_ID, tableId));
    cCrit.add(Restrictions.ilike(Column.PROPERTY_DBCOLUMNNAME, "Taskno"));
    cCrit.setMaxResults(1);

    final Column col = (Column) cCrit.uniqueResult();
    if (col == null) {
      throw new OBException("Couldn't find AD_Column for tableId=" + tableId + " and dbColumnName=Taskno");
    }
    return col.getId();
  }
}