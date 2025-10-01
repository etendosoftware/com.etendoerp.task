package com.smf.jobs;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;

/**
 * Test Action that returns a plain text message (not JSON).
 */
public class TestActionPlain extends Action {

	@Override
	protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
		ActionResult ar = new ActionResult();
		ar.setType(Result.Type.SUCCESS);
		ar.setMessage("plain text message");
		return ar;
	}

  @Override
  protected Class<?> getInputClass() {
		return org.codehaus.jettison.json.JSONObject.class;
  }

}
