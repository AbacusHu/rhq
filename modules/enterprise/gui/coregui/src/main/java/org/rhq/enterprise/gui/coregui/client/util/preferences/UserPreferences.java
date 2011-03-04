/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.util.preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.gwt.user.client.rpc.AsyncCallback;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.enterprise.gui.coregui.client.CoreGUI;
import org.rhq.enterprise.gui.coregui.client.gwt.GWTServiceLookup;
import org.rhq.enterprise.gui.coregui.client.gwt.SubjectGWTServiceAsync;
import org.rhq.enterprise.gui.coregui.client.util.MeasurementUtility;

/**
 * Provides access to the persisted user preferences.
 * 
 * If you want to work with measurement related preferences, you might want to use
 * {@link MeasurementUserPreferences} instead.
 * 
 * @author Greg Hinkle
 * @author Ian Springer
 */
public class UserPreferences {

    protected static final String PREF_LIST_DELIM = "|";
    protected static final String PREF_LIST_DELIM_REGEX = "\\|";

    private Subject subject;
    private Configuration userConfiguration;
    private SubjectGWTServiceAsync subjectService = GWTServiceLookup.getSubjectService();

    private UserPreferenceChangeListener autoPersister = null;

    private ArrayList<UserPreferenceChangeListener> changeListeners = new ArrayList<UserPreferenceChangeListener>();

    public UserPreferences(Subject subject) {
        this.subject = subject;
        this.userConfiguration = subject.getUserConfiguration();
    }

    /**
     * If you pass in true, you are enabling this preferences object to
     * automatically persist changes as they occur. If you pass in false,
     * you are disabling this feature. When enabled, {@link #store(AsyncCallback)} is
     * called every time a preference value is changed or removed.
     * 
     * @param enable true or false to turn on or off automatic persistence
     */
    public void setAutomaticPersistence(boolean enable) {
        if (enable) {
            if (this.autoPersister == null) {
                this.autoPersister = new UserPreferenceChangeListener() {
                    @Override
                    public void onPreferenceChange(UserPreferenceChangeEvent event) {
                        persist();
                    }

                    @Override
                    public void onPreferenceRemove(UserPreferenceChangeEvent event) {
                        persist();
                    }

                    private void persist() {
                        store(new AsyncCallback<Subject>() {
                            @Override
                            public void onSuccess(Subject result) {
                                // don't announce anything, should happen under the covers - just refresh the current page
                                CoreGUI.refresh();
                            }

                            @Override
                            public void onFailure(Throwable caught) {
                                CoreGUI.getErrorHandler().handleError("Cannot store preferences", caught);
                            }
                        });
                    }
                };
                addChangeListener(autoPersister);
            }
        } else {
            if (this.autoPersister != null) {
                this.changeListeners.remove(this.autoPersister);
                this.autoPersister = null;
            }
        }
    }

    public Set<Integer> getFavoriteResources() {
        return getPreferenceAsIntegerSet(UserPreferenceNames.RESOURCE_HEALTH_RESOURCES);
    }

    public void setFavoriteResources(Set<Integer> resourceIds, AsyncCallback<Subject> callback) {
        setPreference(UserPreferenceNames.RESOURCE_HEALTH_RESOURCES, resourceIds);
        store(callback);
    }

    public Set<Integer> getFavoriteResourceGroups() {
        return getPreferenceAsIntegerSet(UserPreferenceNames.GROUP_HEALTH_GROUPS);
    }

    public void setFavoriteResourceGroups(Set<Integer> resourceGroupIds, AsyncCallback<Subject> callback) {
        setPreference(UserPreferenceNames.GROUP_HEALTH_GROUPS, resourceGroupIds);
        store(callback);
    }

    public List<Integer> getRecentResources() {
        return this.getPreferenceAsIntegerList(UserPreferenceNames.RECENT_RESOURCES);
    }

    public void addRecentResource(Integer resourceId, AsyncCallback<Subject> callback) {
        List<Integer> recentResources = getRecentResources();
        if (!recentResources.isEmpty() && recentResources.get(0).equals(resourceId)) {
            return;
        }

        recentResources.remove(resourceId);
        recentResources.add(0, resourceId);
        // limit to the 10 most recent resources
        int size = recentResources.size();
        if (size > 10) {
            recentResources.remove(10);
        }
        setPreference(UserPreferenceNames.RECENT_RESOURCES, recentResources);
        store(callback);
    }

    public List<Integer> getRecentResourceGroups() {
        return getPreferenceAsIntegerList(UserPreferenceNames.RECENT_RESOURCE_GROUPS);
    }

    public void addRecentResourceGroup(Integer resourceGroupId, AsyncCallback<Subject> callback) {
        List<Integer> recentResourceGroups = getRecentResourceGroups();
        if (!recentResourceGroups.isEmpty() && recentResourceGroups.get(0).equals(resourceGroupId)) {
            return;
        }

        recentResourceGroups.remove(resourceGroupId);
        recentResourceGroups.add(0, resourceGroupId);
        // limit to the 5 most recent resources
        int size = recentResourceGroups.size();
        if (size > 5) {
            recentResourceGroups.remove(5);
        }
        setPreference(UserPreferenceNames.RECENT_RESOURCE_GROUPS, recentResourceGroups);
        store(callback);
    }

    public int getPageRefreshInterval() {
        if ((getPreference(UserPreferenceNames.PAGE_REFRESH_PERIOD) == null)
            || (Integer.valueOf(getPreference(UserPreferenceNames.PAGE_REFRESH_PERIOD)) == 60)) {//default to 60 seconds
            setPreference(UserPreferenceNames.PAGE_REFRESH_PERIOD, String.valueOf(MeasurementUtility.MINUTES));
        }
        return getPreferenceAsInteger(UserPreferenceNames.PAGE_REFRESH_PERIOD);
    }

    public void setPageRefreshInterval(int refreshInterval, AsyncCallback<Subject> callback) {
        setPreference(UserPreferenceNames.PAGE_REFRESH_PERIOD, String.valueOf(refreshInterval));
        store(callback);
    }

    protected String getPreference(String name) {
        return userConfiguration.getSimpleValue(name, null);
    }

    protected String getPreference(String name, String defaultValue) {
        return userConfiguration.getSimpleValue(name, defaultValue);
    }

    /**
     * Similar to {@link #getPreference(String, String)} except if the preference
     * exists, but its value is an empty string, this method returns the defaultValue.
     * In other words, an empty preference value is just as if it was null.
     * 
     * @param name name of preference
     * @param defaultValue the value returned if the preference value was null or an empty string
     * @return the preference value
     */
    protected String getPreferenceEmptyStringIsDefault(String name, String defaultValue) {
        String value = userConfiguration.getSimpleValue(name, null);
        if (value == null || value.trim().length() == 0) {
            value = defaultValue;
        }
        return value;
    }
    
    protected void setPreference(String name, Collection<?> value) {
        StringBuilder buffer = new StringBuilder();
        boolean first = true;
        for (Object item : value) {
            if (first) {
                first = false;
            } else {
                buffer.append(PREF_LIST_DELIM);
            }
            buffer.append(item);
        }
        setPreference(name, buffer.toString());
    }

    protected void setPreference(String name, String value) {
        PropertySimple prop = this.userConfiguration.getSimple(name);
        String oldValue = null;
        if (prop == null) {
            this.userConfiguration.put(new PropertySimple(name, value));
        } else {
            oldValue = prop.getStringValue();
            prop.setStringValue(value);
        }

        UserPreferenceChangeEvent event = new UserPreferenceChangeEvent(name, value, oldValue);
        for (UserPreferenceChangeListener listener : changeListeners) {
            listener.onPreferenceChange(event);
        }
    }

    protected void unsetPreference(String name) {
        PropertySimple doomedProp = this.userConfiguration.getSimple(name);

        // it's possible property was already removed, and thus this operation becomes a no-op
        if (doomedProp != null) {
            String oldValue = doomedProp.getStringValue();
            this.userConfiguration.remove(name);
            UserPreferenceChangeEvent event = new UserPreferenceChangeEvent(name, null, oldValue);
            for (UserPreferenceChangeListener listener : changeListeners) {
                listener.onPreferenceRemove(event);
            }
        }
    }

    public void clearConfiguration() {
        ArrayList<String> names = new ArrayList<String>(this.userConfiguration.getNames()); // need separate list to avoid concurrent mod exception
        for (String name : names) {
            unsetPreference(name);
        }
    }

    public void store(AsyncCallback<Subject> callback) {
        this.subjectService.updateSubject(this.subject, callback);
    }

    public Configuration getConfiguration() {
        return userConfiguration;
    }

    public List<String> getPreferenceAsList(String key) {
        String pref = null;
        try {
            pref = getPreference(key);
        } catch (IllegalArgumentException e) {
        }

        return (pref != null) ? Arrays.asList(pref.split(PREF_LIST_DELIM_REGEX)) : new ArrayList<String>();
    }

    public Set<Integer> getPreferenceAsIntegerSet(String key) {
        try {
            List<String> value = getPreferenceAsList(key);
            // Use a TreeSet, so the Resource id's are sorted.
            Set<Integer> result = new TreeSet<Integer>();
            for (String aValue : value) {
                String trimmed = aValue.trim();
                if (trimmed.length() > 0) {
                    Integer intValue = Integer.valueOf(trimmed);
                    result.add(intValue);
                }
            }
            return result;
        } catch (Exception e) {
            return new HashSet<Integer>();
        }
    }

    public List<Integer> getPreferenceAsIntegerList(String key) {
        try {
            List<String> value = getPreferenceAsList(key);
            List<Integer> result = new ArrayList<Integer>(value.size());
            for (String aValue : value) {
                String trimmed = aValue.trim();
                if (trimmed.length() > 0) {
                    Integer intValue = Integer.valueOf(trimmed);
                    result.add(intValue);
                }
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<Integer>();
        }
    }

    public Integer getPreferenceAsInteger(String key) {
        String pref = null;
        try {
            pref = getPreference(key);
        } catch (IllegalArgumentException e) {
        }
        return (pref != null) ? Integer.valueOf(pref) : Integer.valueOf(0);
    }

    public void addChangeListener(UserPreferenceChangeListener listener) {
        changeListeners.add(listener);
    }
}
