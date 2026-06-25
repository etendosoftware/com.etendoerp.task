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
import org.openbravo.dal.service.Restrictions;
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
import com.etendoerp.task.utils.TaskConstants;

/**
 * Transactional sequence implementation that generates values based on a task type context.
 *
 * <p>This class extends {@link DefaultTransactionalSequence} to support task type–specific
 * sequence generation.
 */
public class TaskTypeTransactionalSequence extends DefaultTransactionalSequence {
  protected static final Logger log = LogManager.getLogger();
  protected static volatile String taskNoAdColumnId;

  /**
   * Creates a new transactional sequence using the given property value.
   *
   * @param propertyValue
   *     the property value used to initialize the transactional sequence
   */
  public TaskTypeTransactionalSequence(String propertyValue) {
    super(propertyValue);
  }

  /**
   * Generates and returns a task number value for the given owner.
   *
   * <p>If the owner is not a {@link Task}, the default generation logic is applied.
   * For tasks, an existing non-preview value is reused when present; otherwise,
   * a new value is generated using the configured sequence parameters. If required
   * information is missing or no sequence is found, an empty string is returned.
   *
   * @param session
   *     the Hibernate session used during value generation
   * @param owner
   *     the entity for which the value is being generated
   * @return the generated or existing task number, or an empty string if it cannot be resolved
   * @throws OBException
   *     if a required dimension is missing or an unexpected error occurs
   */
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

  /**
   * Resolves and returns the identifier of the database column associated with the task number.
   *
   * <p>If the identifier has already been resolved, it is returned from cache. Otherwise, the method
   * queries the active task table and retrieves the identifier of the active column named {@code Taskno}.
   *
   * @return the identifier of the task number column
   */
  protected static String resolveTaskNoColumnId() {
    if (taskNoAdColumnId != null) {
      return taskNoAdColumnId;
    }

    final var tableCrit = OBDal.getInstance().createCriteria(Table.class);
    tableCrit.add(Restrictions.ilike(Table.PROPERTY_DBTABLENAME, TaskConstants.TASK_TABLENAME));
    tableCrit.add(Restrictions.eq(Table.PROPERTY_ACTIVE, true));
    tableCrit.setMaxResults(1);

    final var tables = tableCrit.list();
    final Table taskTable = tables.get(0);

    final var colCrit = OBDal.getInstance().createCriteria(Column.class);
    colCrit.add(Restrictions.eq(Column.PROPERTY_TABLE, taskTable));
    colCrit.add(Restrictions.eq(Column.PROPERTY_DBCOLUMNNAME, TaskConstants.TASK_NO));
    colCrit.add(Restrictions.eq(Column.PROPERTY_ACTIVE, true));
    colCrit.setMaxResults(1);

    final var cols = colCrit.list();

    taskNoAdColumnId = cols.get(0).getId();
    return taskNoAdColumnId;
  }

  /**
   * Determines whether the given value represents a preview placeholder.
   *
   * <p>A value is considered a preview if it is non-null, trimmed, longer than two characters,
   * starts with {@code '<'}, and ends with {@code '>'}.
   *
   * @param v
   *     the value to evaluate
   * @return {@code true} if the value matches the preview format, {@code false} otherwise
   */
  protected boolean isPreviewValue(String v) {
    if (v == null) return false;
    final String s = v.trim();
    return s.length() > 2 && s.startsWith("<") && s.endsWith(">");
  }
}