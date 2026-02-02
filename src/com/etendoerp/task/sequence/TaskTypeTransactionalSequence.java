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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.datamodel.Column;
import org.openbravo.model.ad.datamodel.Table;
import org.openbravo.model.ad.utility.Sequence;

import com.etendoerp.sequences.SequenceDatabaseUtils;
import com.etendoerp.sequences.parameters.SequenceParameterList;
import com.etendoerp.sequences.parameters.SequenceParametersUtils;
import com.etendoerp.sequences.transactional.DefaultTransactionalSequence;
import com.etendoerp.sequences.transactional.NotFoundSequenceException;
import com.etendoerp.sequences.transactional.RequiredDimensionException;
import com.etendoerp.sequences.transactional.TransactionalSequenceUtils;
import com.etendoerp.task.data.Task;
import com.etendoerp.task.data.TaskType;

public class TaskTypeTransactionalSequence extends DefaultTransactionalSequence {

  protected static final Logger log = LogManager.getLogger();
  private static volatile String TASKNO_AD_COLUMN_ID;

  public TaskTypeTransactionalSequence(String propertyValue) {
    super(propertyValue);
  }

  @Override
  public String generateValue(Session session, Object owner) {
    if (!(owner instanceof Task)) {
      return super.generateValue(session, owner);
    }

    final Task task = (Task) owner;
    final String current = task.getTaskNo();
    if (current != null && !current.trim().isEmpty()) {
      if (isPreviewValue(current)) {
        task.setTaskNo(null);
      } else {
        return current.trim();
      }
    }

    final TaskType taskType = task.getTaskType();
    if (taskType == null) {
      return "";
    }

    final SequenceParameterList params = new SequenceParameterList();
    final String realColumnId = resolveTaskNoColumnId();
    params.setParameter(SequenceDatabaseUtils.PROPERTY_COLUMN,
        SequenceParametersUtils.generateTableDirParameter(realColumnId));
    params.setParameter(SequenceDatabaseUtils.PROPERTY_CLIENT,
        SequenceParametersUtils.generateTableDirParameter(task.getClient().getId()));
    params.setParameter(SequenceDatabaseUtils.PROPERTY_ORGANIZATION,
        SequenceParametersUtils.generateTableDirParameter(task.getOrganization().getId()));
    params.setParameter(Sequence.PROPERTY_ETASKTASKTYPEID,
        SequenceParametersUtils.generateTableDirParameter(taskType.getId()));

    try {
      final Sequence seq = TransactionalSequenceUtils.getSequenceFromParameters(params);
      return TransactionalSequenceUtils.getNextValueFromSequence(seq, true);
    } catch (NotFoundSequenceException e) {
      return "";
    } catch (RequiredDimensionException e) {
      throw new OBException("Missing required dimension: " + e.getRequiredDimension(), e);
    } catch (Exception e) {
      throw new OBException(e.getMessage(), e);
    }
  }

  private String resolveTaskNoColumnId() {
    if (TASKNO_AD_COLUMN_ID != null) {
      return TASKNO_AD_COLUMN_ID;
    }

    final var tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, "etask_task"));
    tableCrit.add(Restrictions.eq(Table.PROPERTY_ACTIVE, true));
    tableCrit.setMaxResults(1);

    final var tables = tableCrit.list();
    final Table taskTable = tables.get(0);
    final var colCrit = OBDal.getInstance().createCriteria(Column.class);
    colCrit.add(Restrictions.eq(Column.PROPERTY_TABLE, taskTable));
    colCrit.add(Restrictions.ilike(Column.PROPERTY_DBCOLUMNNAME, "taskno"));
    colCrit.add(Restrictions.eq(Column.PROPERTY_ACTIVE, true));
    colCrit.setMaxResults(1);
    final var cols = colCrit.list();

    TASKNO_AD_COLUMN_ID = cols.get(0).getId();
    return TASKNO_AD_COLUMN_ID;
  }

  private boolean isPreviewValue(String v) {
    if (v == null) return false;
    final String s = v.trim();
    return s.length() > 2 && s.startsWith("<") && s.endsWith(">");
  }
}