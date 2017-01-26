package com.cq.akamai.replication.agent.ccu.v3;

import org.apache.http.client.methods.HttpRequestBase;



public interface AkamaiEdgeGridSigner {
	
	
	/**
	 * Signs a request with the client credential.
	 * 
	 * @param request the request to sign.
	 * @param credential the credential used in the signing.
	 * @return the signed request.
	 * @throws AkamaiException
	 */
	public HttpRequestBase sign(HttpRequestBase request, ClientCredential credential) throws AkamaiException;

}
