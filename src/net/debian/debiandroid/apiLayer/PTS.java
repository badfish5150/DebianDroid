package net.debian.debiandroid.apiLayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.debian.debiandroid.apiLayer.soaptools.PTSSoapCaller;


import android.content.Context;
import android.net.Uri;
import androidStorageUtils.StorageUtils;

public class PTS extends PTSSoapCaller implements Subscribable {

	private StorageUtils ptsStorage;
	private HTTPCaller httpCaller;
	
	public static final String PTSSUBSCRIPTIONS = "PTSSubscriptions";
	
	private static final String PTSPCKGNAMESEARCHURL = "http://sources.debian.net/api/search/";
	
	public PTS(Context context) {
		super(context);
		ptsStorage = StorageUtils.getInstance(context);
		httpCaller = new HTTPCaller(context);
	}

	public boolean isSubscribedTo(String subcriptionID) {
		return ptsStorage.getPreferenceSet(PTSSUBSCRIPTIONS, new HashSet<String>()).contains(subcriptionID);
	}

	public boolean removeSubscriptionTo(String subcriptionID) {
		return ptsStorage.removePreferenceFromSet(PTSSUBSCRIPTIONS, subcriptionID);
	}

	public boolean addSubscriptionTo(String subcriptionID) {
		return ptsStorage.addPreferenceToSet(PTSSUBSCRIPTIONS, subcriptionID);
	}
	
	public Set<String> getSubscriptions() {
		return ptsStorage.getPreferenceSet(PTSSUBSCRIPTIONS, new HashSet<String>());
	}
	
	public static boolean isPTSHost(String host) {
		return host.equalsIgnoreCase("packages.qa.debian.org");
	}
	
	public static String PTSURIToPckgName(Uri uri) {
		return uri.getLastPathSegment().replace(".html", "");
	}
	
	public ArrayList<String> getSimilarPckgNames(String pckgName) {
        try {
        	ArrayList<String> pckgNames = new ArrayList<String>();
        	JSONObject json = new JSONObject(httpCaller.doQueryRequest(PTSPCKGNAMESEARCHURL + pckgName));
        	JSONObject results;
        	if(json.has("results")) {
        		results = json.optJSONObject("results");
        		if(results!=null) {
	        		if(results.has("exact")) {
	        			JSONObject exact = results.optJSONObject("exact");
	        			if(exact!=null) {
		        			String exactValue = exact.optString("name");
		        			if(exactValue!=null) {
		        				pckgNames.add(exactValue);
		        			}
	        			}
	        		}
	        		if(results.has("other")) {
	        			JSONArray otherArray = results.optJSONArray("other");
	        			if(otherArray!=null) {
		        			for(int i=0; i<otherArray.length(); i++) {
		        				JSONObject other = otherArray.optJSONObject(i);
		        				if(other!=null) {
			        				String otherValue = other.optString("name");
			        				if(otherValue!=null) {
			        					pckgNames.add(otherValue);
			        				}
		        				}
		        			}
	        			}
	        		}
        		}
        	}
        	return pckgNames;
        } catch (JSONException e) {
            e.printStackTrace();
        }
		return new ArrayList<String>();
	}
}
