/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
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
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package org.rhq.common.jbossas.client.controller;

import org.jboss.as.controller.client.ModelControllerClient;

/**
 * Helper class to get a ModelContollerClient for the RHQ default settings.
 * This follows the methods in the Installer, but assumes defaults.
 * @author Heiko W. Rupp
 */
public class MCCHelper {


    public static ModelControllerClient getModelControllerClient() {
        return getModelControllerClient("localhost",6999);
    }

    public static ModelControllerClient getModelControllerClient(String host,int port) {
        ModelControllerClient client;
        try {
            if (host==null || host.isEmpty())
                host = "localhost";

            if (port <= 0)
                port = 6999; // Default for RHQ TODO obtain from rhq-server.properties, jboss.management.native.port=6999 ?
            client = ModelControllerClient.Factory.create(host, port);
        } catch (Exception e) {
            throw new RuntimeException("Cannot obtain client connection to the app server", e);
        }
        return client;
    }

}
