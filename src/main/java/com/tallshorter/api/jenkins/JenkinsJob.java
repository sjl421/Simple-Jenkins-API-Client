package com.tallshorter.api.jenkins;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class JenkinsJob {

	private static final int ONE_SECOND = 1000;
	private IJenkinsClient client;
	private Logger logger;
	
	public enum Status {
		SUCCESS, FAILURE, UNSTABLE, RUNNING, NOT_RUN, ABORTED, INVALID;
		
		public static Status fromColor(String color) {
			if (color.equalsIgnoreCase("blue")) {
				return SUCCESS;
			} else if (color.equalsIgnoreCase("red")) {
				return FAILURE;
			} else if (color.equalsIgnoreCase("yellow")) {
				return UNSTABLE;
			} else if (color.toLowerCase().contains("anime")) {
				return RUNNING;
			} else if (color.equalsIgnoreCase("grey") || color.equalsIgnoreCase("notbuilt")) {
				return NOT_RUN;
			} else if (color.equalsIgnoreCase("aborted")) {
				return ABORTED;
			} else {
				return INVALID;
			}
		}
	}
	
	public JenkinsJob(IJenkinsClient client) {
		this.client = client;
		this.logger = IJenkinsClient.logger;
	}
	
	public JSONObject getDetails(String jobName) {
		return client.apiGetRequest("/job/" + jobName);
	}
	
	public long getNextBuildNumber(String jobName) {
		return (Long) getDetails(jobName).get("nextBuildNumber");
	}
	
	public long getCurrentBuildNumber(String jobName) {
		return getNextBuildNumber(jobName) - 1L;
	}
	
	public Status getCurrentBuildStatus(String jobName) {
		return getJobStatus(jobName); // equal to job status
	}
	
	public Status getJobStatus(String jobName) {
		String color = (String) getDetails(jobName).get("color");
		return Status.fromColor(color);
	}
	
	public JSONArray getBuilds(String jobName) {
		return (JSONArray) getDetails(jobName).get("builds");
	}
	
	public JSONObject getBuildDetails(String jobName, long buildNumber) {
		return client.apiGetRequest("/job/" + jobName + "/" + buildNumber);
	}
	
	public Status getBuildStatus(String jobName, long buildNumber) {
		String result = (String) getBuildDetails(jobName, buildNumber).get("result");
		if (result == null) { // Maybe it is running currently
			boolean isBuilding =  (Boolean) getBuildDetails(jobName, buildNumber).get("building");
			if (isBuilding) {
				return Status.RUNNING;
			} else {
				return Status.INVALID;
			}
		} else {
			return Status.valueOf(result);
		}
	}
	
	public boolean isBuilding(String jobName, long buildNumber) {
		return ((Boolean) getBuildDetails(jobName, buildNumber).get("building"));
	}
	
	public void build(String jobName) {
		build(jobName, Collections.<String, String> emptyMap());
	}
	
	public void build(String jobName, Map<String, String> params) {
		buildInternal(jobName, params);
	}
	
	public long buildAndWaitForBuildId(String jobName) {
		return buildAndWaitForBuildId(jobName, Collections.<String, String> emptyMap());
	}
	
	public long buildAndWaitForBuildId(String jobName, Map<String, String> params) {
		Header locationHeader = buildInternal(jobName, params);
		Pattern pattern = Pattern.compile("[/]item[/](\\d*)[/]");
		Matcher matcher = pattern.matcher(locationHeader.getValue());
		long itemId = -1;
		if (matcher.find()) {
			itemId = Long.parseLong(matcher.group(1));
		} else {
			if (params == null || params.isEmpty()) {
				throw new JenkinsClientException("No item ID is found: " + locationHeader.getValue());
			} else {
				// FIXME: Since it needs another HTTP request, it is not guaranteed to get the queue item id due to timing issue.
				itemId = client.queue().getItemIdByParams(params);
			}
		}
		logger.info("Queue item ID for job " + jobName + ": #" + itemId);
		int timePassed = 0;
		while (timePassed <= client.getTimeout()) {
			JSONObject executableObj = (JSONObject) client.queue().getItemById(itemId).get("executable");
			if (executableObj == null) { // still in queue
				int sleepTime = ONE_SECOND; //1s
				timePassed += sleepTime;
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException e) {
					// ignore
				}
				continue;
			} else { // accepted by one executor
				return (Long) executableObj.get("number");
			}
		}
		throw new JobIdNotFoundException("No job ID is found for queue item ID #" + itemId);
	}

	protected Header buildInternal(String jobName, Map<String, String> params) {
		String buildEndpoint = null;
		if (params == null || params.isEmpty()) {
			logger.info("Building job " + jobName);
			buildEndpoint = "build";
		} else {
			logger.info("Building job " + jobName + " with parameters: " + params);
			buildEndpoint = "buildWithParameters";
		}
		HttpResponse response = client.apiPostRequestWithRawResponse("/job/" + jobName + "/" + buildEndpoint, params);
		Header locationHeader = response.getFirstHeader("location");
		if (locationHeader == null) {
			throw new JenkinsClientException("No location header is found");
		}
		return locationHeader;
	}
	
	public void abortBuild(String jobName) {
		abortBuild(jobName, this.getCurrentBuildNumber(jobName));
	}
	
	public void abortBuild(String jobName, long buildNumber) {
		if (buildNumber <= 0) {
			throw new JenkinsClientException("No builds for " + jobName);
		}
		logger.info("Stopping job " + jobName + " Build #" + buildNumber);
		if (isBuilding(jobName, buildNumber)) {
			client.apiPostRequestWithRawResponse("/job/" + jobName + "/" + buildNumber + "/stop");
		}
	}

}
