package org.sakaiproject.nakamura.migratecustommimetype;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
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
import org.sakaiproject.nakamura.lite.content.InternalContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
public class ContentMimetypeMigrator {

  private static final Logger log = LoggerFactory.getLogger(ContentMimetypeMigrator.class);

  private static final String OLD_MIME_FIELD = "sakai:custom-mimetype";

  private final int PAGE_SIZE = 25;

  @Reference
  Repository repository;

  @Reference
  SolrServerService solrServerService;

  @Activate
  public void activate(Map<String,Object> props){
    migrateCustomMimetype();
  }

  /**
   *
   * This method iterates over all of the sakai content items and renames the custom-mimetype field
   * to _mimeType field by copying custom-mimetype => _mimeType and deleting the custom-mimetype field.
   *
   * {@link InternalContent} defines a constant for the content object's mimeType field.
   *
   * If you don't run this after upgrading OAE you won't be able to see any
   * sakai-docs or sakai-links that were in the system before the upgrade.
   */
  public void migrateCustomMimetype(){
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
                String contentMimetype = (String)content.getProperty(OLD_MIME_FIELD);
                if (contentMimetype != null){
                        content.setProperty(InternalContent.MIMETYPE_FIELD, contentMimetype);
                        content.removeProperty(OLD_MIME_FIELD);
            cm.update(content);
                        log.debug("Updated {} ", contentMimetype);
                }
            }
            start += resultDocs.size();
                log.debug("Processed {} of {}.", resultDocs.size(), totalResults);
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
