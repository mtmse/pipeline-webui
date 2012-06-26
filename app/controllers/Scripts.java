package controllers;

import java.util.ArrayList;
import java.util.List;

import models.Setting;
import models.User;

import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
import pipeline2.models.Script;
import pipeline2.models.script.*;
import play.Logger;
import play.libs.XPath;
import play.mvc.*;
import utils.XML;

public class Scripts extends Controller {
	
	public static Result getScripts() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, "");
		}
		
		List<Script> scripts = Script.getScripts(response);
		
		return ok(views.html.Scripts.getScripts.render(scripts));
	}
	
	public static Result getScript(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, "");
		}
		
		Script script = new Script(response);
		
		boolean uploadFiles = false;
		script.hideAdvancedOptions = "true".equals(Setting.get("jobs.hideAdvancedOptions"));
		for (Argument arg : script.arguments) {
			if ("input".equals(arg.kind) || "anyFileURI".equals(arg.xsdType)) {
				uploadFiles = true;
			}
			if (script.hideAdvancedOptions && arg.required == false)
				arg.hide = true;
		}
		
		return ok(views.html.Scripts.getScript.render(script, uploadFiles));

	}

}