
package com.cq.akamai.replication.agent.ccu.v3;

import org.apache.commons.lang3.StringUtils;

/**
 * Default implementation of the {@link ClientCredential}.
 *
 */
public class DefaultClientCredential implements ClientCredential {

	/**
	 * The client token.
	 */
	private final String clientToken;
	
	/**
	 * The access token.
	 */
	private final String accessToken;
	
	/**
	 * The secret associated with the client token.
	 */
	private final String clientSecret;
	
	/**
	 * Constructor.
	 * 
	 * @param clientToken the client token, cannot be null or empty.
	 * @param accessToken the access token, cannot be null or empty.
	 * @param clientSecret the client secret, cannot be null or empty.
	 * 
	 * @throws IllegalArgumentException if any of the parameters is null or empty.
	 */
	public DefaultClientCredential(String clientToken, String accessToken, String clientSecret) {
		if (StringUtils.isEmpty(clientToken)) {
			throw new IllegalArgumentException("clientToken cannot be empty.");
		}
		if (StringUtils.isEmpty(accessToken)) {
			throw new IllegalArgumentException("accessToken cannot be empty.");
		}
		if (StringUtils.isEmpty(clientSecret)) {
			throw new IllegalArgumentException("clientSecret cannot be empty.");
		}
		
		this.clientToken = clientToken;
		this.accessToken = accessToken;
		this.clientSecret = clientSecret;
	}
	
	/**
	 * Gets the client token.
	 * @return The client token.
	 */
	public String getClientToken() {
		return clientToken;
	}

	/**
	 * Gets the access token.
	 * @return the access token.
	 */
	public String getAccessToken() {
		return accessToken;
	}

	/**
	 * Gets the secret associated with the client token.
	 * @return the secret.
	 */
	public String getClientSecret() {
		return clientSecret;
	}

}
