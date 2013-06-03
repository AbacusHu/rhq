/*
 *
 *  * RHQ Management Platform
 *  * Copyright (C) 2005-2012 Red Hat, Inc.
 *  * All rights reserved.
 *  *
 *  * This program is free software; you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License, version 2, as
 *  * published by the Free Software Foundation, and/or the GNU Lesser
 *  * General Public License, version 2.1, also as published by the Free
 *  * Software Foundation.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License and the GNU Lesser General Public License
 *  * for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * and the GNU Lesser General Public License along with this program;
 *  * if not, write to the Free Software Foundation, Inc.,
 *  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

package org.rhq.enterprise.server.measurement;

import static java.util.Arrays.asList;
import static org.joda.time.DateTime.now;
import static org.rhq.core.domain.measurement.DataType.MEASUREMENT;
import static org.rhq.core.domain.measurement.NumericType.DYNAMIC;
import static org.rhq.core.domain.resource.ResourceCategory.SERVER;
import static org.rhq.test.AssertUtils.assertCollectionEqualsNoOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.criteria.ResourceCriteria;
import org.rhq.core.domain.measurement.MeasurementBaseline;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.measurement.MeasurementOOB;
import org.rhq.core.domain.measurement.MeasurementSchedule;
import org.rhq.core.domain.resource.Agent;
import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.core.util.jdbc.JDBCUtil;
import org.rhq.enterprise.server.drift.DriftServerPluginService;
import org.rhq.enterprise.server.measurement.util.MeasurementDataManagerUtility;
import org.rhq.enterprise.server.resource.ResourceManagerLocal;
import org.rhq.enterprise.server.test.AbstractEJB3Test;
import org.rhq.enterprise.server.test.TransactionCallback;
import org.rhq.enterprise.server.test.TransactionCallbackReturnable;
import org.rhq.enterprise.server.util.Overlord;
import org.rhq.enterprise.server.util.ResourceTreeHelper;

/**
 * @author John Sanda
 */
public class MeasurementOOBManagerBeanTest extends AbstractEJB3Test {

    private final String RESOURCE_TYPE = getClass().getName() + "_TYPE";

    private final String PLUGIN = getClass().getName() + "_PLUGIN";

    private final String AGENT_NAME = getClass().getName() + "_AGENT";

    private final String DYNAMIC_DEF_NAME = getClass().getName() + "_DYNAMIC";

    private final String RESOURCE_KEY = getClass().getName() + "_RESOURCE_KEY";

    private final String RESOURCE_NAME = getClass().getName() + "_NAME";

    private final String RESOURCE_UUID = getClass().getSimpleName() + "_UUID";

    private ResourceType resourceType;

    private Agent agent;

    private Resource resource;

    private List<MeasurementDefinition> measurementDefs;

    private List<MeasurementSchedule> schedules;

    @Inject @Overlord
    private Subject overlord;

    @EJB
    private ResourceManagerLocal resourceManager;

    @EJB
    private MeasurementOOBManagerLocal oobManager;

    @EJB
    private MeasurementBaselineManagerLocal baselineManager;

    @Override
    protected void beforeMethod() throws Exception {
        // MeasurementDataManagerUtility looks up config settings from SystemManagerBean.
        // SystemManagerBean.getDriftServerPluginManager method requires drift server plugin.
        DriftServerPluginService driftServerPluginService = new DriftServerPluginService(getTempDir());
        prepareCustomServerPluginService(driftServerPluginService);
        driftServerPluginService.masterConfig.getPluginDirectory().mkdirs();

        measurementDefs = new ArrayList<MeasurementDefinition>();
        schedules = new ArrayList<MeasurementSchedule>();
        createInventory();
    }

    @Override
    protected void afterMethod() throws Exception {
        purgeDB();
    }

    /**
     * Verifies that OOBs are calculated when there are both upper and lower bound
     * violations. It also verifies that no OOB is generated a schedule whose values stay
     * in bounds.
     */
    @Test
    public void calculateOOBs() {
        final MeasurementSchedule schedule1 = createSchedule();
        final MeasurementSchedule schedule2 = createSchedule();
        MeasurementSchedule schedule3 = createSchedule();

        DateTime currentHour = now().hourOfDay().roundFloorCopy();
        final DateTime lastHour = currentHour.minusHours(1);

        insertBaselines(asList(
            baseline(schedule1, 4.34, 5.2, 3.9),
            baseline(schedule2, 7.43, 7.49, 7.38),
            baseline(schedule3, 3.2, 3.6, 2.95)
        ));

        insert1HourData(asList(
            // schedule1 should be out of bounds on the lower bound
            new AggregateTestData(lastHour.getMillis(), schedule1.getId(), 3.8, 4.6, 2.11),
            // schedule2 should be out of bounds on the upper bound
            new AggregateTestData(lastHour.getMillis(), schedule2.getId(), 9.492, 9.53, 9.481),
            // schedule3 should be in bounds
            new AggregateTestData(lastHour.getMillis(), schedule3.getId(), 3.15, 3.59, 2.96)
        ));

        oobManager.computeOOBsFromLastHour(overlord);

        executeInTransaction(new TransactionCallback() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute() throws Exception {
                EntityManager em = getEntityManager();
                List<MeasurementOOB> oobs = em.createQuery("select oob from MeasurementOOB oob").getResultList();
                List<TestMeasurementOOB> actual = new ArrayList<TestMeasurementOOB>();
                for (MeasurementOOB oob : oobs) {
                    actual.add(new TestMeasurementOOB(oob));
                }

                List<TestMeasurementOOB> expected = asList(
                    new TestMeasurementOOB(schedule1.getId(), lastHour.getMillis(), 138),
                    new TestMeasurementOOB(schedule2.getId(), lastHour.getMillis(), 1855)
                );

                assertCollectionEqualsNoOrder(expected, actual, "The OOBs do not match");
            }
        });
    }

    private void createInventory() throws Exception {
        purgeDB();
        executeInTransaction(false, new TransactionCallback() {
            @Override
            public void execute() throws Exception {

                resourceType = new ResourceType(RESOURCE_TYPE, PLUGIN, SERVER, null);
                em.persist(resourceType);

                agent = new Agent(AGENT_NAME, "localhost", 9999, "", "randomToken");
                em.persist(agent);

                resource = new Resource(RESOURCE_KEY, RESOURCE_NAME, resourceType);
                resource.setUuid(RESOURCE_UUID);
                resource.setAgent(agent);

                em.persist(resource);
            }
        });
    }

    private void purgeDB() {
        purgeRawData();
        purge1HourData();
        purge6HourData();
        purge24HourData();
        purgeBaselines();
        purgeOOBs();

        executeInTransaction(false, new TransactionCallback() {
            @Override
            public void execute() throws Exception {
                ResourceCriteria c = new ResourceCriteria();
                c.addFilterInventoryStatus(null);
                c.addFilterResourceKey(RESOURCE_KEY);
                c.fetchSchedules(true);
                List<Resource> r = resourceManager.findResourcesByCriteria(overlord, c);

                // Note that the order of deletes is important due to FK
                // constraints.
                if (!r.isEmpty()) {
                    assertTrue("Should be only 1 resource", r.size() == 1);
                    Resource doomedResource = r.get(0);
                    deleteMeasurementSchedules();
                    deleteResource(doomedResource);
                }
                deleteAgent();
                deleteDynamicMeasurementDef();
                deleteResourceType();
            }
        });
    }

    private void deleteDynamicMeasurementDef() {
        if (!measurementDefs.isEmpty()) {
            em.createQuery("delete from MeasurementDefinition d where d in :defs")
                .setParameter("defs", measurementDefs)
                .executeUpdate();
        }
    }

    private void deleteAgent() {
        em.createQuery("delete from Agent where name = :name").setParameter("name", AGENT_NAME).executeUpdate();
    }

    private void deleteResourceType() {
        em.createQuery("delete from ResourceType where name = :name and plugin = :plugin")
            .setParameter("name", RESOURCE_TYPE).setParameter("plugin", PLUGIN).executeUpdate();
    }

    private void deleteResource(Resource doomedResource) {
        ResourceTreeHelper.deleteResource(em, doomedResource);
        em.flush();
    }

    private void deleteMeasurementSchedules() {
        for (MeasurementSchedule schedule : schedules) {
            em.createQuery("delete from MeasurementSchedule where id = :id").setParameter("id", schedule.getId())
                .executeUpdate();
        }
        em.flush();
    }

    public void purgeRawData() {
        purgeTables(MeasurementDataManagerUtility.getAllRawTables());
    }

    public void purge1HourData() {
        purgeTables("RHQ_MEASUREMENT_DATA_NUM_1H");
    }

    public void purge6HourData() {
        purgeTables("RHQ_MEASUREMENT_DATA_NUM_6H");
    }

    public void purge24HourData() {
        purgeTables("RHQ_MEASUREMENT_DATA_NUM_1D");
    }

    public void purgeBaselines() {
        purgeTables("RHQ_MEASUREMENT_BLINE");
    }

    public void purgeOOBs() {
        purgeTables("RHQ_MEASUREMENT_OOB");
    }

    private void purgeTables(String... tables) {
        // This method was previous implemented using EntityManager.createNativeQuery
        // and called from within a TransactionCallback. It was causing a
        // TransactionRequiredException, and I am not clear why. I suspect it is a
        // configuration issue in our testing environment, but I haven't figured it out
        // yet. For now,  raw tables are purges in their own separate JDBC transaction.
        //
        // jsanda
        Connection connection = null;

        try {
            connection = getConnection();
            connection.setAutoCommit(false);
            for (String table : tables) {
                Statement statement = connection.createStatement();
                try {
                    statement.execute("delete from " + table);
                } finally {
                    JDBCUtil.safeClose(statement);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                throw new RuntimeException("Failed to rollback transaction", e1);
            }
            throw new RuntimeException("Failed to purge data from " + tables, e);
        } finally {
            JDBCUtil.safeClose(connection);
        }
    }

    private void insert1HourData(List<AggregateTestData> data) {
        Connection connection = null;

        try {
            connection = getConnection();
            connection.setAutoCommit(false);
            String sql = "insert into RHQ_MEASUREMENT_DATA_NUM_1H(time_stamp, schedule_id, value, min_value, max_value) values(?, ?, ?, ?, ?)";
            PreparedStatement statement = connection.prepareStatement(sql);

            for (AggregateTestData datum : data) {
                statement.setLong(1, datum.getTimestamp());
                statement.setInt(2, datum.getScheduleId());
                statement.setDouble(3, datum.getAvg());
                statement.setDouble(4, datum.getMin());
                statement.setDouble(5, datum.getMax());

                statement.addBatch();
            }

            statement.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e1) {
                throw new RuntimeException("Failed to rollback transaction", e1);
            }
            throw new RuntimeException("Failed to insert 1 hour data", e);
        } finally {
            JDBCUtil.safeClose(connection);
        }
    }

    private void insertBaselines(final List<MeasurementBaseline> baselines) {
        executeInTransaction(false, new TransactionCallback() {
            @Override
            public void execute() throws Exception {
                EntityManager em = getEntityManager();
                for (MeasurementBaseline baseline : baselines) {
                    em.persist(baseline);
                }
            }
        });
    }

    private MeasurementBaseline baseline(MeasurementSchedule schedule, double mean, double max, double min) {
        MeasurementBaseline baseline = new MeasurementBaseline();
        baseline.setSchedule(schedule);
        baseline.setMean(mean);
        baseline.setMax(max);
        baseline.setMin(min);

        return baseline;
    }

    private MeasurementSchedule createSchedule() {
        return executeInTransaction(false, new TransactionCallbackReturnable<MeasurementSchedule>() {
            @Override
            public MeasurementSchedule execute() throws Exception {
                EntityManager em = getEntityManager();

                MeasurementDefinition definition = new MeasurementDefinition(resourceType, DYNAMIC_DEF_NAME +
                    measurementDefs.size());
                definition.setDefaultOn(true);
                definition.setDataType(MEASUREMENT);
                definition.setMeasurementType(DYNAMIC);
                em.persist(definition);

                MeasurementSchedule schedule = new MeasurementSchedule(definition, resource);
                schedule.setEnabled(true);
                resource.addSchedule(schedule);
                em.persist(schedule);

                schedules.add(schedule);
                measurementDefs.add(definition);

                return schedule;
            }
        });
    }

}
