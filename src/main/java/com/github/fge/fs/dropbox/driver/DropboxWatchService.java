/*******************************************************************************
 * Copyright (C) 2020 Frédéric Bard
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *******************************************************************************/
package com.github.fge.fs.dropbox.driver;

import com.dropbox.core.DbxApiException;
import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.DeletedMetadata;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderGetLatestCursorResult;
//import com.dropbox.core.v2.files.ListFolderLongpollResult;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import java.io.IOException;
import java.net.URI;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class DropboxWatchService implements WatchService {

   private DbxClientV2         _dropboxClient;
   private AtomicBoolean       _hasChanges;
   private boolean             _continuePolling;

   private CloseableHttpClient _httpClient;

   public DropboxWatchService(final DbxClientV2 client) {
	   _dropboxClient = client;
      _httpClient = HttpClientBuilder.create().build();
   }

   /**
    * Returns latest cursor for listing changes to a directory in
    * Dropbox with the given path.
    *
    * @param dbxClient
    *           Dropbox client to use for fetching the latest cursor
    * @param path
    *           path to directory in Dropbox
    * @return cursor for listing changes to the given Dropbox directory
    */
   private String getLatestCursor(final String path)
         throws DbxApiException, DbxException {

      final ListFolderGetLatestCursorResult result = _dropboxClient
            .files()
            .listFolderGetLatestCursorBuilder(path)
            .withIncludeDeleted(true)
            .withIncludeMediaInfo(false)
            .withRecursive(true)
            .start();

      return result.getCursor();
   }

   @Override
   public void close() throws IOException {

      //We need to set this to false and we close the http client below
      //otherwise we will poll again.
      _continuePolling = false;

      if (_httpClient != null) {
         try {
            _httpClient.close();
         } catch (final Exception e) {
        	 throw new IOException("Error when closing the httpclient", e);
         }
      }
   }

   /**
    * Prints changes made to a folder in Dropbox since the given
    * cursor was retrieved.
    *
    * @param dbxClient
    *           Dropbox client to use for fetching folder changes
    * @param cursor
    *           Latest cursor received since last set of changes
    * @return latest cursor after changes
    */
   private String examineChanges(String cursor)
         throws DbxApiException, DbxException {

      while (true) {
         final ListFolderResult result = _dropboxClient
               .files()
               .listFolderContinue(cursor);
         for (final Metadata metadata : result.getEntries()) {
            if (metadata instanceof FileMetadata ||
                  metadata instanceof DeletedMetadata) {
               _hasChanges.getAndSet(true);
            }
         }
         // update cursor to fetch remaining results
         cursor = result.getCursor();

         if (!result.getHasMore()) {
            break;
         }
      }

      return cursor;
   }

   /**
    * Solution/Hack found here
    * https://www.dropboxforum.com/t5/Dropbox-API-Support-Feedback/Abort-call-to-listFolderLongpoll-in-Java-SDK/m-p/192787
    *
    * @param cursor
    * @return
    */
   private ListFolderLongpollResult listFolderLongpoll(final String cursor) throws IOException {
      final URI uri = URI.create("https://notify.dropboxapi.com/2/files/list_folder/longpoll"); //$NON-NLS-1$

      final HttpPost postRequest = new HttpPost(uri);
      postRequest.addHeader("Content-Type", "application/json"); //$NON-NLS-1$ //$NON-NLS-2$

      final String jsonPayload = "{\"cursor\":\"" + cursor + "\"}"; //$NON-NLS-1$ //$NON-NLS-2$

      postRequest.setEntity(new StringEntity(jsonPayload));

      try (CloseableHttpResponse httpResponse = _httpClient.execute(postRequest)) {
         final String entity = EntityUtils.toString(httpResponse.getEntity());
         if (entity == null || entity.length() == 0) {
            return null;
         }

         final JSONObject jsonContent = new JSONObject(entity);

         final String changesElementName = "changes"; //$NON-NLS-1$
         final String backoffElementName = "backoff"; //$NON-NLS-1$
         final boolean hasChanges = jsonContent.has(changesElementName);
         final boolean hasbackoff = jsonContent.has(backoffElementName);
         if (hasChanges == false && hasbackoff == false) {
            return null;
         }
         final boolean changes = hasChanges ? Boolean.valueOf(jsonContent.get(changesElementName).toString()) : false;
         final Long backoff = hasbackoff ? Long.valueOf(jsonContent.get(backoffElementName).toString()) : null;
         final ListFolderLongpollResult listFolderLongpollResult = new ListFolderLongpollResult(changes, backoff);

         return listFolderLongpollResult;
      } catch (final IOException e) {
         //Reached when the dropbox folder watch will be stopped
      }

      return null;
   }

   @Override
   public WatchKey poll() {
      // TODO Auto-generated method stub
      return null;
   }

   @Override
   public WatchKey poll(final long timeout, final TimeUnit unit) throws InterruptedException {
      // TODO Auto-generated method stub
      return null;
   }

   /**
    * See listFolderLongpoll() description to understand why we can't use the Dropbox SDK
    * function listFolderLongpoll().
    */
   @Override
   public WatchKey take() throws InterruptedException {
      if (_httpClient == null) {
         return null;
      }

      _hasChanges = new AtomicBoolean(false);
      _continuePolling = true;

      while (_hasChanges.get() == false && _continuePolling) {
         try {

            final String cursor = getLatestCursor("");

            //  final ListFolderLongpollResult listFolderLongpollResult = DropboxClient.getDefault().files().listFolderLongpoll(cursor);
            //  if (listFolderLongpollResult.getChanges()) {

            final ListFolderLongpollResult listFolderLongpollResult = listFolderLongpoll(cursor);
            if (listFolderLongpollResult == null) {
               continue;
            }
            if (listFolderLongpollResult.getChanges()) {
               examineChanges(cursor);
            }

            // we were asked to back off from our polling, wait the requested amount of seconds
            // before issuing another longpoll request.
            final Long backoff = listFolderLongpollResult.getBackoff();
            if (backoff != null) {
               try {
                  // backing off for %d secs...\n", backoff.longValue());s
                  Thread.sleep(TimeUnit.SECONDS.toMillis(backoff));
               } catch (final InterruptedException ex) {
            	   throw new IOException("Error when backing off from watching the Dropbox folder", ex);
               }
            }

         } catch (final DbxException | IOException ex) {
         }
      }

      //We return an empty Watchkey. The goal here is that we only
      //want to notify that some changes happened
      final WatchKey dropboxWatchKey = new WatchKey() {

         @Override
         public void cancel() {
            // TODO Auto-generated method stub

         }

         @Override
         public boolean isValid() {
            // TODO Auto-generated method stub
            return false;
         }

         @Override
         public List<WatchEvent<?>> pollEvents() {
            // TODO Auto-generated method stub
            return null;
         }

         @Override
         public boolean reset() {
            return _continuePolling;
         }

         @Override
         public Watchable watchable() {
            // TODO Auto-generated method stub
            return null;
         }
      };

      return dropboxWatchKey;
   }
}

