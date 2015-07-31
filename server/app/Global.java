import play.*;
import models.*;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.daisy.pipeline.client.Pipeline2WS;
import org.daisy.pipeline.client.Pipeline2WSException;
import org.daisy.pipeline.client.Pipeline2WSResponse;
import org.daisy.pipeline.client.Pipeline2WSLogger;

import controllers.Administrator;
import controllers.FirstUse;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.Pipeline2PlayLogger;

public class Global extends GlobalSettings {
	
	@Override
	public synchronized void beforeStart(play.Application app) {
		
	}
	
	@Override
	public synchronized void onStart(play.Application app) {
		// Application has started...
		
		Pipeline2WS.setLoggerImplementation(new Pipeline2PlayLogger());
		if ("DEBUG".equals(Configuration.root().getString("logger.application"))) {
			Logger.debug("Enabling clientlib debug mode");
			Pipeline2WS.logger().setLevel(Pipeline2WSLogger.LEVEL.DEBUG);
		}
		
		NotificationConnection.notificationConnections = new ConcurrentHashMap<Long,List<NotificationConnection>>();
		
		if (Setting.get("appearance.title") == null)
			Setting.set("appearance.title", "DAISY Pipeline 2");
		
		if (Setting.get("appearance.titleLink") == null)
			Setting.set("appearance.titleLink", "scripts");
		
		if (Setting.get("appearance.titleLink.newWindow") == null)
			Setting.set("appearance.titleLink.newWindow", "false");
		
		if (Setting.get("appearance.landingPage") == null)
			Setting.set("appearance.landingPage", "welcome");
		
		if (Setting.get("appearance.theme") == null)
			Setting.set("appearance.theme", "");
		
		if (Setting.get("jobs.hideAdvancedOptions") == null)
			Setting.set("jobs.hideAdvancedOptions", "true");
		
		if (Setting.get("jobs.deleteAfterDuration") == null)
			Setting.set("jobs.deleteAfterDuration", "0");
		
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(10, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						try {
							if (Setting.get("dp2ws.endpoint") == null)
								return;
							
							Pipeline2WSResponse response;
							try {
								String endpoint = Setting.get("dp2ws.endpoint");
								if (endpoint == null) {
									controllers.Application.setAlive(null);
									return;
								}
								
								response = org.daisy.pipeline.client.Alive.get(endpoint);
								if (response.status != 200) {
									controllers.Application.setAlive(null);
									
								} else {
									controllers.Application.setAlive(new org.daisy.pipeline.client.models.Alive(response));
								}
							} catch (Pipeline2WSException e) {
								Logger.error(e.getMessage(), e);
								controllers.Application.setAlive(null);
							}
							
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
				);
		
		// Push "heartbeat" notifications (keeping the push notification connections alive). Hopefully this scales...
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						try {
							synchronized (NotificationConnection.notificationConnections) {
								for (Long userId : NotificationConnection.notificationConnections.keySet()) {
									List<NotificationConnection> browsers = NotificationConnection.notificationConnections.get(userId);
									
									for (int c = browsers.size()-1; c >= 0; c--) {
										if (!browsers.get(c).isAlive()) {
	//										Logger.debug("Browser: user #"+userId+" timed out browser window #"+browsers.get(c).browserId+" (last read: "+browsers.get(c).lastRead+")");
											browsers.remove(c);
										}
									}
									
									for (NotificationConnection c : browsers) {
										if (c.notifications.size() == 0) {
	//										Logger.debug("*heartbeat* for user #"+userId+" and browser window #"+c.browserId);
											c.push(new Notification("heartbeat", controllers.Application.pipeline2EngineAvailable()));
										}
									}
								}
							}
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
			);
		
		// Delete jobs and uploads after a certain time. Configurable by administrators.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
						try {
							// unused uploads are deleted after one hour
							Date timeoutDate = new Date(new Date().getTime() - 600000L);
							
							List<Upload> uploads = Upload.find.all();
							for (Upload upload : uploads) {
								if (upload.job == null && NotificationConnection.getBrowser(upload.browserId) == null && upload.uploaded.before(timeoutDate)) {
									Logger.info("Deleting old upload that is not open in any browser window: "+upload.id+(upload.getFile()!=null?" ("+upload.getFile().getName()+")":""));
									upload.delete();
								}
							}
							
							// jobs are only deleted if that option is set in admin settings
							if ("0".equals(Setting.get("jobs.deleteAfterDuration")))
								return;
							
							timeoutDate = new Date(new Date().getTime() - Long.parseLong(Setting.get("jobs.deleteAfterDuration")));
							
							List<Job> jobs = Job.find.all();
							for (Job job : jobs) {
								if (job.finished != null && job.finished.before(timeoutDate)) {
									Logger.info("Deleting old job: "+job.id+" ("+job.nicename+")");
									job.delete();
								}
							}
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
				);
		
		// If jobs.deleteAfterDuration is not set; clean up jobs that no longer exists in the Pipeline engine. This typically happens if the Pipeline engine is restarted.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
						try {
							String endpoint = Setting.get("dp2ws.endpoint");
							if (endpoint == null)
								return;
							
							List<org.daisy.pipeline.client.models.Job> fwkJobs;
							try {
								fwkJobs = org.daisy.pipeline.client.models.Job.getJobs(org.daisy.pipeline.client.Jobs.get(endpoint, Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret")));
								
							} catch (Pipeline2WSException e) {
								Logger.error(e.getMessage(), e);
								return;
							}
							
							List<Job> webUiJobs = Job.find.all();
							
							for (Job webUiJob : webUiJobs) {
								boolean exists = false;
								for (org.daisy.pipeline.client.models.Job fwkJob : fwkJobs) {
									if (webUiJob.id.equals(fwkJob.id)) {
										exists = true;
										break;
									}
								}
								if (!exists) {
									Logger.info("Deleting job that no longer exists in the Pipeline engine: "+webUiJob.id+" ("+webUiJob.nicename+")");
									webUiJob.delete();
								}
							}
							
							if (controllers.Application.getAlive() != null) {
								for (org.daisy.pipeline.client.models.Job fwkJob : fwkJobs) {
									boolean exists = false;
									for (Job webUiJob : webUiJobs) {
										if (fwkJob.id.equals(webUiJob.id)) {
											exists = true;
											break;
										}
									}
									if (!exists) {
										Logger.info("Adding job from the Pipeline engine that does not exist in the Web UI: "+fwkJob.id);
										Job webUiJob = new Job(fwkJob);
										webUiJob.save();
									}
								}
							}
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
			);
	}

}
