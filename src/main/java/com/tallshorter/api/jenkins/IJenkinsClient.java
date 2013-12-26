package com.tallshorter.api.jenkins;

import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;

/**
 * The Jenkins API Client
 *
 */
public interface IJenkinsClient {
	
	Logger logger = Logger.getLogger(IJenkinsClient.class.getName());

	int getTimeout();

	void setTimeout(int milliseconds);

	JSONObject apiGetRequest(String urlPrefix);

	JSONObject apiGetRequest(String urlPrefix, String tree);

	JSONObject apiGetRequest(String urlPrefix, String tree, String urlSuffix);

	HttpResponse apiPostRequestWithRawResponse(String urlPrefix);
	
	HttpResponse apiPostRequestWithRawResponse(String urlPrefix, Map<String, String> formData);

	JenkinsJob job();

	JenkinsBuildQueue queue();

	void close();

}