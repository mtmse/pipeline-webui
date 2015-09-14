package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.RuntimeErrorException;

import models.Job;
import models.Notification;
import models.NotificationConnection;
import models.Setting;
import models.Upload;
import models.User;
import models.UserSetting;

import com.fasterxml.jackson.databind.JsonNode;






//import org.daisy.pipeline.client.Pipeline2WS;
import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.Pipeline2Logger;
import org.daisy.pipeline.client.http.Pipeline2HttpClient;
import org.daisy.pipeline.client.http.WS;
import org.daisy.pipeline.client.http.WSResponse;
import org.daisy.pipeline.client.utils.XPath;
//import org.daisy.pipeline.client.models.Script;
//import org.daisy.pipeline.client.models.script.Argument;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import play.Logger;
import play.mvc.*;
import utils.ContentType;
import utils.Files;
import views.html.defaultpages.error;

public class Jobs extends Controller {

	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public static Result newJob() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());

		User.flashBrowserId(user);
		return ok(views.html.Jobs.newJob.render(Application.pipeline2EngineAvailable()));
	}
	
	public static Result getJobs() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return redirect(routes.Login.login());
		
		if (user.admin)
			flash("showOwner", "true");
		flash("userid", user.id+"");
		
		User.flashBrowserId(user);
		return ok(views.html.Jobs.getJobs.render());
	}
	
	public static Result getJobsJson() {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return unauthorized("unauthorized");
		
		WSResponse jobs;
		NodeList jobNodes;
		List<Job> jobList;
		/*try {
			jobs = org.daisy.pipeline.client.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
			
			if (jobs.status != 200) {
				Logger.error(jobs.status+": "+jobs.statusName+" - "+jobs.statusDescription+" : "+jobs.asText());
				return internalServerError(jobs.statusDescription);
			}
			
			jobNodes = XPath.selectNodes("//d:job", jobs.asXml(), Pipeline2WS.ns);
			
			jobList = new ArrayList<Job>();
			for (int n = 0; n < jobNodes.getLength(); n++) {
				Node jobNode = jobNodes.item(n);
				
				Job job = Job.findById(XPath.selectText("@id", jobNode, XPath.dp2ns));
				if (job == null) {
					Logger.warn("No job with id "+XPath.selectText("@id", jobNode, XPath.dp2ns)+" was found.");
				} else {
					job.href = XPath.selectText("@href", jobNode, XPath.dp2ns);
					job.status = XPath.selectText("@status", jobNode, XPath.dp2ns);
					if (user.admin || user.id >= 0 && user.id.equals(job.user) || user.id < 0 && job.user < 0 && "true".equals(Setting.get("users.guest.shareJobs"))) {
						jobList.add(job);
					}
				}
			}
			
		} catch (Pipeline2Exception e) {
			Logger.error(e.getMessage(), e);
			return internalServerError("A problem occured while communicating with the Pipeline engine");
		}*/
		
//		Collections.sort(jobList);
//		Collections.reverse(jobList);
		
//		JsonNode jobsJson = play.libs.Json.toJson(jobList);
//		return ok(jobsJson);
		return null;
	}
	
	public static Result getJob(String id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug("getJob("+id+")");
		
		WSResponse response;
		/*org.daisy.pipeline.client.models.Job job;
		try {
			Logger.debug("org.daisy.pipeline.client.Jobs.get ...");
			response = org.daisy.pipeline.client.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id, null);
			Logger.debug("org.daisy.pipeline.client.Jobs.get done");
			
			if (response.status != 200 && response.status != 201) {
				return Application.error(response.status, response.statusName, response.statusDescription, response.asText());
			}
			
			Logger.debug("new org.daisy.pipeline.client.models.Job ...");
			job = new org.daisy.pipeline.client.models.Job(response.asXml());
			Logger.debug("new org.daisy.pipeline.client.models.Job done");
			
		} catch (Pipeline2Exception e) {
			Logger.error(e.getMessage(), e);
			return Application.error(500, "Sorry, something unexpected occured", "A problem occured while communicating with the Pipeline engine", e.getMessage());
		}
		
		Job webuiJob = Job.findById(job.id);
		if (webuiJob == null) {
			Logger.debug("Job #"+job.id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
				)) {
			return forbidden("You are not allowed to view this job.");
		}
		
		webuiJob.status = job.status.toString();
		try {
			webuiJob.messages = job.getMessagesAsList();
		} catch (Pipeline2Exception e) {
			return internalServerError(e.getMessage());
		}
//		if (!Job.lastMessageSequence.containsKey(job.id) && job.messages.size() > 0) {
//			Collections.sort(job.messages);
//			Job.lastMessageSequence.put(job.id, job.messages.get(job.messages.size()-1).sequence);
//		}
//		if (!Job.lastStatus.containsKey(job.id)) {
//			Job.lastStatus.put(job.id, job.status);
//		}
		
		User.flashBrowserId(user);
		return ok(views.html.Jobs.getJob.render(job, webuiJob));*/ return null;
	}
	
	public static Result getJobJson(String id) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		
		if (!(	user.admin
			||	webuiJob.user.equals(user.id)
			||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
			)) {
			return forbidden("You are not allowed to view this job.");
		}
		
		org.daisy.pipeline.client.models.Job job = Application.ws.getJob(id, 0);
		if (job == null) {
			Logger.error("An error occured while fetching the job from the engine");
			return internalServerError("An error occured while fetching the job from the engine");
		}
		
		webuiJob.status = job.getStatus().toString();
		try {
			webuiJob.messages = job.getMessages();
			
		} catch (Pipeline2Exception e) {
			return play.mvc.Results.internalServerError(e.getMessage());
		}
		Collections.sort(webuiJob.messages);
		
		JsonNode jobJson = play.libs.Json.toJson(webuiJob);
		return ok(jobJson);
	}
	
	public static Result getAllResults(String id) {
		return getResult(id, null);
	}
	
	public static Result getResult(String id, String href) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
				||	webuiJob.user.equals(user.id)
				||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
					))
				return forbidden("You are not allowed to view this job.");
		
//		try {
			Logger.info("retrieving result from Pipeline 2 engine...");
			
			Logger.debug("href: "+(href==null?"[null]":href));
			
			org.daisy.pipeline.client.models.Job job = Application.ws.getJob(id, 0);
			org.daisy.pipeline.client.models.Result result = job.getResultFromHref(href);
			File resultFile = result.asFile();
			// org.daisy.pipeline.client.Jobs.getResultFromFile(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id, href);
			
			if (resultFile == null || !resultFile.exists()) {
				return badRequest("Unable to retrieve file.");
			}
			
			try {
				String contentType = ContentType.probe(resultFile.getName(), new FileInputStream(resultFile));
				response().setContentType(contentType);
				
				Logger.debug("contentType: "+contentType);
				
			} catch (FileNotFoundException e) {
				/* ignore */
			}
			
			long size = resultFile.length();
			if (size > 0) {
				response().setHeader("Content-Length", size+"");
				Logger.debug("size: "+size);
			} else {
				Logger.debug("content size unknown (size="+size+")");
			}
			
			String filename;
			if (href == null)
				filename = id+".zip";
			else if (href.matches("^[^/]*/[^/]*/idx/.*$"))
				filename = href.replaceFirst("^.*/([^/]*)$", "$1");
			else
				filename = id+"-"+href.replaceFirst("^[^/]*/([^/]*)/?.*?$", "$1")+".zip";
			response().setHeader("Content-Disposition", "attachment; filename=\""+filename);
			
			String parse = request().getQueryString("parse");
			if ("report".equals(parse)) {
				response().setContentType("text/html");
				String report = Files.read(resultFile);
				Pattern regex = Pattern.compile("^.*<body[^>]*>(.*)</body>.*$", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
				Matcher regexMatcher = regex.matcher(report);
				String body = null;
				if (regexMatcher.find()) {
					body = regexMatcher.group(1);
				} else {
					Logger.info("no body element found in report; returning the entire report");
					body = report;
				}
				final byte[] utf8Bytes = body.getBytes(StandardCharsets.UTF_8);
				response().setHeader(CONTENT_LENGTH, ""+utf8Bytes.length);
				return ok(body);
				
			}
			
			return ok(resultFile);
			
//		} catch (Pipeline2Exception e) {
//			Logger.error(e.getMessage(), e);
//			return Application.error(500, "Sorry, something unexpected occured", "A problem occured while communicating with the Pipeline engine", e.getMessage());
//		}
	}

	public static Result getLog(final String id) {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Job webuiJob = Job.findById(id);
		if (webuiJob == null) {
			Logger.debug("Job #"+id+" was not found.");
			return notFound("Sorry; something seems to have gone wrong. The job was not found.");
		}
		if (!(	user.admin
				||	webuiJob.user.equals(user.id)
				||	webuiJob.user < 0 && user.id < 0 && "true".equals(Setting.get("users.guest.shareJobs"))
					))
				return forbidden("You are not allowed to view this job.");
		
		String jobLog = Application.ws.getJobLog(id);
		//jobLog = org.daisy.pipeline.client.Jobs.getLog(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (jobLog == null) {
			Pipeline2Logger.logger().error("Was not able to read job log for "+id);
			return Application.internalServerError("Unable to retrieve job log.");
		}
		
		if (jobLog.length() == 0) {
			return ok("The log is empty.");
		}

		return ok(jobLog);
	}

    public static Result postJob() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug("------------------------------ Posting job... ------------------------------");

		Map<String, String[]> params = request().body().asFormUrlEncoded();
		if (params == null) {
			return Application.error(500, "Internal Server Error", "Could not read form data", request().body().asText());
		}

		String id = params.get("id")[0];
		
		if ("false".equals(UserSetting.get(user.id, "scriptEnabled-"+id))) {
			return forbidden();
		}

		// Get a description of the script from Pipeline 2 Web API
		WSResponse scriptResponse;
		org.daisy.pipeline.client.models.Script script = Application.ws.getScript(id);
		if (script == null) {
			Logger.error("An error occured while trying to ready the script with id '"+id+"' from the engine.");
			return Application.internalServerError("An error occured while trying to ready the script with id '"+id+"' from the engine.");
		}
		
		// Parse and validate the submitted form (also create any necessary output directories in case of local mode)
		Scripts.ScriptForm scriptForm = new Scripts.ScriptForm(user.id, script, params);
		String timeString = new Date().getTime()+"";
		scriptForm.validate();

		File contextZipFile = null;

		if (scriptForm.uploads.size() > 0) {

			// ---------- Create a temporary directory ("the context") ----------
			File contextDir = null;
			try {
				contextDir = File.createTempFile("jobContext", "");

				if (!(contextDir.delete())) {
					Logger.error("Could not delete contextDir file: " + contextDir.getAbsolutePath());

				} if (!(contextDir.mkdir())) {
					Logger.error("Could not create contextDir directory: " + contextDir.getAbsolutePath());
				}

			} catch (IOException e) {
				Logger.error("Could not create temporary context directory: "+e.getMessage(), e);
				return internalServerError("Could not create temporary context directory");
			}

			Logger.debug("Created context directory: "+contextDir.getAbsolutePath());

			// ---------- Copy or unzip all uploads to a common directory ----------
			Logger.debug("number of uploads: "+scriptForm.uploads.size());
			for (Long uploadId : scriptForm.uploads.keySet()) {
				Upload upload = scriptForm.uploads.get(uploadId);
				if (upload.isZip()) {
					Logger.debug("unzipping "+upload.getFile()+" to contextDir");
					try {
						utils.Files.unzip(upload.getFile(), contextDir);
					} catch (IOException e) {
						Logger.error("Unable to unzip files into context directory.", e);
						return Application.error(500, "Internal Server Error", "Unable to unzip uploaded ZIP file", "");
					}
				} else {
					File from = upload.getFile();
					File to = new File(contextDir, from.getName());
					Logger.debug("copying "+from+" to "+to);
					try {
						utils.Files.copy(from, to); // We could do file.renameTo here to move it instead of making a copy, but copying makes it easier in case we need to re-run a job
					} catch (IOException e) {
						Logger.error("Unable to copy files to context directory.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to copy files to context directory.");
					}
				}
			}
			
			/*if (Application.getAlive().localfs) {
				Logger.debug("Running the Web UI and fwk on the same filesystem, no need to ZIP files...");
				for (Argument arg : script.arguments) {
					if (arg.output != null) {
						Logger.debug(arg.name+" is output (\""+arg.output+"\"); don't resolve URI");
						continue;
					}
					if ("anyFileURI".equals(arg.xsdType)) {
						Logger.debug(arg.name+" is file(s); resolve URI(s)");
						for (int i = 0; i < arg.size(); i++) {
							arg.set(i, contextDir.toURI().resolve(Files.encodeURI(arg.get(i))));
						}
					}
				}
				
			} else {
				for (Argument arg : script.arguments) {
					if (arg.output == null && "anyFileURI".equals(arg.xsdType)) {
						Logger.debug(arg.name+" is file(s); resolve relative URI(s)");
						for (int i = 0; i < arg.size(); i++) {
							arg.set(i, new File(arg.get(i)).toURI().toString().substring(new File("").toURI().toString().length()));
						}
					}
				}
				
				if (contextDir.list().length == 0) {
					contextZipFile = null;
				} else {
					try {
						contextZipFile = File.createTempFile("jobContext", ".zip");
						Logger.debug("Created job context zip file: "+contextZipFile);
					} catch (IOException e) {
						Logger.error("Unable to create temporary job context ZIP file.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to create temporary job context ZIP file.");
					}
					try {
						utils.Files.zip(contextDir, contextZipFile);
					} catch (IOException e) {
						Logger.error("Unable to zip context directory.", e);
						throw new RuntimeErrorException(new Error(e), "Unable to zip context directory.");
					}
				}
			}*/

		}
		
		Map<String,String> callbacks = new HashMap<String,String>();
//		if (play.Play.isDev()) { // TODO: only in dev for now
//			callbacks.put("messages", routes.Callbacks.postCallback("messages").absoluteURL(request()));
//			callbacks.put("status", routes.Callbacks.postCallback("status").absoluteURL(request()));
//		}
		
		if (contextZipFile == null)
			Logger.debug("No files in context, submitting job without context ZIP file");
		else
			Logger.debug("Context ZIP file is present, submitting job with context ZIP file");
		
		//WSResponse job;
		String jobId;
		org.daisy.pipeline.client.models.Job job = null; // TODO: populate job instead of setting it to null! new Job (script href, script arguments, job context, callbacks, etc.)
//		try {
			job = Application.ws.postJob(job, contextZipFile);
//			job = org.daisy.pipeline.client.Jobs.post(
//					Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"),
//					scriptForm.script.href, scriptForm.script.arguments, contextZipFile, callbacks
//			);
			
			if (job == null) {
				Logger.error("An error occured when trying to post job");
				Application.internalServerError("An error occured when trying to post job");
			}
			
//			if (job.status != 200 && job.status != 201) {
//				return Application.error(job.status, job.statusName, job.statusDescription, job.asText());
//			}
//			
//			jobId = XPath.selectText("/*/@id", job.asXml(), Pipeline2WS.ns);
			
//		} catch (Pipeline2Exception e) {
//			Logger.error(e.getMessage(), e);
//			return Application.error(500, "Sorry, something unexpected occured", "A problem occured while communicating with the Pipeline engine", e.getMessage());
//		}
		
		Job webUiJob = new Job(job.getId(), user);
		webUiJob.nicename = id;
		webUiJob.localDirName = timeString;
		webUiJob.scriptId = script.getId();
		webUiJob.scriptName = script.getNicename();
		if (scriptForm.uploads != null && scriptForm.uploads.size() > 0) {
			String filenames = "";
			int i = 0;
			for (Long uploadId : scriptForm.uploads.keySet()) {
				if (i > 0)
					filenames += ", ";
				if (i++ >= 3) {
					filenames += "...";
					break;
				}
				filenames += scriptForm.uploads.get(uploadId).getFile().getName();
			}
			if (filenames.length() > 0)
				webUiJob.nicename = filenames;
		}
		webUiJob.save();
		NotificationConnection.push(webUiJob.user, new Notification("job-created-"+webUiJob.id, webUiJob.created.toString()));
		for (Long uploadId : scriptForm.uploads.keySet()) {
			// associate uploads with job
			scriptForm.uploads.get(uploadId).job = job.getId();
			scriptForm.uploads.get(uploadId).save();
		}
		
		webUiJob.status = "IDLE";
		
		JsonNode jobJson = play.libs.Json.toJson(webUiJob);
		Notification jobNotification = new Notification("new-job", jobJson);
		Logger.debug("pushed new-job notification with status=IDLE for job #"+job.getId());
		NotificationConnection.pushJobNotification(webUiJob.user, jobNotification);
		webUiJob.pushNotifications();
		
		if (user.id < 0 && scriptForm.guestEmail != null && scriptForm.guestEmail.length() > 0) {
			String jobUrl = Application.absoluteURL(routes.Jobs.getJob(job.getId()).absoluteURL(request())+"?guestid="+(models.User.parseUserId(session())!=null?-models.User.parseUserId(session()):""));
			String html = views.html.Account.emailJobCreated.render(jobUrl, webUiJob.nicename).body();
			String text = "To view your Pipeline 2 job, go to this web address: " + jobUrl;
			if (Account.sendEmail("Job created: "+webUiJob.nicename, html, text, scriptForm.guestEmail, scriptForm.guestEmail))
				flash("success", "An e-mail was sent to "+scriptForm.guestEmail+" with a link to this job.");
			else
				flash("error", "Was unable to send an e-mail with a link to this job.");
		}
		
		return redirect(controllers.routes.Jobs.getJob(job.getId()));
	}
    
    public static Result delete(String jobId) {
    	Job job = Job.findById(jobId);
    	if (job != null) {
    		Logger.debug("deleting "+jobId);
    		job.delete();
    		return ok();
    	} else {
    		Logger.debug("no such job: "+jobId);
    		return badRequest();
    	}
    }
	
}
