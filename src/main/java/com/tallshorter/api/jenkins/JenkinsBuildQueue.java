package com.tallshorter.api.jenkins;

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;

public class JenkinsBuildQueue {

	private IJenkinsClient client;
	private Logger logger;
	
	public JenkinsBuildQueue(IJenkinsClient client) {
		this.client = client;
		this.logger = IJenkinsClient.logger;
	}
	
	public int size() {
		logger.info("Obtaining the number of tasks in build queue");
		return getItems().size();
	}
	
	public JSONArray getItems() {
		logger.info("Obtaining the tasks in build queue");
		return (JSONArray) client.apiGetRequest("/queue").get("items");
	}
	
	public JSONObject getItemById(long itemId) {
		logger.info("Obtaining the details of task with ID " + itemId);
		return client.apiGetRequest("/queue/item/" + itemId);
	}
	
	public long getItemIdByParams(Map<String, String> params) {
		logger.info("Obtaining the task ID by the given params: " + params);
		JSONArray items = getItems();
		for (int i = 0; i < items.size(); i++) {
			JSONObject item = (JSONObject) items.get(i);
			String paramsStr = (String) item.get("params");
			if (paramsStr != null) {
				Set<String> paramSet = Sets.newHashSet(Splitter.on("\n").omitEmptyStrings().split(paramsStr));
				Set<String> otherParamSet = Sets.newHashSet();
				for (Map.Entry<String, String> entry : params.entrySet()) {
					otherParamSet.add(Joiner.on("=").join(entry.getKey(), entry.getValue()));
				}
				if (paramSet.equals(otherParamSet)) {
					return (Long) item.get("id");
				}
			}
		}
		throw new JenkinsClientException("No task is found for: " + params);
	}
	
}
