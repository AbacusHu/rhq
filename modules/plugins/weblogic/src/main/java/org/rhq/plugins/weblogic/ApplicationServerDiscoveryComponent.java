package org.rhq.plugins.weblogic;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.rhq.core.pluginapi.inventory.DiscoveredResourceDetails;
import org.rhq.core.pluginapi.inventory.ProcessScanResult;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryComponent;
import org.rhq.core.pluginapi.inventory.ResourceDiscoveryContext;
import org.rhq.core.system.ProcessInfo;

/**
 * Discovery class
 */
public class ApplicationServerDiscoveryComponent implements ResourceDiscoveryComponent<ResourceComponent<?>>

{

    private final Log log = LogFactory.getLog(this.getClass());

    /**
     * Run the auto-discovery
     */
    public Set<DiscoveredResourceDetails> discoverResources(ResourceDiscoveryContext<ResourceComponent<?>> context)
        throws Exception {
        Set<DiscoveredResourceDetails> discoveredResources = new HashSet<DiscoveredResourceDetails>();
        String hostAddress = InetAddress.getLocalHost().getHostAddress();
        for (ProcessScanResult process : context.getAutoDiscoveredProcesses()) {
            ProcessInfo processInfo = process.getProcessInfo();
            String[] commandLine = processInfo.getCommandLine();

            log.info("Examining process " + processInfo.getPid());
            log.info("Command line: " + Arrays.asList(commandLine));

            for (String command : commandLine) {
                if (command.contains("-Dweblogic.Name=")) {
                    int index = command.indexOf("=");
                    String serverName = command.substring(index + 1).trim();
                    discoveredResources.add(new DiscoveredResourceDetails(context.getResourceType(), hostAddress + "_"
                        + serverName, hostAddress + "_" + serverName, null, "Weblogic application server", null,
                        processInfo));
                    break;
                }
            }
        }
        return discoveredResources;
    }
}