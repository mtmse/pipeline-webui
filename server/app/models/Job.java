package models;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import play.Logger;

import com.avaje.ebean.*;

import javax.persistence.*;

import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.Pipeline2Logger;
import org.daisy.pipeline.client.filestorage.JobStorage;
import org.daisy.pipeline.client.models.Job.Status;
import org.daisy.pipeline.client.models.Result;
import org.daisy.pipeline.client.utils.Files;

import controllers.Application;
import akka.actor.Cancellable;
import play.data.validation.*;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

@Entity
public class Job extends Model implements Comparable<Job> {
	/** Key is the job ID; value is the sequence number of the last message read from the Pipeline 2 Web API. */ 
	public static Map<String,Integer> lastMessageSequence = Collections.synchronizedMap(new HashMap<String,Integer>());
	public static Map<String,org.daisy.pipeline.client.models.Job.Status> lastStatus = Collections.synchronizedMap(new HashMap<String,org.daisy.pipeline.client.models.Job.Status>());
	
	@Id
	public Long id;

	// General information
	public String engineId;
	public String nicename;
	public Date created;
	public Date started;
	public Date finished;
	@Column(name="user_id") public Long user;
	public String guestEmail; // Guest users may enter an e-mail address to receive notifications
	public String localDirName;
	public String scriptId;
	public String scriptName;
	public String status;
	
	// Notification flags
	public boolean notifiedCreated;
	public boolean notifiedComplete;

	// Not stored in the job table; retrieved dynamically
	@Transient
	public String href;
	@Transient
	public String userNicename;
	@Transient
	org.daisy.pipeline.client.models.Job clientlibJob;

	@Transient
	private Cancellable pushNotifier;
	
	/** Make job belonging to user */
	public Job(User user) {
		super();
		this.user = user.id;
		this.nicename = "Job #"+id;
		this.created = new Date();
		this.notifiedCreated = false;
		this.notifiedComplete = false;
		if (user.id < 0)
			this.userNicename = Setting.get("users.guest.name");
		else
			this.userNicename = User.findById(user.id).name;
	}
	
	/** Make job from engine job */
	public Job(org.daisy.pipeline.client.models.Job job, User user) {
		super();
		this.engineId = job.getId();
		this.user = user.id;
		this.nicename = job.getNicename();
		this.created = new Date();
		this.notifiedCreated = false;
		this.notifiedComplete = false;
		if (user.id < 0)
			this.userNicename = Setting.get("users.guest.name");
		else
			this.userNicename = User.findById(user.id).name;
		
		if (!org.daisy.pipeline.client.models.Job.Status.IDLE.equals(job.getStatus())) {
			this.started = this.created;
			if (!org.daisy.pipeline.client.models.Job.Status.RUNNING.equals(job.getStatus())) {
				this.finished = this.started;
			}
		}
		
		this.scriptId = job.getScript().getId();
		this.scriptName = job.getScript().getNicename();
		
		File jobDir = new File(new File(Setting.get("jobs")), job.getId());
		try {
			this.localDirName = jobDir.getCanonicalPath();
		} catch (IOException e) {
			this.localDirName = jobDir.getPath();
		}
	}

	public int compareTo(Job other) {
		return created.compareTo(other.created);
	}

	// -- Queries

	public static Model.Finder<Long,Job> find = new Model.Finder<Long, Job>(Job.class);

	/** Retrieve a Job by its id. */
	public static Job findById(Long id) {
		Job job = find.where().eq("id", id).findUnique();
		if (job != null) {
			User user = User.findById(job.user);
			if (user != null)
				job.userNicename = user.name;
			else if (job.user < 0)
				job.userNicename = Setting.get("users.guest.name");
			else
				job.userNicename = "User";
		}
		return job;
	}

	/** Retrieve a Job by its engine id. */
	public static Job findByEngineId(String id) {
		Job job = find.where().eq("engine_id", id).findUnique();
		if (job != null) {
			User user = User.findById(job.user);
			if (user != null)
				job.userNicename = user.name;
			else if (job.user < 0)
				job.userNicename = Setting.get("users.guest.name");
			else
				job.userNicename = "User";
		}
		return job;
	}

	public void pushNotifications() {
		if (pushNotifier != null)
			return;

		pushNotifier = Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1000, TimeUnit.MILLISECONDS), // change to maybe every 10s if callbacks are activated
				new Runnable() {
					public void run() {
						try {
							Integer fromSequence = Job.lastMessageSequence.containsKey(id) ? Job.lastMessageSequence.get(id) : null;
//							Logger.debug("checking job #"+id+" for updates from message #"+fromSequence);
							
							org.daisy.pipeline.client.models.Job job = Application.ws.getJob(engineId, fromSequence);
							
							if (job == null) {
								return;
							}
							
							Job webUiJob = Job.findByEngineId(job.getId());
							
							if (webUiJob == null) {
								// Job has been deleted; stop updates
								pushNotifier.cancel();
							}
							
							if (job.getStatus() != Status.RUNNING && job.getStatus() != Status.IDLE) {
								pushNotifier.cancel();
								if (webUiJob.finished == null) {
									// pushNotifier tends to fire multiple times after canceling it, so this if{} is just to fire the "finished" event exactly once
									webUiJob.finished = new Date();
									webUiJob.save();
									Map<String,String> finishedMap = new HashMap<String,String>();
									finishedMap.put("text", webUiJob.finished.toString());
									finishedMap.put("number", webUiJob.finished.getTime()+"");
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-finished-"+job.getId(), finishedMap));
									Map<Result,Map<Result,List<Result>>> results = new HashMap<Result,Map<Result,List<Result>>>();
									results.put(job.getResult(), job.getResults());
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-results-"+job.getId(), results));
								}
							}
							
							if (!job.getStatus().equals(lastStatus.get(job.getId()))) {
								lastStatus.put(job.getId(), job.getStatus());
								NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-status-"+job.getId(), job.getStatus()));
								
								webUiJob.status = job.getStatus().toString();
								
								if (job.getStatus() == Status.RUNNING) {
									// job status changed from IDLE to RUNNING
									webUiJob.started = new Date();
									Map<String,String> startedMap = new HashMap<String,String>();
									startedMap.put("text", webUiJob.started.toString());
									startedMap.put("number", webUiJob.started.getTime()+"");
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-started-"+job.getId(), startedMap));
								}
								
								webUiJob.save();
							}
							
							try {
								List<org.daisy.pipeline.client.models.Message> messages = job.getMessages();
								
								for (org.daisy.pipeline.client.models.Message message : messages) {
									Notification notification = new Notification("job-message-"+job.getId(), message);
									NotificationConnection.pushJobNotification(webUiJob.user, notification);
								}
								
								if (messages.size() > 0) {
									Job.lastMessageSequence.put(job.getId(), messages.get(messages.size()-1).sequence);
								}
								
							} catch (Pipeline2Exception e) {
								Logger.error("An error occured while trying to parse the job messages for job "+job.getId(), e);
								e.printStackTrace();
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
	
	@Override
	public void delete() {
		Logger.debug("deleting "+this.id+" (sending DELETE request)");
		boolean success = Application.ws.deleteJob(this.engineId);
		if (!success) {
			Pipeline2Logger.logger().error("An error occured when trying to delete job "+this.id+" ("+this.engineId+") from the Pipeline 2 Engine");
		}
		asJob().getJobStorage().delete();
		super.delete();
	}
	
//	public void compile() {
//		
//	}

	public org.daisy.pipeline.client.models.Job asJob() {
		if (clientlibJob == null) {
			File jobStorageDir = new File(Setting.get("jobs"));
			clientlibJob = JobStorage.loadJob(""+id, jobStorageDir);
			if (clientlibJob == null) {
				clientlibJob = new org.daisy.pipeline.client.models.Job();
				clientlibJob.setNicename(nicename);
				new JobStorage(clientlibJob, jobStorageDir, ""+id);
			}
		}
		return clientlibJob;
	}
	
}
