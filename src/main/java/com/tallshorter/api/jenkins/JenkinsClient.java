package com.tallshorter.api.jenkins;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public class JenkinsClient implements IJenkinsClient {

	private static int DEFAULT_PORT = 8080;

	private static final int MAX_RETRY = 5;
	private boolean secure = false;
	private String serverHostname;
	private int serverPort;
	private int timeout = 60000; // 60s
	private HttpContext localHttpContext;

	private DefaultHttpClient httpClient;

	public JenkinsClient(String serverHostname) {
		this(serverHostname, DEFAULT_PORT);
	}

	public JenkinsClient(String serverHostname, int serverPort) {
		this(serverHostname, serverPort, null, null);
	}

	public JenkinsClient(String serverHostname, String username, String password) {
		this(serverHostname, DEFAULT_PORT, username, password);
	}

	public JenkinsClient(String serverHostname, int serverPort, String username, String password) {
		this(serverHostname, serverPort, username, password, false);
	}

	public JenkinsClient(String serverHostname, int serverPort, String username, String password, boolean secure) {
		this.serverHostname = serverHostname;
		this.serverPort = serverPort;
		this.secure = secure;
		httpClient = buildHttpClient();
		httpClient.setHttpRequestRetryHandler(buildRetryHandler());
		if (hasCredentials(username, password)) {
			httpClient.getCredentialsProvider().setCredentials(AuthScope.ANY,
					new UsernamePasswordCredentials(username, password));
			localHttpContext = buildHttpContext();
		}
		// httpclient.setRedirectStrategy(new LaxRedirectStrategy());
	}

	private DefaultHttpClient buildHttpClient() {
		try {
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			schemeRegistry.register(new Scheme("https", this.getServerPort(), new SSLSocketFactory(new TrustStrategy() {
				public boolean isTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
					return true;
				}
			})));
			schemeRegistry.register(new Scheme("http", this.getServerPort(), PlainSocketFactory.getSocketFactory()));
			ClientConnectionManager cm = new BasicClientConnectionManager(schemeRegistry);
			DefaultHttpClient httpClient = new DefaultHttpClient(cm);
			return httpClient;
		} catch (Exception ex) {
			throw new JenkinsClientException("Failed to create HttpClient instance", ex);
		}
	}
	
	private boolean hasCredentials(String username, String password) {
		return username != null && password != null;
	}
	
	private HttpContext buildHttpContext() {
		HttpHost targetHost = new HttpHost(getServerHostname(), getServerPort(), getScheme());
		// Create AuthCache instance
		AuthCache authCache = new BasicAuthCache();
		// Generate BASIC scheme object and add it to the local auth cache
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(targetHost, basicAuth);

		// Add AuthCache to the execution context
		BasicHttpContext localContext = new BasicHttpContext();
		localContext.setAttribute(ClientContext.AUTH_CACHE, authCache);
		return localContext;
	}

	protected HttpRequestRetryHandler buildRetryHandler() {
		HttpRequestRetryHandler myRetryHandler = new HttpRequestRetryHandler() {
			public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
				if (executionCount >= MAX_RETRY) {
					// Do not retry if over max retry count
					return false;
				}
				if (exception instanceof InterruptedIOException) {
					// Timeout
					return false;
				}
				if (exception instanceof UnknownHostException) {
					// Unknown host
					return false;
				}
				if (exception instanceof ConnectException) {
					// Connection refused
					return false;
				}
				if (exception instanceof SSLException) {
					// SSL handshake exception
					return false;
				}
				HttpRequest request = (HttpRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
				boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
				if (idempotent) {
					// Retry if the request is considered idempotent
					return true;
				}
				return false;
			}
		};
		return myRetryHandler;
	}

	public String getServerHostname() {
		return serverHostname;
	}

	public int getServerPort() {
		return serverPort;
	}

	public boolean isSecure() {
		return secure;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.adobe.sst.ccm.jenkins.IJfenkinsClient#getTimeout()
	 */
	public int getTimeout() {
		return timeout;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.adobe.sst.ccm.jenkins.IJfenkinsClient#setTimeout(int)
	 */
	public void setTimeout(int milliseconds) {
		this.timeout = milliseconds;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.adobe.sst.ccm.jenkins.IJfenkinsClient#apiGetRequest(java.lang.String)
	 */
	public JSONObject apiGetRequest(String urlPrefix) {
		return apiGetRequest(urlPrefix, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.adobe.sst.ccm.jenkins.IJfenkinsClient#apiGetRequest(java.lang.String,
	 * java.lang.String)
	 */
	public JSONObject apiGetRequest(String urlPrefix, String tree) {
		return apiGetRequest(urlPrefix, tree, "/api/json");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.adobe.sst.ccm.jenkins.IJfenkinsClient#apiGetRequest(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	public JSONObject apiGetRequest(String urlPrefix, String tree, String urlSuffix) {
		URI uri = buildURI(urlPrefix, tree, urlSuffix);
		HttpGet httpGet = new HttpGet(uri);
		logger.info("GET " + uri);
		String responseBody = makeHttpRequest(httpGet);
		logger.info("RESPONSE BODY: " + responseBody);
		JSONObject obj = null;
		try {
			obj = (JSONObject) JSONValue.parseWithException(responseBody);
		} catch (ParseException e) {
			throw new JenkinsClientException(e);
		}
		return obj;
	}

	public HttpResponse apiPostRequestWithRawResponse(String urlPrefix) {
		return apiPostRequestWithRawResponse(urlPrefix, Collections.<String, String> emptyMap());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.adobe.sst.ccm.jenkins.IJfenkinsClient#apiPostRequestWithRawResponse
	 * (java.lang.String, java.util.Map)
	 */
	public HttpResponse apiPostRequestWithRawResponse(String urlPrefix, Map<String, String> formData) {
		List<NameValuePair> formParams = new ArrayList<NameValuePair>();
		if (formData != null) {
			for (Map.Entry<String, String> entry : formData.entrySet()) {
				formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
			}
		}
		URI uri = buildURI(urlPrefix, null, "");
		logger.info("POST " + uri);
		UrlEncodedFormEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(formParams, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new JenkinsClientException(e);
		}
		HttpPost httpPost = new HttpPost(uri);
		httpPost.setEntity(entity);
		return makeHttpRequestWithRawResponse(httpPost);
	}

	protected JSONObject apiHttpRequest(HttpPost httpPost) {
		String responseBody = makeHttpRequest(httpPost);
		JSONObject obj = null;
		try {
			obj = (JSONObject) JSONValue.parseWithException(responseBody);
		} catch (ParseException e) {
			throw new JenkinsClientException(e);
		}
		return obj;
	}

	protected URI buildURI(String urlPrefix, String tree, String urlSuffix) {
		URIBuilder builder = new URIBuilder();
		String scheme = getScheme();
		builder.setScheme(scheme)
			.setHost(getServerHostname())
			.setPort(getServerPort())
			.setPath(urlPrefix + urlSuffix);
		if (tree != null && !tree.isEmpty()) {
			builder.addParameter("tree", tree);
		}
		URI uri = null;
		try {
			uri = builder.build();
		} catch (URISyntaxException e) {
			throw new JenkinsClientException(e);
		}
		return uri;
	}

	private String getScheme() {
		return isSecure() ? "https" : "http";
	}

	protected String makeHttpRequest(HttpUriRequest request) {
		ResponseHandler<String> responseHandler = new BasicResponseHandler();
		String responseBody = null;
		try {
			if (localHttpContext != null) {
				responseBody = httpClient.execute(request, responseHandler, localHttpContext);
			} else {
				responseBody = httpClient.execute(request, responseHandler);
			}
		} catch (Exception e) {
			throw new JenkinsClientException(e);
		}
		return responseBody;
	}

	protected HttpResponse makeHttpRequestWithRawResponse(HttpUriRequest request) {
		HttpResponse response = null;
		try {
			if (localHttpContext != null) {
				response = httpClient.execute(request, localHttpContext);
			} else {
				response = httpClient.execute(request);
			}
			EntityUtils.consume(response.getEntity());
			StatusLine statusLine = response.getStatusLine();
			if (statusLine.getStatusCode() >= 300 && statusLine.getStatusCode() != 302) { // Ignore 302 Found
				throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
			}
		} catch (Exception e) {
			throw new JenkinsClientException(e);
		}
		return response;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.adobe.sst.ccm.jenkins.IJfenkinsClient#job()
	 */
	public JenkinsJob job() {
		return new JenkinsJob(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.adobe.sst.ccm.jenkins.IJfenkinsClient#queue()
	 */
	public JenkinsBuildQueue queue() {
		return new JenkinsBuildQueue(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.adobe.sst.ccm.jenkins.IJfenkinsClient#close()
	 */
	public void close() {
		// When HttpClient instance is no longer needed,
		// shut down the connection manager to ensure
		// immediate deallocation of all system resources
		httpClient.getConnectionManager().shutdown();
	}

}
