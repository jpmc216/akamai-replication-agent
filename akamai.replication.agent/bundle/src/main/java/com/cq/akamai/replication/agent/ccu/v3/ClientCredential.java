
package com.cq.akamai.replication.agent.ccu.v3;

/**
 * Interface representing the client credential that is used in service requests.
 * 
 * It contains the client token that represents the service client, the client secret
 * that is associated with the client token used for request signing, and the access token
 * that represents the authorizations the client has for accessing the service.
 *
 */
public interface ClientCredential {
	/**
	 * Gets the client token.
	 * @return The client token.
	 */
	public String getClientToken();
	
	/**
	 * Gets the access token.
	 * @return the access token.
	 */
	public String getAccessToken();
	
	/**
	 * Gets the secret associated with the client token.
	 * @return the secret.
	 */
	public String getClientSecret();
}
