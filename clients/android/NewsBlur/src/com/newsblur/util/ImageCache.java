package com.newsblur.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility to cache images for offline reading. Takes an image URL and turns it into
 * a local file with a unique name that can be easily re-calculated in the future. Also
 * supports automatic cleanup of old files.
 */
public class ImageCache {

    private static final String CACHE_SUBDIR = "olimages";
    private static final long MAX_FILE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final long MIN_FREE_SPACE_BYTES = 100L * 1024L * 1024L;
    private static final int MIN_VALID_CACHE_BYTES = 64;

    private File cacheDir;
    private Pattern postfixPattern;

    public ImageCache(Context context) {
        cacheDir = new File(context.getCacheDir(), CACHE_SUBDIR);
		if (!cacheDir.exists()) {
			cacheDir.mkdirs();
		}

        postfixPattern = Pattern.compile("(\\.[a-zA-Z0-9]+)[^\\.]*$");
    }

    public void cacheImage(String url) {
        try {
            // don't be evil and download images if the user is low on storage
            if (cacheDir.getFreeSpace() < MIN_FREE_SPACE_BYTES) {
                Log.w(this.getClass().getName(), "device low on storage, not caching images");
                return;
            }
            
            String fileName = getFileName(url);
            if (fileName == null) {
                Log.w(this.getClass().getName(), "failed to cache image: no file extension");
                return;
            }

            File f = new File(cacheDir, fileName);
            if (f.exists()) return;
            long size = NetworkUtils.loadURL(new URL(url), f);
            // images that are super-small tend to be errors or invisible. don't waste file handles on them
            if (size < MIN_VALID_CACHE_BYTES) {
                f.delete();
            }
        } catch (IOException e) {
            // a huge number of things could go wrong fetching and storing an image. don't spam logs with them
        }
    }

    /**
     * Gets the cached location of the specified network image, if it has 
     * been cached.  Fails fast and returns null if for any reason the image
     * is not available.
     */
    public String getCachedLocation(String url) {
        try {
            String fileName = getFileName(url);
            if (fileName == null) {
                return null;
            }
            File f = new File(cacheDir, fileName);
            if (f.exists()) {
                return f.getAbsolutePath();
            } else { 
                return null;
            }
        } catch (Exception e) {
            Log.e(this.getClass().getName(), "image cache error", e);
            return null;
        }
    }

    private String getFileName(String url) {
        Matcher m = postfixPattern.matcher(url);
        if (! m.find()) {
            return null;
        }
        String fileName = Integer.toString(Math.abs(url.hashCode())) + m.group(1);
        return fileName;
    }

    public void cleanup(Set<String> currentImages) {
        // if there appear to be zero images in the system, a DB rebuild probably just
        // occured, so don't trust that data for cleanup
        if (currentImages.size() == 0) return;

        Set<String> currentFiles = new HashSet<String>(currentImages.size());
        for (String url : currentImages) currentFiles.add(getFileName(url));
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            long timestamp = f.lastModified();
            if ((System.currentTimeMillis() > (timestamp + MAX_FILE_AGE_MILLIS)) ||
                (!currentFiles.contains(f.getName()))) {
                f.delete();
            }
        }
    }

}
