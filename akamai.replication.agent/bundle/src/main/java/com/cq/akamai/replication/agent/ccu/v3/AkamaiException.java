
package com.cq.akamai.replication.agent.ccu.v3;

/**
 * Exception representing errors during requests related to Akamai.
 *
 */
public class AkamaiException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4716437270940718895L;

	public AkamaiException() {
		super();
	}

	public AkamaiException(String message) {
		super(message);
	}

	public AkamaiException(Throwable t) {
		super(t);
	}

	public AkamaiException(String message, Throwable t) {
		super(message, t);
	}
}
