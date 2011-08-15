package org.sakaiproject.nakamura.migratesparseuuid;

import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(metatype = true)
public class ContentUUIDMigrator {

	private static final Logger log = LoggerFactory.getLogger(ContentUUIDMigrator.class);

	private static final String DEFAULT_ORIGINAL_FIELD = "_id";
	@Property(value=DEFAULT_ORIGINAL_FIELD)
	private static final String PROP_ORIGINAL_FIELD = "migrateuuid.original.field";

	private static final String DEFAULT_DESTINATION_FIELD = "_sparseId";
	@Property(value=DEFAULT_DESTINATION_FIELD)
	private static final String PROP_DESTINATION_FIELD = "migrateuuid.destination.field";

	@Property(boolValue=true)
	private static final String PROP_DRYRUN = "migrateuuid.dryrun";
	
	private String originalField;
	private String destinationField;
	private boolean dryrun;
	
	private final int PAGE_SIZE = 25;

	@Reference
	Repository repository;

	@Reference
	SolrServerService solrServerService;

	@Activate
	public void activate(Map<String,Object> props){
		originalField = OsgiUtil.toString(PROP_ORIGINAL_FIELD, DEFAULT_ORIGINAL_FIELD);
		destinationField = OsgiUtil.toString(PROP_DESTINATION_FIELD, DEFAULT_DESTINATION_FIELD);
		dryrun = OsgiUtil.toBoolean(PROP_DRYRUN, true);
		migrateUUIDs();
	}

	/**
	 * This migration iterates over all of the sakai content items and renames the a property to another property.
	 */
	public void migrateUUIDs(){
		if (originalField.equals(destinationField)){
			log.error("Nothing to do: {} = {}", PROP_DESTINATION_FIELD, destinationField);
			return;
		}

		Session session;
		try {
			session = repository.loginAdministrative();
			ContentManager cm = session.getContentManager();
			cm.setMaintanenceMode(true);

			int start = 0;

			// Search for all content and page through it.
			SolrServer server = solrServerService.getServer();
			SolrQuery query = new SolrQuery();
			query.setQuery("resourceType:sakai/pooled-content");
			query.setStart(start);
			query.setRows(PAGE_SIZE);

			QueryResponse response = server.query(query);
		    long totalResults = response.getResults().getNumFound();
		    log.info("Attempting to migrate {} content items.", totalResults);

		    while (start < totalResults){
		        query.setStart(start);
		        SolrDocumentList resultDocs = response.getResults();
		        for (SolrDocument doc : resultDocs){
		            String id = (String)doc.get("id");
		            if (id == null){
		            	continue;
		            }
		            Content content = cm.get(id);

		            String theValue = (String)content.getProperty(originalField);
		            if (theValue != null && dryrun == false){
		            	content.setProperty(destinationField, theValue);
		            	content.removeProperty(originalField);
		                cm.update(content);
		            }
                    log.debug("Processed {}", id);
		        }
		        start += resultDocs.size();
                log.debug("Processed {} of {}.", start, totalResults);
		    }
			session.logout();

		} catch (ClientPoolException e) {
			log.error("Problem with the connection to the sparse storage.", e);
		} catch (StorageClientException e) {
			log.error("Problem with the sparse storage.", e);
		} catch (AccessDeniedException e) {
			log.error("Unable to access an object due to lack of permission. " +
					  "Hard to imagine though since we're logging in as the admin.", e);
		} catch (SolrServerException e) {
			log.error("An exception occurred while searching.", e);
		} finally {
			session = null;
		}
	}
}
