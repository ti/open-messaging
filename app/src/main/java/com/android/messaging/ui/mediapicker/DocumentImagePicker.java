/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.ui.mediapicker;

import android.app.Activity;
import android.app.Fragment;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.SafeAsyncTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps around the functionalities to allow the user to pick images from the document
 * picker.  Instances of this class must be tied to a Fragment which is able to delegate activity
 * result callbacks.
 */
public class DocumentImagePicker {

    /**
     * An interface for a listener that listens for when a document has been picked.
     */
    public interface SelectionListener {
        /**
         * Called when an document is selected from picker. At this point, the file hasn't been
         * actually loaded and staged in the temp directory, so we are passing in a pending
         * MessagePartData, which the consumer should use to display a placeholder image.
         * @param pendingItem a temporary attachment data for showing the placeholder state.
         */
        void onDocumentSelected(PendingAttachmentData pendingItem);
    }

    // The owning fragment.
    private final Fragment mFragment;

    // The listener on the picker events.
    private final SelectionListener mListener;

    private static final String EXTRA_PHOTO_URL = "photo_url";

    /**
     * Creates a new instance of DocumentImagePicker.
     * @param fragment The activity that owns the picker, or the activity that hosts the owning
     *        fragment.
     */
    public DocumentImagePicker(final Fragment fragment,
            final SelectionListener listener) {
        mFragment = fragment;
        mListener = listener;
    }

    /**
     * Intent out to open an image/video from document picker.
     */
    public void launchPicker() {
        UIIntents.get().launchDocumentImagePicker(mFragment);
    }

    /**
     * Must be called from the fragment/activity's onActivityResult().
     */
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == UIIntents.REQUEST_PICK_IMAGE_FROM_DOCUMENT_PICKER &&
                resultCode == Activity.RESULT_OK) {
            List<Uri> uris = new ArrayList<>();
            //form standard file picker
            ClipData clipData = data.getClipData();
            if(clipData != null && clipData.getItemCount()>0) {
                int count = clipData.getItemCount();
                for(int i=0; i< count;i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    uris.add(item.getUri());
                }
            }
            if(uris.size()==0){
                Uri uri = data.getData();
                if(uri!=null){
                    uris.add(uri);
                }
            }
            //form other file picker
            if(uris.size()==0) {
                String url = data.getStringExtra(EXTRA_PHOTO_URL);
                if(url != null) {
                    final Uri uriParsed = Uri.parse(url);
                    uris.add(uriParsed);
                } else {
                    final Bundle extras = data.getExtras();
                    if (extras != null) {
                        final Uri uriStream = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
                        uris.add(uriStream);
                    }
                }
            }
            if(uris.size()>0){
                for(int i=0;i<uris.size();i++) {
                    prepareDocumentForAttachment(uris.get(i));
                }
            }
        }
    }

    private void prepareDocumentForAttachment(final Uri documentUri) {
        // Notify our listener with a PendingAttachmentData containing the metadata.
        // Asynchronously get the content type for the picked image since
        // ImageUtils.getContentType() potentially involves I/O and can be expensive.
        new SafeAsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackgroundTimed(final Void... params) {
                return ImageUtils.getContentType(
                        Factory.get().getApplicationContext().getContentResolver(), documentUri);
            }

            @Override
            protected void onPostExecute(final String contentType) {
                // Ask the listener to create a temporary placeholder item to show the progress.
                final PendingAttachmentData pendingItem =
                        PendingAttachmentData.createPendingAttachmentData(contentType,
                                documentUri);
                mListener.onDocumentSelected(pendingItem);
            }
        }.executeOnThreadPool();
    }
}
