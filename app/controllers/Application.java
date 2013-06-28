package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.daisy.pipeline.client.models.Alive;

import models.Notification;
import models.NotificationConnection;
import models.Setting;
import models.User;
import play.Configuration;
import play.Logger;
import play.api.mvc.Call;
import play.mvc.*;
import utils.Pipeline2Engine;

public class Application extends Controller {
	
	public static final boolean debug = "DEBUG".equals(Configuration.root().getString("logger.application"));
	
	public static final String DEFAULT_DP2_ENDPOINT_LOCAL = "http://localhost:8181/ws";
	public static final String DEFAULT_DP2_ENDPOINT_REMOTE = "http://localhost:8182/ws";
	public static final String SLASH = System.getProperty("file.separator");
	
	public static final String datasource = Configuration.root().getString("dp2.datasource");
	private static Alive alive = null;
	
	public static final String version = Configuration.root().getString("version");
	
	public static Result index() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		String landingPage = Setting.get("appearance.landingPage");
		if ("welcome".equals(landingPage)) return redirect(routes.FirstUse.welcome());
		if ("scripts".equals(landingPage)) return redirect(routes.Jobs.newJob());
		if ("jobs".equals(landingPage) && !(user.id <= -2 && !"true".equals(Setting.get("users.guest.shareJobs")))) return redirect(routes.Jobs.getJobs());
		if ("about".equals(landingPage)) return redirect(routes.Application.about());
		if ("admin".equals(landingPage) && user.admin) return redirect(routes.Administrator.getSettings());
		if ("account".equals(landingPage) && user.id >= 0) return redirect(routes.Account.overview());
		
		return redirect(routes.Jobs.newJob());
	}
	
	public static Result about() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		User.flashBrowserId(user);
		
		File about = new File("about.html");
		if (about.exists()) {
			return ok(views.html.about.render(utils.Files.read(about)));
		} else {
			return ok(views.html.about.render(null));
		}
	}
	
	public static Result theme(String filename) {
		if ("".equals(themeName())) {
			return redirect(routes.Assets.at(filename));
			
		} else {
			String theme = Application.themeName();
			File file = new File("themes/"+theme+"/"+filename);
			if (file.exists()) {
				try {
					if (filename.endsWith("css"))
						response().setContentType("text/css");
					else if (filename.endsWith("png"))
						response().setContentType("image/png");
					else if (filename.endsWith("jpg") || filename.endsWith("jpeg"))
						response().setContentType("image/jpeg");
					else if (filename.endsWith("gif"))
						response().setContentType("image/gif");
					else if (filename.endsWith("js"))
						response().setContentType("application/javascript");
					
					return ok(new FileInputStream(file));
					
				} catch (FileNotFoundException e) {
					Logger.error("Could not open file input stream for '"+filename+"' in theme '"+theme+"'.", e);
					return redirect(routes.Assets.at(filename));
				}
			} else {
				return redirect(routes.Assets.at(filename));
			}
		}
	}
	
	public static Result error(int status, String name, String description, String message) {
		User user = User.authenticate(request(), session());
		User.flashBrowserId(user);
		return status(status, views.html.error.render(status, name, description, message));
	}
	
	public static Result redirect(String path, String file) {
		return movedPermanently(path+file);
	}
	
	public static String themeName = null;
	public static String themeName() {
		if (themeName == null)
			themeName = Setting.get("appearance.theme");
		return themeName;
	}
	
	public static String titleLink() {
		String titleLink = Setting.get("appearance.titleLink");
		if ("welcome".equals(titleLink)) titleLink = routes.FirstUse.welcome().absoluteURL(request());
		else if ("scripts".equals(titleLink)) titleLink = routes.Jobs.newJob().absoluteURL(request());
		else if ("jobs".equals(titleLink)) titleLink = routes.Jobs.getJobs().absoluteURL(request());
		else if ("about".equals(titleLink)) titleLink = routes.Application.about().absoluteURL(request());
		else if ("admin".equals(titleLink)) titleLink = routes.Administrator.getSettings().absoluteURL(request());
		else if ("account".equals(titleLink)) titleLink = routes.Account.overview().absoluteURL(request());
		return titleLink;
	}
	
	private static String deployment = null;
	/**
	 * Returns a buffered value of the deployment type instead of having to check the DB each time using Setting.get("deployment").
	 * @return
	 */
	public static String deployment() {
		return deployment != null ? deployment : Setting.get("deployment");
	}

	public static String getPipeline2EngineState() {
		if (Pipeline2Engine.getState() != null)
			return Pipeline2Engine.getState()+"";
		
		if (Application.alive == null || Application.alive.error)
			return Pipeline2Engine.State.STOPPED+"";
		
		return Pipeline2Engine.State.RUNNING+"";
	}
	
	public static Alive getAlive() {
		return alive;
	}
	
	public static void setAlive(Alive alive) {
		Application.alive = alive;
		NotificationConnection.pushAll(new Notification("dp2.engine", alive));
	}
}
