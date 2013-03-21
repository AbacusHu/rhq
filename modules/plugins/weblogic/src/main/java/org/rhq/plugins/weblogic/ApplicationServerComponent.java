package org.rhq.plugins.weblogic;

import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.sigar.ProcCpu;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationContext;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.core.system.ProcessInfo;

public class ApplicationServerComponent<T extends ResourceComponent<?>> implements ResourceComponent<T>,
    MeasurementFacet, OperationFacet {
    private final Log log = LogFactory.getLog(this.getClass());

    ResourceContext<?> resourceContext;

    /**
     * Return availability of this resource
     *  @see org.rhq.core.pluginapi.inventory.ResourceComponent#getAvailability()
     */
    public AvailabilityType getAvailability() {
        ProcessInfo process = resourceContext.getNativeProcess();
        if (process.freshSnapshot().isRunning()) {
            return AvailabilityType.UP;
        } else {
            return AvailabilityType.DOWN;
        }

    }

    /**
     * Start the resource connection
     * @see org.rhq.core.pluginapi.inventory.ResourceComponent#start(org.rhq.core.pluginapi.inventory.ResourceContext)
     */
    public void start(ResourceContext context) throws InvalidPluginConfigurationException, Exception {
        this.resourceContext = context;
    }

    /**
     * Tear down the resource connection
     * @see org.rhq.core.pluginapi.inventory.ResourceComponent#stop()
     */
    public void stop() {

    }

    /**
     * Gather measurement data
     *  @see org.rhq.core.pluginapi.measurement.MeasurementFacet#getValues(org.rhq.core.domain.measurement.MeasurementReport, java.util.Set)
     */
    public void getValues(MeasurementReport report, Set<MeasurementScheduleRequest> metrics) throws Exception {
        log.info("collect measure report");

        for (MeasurementScheduleRequest req : metrics) {
            if (req.getName().equals("cpuPercentage")) {
                ProcessInfo process = resourceContext.getNativeProcess();
                ProcCpu cpu = process.getCpu();
                Double value = cpu.getPercent();
                MeasurementDataNumeric res = new MeasurementDataNumeric(req, value);
                report.addData(res);
            }
        }
    }

    public void startOperationFacet(OperationContext context) {

    }

    /**
     * Invokes the passed operation on the managed resource
     * @param name Name of the operation
     * @param params The method parameters
     * @return An operation result
     * @see org.rhq.core.pluginapi.operation.OperationFacet
     */
    public OperationResult invokeOperation(String name, Configuration params) throws Exception {

        OperationResult res = new OperationResult();
        if ("dummyOperation".equals(name)) {
            // TODO implement me

        }
        return res;
    }

}
