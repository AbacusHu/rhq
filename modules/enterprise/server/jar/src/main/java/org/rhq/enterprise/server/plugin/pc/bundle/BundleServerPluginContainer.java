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
package org.rhq.enterprise.server.plugin.pc.bundle;

import org.rhq.enterprise.server.plugin.pc.AbstractTypeServerPluginContainer;
import org.rhq.enterprise.server.plugin.pc.MasterServerPluginContainer;
import org.rhq.enterprise.server.plugin.pc.ServerPluginManager;
import org.rhq.enterprise.server.plugin.pc.ServerPluginType;
import org.rhq.enterprise.server.xmlschema.generated.serverplugin.bundle.BundlePluginDescriptorType;

/**
 * The container responsible for managing the lifecycle of bundle server-side plugins.
 *
 * @author John Mazzitelli
 */
public class BundleServerPluginContainer extends AbstractTypeServerPluginContainer {

    public BundleServerPluginContainer(MasterServerPluginContainer master) {
        super(master);
    }

    @Override
    public ServerPluginType getSupportedServerPluginType() {
        return new ServerPluginType(BundlePluginDescriptorType.class);
    }

    @Override
    protected ServerPluginManager createPluginManager() {
        return new BundleServerPluginManager(this);
    }

    public BundleServerPluginManager getBundleServerPluginManager() {
        return (BundleServerPluginManager) getPluginManager();
    }

}