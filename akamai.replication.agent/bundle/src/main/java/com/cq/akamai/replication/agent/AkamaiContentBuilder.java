package com.cq.akamai.replication.agent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.jcr.resource.JcrResourceConstants;

import com.day.cq.commons.Externalizer;
import com.day.cq.replication.ContentBuilder;
import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationContent;
import com.day.cq.replication.ReplicationContentFactory;
import com.day.cq.replication.ReplicationException;
import com.day.cq.replication.ReplicationLog;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

// TODO: Auto-generated Javadoc
/**
 * Akamai content builder to create replication content containing a JSON array
 * of URLs for Akamai to purge through the Akamai Transport Handler. This class
 * takes the internal resource path and converts it to external URLs as well as
 * adding vanity URLs and pages that may Sling include the activated resource.
 */
@Component(metatype=false)
@Service(ContentBuilder.class)
@Property(name="name", value="akamai")
public class AkamaiContentBuilder implements ContentBuilder {

	/** The resolver factory. */
	@Reference
    private transient ResourceResolverFactory resolverFactory;

    /**  The name of the replication agent. */
    public static final String NAME = "akamai";

    /**
     * The serialization type as it will display in the replication
     * agent edit dialog selection field.
     */
    public static final String TITLE = "Akamai Purge Agent";

    /**
     * {@inheritDoc}
     */
    public ReplicationContent create(final Session session, final ReplicationAction action,
            final ReplicationContentFactory factory) throws ReplicationException {
        return create(session, action, factory, null);
    }

    /**
     * Create the replication content containing the public facing URLs for Akamai to purge.
     * @param session the session
     * @param action the action
     * @param factory the factory
     * @param parameters the parameters
     * @return the replication content
     * @throws ReplicationException the replication exception
     */
    public ReplicationContent create(final Session session, final ReplicationAction action,
            final ReplicationContentFactory factory, final Map<String, Object> parameters)
            throws ReplicationException {

        final String path = action.getPath();
        final ReplicationLog log = action.getLog();

        ResourceResolver resolver = null;
        PageManager pageManager = null;
        final JSONArray jsonArray = new JSONArray();

        if (StringUtils.isNotBlank(path)) {
            try {
                final Map<String, Object> sessionMap = new HashMap<String, Object>();
                sessionMap.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
                resolver = resolverFactory.getResourceResolver(sessionMap);

                if (resolver != null) {
                    pageManager = resolver.adaptTo(PageManager.class);
                }
            } catch (LoginException e) {
                log.error("Could not retrieve Page Manager", e);
            }

            if (pageManager != null) {
                Page purgedPage = pageManager.getPage(path);

                /* 
                 * Use the Externalizer, Sling mappings, Resource Resolver mapping and/or
                 * string manipulation to transform "/content/my-site/foo/bar" into
                 * "https://www.my-site.com/foo/bar.html". This example assumes a custom
                 * "production" externalizer setting.
                 */
                final Externalizer externalizer = resolver.adaptTo(Externalizer.class);
                
                /*
                 * Get the external URL if the resource is a page. Otherwise, use the
                 * provided resource path.
                 */
                if (purgedPage != null) {
                    //This one will provide direct link For e.g path is "/content/my-site/foo/bar" into "https://www.my-site.com/content/my-site/foo/bar.html"
                    final String link = externalizer.publishLink(null, "http", path) + ".html";
                    jsonArray.put(link);
                    
                    /*
                     * Add page's vanity URL if it exists.
                     */
                    String vanityUrl = purgedPage.getVanityUrl();

                    if (StringUtils.isNotBlank(vanityUrl)) {
                    	if(vanityUrl.charAt(0) != '/'){
                    		vanityUrl = externalizer.publishLink(resolver, "http", "/"+vanityUrl);  
                    	}

                    	jsonArray.put(vanityUrl);
                    	if(!vanityUrl.endsWith(".html")){
                    		jsonArray.put(vanityUrl+".html");
                    	}
                    	
                    }
                } else {
                    jsonArray.put(externalizer.publishLink(null, "http", path));
                }

                return createContent(factory, jsonArray);
            }
        }

        return ReplicationContent.VOID;
    }

    /**
     * Create the replication content containing .
     *
     * @param factory Factory to create replication content
     * @param jsonArray JSON array of URLS to include in replication content
     * @return replication content
     * @throws ReplicationException if an error occurs
     */
    private ReplicationContent createContent(final ReplicationContentFactory factory,
            final JSONArray jsonArray) throws ReplicationException {

        Path tempFile;

        try {
            tempFile = Files.createTempFile("akamai_purge_agent", ".tmp");
        } catch (IOException e) {
            throw new ReplicationException("Could not create temporary file", e);
        }

        try{
        	final BufferedWriter writer = Files.newBufferedWriter(tempFile, Charset.forName("UTF-8"));
            writer.write(jsonArray.toString());
            writer.flush();

            return factory.create("text/plain", tempFile.toFile(), true);
        } catch (IOException e) {
            throw new ReplicationException("Could not write to temporary file", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #NAME}
     */
    public String getName() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@value #TITLE}
     */
    public String getTitle() {
        return TITLE;
    }
}
