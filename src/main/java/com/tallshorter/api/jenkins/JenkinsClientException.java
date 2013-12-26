package com.tallshorter.api.jenkins;

public class JenkinsClientException extends RuntimeException {

	private static final long serialVersionUID = 1748605773002738313L;

	public JenkinsClientException() {
	}

	public JenkinsClientException(String message) {
		super(message);
	}

	public JenkinsClientException(Throwable cause) {
		super(cause);
	}

	public JenkinsClientException(String message, Throwable cause) {
		super(message, cause);
	}

}
