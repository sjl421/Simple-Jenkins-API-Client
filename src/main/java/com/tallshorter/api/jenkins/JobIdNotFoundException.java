package com.tallshorter.api.jenkins;

public class JobIdNotFoundException extends JenkinsClientException {

	private static final long serialVersionUID = -3434020295157547757L;

	public JobIdNotFoundException() {
	}

	public JobIdNotFoundException(String message) {
		super(message);
	}

	public JobIdNotFoundException(Throwable cause) {
		super(cause);
	}

	public JobIdNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}

}
