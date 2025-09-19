package com.etendoerp.task.logs;

import com.etendoerp.asyncprocess.data.LogHeader;
import com.etendoerp.asyncprocess.hooks.LogPersistorIdentifierHook;
import com.etendoerp.task.data.Task;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.OBDal;

public class TaskLogsIdentifier implements LogPersistorIdentifierHook {
  @Override
  public boolean identifyLogRecord(LogHeader header) {
    JSONObject paramsJson = null;
    try {
      paramsJson = new JSONObject(header.getProcess());
      if (paramsJson != null && paramsJson.has("etask_task_id")) {
        Task task = OBDal.getInstance().get(Task.class, paramsJson.optString("etask_task_id"));
        if (task != null) {
          header.setEtaskTask(task);
          return true;
        }
      }
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
    return false;
  }
}
