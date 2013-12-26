package com.tallshorter.api.jenkins;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JenkinsClientTest {

	JenkinsClient client;
	
	@Before
	public void setUp() throws Exception {
		client = new JenkinsClient("localhost");
	}
	
	@Test
	public void testJobStatus() {
		Assert.assertEquals(JenkinsJob.Status.SUCCESS, JenkinsJob.Status.fromColor("Blue"));
		Assert.assertEquals(JenkinsJob.Status.FAILURE, JenkinsJob.Status.fromColor("Red"));
		Assert.assertEquals(JenkinsJob.Status.UNSTABLE, JenkinsJob.Status.fromColor("Yellow"));
		Assert.assertEquals(JenkinsJob.Status.RUNNING, JenkinsJob.Status.fromColor("Aborted_anime"));
		Assert.assertEquals(JenkinsJob.Status.NOT_RUN, JenkinsJob.Status.fromColor("Grey"));
		Assert.assertEquals(JenkinsJob.Status.ABORTED, JenkinsJob.Status.fromColor("Aborted"));
	}
	
	@Test
	public void testJob() {
		Assert.assertEquals(188, client.job().getCurrentBuildNumber("demo"));
		Assert.assertEquals(0, client.job().getCurrentBuildNumber("demo_no_builds"));
		
		Assert.assertEquals(189, client.job().getNextBuildNumber("demo"));
		
		Assert.assertEquals(JenkinsJob.Status.SUCCESS, client.job().getCurrentBuildStatus("demo3"));
		Assert.assertTrue(client.job().getBuilds("demo").size() > 20);
		
		Assert.assertEquals(new Long(162), (Long) client.job().getBuildDetails("demo", 162).get("number"));
		Assert.assertEquals("ABORTED", client.job().getBuildDetails("demo", 162).get("result"));
		
		Assert.assertEquals(JenkinsJob.Status.FAILURE, client.job().getBuildStatus("demo2", 3));
		Assert.assertEquals(JenkinsJob.Status.SUCCESS, client.job().getBuildStatus("demo2", 2));
		Assert.assertEquals(JenkinsJob.Status.ABORTED, client.job().getBuildStatus("demo", 162));
		
		Assert.assertFalse(client.job().isBuilding("demo2", 2));
		
		Assert.assertEquals(0, client.queue().size());
		long nextBuildNumber = client.job().getNextBuildNumber("demo3");
		Assert.assertEquals(nextBuildNumber, client.job().buildAndWaitForBuildId("demo3"));
		
		Assert.assertEquals(0, client.queue().size());
		
		Map<String, String> params = new HashMap<String, String>();
		params.put("TestLevel", "Smoke");
		params.put("Platform", "Windows");
		params.put("Email", "tallshort@gmail.com");
		params.put("Timestamp", "TS_" + System.nanoTime());
		nextBuildNumber = client.job().getNextBuildNumber("demo_params");
		Assert.assertEquals(nextBuildNumber, client.job().buildAndWaitForBuildId("demo_params", params));
		
		client.job().build("demo3");
		client.job().build("demo_params", params);
		
		client.job().abortBuild("demo2");
		client.job().abortBuild("demo2", 3);
		client.job().abortBuild("demo");
	}
	
	@Test(expected=JenkinsClientException.class)
	public void testAbortBuild_NotExistedBuild() {
		client.job().abortBuild("demo", 9999999L);
	}
	
	@Test(expected=JenkinsClientException.class)
	public void testAbortBuild_InvalidBuildId() {
		client.job().abortBuild("demo", -1);
	}
	
	@Test(expected=JenkinsClientException.class)
	public void testAbortBuild_NoBuilds() {
		client.job().abortBuild("demo_no_builds");
	}
	
	@Test(expected=JenkinsClientException.class)
	public void testInvalidCredentials() {
		client = new JenkinsClient("localhost", "tallshort", "xxxxxx");
		client.job().getCurrentBuildNumber("demo");
	}
	
	@Test
	public void testBuildWithParams() {
		Map<String, String> params = new HashMap<String, String>();
		params.put("Email", "tallshort@gmail.com");
		client.job().build("demo_params", params);
	}
	
	@Test
	public void testLDAPCredentials() {
		// hide the password here for security
		client = new JenkinsClient("localhost", 443, "tallshort", "XXXXXX", true); 
		System.out.println(client.job().getCurrentBuildNumber("demo"));
	}

}
