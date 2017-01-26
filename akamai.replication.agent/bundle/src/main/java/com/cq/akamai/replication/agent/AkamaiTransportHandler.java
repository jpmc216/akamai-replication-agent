package com.cq.akamai.replication.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.jackrabbit.util.Base64;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cq.akamai.replication.agent.ccu.v3.AkamaiEdgeGridSigner;
import com.cq.akamai.replication.agent.ccu.v3.AkamaiEdgeGridSignerImpl;
import com.cq.akamai.replication.agent.ccu.v3.AkamaiException;
import com.cq.akamai.replication.agent.ccu.v3.CCUPostData;
import com.cq.akamai.replication.agent.ccu.v3.CCUV3Constants;
import com.cq.akamai.replication.agent.ccu.v3.ClientCredential;
import com.cq.akamai.replication.agent.ccu.v3.DefaultClientCredential;
import com.day.cq.commons.Externalizer;
import com.day.cq.replication.AgentConfig;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.day.cq.replication.ReplicationResult;
import com.day.cq.replication.ReplicationTransaction;
import com.day.cq.replication.TransportContext;
import com.day.cq.replication.TransportHandler;

// TODO: Auto-generated Javadoc
/**
 * Transport handler to send test and purge requests to Akamai and handle
 * responses. The handler sets up basic authentication with the user/pass from
 * the replication agent's transport config and sends a GET request as a test
 * and POST as purge request. A valid test response is 200 while a valid purge
 * response is 201.
 * 
 * The transport handler is triggered by setting your replication agent's
 * transport URL's protocol to "akamai://".
 *
 * The transport handler builds the POST request body in accordance with
 * Akamai's CCU REST APIs {@link https://api.ccu.akamai.com/ccu/v2/docs/}
 * using the replication agent properties. 
 */
@Service(TransportHandler.class)
@Component(label = "Akamai Purge Agent", immediate = true)
public class AkamaiTransportHandler implements TransportHandler {

	/** The Constant LOGGER. */
	private static final Logger LOGGER = LoggerFactory.getLogger(AkamaiTransportHandler.class);
	
    /** Protocol for replication agent transport URI that triggers this transport handler. */
    private final static String AKAMAI_PROTOCOL = "akamai://";
    
    private final static String HTTP = "http";
    
    private final static String HTML_EXTN = ".html";

    /**  Akamai CCU REST API URL. */
    private final static String AKAMAI_CCU_REST_API_URL = "https://api.ccu.akamai.com/ccu/v2/queues/default";

    /** Replication agent type property name. Valid values are "arl" and "cpcode". */
    private final static String PROPERTY_TYPE = "akamaiType";

    /** Replication agent multifield CP Code property name.*/
    private final static String PROPERTY_CP_CODES = "akamaiCPCodes";

    /** Replication agent domain property name. Valid values are "staging" and "production". */
    private final static String PROPERTY_DOMAIN = "akamaiDomain";

    /** Replication agent action property name. Valid values are "remove" and "invalidate". */
    private final static String PROPERTY_ACTION = "akamaiAction";
    
    /** Replication agent action property name. Enable it for purging https url as well. */
    private final static String PROPERTY_HTTPS_ENABLED = "httpsEnabled";

    /**  Replication agent default type value. */
    private final static String PROPERTY_TYPE_DEFAULT = "arl";

    /**  Replication agent default domain value. */
    private final static String PROPERTY_DOMAIN_DEFAULT = "production";

    /**  Replication agent default action value. */
    private final static String PROPERTY_ACTION_DEFAULT = "invalidate";
    
    /** Replication agent property name. Populate Parent Folder paths which needs to be auto-invalidated as well */
    private final static String PROPERTY_PARENT_PATHS = "parentfolderpaths";
    
    /** Replication agent CCU API type property. Valid values are "V2" and "V3" */
    private final static String PROPERTY_CCU_API_TYPE = "ccuapitype";
    
    /**  Replication agent default CCUP API type value. */    
    private final static String PROPERTY_CCU_API_V2 = "V2";
    
    /** Replication agent Client Token property for performing CCU V3 Request.*/
    private final static String PROPERTY_CLIENT_TOKEN = "clientToken";
    
    /** Replication agent Access Token property for performing CCU V3 Request.*/
    private final static String PROPERTY_ACCESS_TOKEN = "accessToken";
    
    /** Replication agent Client Secret property for performing CCU V3 Request.*/
    private final static String PROPERTY_CLIENT_SECRET = "clientSecret";
    
    /** Replication agent Host Header property for performing CCU V3 Request.*/
    private final static String PROPERTY_HOST_HEADER = "hostHeader";
    
    /** Replication agent Open Auth Service URL property for performing CCU V3 Request.*/
    private final static String PROPERTY_OPEN_AUTH_SERVICE_URL = "openAuthServiceURL";
    
    /**  Home Page URL which will be used for Externalizing Page Paths. */
    private final static String PROPERTY_HOME_PAGE_URL = "homePageURL";
    
    /** The invalidateparentpaths. */
    private transient Set<String> invalidateparentpaths;

    /** The resolver factory. */
    @Reference
    private ResourceResolverFactory resolverFactory;
    
	/** The externalizer. */
	@Reference
    private transient Externalizer externalizer;
    /**
     * {@inheritDoc}
     */
    public boolean canHandle(final AgentConfig config) {
        final String transportURI = config.getTransportURI();

        return (transportURI != null) ? transportURI.toLowerCase().startsWith(AKAMAI_PROTOCOL) : false;
    }

    /**
     * {@inheritDoc}
     */
    public ReplicationResult deliver(final TransportContext ctx, final ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationActionType replicationType = tx.getAction().getType();

        if (replicationType == ReplicationActionType.TEST) {
            return doTest(ctx, tx);
        } else if (replicationType == ReplicationActionType.ACTIVATE || replicationType == ReplicationActionType.DEACTIVATE || replicationType == ReplicationActionType.DELETE) {
            return doActivate(ctx, tx);
        } else {
        	LOGGER.error("Replication action type " + replicationType + " not supported.");
        	return ReplicationResult.OK;
        }
    }

    /**
     * Send test request to Akamai via a GET request.
     * 
     * Akamai will respond with a 200 HTTP status code if the request was
     * successfully submitted. The response will have information about the
     * queue length, but we're simply interested in the fact that the request
     * was authenticated.
     *
     * @param ctx Transport Context
     * @param tx Replication Transaction
     * @return ReplicationResult OK if 200 response from Akamai
     * @throws ReplicationException the replication exception
     */
    private ReplicationResult doTest(final TransportContext ctx, final ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationLog log = tx.getLog();
        final HttpGet request = new HttpGet(AKAMAI_CCU_REST_API_URL);
        final HttpResponse response = sendRequest(request, ctx, tx);

        if (response != null) {
            final int statusCode = response.getStatusLine().getStatusCode();

            log.info(response.toString());
            log.info("---------------------------------------");

            if (statusCode == HttpStatus.SC_OK) {
                return ReplicationResult.OK;
            }
        }

        return new ReplicationResult(false, 0, "Replication test failed");
    }

    /**
     * Send purge request to Akamai via a POST request
     * 
     * Akamai will respond with a 201 HTTP status code if the purge request was
     * successfully submitted.
     *
     * @param ctx Transport Context
     * @param tx Replication Transaction
     * @return ReplicationResult OK if 201 response from Akamai
     * @throws ReplicationException the replication exception
     */
    private ReplicationResult doActivate(final TransportContext ctx, final ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationLog log = tx.getLog();
        final ValueMap properties = ctx.getConfig().getProperties();
        final String ccuapitype = PropertiesUtil.toString(properties.get(PROPERTY_CCU_API_TYPE), PROPERTY_CCU_API_V2);
        HttpResponse response = null;
        BufferedReader breader = null;
        int statusCode = 0;
        final StringBuilder responseString = new StringBuilder();
        
        
        if(ccuapitype.equals(PROPERTY_CCU_API_V2)){
        	final HttpPost request = new HttpPost(AKAMAI_CCU_REST_API_URL);
        	createPostBody(request, ctx, tx);
        	response = sendRequest(request, ctx, tx);
        }else{
        	 response = postCCUV3Call(ctx, tx);
        }
        
        if (response != null) {        	
            statusCode = response.getStatusLine().getStatusCode();

            try {
				breader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				String line = "";

	            while ((line = breader.readLine()) != null) {
	            	responseString.append(line);
	            }
			} catch (IllegalStateException e) {
				throw new ReplicationException("Could not retrieve response from Akamai", e);
			} catch (IOException e) {
				throw new ReplicationException("Could not retrieve response from Akamai", e);
			}
            
            log.info(responseString.toString());
            log.info(response.toString());
            log.info("---------------------------------------");
            
            
        }
        
        if (statusCode == HttpStatus.SC_CREATED) {
        	
        	
        	
            return ReplicationResult.OK;
        }

        

        return new ReplicationResult(false, 0, "Replication failed");
    }

    /**
     * Build preemptive basic authentication headers and send request.
     *
     * @param <T> the generic type
     * @param request The request to send to Akamai
     * @param ctx The TransportContext containing the username and password
     * @param tx the tx
     * @return HttpResponse The HTTP response from Akamai
     * @throws ReplicationException if a request could not be sent
     */
    private <T extends HttpRequestBase> HttpResponse sendRequest(final T request,
            final TransportContext ctx, final ReplicationTransaction tx)
            throws ReplicationException {

        final ReplicationLog log = tx.getLog();
        final String auth = ctx.getConfig().getTransportUser() + ":" + ctx.getConfig().getTransportPassword();
        final String encodedAuth = Base64.encode(auth);
        
        request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response;

        try {
            response = client.execute(request);
        } catch (IOException e) {
            throw new ReplicationException("Could not send replication request.", e);
        }

        return response;
    }

    /**
     * Build the Akamai purge request body based on the replication agent
     * settings and append it to the POST request.
     *
     * @param request The HTTP POST request to append the request body
     * @param ctx TransportContext
     * @param tx ReplicationTransaction
     * @throws ReplicationException if errors building the request body 
     */
    private void createPostBody(final HttpPost request, final TransportContext ctx,
            final ReplicationTransaction tx) throws ReplicationException {

    	final ReplicationLog log = tx.getLog();
        final ValueMap properties = ctx.getConfig().getProperties();
        final String type = PropertiesUtil.toString(properties.get(PROPERTY_TYPE), PROPERTY_TYPE_DEFAULT);
        final String domain = PropertiesUtil.toString(properties.get(PROPERTY_DOMAIN), PROPERTY_DOMAIN_DEFAULT);
        final String action = PropertiesUtil.toString(properties.get(PROPERTY_ACTION), PROPERTY_ACTION_DEFAULT);
        final boolean httpsEnabled =  PropertiesUtil.toBoolean(properties.get(PROPERTY_HTTPS_ENABLED), false);
        final String homePagePath = PropertiesUtil.toString(properties.get(PROPERTY_HOME_PAGE_URL), "");
        
        final JSONObject json = new JSONObject();
        JSONArray purgeObjects = null;
        final String invalidatedpath = tx.getAction().getPath();
        String externalizedPath = null;
        /*
         * Get list of CP codes or ARLs/URLs depending on agent setting
         */
        if (type.equals(PROPERTY_TYPE_DEFAULT)) {

 	        	/*
	             * Get the content created with the custom content builder class
	             * 
	             * The list of activated resources (e.g.: ["/content/geometrixx/en/blog"])
	             * is available in tx.getAction().getPaths(). For this example, we want the
	             * content created in our custom content builder which is available in
	             * tx.getContent().getInputStream().
	             */
	            try {
	                final String content = IOUtils.toString(tx.getContent().getInputStream());
	
	                if (StringUtils.isNotBlank(content)) {
	                	purgeObjects  = new JSONArray(content);
	                	
	                	//Populate Externalize URL with out resolver mapping by considering Home Page URL
	                	if(StringUtils.isNotBlank(homePagePath) && null!=purgeObjects.getString(0) && purgeObjects.getString(0).endsWith(HTML_EXTN)){
	                		externalizedPath = invalidatedpath.replace(homePagePath, "");
	                		addExternalizedLinkstoArray(purgeObjects,externalizer,null,externalizedPath,HTTP);
	                	}
	                	
	                    //Fetch the Parent Folder Path(s) that also need to be invalidated
	                    final String[] parentPaths = PropertiesUtil.toStringArray(properties.get(PROPERTY_PARENT_PATHS));
	                    if(ArrayUtils.isNotEmpty(parentPaths)){
	                    	invalidateparentpaths = new HashSet<String>();
	                        invalidateparentpaths.addAll(Arrays.asList(parentPaths));
	                        
	                        final Set<String> selectedparentpaths = checkforInvalidateParentPath(invalidatedpath);
	                        LOGGER.info("selectedparentpaths Path(s)::" + selectedparentpaths);
	                        
	                        if(!selectedparentpaths.isEmpty()){
	                        	for(final String parentPath: selectedparentpaths){
	                        		
	                        		//This one will provide direct link For e.g path is "/content/my-site/foo/bar" into "https://www.my-site.com/content/my-site/foo/bar.html"
	                        		addExternalizedLinkstoArray(purgeObjects,externalizer,null,parentPath,HTTP);
	                                
	                                //This one will provide resolver link For e.g path is "/content/my-site/foo/bar" into "https://www.my-site.com/foo/bar.html"
	                        		if(StringUtils.isNotBlank(homePagePath) && null!=purgeObjects.getString(0) && purgeObjects.getString(0).endsWith(HTML_EXTN)){
	        	                		externalizedPath = parentPath.replace(homePagePath, "") ;
	        	                		addExternalizedLinkstoArray(purgeObjects,externalizer,null,externalizedPath,HTTP);
	        	                	}
	                        	}
	                        	
	                        }
	                    }//End of Parent Folder Path(s) related code
	                    
	                    //Create HTTPS Links if "httpsEnabled" is enabled
	                    if(httpsEnabled){
	                    	final JSONArray httpsArray = new JSONArray();
	                    	for(int iCount = 0;iCount < purgeObjects.length();iCount++){
	                    		final String httpLink = (String)purgeObjects.get(iCount);
	                    		if(null!= purgeObjects.get(iCount) && httpLink.startsWith("http://")){
	                    			httpsArray.put(httpLink.replace("http://", "https://"));
	                    		}
	                    	}
	                    	
	                    	for(int iCount = 0;iCount < httpsArray.length();iCount++){
	                    		purgeObjects.put((String)httpsArray.get(iCount));
	                    	}
	                    }
	                    
	                    
	                    log.info("PurgeObject Path(s)::" + purgeObjects);
	                }
	            } catch (IOException e) {
	                throw new ReplicationException("Could not retrieve content from content builder", e);
	            }catch (JSONException e) {
	                throw new ReplicationException("Could not retrieve content from content builder", e);
	            } 
        } else {
            final String[] cpCodes = PropertiesUtil.toStringArray(properties.get(PROPERTY_CP_CODES));
            purgeObjects = new JSONArray(Arrays.asList(cpCodes));
        }

        if (purgeObjects != null && purgeObjects.length() > 0) {
            try {
                json.put("type", type)
                    .put("action", action)
                    .put("domain", domain)
                    .put("objects", purgeObjects);
            } catch (JSONException e) {
                throw new ReplicationException("Could not build purge request content", e);
            }

            final StringEntity entity = new StringEntity(json.toString(), CharEncoding.ISO_8859_1);
            request.setEntity(entity);

        } else {
            throw new ReplicationException("No CP codes or pages to purge");
        }
    }
    
    /**
     * Check for invalidate parent path.
     *
     * @param path the path
     * @return the List of matched parent path(s)
     */
    private Set<String> checkforInvalidateParentPath(final String path){
    	
    	final Set<String> pathsSet = new HashSet<String>();
    	
    	for (final String invalidateParentPath : invalidateparentpaths) {
            if (path.contains(invalidateParentPath)) {
            	pathsSet.add(invalidateParentPath);
            }
        }
    	
    	return pathsSet;
    }
    
    
	/**
	 * Post ccu v3 call.
	 *
	 * @param ctx the ctx
	 * @param tx the tx
	 * @return the http response
	 * @throws ReplicationException the replication exception
	 */
	private HttpResponse postCCUV3Call(final TransportContext ctx,
            final ReplicationTransaction tx) throws ReplicationException{
    	
    	final ReplicationLog log = tx.getLog();
        final ValueMap properties = ctx.getConfig().getProperties();
        
		final String type = PropertiesUtil.toString(properties.get(PROPERTY_TYPE), PROPERTY_TYPE_DEFAULT);
        final String domain = PropertiesUtil.toString(properties.get(PROPERTY_DOMAIN), PROPERTY_DOMAIN_DEFAULT);
        final String action = PropertiesUtil.toString(properties.get(PROPERTY_ACTION), PROPERTY_ACTION_DEFAULT);
        
		final String clientToken = PropertiesUtil.toString(properties.get(PROPERTY_CLIENT_TOKEN), "");
		final String accessToken = PropertiesUtil.toString(properties.get(PROPERTY_ACCESS_TOKEN), "");
		final String clientSecret = PropertiesUtil.toString(properties.get(PROPERTY_CLIENT_SECRET), "");
		final String hostHeader = PropertiesUtil.toString(properties.get(PROPERTY_HOST_HEADER), "");
		final String openAuthServiceURL = PropertiesUtil.toString(properties.get(PROPERTY_OPEN_AUTH_SERVICE_URL), "");
		final String homePagePath = PropertiesUtil.toString(properties.get(PROPERTY_HOME_PAGE_URL), "");
		
        	String postDataJSON =null;
        	JSONArray purgeObjects = null;
        	final CCUPostData postData = new CCUPostData();
        	final List<String> purgeObjList = new ArrayList<String>(); 
        	String externalizedPath = null;
        	String invalidateDomain = null;
        	try {
			
        		if (type.equals(PROPERTY_TYPE_DEFAULT)) {
					
        			final String invalidatedpath = tx.getAction().getPath();
        			final String contentFrombuilder = IOUtils.toString(tx.getContent().getInputStream());
        			
		        	if (StringUtils.isNotBlank(contentFrombuilder)) {
		                	purgeObjects  = new JSONArray(contentFrombuilder);
		                    
		                	//Populate Externalize URL with out resolver mapping by considering Home Page URL
		                	if(StringUtils.isNotBlank(homePagePath) && null!=purgeObjects.getString(0) && purgeObjects.getString(0).endsWith(HTML_EXTN)){
		                		externalizedPath = invalidatedpath.replace(homePagePath, "") ;
		                		addExternalizedLinkstoArray(purgeObjects,externalizer,null,externalizedPath,HTTP);
		                	}
		                	
		                    //Fetch the Parent Folder Path(s) that also need to be invalidated
		                    final String[] parentPaths = PropertiesUtil.toStringArray(properties.get(PROPERTY_PARENT_PATHS));
		                    if(ArrayUtils.isNotEmpty(parentPaths)){
		                    	invalidateparentpaths = new HashSet<String>();
		                        invalidateparentpaths.addAll(Arrays.asList(parentPaths));
		                        
		                        final Set<String> selectedparentpaths = checkforInvalidateParentPath(invalidatedpath);
		                        LOGGER.info("selectedparentpaths Path(s)::" + selectedparentpaths);
		                        
		                        if(!selectedparentpaths.isEmpty()){
		                        	for(final String parentPath: selectedparentpaths){
		                        		
		                        		//This one will provide direct link For e.g path is "/content/my-site/foo/bar" into "https://www.my-site.com/content/my-site/foo/bar.html"
		                        		addExternalizedLinkstoArray(purgeObjects,externalizer,null,parentPath,HTTP);
		                                
		                                //This one will provide resolver link For e.g path is "/content/my-site/foo/bar" into "https://www.my-site.com/foo/bar.html"
		                        		//Populate Externalize URL with out resolver mapping by considering Home Page URL
		        	                	if(StringUtils.isNotBlank(homePagePath) && null!=purgeObjects.getString(0) && purgeObjects.getString(0).endsWith(HTML_EXTN)){
		        	                		externalizedPath = parentPath.replace(homePagePath, "");
		        	                		addExternalizedLinkstoArray(purgeObjects,externalizer,null,externalizedPath,HTTP);
		        	                	}
		                        	}
		                        	
		                        }
		                    }//End of Parent Folder Path(s) related code
			        	
						String hostName = externalizer.publishLink( null, HTTP, "");
			        	
			        	
			        	for(int iCount = 0;iCount< purgeObjects.length();iCount++){
			        		
			        		String link = purgeObjects.getString(iCount);
			        		
			        		if(link.contains(hostName)){
			        			link = link.replace(hostName, "");
			        		}
			        		purgeObjList.add(link);
			        	}
			        	
			        	if(StringUtils.isEmpty(hostName)){
			        		log.error("Host Name for performing CCU V3 is empty. Please configure in the Externalizer Configuration on 'Publish' Field");
			        	}else{
			        		hostName = hostName.replace("http://", "");
			        	}
			        	
			        	postData.setHostname(hostName);
			        	postData.setObjects(purgeObjList);
				        postDataJSON = postData.toJSON();
				        
				        if(StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
				        	invalidateDomain = CCUV3Constants.ARL_INVALIDATE_URL_PROD_PURGING;
				        }else if(StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && !StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
				        	invalidateDomain = CCUV3Constants.ARL_REMOVE_URL_PROD_PURGING;
				        }else if(!StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
				        	invalidateDomain = CCUV3Constants.ARL_INVALIDATE_URL_STAGE_PURGING;
				        }else{
				        	invalidateDomain = CCUV3Constants.ARL_REMOVE_URL_STAGE_PURGING;
				        }
		        	}
        		}else{
        			final String[] cpCodes = PropertiesUtil.toStringArray(properties.get(PROPERTY_CP_CODES));
        			
        			postData.setObjects(Arrays.asList(cpCodes));
                    postDataJSON = postData.toJSON();
                    
                    if(StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
			        	invalidateDomain = CCUV3Constants.ARL_INVALIDATE_CPCODE_PROD_PURGING;
			        }else if(StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && !StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
			        	invalidateDomain = CCUV3Constants.ARL_REMOVE_CPCODE_PROD_PURGING;
			        }else if(!StringUtils.equals(domain, PROPERTY_DOMAIN_DEFAULT) && StringUtils.equals(action, PROPERTY_ACTION_DEFAULT)){
			        	invalidateDomain = CCUV3Constants.ARL_INVALIDATE_CPCODE_STAGE_PURGING;
			        }else{
			        	invalidateDomain = CCUV3Constants.ARL_REMOVE_CPCODE_STAGE_PURGING;
			        }
        		}
        		log.info("invalidateDomain ::" + invalidateDomain);
        		log.info("PurgeObject JSON::" + postDataJSON);
        		
		        
		        
		        
		        final URI uri = new URI(openAuthServiceURL + invalidateDomain);
		        
		        final HttpPost httpPost = new HttpPost(uri);

		        httpPost.setHeader("Content-Type", "application/json");
		        httpPost.setHeader("Host", hostHeader);
		        
		        final HttpClient httpclient = HttpClientBuilder.create().build();
		        
		        final StringEntity jsonEntity = new StringEntity(postDataJSON);

		        httpPost.setEntity(jsonEntity);
		        
		        final ClientCredential credential = new DefaultClientCredential(clientToken, accessToken, clientSecret);
		        
		        //Perform CCU V3 Call
		        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(5000).setConnectTimeout(5000).build();
		        
		        httpPost.setConfig(requestConfig);
		        
		        final AkamaiEdgeGridSigner signer = new AkamaiEdgeGridSignerImpl(Collections.EMPTY_LIST, 1024 * 2);
		        final HttpRequestBase signedRequest = signer.sign(httpPost, credential);
		        final HttpResponse response   = httpclient.execute(signedRequest);
		        
		        return response;
			}catch (AkamaiException e) {
	        	throw new ReplicationException("RequestSigningException, Could not retrieve content from content builder", e);
			}catch (IOException e) {
                throw new ReplicationException("IOException, Could not retrieve content from content builder", e);
            } catch (JSONException e) {
				throw new ReplicationException("JSONException, Could not retrieve content from content builder", e);
			} catch (URISyntaxException e) {
				throw new ReplicationException("URISyntaxException, Could not retrieve content from content builder", e);
			}
    	
    }
    
    
    /**
     * Adds the externalized links to array.
     *
     * @param purgeObjects the purge objects
     * @param externalizer the externalizer
     * @param resolver the resolver
     * @param path the path
     * @param protocol the protocol
     * @return the JSON array
     */
    private JSONArray addExternalizedLinkstoArray(final JSONArray purgeObjects, final Externalizer externalizer, final ResourceResolver resolver, final String path, final String protocol){
    	String extn = HTML_EXTN;
    	if(StringUtils.isBlank(path)){
    		extn = "/";
    	}
    	return purgeObjects.put(externalizer.publishLink(resolver, protocol, path) + extn);
    	
    }
    
}