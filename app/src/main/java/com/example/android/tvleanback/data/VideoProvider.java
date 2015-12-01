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

package com.example.android.tvleanback.data;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import com.example.android.tvleanback.R;
import com.example.android.tvleanback.model.Movie;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This class loads videos from a backend and saves them into a HashMap
 */
public class VideoProvider {
    private static final String TAG = "VideoProvider";
    private static final String TAG_MEDIA = "videos";
    private static final String TAG_GOOGLE_VIDEOS = "googlevideos";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_STUDIO = "studio";
    private static final String TAG_SOURCES = "sources";
    private static final String TAG_DESCRIPTION = "description";
    private static final String TAG_CARD_THUMB = "card";
    private static final String TAG_BACKGROUND = "background";
    private static final String TAG_TITLE = "title";

    private static HashMap<String, List<Movie>> sMovieList;
    private static HashMap<String, Movie> sMovieListById;
    private static List<Movie> sCurrentQueue;
    private static int sCurrentMovieIndex;

    private static Resources sResources;
    private static Uri sPrefixUrl;

    public static void setContext(Context context) {
        if (null == sResources) {
            sResources = context.getResources();
        }
    }

    public static Movie getMovieById(String mediaId) {
        return sMovieListById.get(mediaId);
    }

    public static HashMap<String, List<Movie>> getMovieList() {
        return sMovieList;
    }

    public static List<Movie> getCurrentQueue() {
        return sCurrentQueue;
    }

    public static String prevVideoId() {
        if (--sCurrentMovieIndex < 0) {
            sCurrentMovieIndex = sCurrentQueue.size() - 1;
        }

        return sCurrentQueue.get(sCurrentMovieIndex).getId();
    }

    public static String nextVideoId() {
        if (++sCurrentMovieIndex >= sCurrentQueue.size()) {
            sCurrentMovieIndex = 0;
        }

        return sCurrentQueue.get(sCurrentMovieIndex).getId();
    }

    /**
     * Set the current queue to the list of Movies in the given Movie's category.
     *
     * @param selectedMovie Movie whose category will be used to determine queue to be set.
     */
    public static void setQueue(Movie selectedMovie) {
        if (sMovieList == null) {
            sCurrentQueue = null;
            sCurrentMovieIndex = -1;
            return;
        }

        for (Map.Entry<String, List<Movie>> entry : sMovieList.entrySet()) {
            if (selectedMovie.getCategory().equals(entry.getKey())) {
                List<Movie> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    sCurrentQueue = list;
                    sCurrentMovieIndex = list.indexOf(selectedMovie);
                    return;
                }
            }
        }
    }

    public static HashMap<String, List<Movie>> buildMedia(String url)
            throws JSONException {
        if (null != sMovieList) {
            return sMovieList;
        }
        sMovieList = new HashMap<>();
        sMovieListById = new HashMap<>();

        JSONObject jsonObj = new VideoProvider().fetchJSON(url);

        if (null == jsonObj) {
            Log.e(TAG, "An error occurred fetching videos.");
            return sMovieList;
        }

        JSONArray categories = jsonObj.getJSONArray(TAG_GOOGLE_VIDEOS);

        if (null != categories) {
            final int categoryLength = categories.length();
            Log.d(TAG, "category #: " + categoryLength);

            String id;
            String title;
            String videoUrl;
            String bgImageUrl;
            String cardImageUrl;
            String studio;

            for (int catIdx = 0; catIdx < categoryLength; catIdx++) {
                JSONObject category = categories.getJSONObject(catIdx);
                String categoryName = category.getString(TAG_CATEGORY);
                JSONArray videos = category.getJSONArray(TAG_MEDIA);
                Log.d(TAG,
                        "category: " + catIdx + " Name:" + categoryName + " video length: "
                                + (null != videos ? videos.length() : 0));
                List<Movie> categoryList = new ArrayList<>();
                Movie movie;
                if (null != videos) {
                    for (int vidIdx = 0, vidSize = videos.length(); vidIdx < vidSize; vidIdx++) {
                        JSONObject video = videos.getJSONObject(vidIdx);
                        String description = video.getString(TAG_DESCRIPTION);
                        JSONArray videoUrls = video.getJSONArray(TAG_SOURCES);
                        if (null == videoUrls || videoUrls.length() == 0) {
                            continue;
                        }

                        id = "" + sMovieListById.size();
                        title = video.getString(TAG_TITLE);
                        videoUrl = getVideoPrefix(categoryName, getVideoSourceUrl(videoUrls));
                        bgImageUrl = getThumbPrefix(categoryName, title,
                                video.getString(TAG_BACKGROUND));
                        cardImageUrl = getThumbPrefix(categoryName, title,
                                video.getString(TAG_CARD_THUMB));
                        studio = video.getString(TAG_STUDIO);

                        // Create Movie object.
                        movie = buildMovieInfo(id, categoryName, title, description, studio,
                                videoUrl, cardImageUrl, bgImageUrl);

                        // Add it to the list.
                        sMovieListById.put(movie.getId(), movie);
                        categoryList.add(movie);
                    }
                    sMovieList.put(categoryName, categoryList);
                }
            }
        }
        return sMovieList;
    }

    private static Movie buildMovieInfo(String id,
                                        String category,
                                        String title,
                                        String description,
                                        String studio,
                                        String videoUrl,
                                        String cardImageUrl,
                                        String bgImageUrl) {

        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setDescription(description);
        movie.setStudio(studio);
        movie.setCategory(category);
        movie.setCardImageUrl(cardImageUrl);
        movie.setBackgroundImageUrl(bgImageUrl);
        movie.setVideoUrl(videoUrl);

        return movie;
    }

    // workaround for partially pre-encoded sample data
    private static String getVideoSourceUrl(final JSONArray videos) throws JSONException {
        try {
            final String url = videos.getString(0);
            return (-1) == url.indexOf('%') ? url : URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new JSONException("Broken VM: no UTF-8");
        }
    }

    private static String getVideoPrefix(String category, String videoUrl) {
        return sPrefixUrl.buildUpon()
                .appendPath(category)
                .appendPath(videoUrl)
                .toString();
    }

    private static String getThumbPrefix(String category, String title, String imageUrl) {
        return sPrefixUrl.buildUpon()
                .appendPath(category)
                .appendPath(title)
                .appendPath(imageUrl)
                .toString();
    }

    private JSONObject fetchJSON(String urlString) {
        Log.d(TAG, "Parse URL: " + urlString);
        BufferedReader reader = null;

        sPrefixUrl = Uri.parse(sResources.getString(R.string.prefix_url));

        try {
            java.net.URL url = new java.net.URL(urlString);
            URLConnection urlConnection = url.openConnection();
            reader = new BufferedReader(new InputStreamReader(
                    urlConnection.getInputStream(), "iso-8859-1"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String json = sb.toString();
            return new JSONObject(json);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse the json for media list", e);
            return null;
        } finally {
            if (null != reader) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.d(TAG, "JSON feed closed", e);
                }
            }
        }
    }
}
