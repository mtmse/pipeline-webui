package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.http.WSResponse;
import org.daisy.pipeline.client.models.Script;
//import org.daisy.pipeline.client.models.script.Argument;

import models.Setting;
import models.Upload;
import models.User;
import models.UserSetting;

import play.Logger;
import play.mvc.*;

public class Scripts extends Controller {

	public static Result getScriptsJson() {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");

		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		WSResponse response;
		List<Script> scripts = null;
		String error = null;

		int status = 200;

		/*try {
			/*response = org.daisy.pipeline.client.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
			if (response.status != 200) {
				status = response.status;
				error = response.asText();

			} else {
				scripts = new ArrayList<Script>();
				List<Script> allScripts = Script.getScripts(response);
				for (Script script : allScripts) {
					if (!"false".equals(UserSetting.get(-2L, "scriptEnabled-"+script.id)) || user.admin && request().queryString().containsKey("showAll")) {
						scripts.add(script);
					}
				}
			}
		} catch (Pipeline2Exception e) {
			Logger.error(e.getMessage(), e);
			status = 500;
			error = e.getMessage();
		}*/

		if (status == 200) {
			JsonNode scriptsJson = play.libs.Json.toJson(scripts);
			return ok(scriptsJson);
		} else {
			return status(status,error);
		}
	}
	
	public static Result getScript(String id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		if ("false".equals(UserSetting.get(-2L, "scriptEnabled-"+id))) {
			return forbidden();
		}
		
		WSResponse response;
		Script script = Application.ws.getScript(id);
		
		if (script == null) {
			Logger.error("An error occured while trying to read the script with id '"+id+"' from the engine.");
			return Application.internalServerError("An error occured while trying to read the script with id '"+id+"' from the engine.");
		}

		/* List of mime types that are supported by more than one file argument.
		 * The Web UI cannot automatically assign files of these media types to a
		 * file argument since there are multiple possible file arguments/widgets. */
		List<String> mediaTypeBlacklist = new ArrayList<String>();
		{
			Map<String,Integer> mediaTypeOccurences = new HashMap<String,Integer>();
			/*for (Argument arg : script.arguments) {
				if ("output".equals(arg.kind) || arg.output != null)
					continue;
				for (String mediaType : arg.mediaTypes) {
					if (mediaTypeOccurences.containsKey(mediaType)) {
						mediaTypeOccurences.put(mediaType, mediaTypeOccurences.get(mediaType)+1);
					} else {
						mediaTypeOccurences.put(mediaType, 1);
					}
				}
			}
			for (String mediaType : mediaTypeOccurences.keySet()) {
				if (mediaTypeOccurences.get(mediaType) > 1)
					mediaTypeBlacklist.add(mediaType);
			}*/
		}

		boolean uploadFiles = false;
		boolean hideAdvancedOptions = "true".equals(Setting.get("jobs.hideAdvancedOptions"));
		boolean hasAdvancedOptions = false;
		/*for (Argument arg : script.arguments) {
			if ("output".equals(arg.kind) || arg.output != null)
				continue;
			if (arg.required != Boolean.TRUE)
				hasAdvancedOptions = true;
			if ("input".equals(arg.kind) || "anyFileURI".equals(arg.xsdType)) {
				uploadFiles = true;
			}
		}

		User.flashBrowserId(user);
		return ok(views.html.Scripts.getScript.render(script, script.id.replaceAll(":", "\\x3A"), uploadFiles, hasAdvancedOptions, hideAdvancedOptions, mediaTypeBlacklist));*/
		return null;
	}
	
	public static Result getScriptJson(String id) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");

		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		if ("false".equals(UserSetting.get(-2L, "scriptEnabled-"+id))) {
			return forbidden();
		}
		
		WSResponse response;
		Script script = null;
		String error = null;

		int status = 200;

		/*try {
			response = org.daisy.pipeline.client.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
			if (response.status != 200) {
				status = response.status;
				error = response.asText();

			} else {
				script = new Script(response);
			}
		} catch (Pipeline2Exception e) {
			Logger.error(e.getMessage(), e);
			status = 500;
			error = e.getMessage();
		}*/

		if (status == 200) {
			JsonNode scriptJson = play.libs.Json.toJson(script);
			return ok(scriptJson);
		} else {
			return status(status,error);
		}
	}

	public static class ScriptForm {

		//public org.daisy.pipeline.client.models.Script script;
		public Map<Long,Upload> uploads;
		public Map<String,List<String>> errors;

		public String guestEmail;

		//                                                          kind    position  part      name
		private static final Pattern PARAM_NAME = Pattern.compile("^([A-Za-z]+)(\\d*)([A-Za-z]*?)-(.*)$");
		private static final Pattern FILE_REFERENCE = Pattern.compile("^upload(\\d+)-file(\\d+)$");

		public ScriptForm(Long userId, Script script, Map<String, String[]> params) {
			//this.script = script;

			// Get all referenced uploads from DB
			this.uploads = new HashMap<Long,Upload>();
			for (String uploadId : params.get("uploads")[0].split(",")) {
				if ("".equals(uploadId))
					continue;
				Upload upload = Upload.findById(Long.parseLong(uploadId));
				if (upload != null && upload.user.equals(userId)) {
					uploads.put(upload.id, upload);
				}
			}

			// Parse all arguments
			for (String param : params.keySet()) {
				Matcher matcher = PARAM_NAME.matcher(param);
				if (!matcher.find()) {
					Logger.debug("Unable to parse argument parameter: "+param);
				} else {
					String kind = matcher.group(1);
					String name = matcher.group(4);
					Logger.debug("script form: "+kind+": "+name);

					/*Argument argument = script.getArgument(name, kind);
					if (argument == null) {
						Logger.debug("'"+name+"' is not an argument for the script '"+script.id+"'; ignoring it");
						continue;
					}

					if ("anyFileURI".equals(argument.xsdType)) {
						String[] filerefs = params.get(param)[0].split(",");
						for (String fileref : filerefs) {
							matcher = FILE_REFERENCE.matcher(fileref);
							if (!matcher.find()) {
								Logger.debug("Unable to parse file reference: "+fileref);
							} else {
								Long uploadId = Long.parseLong(matcher.group(1));
								Integer fileNr = Integer.parseInt(matcher.group(2));

								if (uploads.containsKey(uploadId)) {
									argument.add(uploads.get(uploadId).listFiles().get(fileNr).href);

								} else {
									Logger.warn("No such upload: "+uploadId);
								}
							}
						}

					} else if ("parameters".equals(argument.xsdType)) {
						// TODO: parameters are not implemented yet

					} else { // All other types are treated the same
						for (int i = 0; i < params.get(param).length; i++) {
							argument.add(params.get(param)[i]);
						}
					}*/
				}
			}

			if (userId < 0 && params.containsKey("guest-email"))
				this.guestEmail = params.get("guest-email")[0];

			this.errors = new HashMap<String, List<String>>();
		}

		public void validate() {
			if (guestEmail != null && !"".equals(guestEmail) && !guestEmail.matches("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}$")) {
				addError("guest-email", "Please enter a valid e-mail address.");
			}

			// TODO: validate arguments
		}

		public boolean hasErrors() {
			return errors.size() > 0;
		}

		public void addError(String field, String error) {
			if (!errors.containsKey(field))
				errors.put(field, new ArrayList<String>());
			errors.get(field).add(error);
		}
	}

}