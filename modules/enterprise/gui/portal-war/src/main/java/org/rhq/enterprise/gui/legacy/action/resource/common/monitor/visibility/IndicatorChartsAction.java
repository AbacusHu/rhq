/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
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
package org.rhq.enterprise.gui.legacy.action.resource.common.monitor.visibility;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.actions.DispatchAction;

import org.rhq.core.domain.auth.Subject;
import org.rhq.enterprise.gui.legacy.AttrConstants;
import org.rhq.enterprise.gui.legacy.KeyConstants;
import org.rhq.enterprise.gui.legacy.ParamConstants;
import org.rhq.enterprise.gui.legacy.RetCodeConstants;
import org.rhq.enterprise.gui.legacy.WebUser;
import org.rhq.enterprise.gui.legacy.util.MonitorUtils;
import org.rhq.enterprise.gui.legacy.util.RequestUtils;
import org.rhq.enterprise.gui.legacy.util.SessionUtils;
import org.rhq.enterprise.gui.util.MetricsDisplayMode;
import org.rhq.enterprise.gui.util.WebUtility;
import org.rhq.enterprise.server.auth.SessionNotFoundException;
import org.rhq.enterprise.server.auth.SessionTimeoutException;
import org.rhq.enterprise.server.authz.PermissionException;
import org.rhq.enterprise.server.common.EntityContext;
import org.rhq.enterprise.server.measurement.MeasurementChartsManagerLocal;
import org.rhq.enterprise.server.measurement.MeasurementPreferences;
import org.rhq.enterprise.server.measurement.MeasurementScheduleManagerLocal;
import org.rhq.enterprise.server.measurement.MeasurementViewException;
import org.rhq.enterprise.server.measurement.MeasurementViewManagerLocal;
import org.rhq.enterprise.server.measurement.MeasurementPreferences.MetricRangePreferences;
import org.rhq.enterprise.server.measurement.MeasurementPreferences.MetricViewData;
import org.rhq.enterprise.server.measurement.uibean.MetricDisplaySummary;
import org.rhq.enterprise.server.measurement.util.MeasurementUtils;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * Generate the metric info for the indicator charts to be displayed.
 * 
 * Identifying individual metric summaries / charts very much use the concept of a metricToken.
 * This token is a string, that identifies the metric summary and where it comes from - if
 * it is a metric summary for a single resource or for a group. See {@link #getContextKeyChart(MetricDisplaySummary)} and
 * {@link #parseMetricToken(String)} on how the metric token looks like.
 * This token is also used in ListChildResources.jsp to add new charts and in DashCharts.jsp
 * to generate the input for up/down/remove. 
 * 
 * The used {@link IndicatorViewsForm} is filled in the {@link CurrentHealthAction} class,
 * which unlike most of the actions preparing a form is not called *PrepareAction.
 * 
 * @author Heiko W. Rupp (for the RHQ rewrite)
 * @author Joseph Marques
 */
public class IndicatorChartsAction extends DispatchAction {

    private final static Log log = LogFactory.getLog(IndicatorChartsAction.class);

    private MeasurementChartsManagerLocal chartsManager = LookupUtil.getMeasurementChartsManager();
    private MeasurementScheduleManagerLocal scheduleManager = LookupUtil.getMeasurementScheduleManager();
    private MeasurementViewManagerLocal viewManager = LookupUtil.getMeasurementViewManager();

    /**
     * Generate a key, that identifies the summary.
     * The format of the token is (without quotation marks):
     * <ul>
     * <li>For a compatible group: "cg,<i>groupId</i>,<i>definitionId</i>"</li>
     * <li>For an autogroup : "ag,<i>parentId</i>,<i>definitionId</i>,<i>childTypeId</i>"</li>
     * <li>For a single resource: "<i>resourceId</i>,<i>scheduleId</i>"</li>
     * </ul>
     * @see #parseMetricToken(String)
     */
    private String getContextKeyChart(MetricDisplaySummary summary) {

        MetricsDisplayMode mode = getDisplayModeForSummary(summary);

        switch (mode) {
        case RESOURCE:
            if (summary.getScheduleId() != null)
                return summary.getResourceId() + "," + summary.getScheduleId().toString();
            throw new IllegalStateException("MetricsDisplayMode was 'RESOURCE', but the scheduleId was null");
        case COMPGROUP:
            return "cg," + summary.getGroupId() + "," + summary.getDefinitionId();
        case AUTOGROUP:
            return "ag," + summary.getParentId() + "," + summary.getDefinitionId() + "," + summary.getChildTypeId();
        default:
            throw new IllegalArgumentException("Unknown or unsupported MetricsDisplayMode '" + mode + "'");
        }
    }

    private EntityContext getContext(HttpServletRequest request) {
        int resourceId = WebUtility.getOptionalIntRequestParameter(request, ParamConstants.RESOURCE_ID_PARAM, -1);
        int groupId = WebUtility.getOptionalIntRequestParameter(request, ParamConstants.GROUP_ID_PARAM, -1);
        int parentResourceId = WebUtility.getOptionalIntRequestParameter(request, "parent", -1);
        int resourceTypeId = WebUtility.getOptionalIntRequestParameter(request, ParamConstants.RESOURCE_TYPE_ID_PARAM,
            -1);

        return new EntityContext(resourceId, groupId, parentResourceId, resourceTypeId);
    }

    /**
     * Stores the metric in the session and also in the passed form, so it can be
     * identified in moveUp()/moveDown()/remove() 
     */
    private void storeMetricsInSession(HttpServletRequest request, List<MetricDisplaySummary> metrics,
        IndicatorViewsForm form) throws ServletException, SessionTimeoutException, SessionNotFoundException {
        request.setAttribute(AttrConstants.CHART_DATA_KEYS, metrics);

        String[] scheduleIds = new String[metrics.size()];
        int i = 0;
        for (MetricDisplaySummary summary : metrics) {
            scheduleIds[i++] = getContextKeyChart(summary);
        }
        form.setMetric(scheduleIds);

        // Set the metrics in the session
        EntityContext context = new EntityContext(form.getId(), form.getGroupId(), form.getParent(), form.getCtype());
        String key = context.getLegacyKey() + "." + form.getView();
        HttpSession session = request.getSession();

        session.setAttribute(key, metrics);
        session.setAttribute("metricKey", key);

        storeMetricsInUserPreferences(request, metrics, form);
    }

    /**
     * Look up metrics from session and load them if they are not yet there
     */
    private List<MetricDisplaySummary> retrieveMetricsFromSession(HttpServletRequest request, IndicatorViewsForm form)
        throws SessionNotFoundException, SessionTimeoutException, PermissionException, ServletException {

        List<MetricDisplaySummary> metrics = new ArrayList<MetricDisplaySummary>();

        Subject subject = WebUtility.getSubject(request);
        try {
            String viewName = form.getView();
            EntityContext context = null;
            try {
                context = new EntityContext(form.getId(), form.getGroupId(), form.getParent(), form.getCtype());
            } catch (IllegalArgumentException iae) {
                // ok, the form didn't have what we wanted, let's fallback on the request
                context = getContext(request);
            }

            if (context.category == EntityContext.Category.Resource) {
                int resourceId = WebUtility.getResourceId(request);
                metrics = chartsManager.getMetricDisplaySummariesForResource(subject, context.resourceId, viewName);
            } else if (context.category == EntityContext.Category.ResourceGroup) {
                int groupId = WebUtility.getRequiredIntRequestParameter(request, AttrConstants.GROUP_ID);
                metrics = chartsManager.getMetricDisplaySummariesForCompatibleGroup(subject, groupId, viewName);
            } else if (context.category == EntityContext.Category.AutoGroup) {
                int parent = WebUtility.getRequiredIntRequestParameter(request, "parent");
                int type = getChildTypeId(request);
                metrics = chartsManager.getMetricDisplaySummariesForAutoGroup(subject, parent, type, viewName);
            } else {
                throw new IllegalArgumentException("Unknown or unsupported context: " + context);
            }
        } catch (Exception e) {
            log.error("Error loading metrics (they were not found in the session)", e);
        }

        return metrics;
    }

    public ActionForward fresh(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession();
        WebUser user = SessionUtils.getWebUser(session);
        //MeasurementPreferences preferences = user.getMeasurementPreferences();

        IndicatorViewsForm ivf = (IndicatorViewsForm) form;
        String viewName = ivf.getView();

        List<MetricDisplaySummary> metrics = new ArrayList<MetricDisplaySummary>();

        /*
         * First try to load the metrics from the user preferences for that key.
         * If that fails, we load defaults for it.
         */
        EntityContext context = WebUtility.getEntityContext(request);
        try {

            List<String> charts = viewManager.getCharts(user.getSubject(), context, viewName);

            for (String token : charts) {
                MetricDisplaySummary tmp = MeasurementUtils.parseMetricToken(token);
                metrics.add(tmp);
            }
            metrics = reloadMetrics(request, metrics, true);
            storeMetricsInSession(request, metrics, ivf);

            return mapping.findForward(RetCodeConstants.SUCCESS_URL);

        } catch (MeasurementViewException mve) {
            // expected the first time the user is viewing this particular charts context
            if (log.isDebugEnabled())
                log.debug("Error getting charts: " + mve.getMessage());
        }

        // TODO twist the logic around. First try to get the metrics from the preferences
        // and act accordingly with loading the MetricDisplaySummary s and only if that
        // fails, use the defaults.
        // The load from prefs / session and then get the MetricDisplaySummary stuff 
        // can be reused for refresh()

        if (context.category == EntityContext.Category.Resource) {
            metrics = chartsManager.getMetricDisplaySummariesForResource(user.getSubject(), context.resourceId,
                viewName);
            for (MetricDisplaySummary summary : metrics) {
                summary.setMetricToken(getContextKeyChart(summary));
            }
        } else if (context.category == EntityContext.Category.ResourceGroup) {
            metrics = chartsManager.getMetricDisplaySummariesForCompatibleGroup(user.getSubject(), context.groupId,
                viewName);
            // loop over the metrics, put the groupId in and format the provided value
            for (MetricDisplaySummary summary : metrics) {
                summary.setMetricToken(getContextKeyChart(summary));
                MonitorUtils.formatSimpleMetrics(summary, null);
            }

            request.setAttribute(AttrConstants.CHART_DATA_KEYS, metrics); // for the big charts and DashCharts.jsp
        } else if (context.category == EntityContext.Category.AutoGroup) {
            metrics = chartsManager.getMetricDisplaySummariesForAutoGroup(user.getSubject(), context.parentResourceId,
                context.resourceTypeId, viewName);
            for (MetricDisplaySummary summary : metrics) {
                summary.setMetricToken(getContextKeyChart(summary));
                MonitorUtils.formatSimpleMetrics(summary, null);
            }
            request.setAttribute(AttrConstants.CHART_DATA_KEYS, metrics);
        }
        // Set the metrics in the session and preferences
        storeMetricsInSession(request, metrics, ivf);

        return mapping.findForward(RetCodeConstants.SUCCESS_URL);
    }

    /**
     * Reload the passed metrics from the backend. The metrics need to be "preinitialized", which
     * means for each metric needs the identifiers (resource id, definition, group id, etc.) be set.
     * 
     * @param request http servlet request needed to get the time range preferences for the user. 
     * @param metrics The List of metrics to reload
     * @param force If true, always go to the backend, even if the time range preferences show a range in the past.
     * @return the refreshed list of metrics.
     * @todo Implement the timerange check 
     */
    private List<MetricDisplaySummary> reloadMetrics(HttpServletRequest request, List<MetricDisplaySummary> metrics,
        boolean force) {
        HttpSession session = request.getSession();
        WebUser user = SessionUtils.getWebUser(session);
        MeasurementPreferences preferences = user.getMeasurementPreferences();
        MetricRangePreferences rangePreferences = preferences.getMetricRangePreferences();
        long begin = rangePreferences.begin;
        long end = rangePreferences.end;

        // TODO: if the user selected a fixed time range and not "last xxx" and force == false, then 
        //       we should not go to the backend

        List<MetricDisplaySummary> ret = new ArrayList<MetricDisplaySummary>(metrics.size());
        Locale userLocale = request.getLocale();
        for (MetricDisplaySummary sum : metrics) {
            MetricsDisplayMode mode = getDisplayModeForSummary(sum);

            List<MetricDisplaySummary> tmpList;
            MetricDisplaySummary tmp;
            switch (mode) {
            case RESOURCE:
                tmpList = chartsManager.getMetricDisplaySummariesForResource(user.getSubject(), sum.getResourceId(),
                    new int[] { sum.getScheduleId() }, begin, end);
                break;
            case AUTOGROUP:
                tmpList = chartsManager.getMetricDisplaySummariesForAutoGroup(user.getSubject(), sum.getParentId(), sum
                    .getChildTypeId(), new int[] { sum.getDefinitionId() }, begin, end, false);
                break;
            case COMPGROUP:
                tmpList = chartsManager.getMetricDisplaySummariesForCompatibleGroup(user.getSubject(),
                    sum.getGroupId(), new int[] { sum.getDefinitionId() }, begin, end, false);
                break;
            default:
                tmpList = null;
            }
            if (tmpList != null && tmpList.size() > 0) {
                tmp = tmpList.get(0);
                tmp.setMetricToken(getContextKeyChart(tmp));
                if (tmp.getMetricKeys().length > 0)
                    MonitorUtils.formatSimpleMetrics(tmp, userLocale);
                ret.add(tmp);
            } else if (log.isDebugEnabled())
                log.debug("We did not get a result back for " + sum);
        }

        return ret;
    }

    /**
     * A refresh() event coming in from the JSP layer. Is also called at the end of add() to
     * get the actual data from the backend of the newly added metric.
     */
    public ActionForward refresh(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;

        // Look up the metrics from the session
        List<MetricDisplaySummary> metrics = retrieveMetricsFromSession(request, ivf);

        // refresh the metrics
        metrics = reloadMetrics(request, metrics, false);

        // Now store the metrics back
        storeMetricsInSession(request, metrics, ivf);

        return mapping.findForward(RetCodeConstants.SUCCESS_URL);
    }

    /**
     * Add a metric encoded in the form to the list of indicator charts to display.
     */
    public ActionForward add(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;

        // Look up the metrics from the session
        List<MetricDisplaySummary> metrics = retrieveMetricsFromSession(request, ivf);

        if (metrics == null) {
            return mapping.findForward(RetCodeConstants.FAILURE_URL);
        }

        // Now look up the metric that we have to add and parse it
        String newMetric = ivf.getAddMetric();
        MetricDisplaySummary newSummary = MeasurementUtils.parseMetricToken(newMetric);

        // Get the Metric Display summary , taking the display mode into account
        MetricsDisplayMode mode = getDisplayModeForSummary(newSummary); //WebUtility.getMetricsDisplayMode(request);

        // First check if the metric to add is already present
        boolean found = false;
        for (MetricDisplaySummary metric : metrics) {
            Integer definitionId = metric.getDefinitionId();
            switch (mode) {
            case RESOURCE:
                Integer schedId = metric.getScheduleId();
                if (schedId != null && schedId.equals(newSummary.getScheduleId())) {
                    found = true;
                    break;
                }
                break;
            case COMPGROUP:
                if (metric.getGroupId() == newSummary.getGroupId() && definitionId == newSummary.getDefinitionId()) {
                    found = true;
                    break;
                }
                break;
            case AUTOGROUP:
                int parent = metric.getParentId();
                int type = metric.getChildTypeId();
                if (parent == newSummary.getParentId() && type == newSummary.getChildTypeId()
                    && definitionId == newSummary.getDefinitionId()) {
                    found = true;
                    break;
                }
                break;
            default:
                throw new IllegalArgumentException(mode + " not valid here");

            }
        }

        // Add the new metrics
        if (!found) {
            WebUser user = SessionUtils.getWebUser(request.getSession());
            MeasurementPreferences preferences = user.getMeasurementPreferences();
            MetricRangePreferences rangePreferences = preferences.getMetricRangePreferences();
            long begin = rangePreferences.begin;
            long end = rangePreferences.end;

            int[] measurementDefinitionIds = new int[1];
            List<MetricDisplaySummary> newSummaries = new ArrayList<MetricDisplaySummary>();
            ;
            switch (mode) {
            case RESOURCE:
                newSummaries = chartsManager.getMetricDisplaySummariesForResource(user.getSubject(), newSummary
                    .getResourceId(), new int[] { newSummary.getScheduleId() }, begin, end);
                metrics.addAll(newSummaries);
                // Now store the metrics back
                storeMetricsInSession(request, metrics, ivf);
                break;
            case COMPGROUP:
                // Get MetricDisplaySummaries from the backend for the new metrics and add them
                measurementDefinitionIds[0] = newSummary.getDefinitionId();

                newSummaries = chartsManager.getMetricDisplaySummariesForCompatibleGroup(user.getSubject(), newSummary
                    .getGroupId(), measurementDefinitionIds, begin, end, false);
                metrics.addAll(newSummaries);

                // Set the metrics in the session
                storeMetricsInSession(request, metrics, ivf);
                break;
            case AUTOGROUP:
                // Get MetricDisplaySummaries from the backend for the new metrics and add them
                measurementDefinitionIds[0] = newSummary.getDefinitionId();
                newSummaries = chartsManager.getMetricDisplaySummariesForAutoGroup(user.getSubject(), newSummary
                    .getParentId(), newSummary.getChildTypeId(), measurementDefinitionIds, begin, end, false);
                metrics.addAll(newSummaries);
                // Set the metrics in the session
                storeMetricsInSession(request, metrics, ivf);
                break;
            default:
                throw new IllegalArgumentException(mode + " not valid here");
            }
        }

        // trigger an immediate refresh 
        // return mapping.findForward(RetCodeConstants.SUCCESS_URL);
        return refresh(mapping, form, request, response);
    }

    public ActionForward remove(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;

        Subject subject = WebUtility.getSubject(request);
        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());
        viewManager.removeChart(subject, context, ivf.getView(), ivf.getMetric()[0]);

        List<MetricDisplaySummary> metrics = retrieveMetricsFromSession(request, ivf);
        // Now store the metrics back
        storeMetricsInSession(request, metrics, ivf);

        return mapping.findForward(RetCodeConstants.AJAX_URL);
    }

    public ActionForward moveUp(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;

        Subject subject = WebUtility.getSubject(request);
        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());
        viewManager.moveChartUp(subject, context, ivf.getView(), ivf.getMetric()[0]);

        List<MetricDisplaySummary> metrics = retrieveMetricsFromSession(request, ivf);
        // Now store the metrics back
        storeMetricsInSession(request, metrics, ivf);

        return mapping.findForward(RetCodeConstants.AJAX_URL);
    }

    public ActionForward moveDown(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;

        Subject subject = WebUtility.getSubject(request);
        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());
        viewManager.moveChartDown(subject, context, ivf.getView(), ivf.getMetric()[0]);

        List<MetricDisplaySummary> metrics = retrieveMetricsFromSession(request, ivf);
        // Now store the metrics back
        storeMetricsInSession(request, metrics, ivf);

        return mapping.findForward(RetCodeConstants.AJAX_URL);
    }

    public ActionForward go(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        return mapping.findForward(KeyConstants.MODE_MON_CUR);
    }

    //this used to be in StringUtil but was only used here.
    //we should probably handle all user input cases the same,
    //escapeHTML if needed before save, unescapeHTML after retrieving.
    /**
     * Find characters having special meaning <em>inside</em> HTML tags and URLs.
     * <p/>
     * <p/>
     * The special characters are : <ul> <li>< <li>> <li>" <li>' <li>\ <li>& <li>| <li>? </ul>
     * <p/>
     * <p/>
     */
    private static int indexOfSpecialChars(String aTagFragment) {
        final StringCharacterIterator iterator = new StringCharacterIterator(aTagFragment);

        int i = 0;
        for (char character = iterator.current(); character != CharacterIterator.DONE; character = iterator.next(), i++) {
            switch (character) {
            case '<':
            case '>':
            case '\"':
            case '\'':
            case '\\':
            case '&':
            case '|':
            case '?':
                return i;
            default:
                break;
            }
        }
        return -1;
    }

    /**
     * Creates a new view with the passed metrics and the passed name.
     * If a view with the new name exist, an error is reported.
     * @param mapping
     * @param form
     * @param request
     * @param response
     * @return
     * @throws Exception
     */
    public ActionForward create(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;
        WebUser user = SessionUtils.getWebUser(request.getSession());
        MeasurementPreferences preferences = user.getMeasurementPreferences();

        // A couple of checks
        String newViewName = ivf.getView();
        if (newViewName.length() == 0) {
            RequestUtils.setError(request, "resource.common.monitor.visibility.view.error.empty");
            return mapping.findForward(KeyConstants.MODE_MON_CUR);
        }

        if (indexOfSpecialChars(newViewName) > -1) {
            RequestUtils.setError(request, "error.input.badchars");
            return mapping.findForward(KeyConstants.MODE_MON_CUR);
        }

        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());

        try {
            // Make sure that we're not duplicating names
            viewManager.createView(user.getSubject(), context, newViewName);
        } catch (MeasurementViewException mve) {
            RequestUtils.setError(request, "resource.common.monitor.visibility.view.error.exists");
            return mapping.findForward(KeyConstants.MODE_MON_CUR);
        }

        List<String> viewNames = viewManager.getViewNames(user.getSubject(), context);
        ivf.setViews(viewNames.toArray(new String[viewNames.size()]));

        // Call update to save the metrics to be viewed
        return update(mapping, ivf, request, response);
    }

    public ActionForward update(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        refresh(mapping, form, request, response);

        return mapping.findForward(KeyConstants.MODE_MON_CUR);
    }

    /**
     * Stores the metrics in the user preferences so that they
     * survive a logout.
     */
    private void storeMetricsInUserPreferences(HttpServletRequest request, List<MetricDisplaySummary> metrics,
        IndicatorViewsForm ivf) throws SessionNotFoundException, SessionTimeoutException, ServletException {

        MetricViewData data = new MetricViewData();
        data.charts = new ArrayList<String>();
        for (MetricDisplaySummary mds : metrics) {
            String chart = getContextKeyChart(mds);
            data.charts.add(chart);
        }

        // Set the user preferences now
        Subject subject = WebUtility.getSubject(request);

        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());
        viewManager.saveCharts(subject, context, ivf.getView(), data.charts);
    }

    public ActionForward delete(ActionMapping mapping, ActionForm form, HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        IndicatorViewsForm ivf = (IndicatorViewsForm) form;
        Subject subject = WebUtility.getSubject(request);

        String doomedView = ivf.getUpdate();
        EntityContext context = new EntityContext(ivf.getId(), ivf.getGroupId(), ivf.getParent(), ivf.getCtype());
        viewManager.deleteView(subject, context, doomedView);

        return mapping.findForward(KeyConstants.MODE_MON_CUR);
    }

    /**
     * Determine if the passed summary represents single resource, a compatible
     * group or an autogroup. 
     * @param summary a {@link MetricDisplaySummary} to check
     * @return the appropriate {@link MetricsDisplayMode} or UNSET if undeterminable.
     */
    private MetricsDisplayMode getDisplayModeForSummary(MetricDisplaySummary summary) {
        if (summary.getResourceId() > 0)
            return MetricsDisplayMode.RESOURCE;
        else if (summary.getGroupId() > 0)
            return MetricsDisplayMode.COMPGROUP;
        else if (summary.getParentId() > 0 && summary.getChildTypeId() > 0)
            return MetricsDisplayMode.AUTOGROUP;
        else {
            log.debug("Mode could not be determined for " + summary);
            return MetricsDisplayMode.UNSET;
        }
    }

    private static int getChildTypeId(HttpServletRequest request) {
        int type = WebUtility.getOptionalIntRequestParameter(request, ParamConstants.RESOURCE_TYPE_ID_PARAM, -1);
        if (type == -1) // TODO JBNADM-2630
            type = WebUtility.getRequiredIntRequestParameter(request, ParamConstants.CHILD_RESOURCE_TYPE_ID_PARAM);
        return type;
    }
}
