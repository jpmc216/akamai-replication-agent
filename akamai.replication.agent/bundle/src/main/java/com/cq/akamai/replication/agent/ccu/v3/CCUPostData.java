package com.cq.akamai.replication.agent.ccu.v3;

import java.util.List;

import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *  POJO for preparing Post Data JSON for Akamai Purge Call
 */
public class CCUPostData extends JSONObject{
	
	
	private final Logger logger = LoggerFactory.getLogger(CCUPostData.class);
	
	/** The hostname. */
	private String hostname;
	
	/** The objects. */
	private List<String> objects;
	
	
	/**
	 * Gets the hostname.
	 *
	 * @return the hostname
	 */
	public String getHostname() {
		return hostname;
	}
	
	/**
	 * Sets the hostname.
	 *
	 * @param hostname the new hostname
	 */
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	
	/**
	 * Gets the objects.
	 *
	 * @return the objects
	 */
	public List<String> getObjects() {
		return objects;
	}
	
	/**
	 * Sets the objects.
	 *
	 * @param objects the new objects
	 */
	public void setObjects(List<String> objects) {
		this.objects = objects;
	}
	
	
	public String toJSON(){
		
		JSONObject obj = new JSONObject();
		
		try {
			obj.put("hostname", hostname);
			obj.put("objects", objects);
			
		} catch (JSONException e) {
			logger.error("JSONException::", e);
		}
		
		return obj.toString();
	}
	

}
