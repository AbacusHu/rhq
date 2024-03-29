/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
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
package org.rhq.enterprise.gui.coregui.client.test;

import java.util.ArrayList;
import java.util.List;

import com.smartgwt.client.widgets.Canvas;
import com.smartgwt.client.widgets.Label;

import org.rhq.enterprise.gui.coregui.client.components.FullHTMLPane;
import org.rhq.enterprise.gui.coregui.client.components.TitleBar;
import org.rhq.enterprise.gui.coregui.client.components.view.AbstractSectionedLeftNavigationView;
import org.rhq.enterprise.gui.coregui.client.components.view.NavigationItem;
import org.rhq.enterprise.gui.coregui.client.components.view.NavigationSection;
import org.rhq.enterprise.gui.coregui.client.components.view.ViewFactory;
import org.rhq.enterprise.gui.coregui.client.components.view.ViewName;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.selection.ResourceSelector;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeTreeView;
import org.rhq.enterprise.gui.coregui.client.test.configuration.TestConfigurationView;
import org.rhq.enterprise.gui.coregui.client.test.configuration.TestGroupConfigurationView;
import org.rhq.enterprise.gui.coregui.client.test.configuration.TestReadOnlyConfigurationView;
import org.rhq.enterprise.gui.coregui.client.test.configuration.TestReadOnlyGroupConfigurationView;
import org.rhq.enterprise.gui.coregui.client.test.inventory.TestSearchBarView;
import org.rhq.enterprise.gui.coregui.client.util.enhanced.EnhancedVLayout;

/**
 * The Test top-level view. This view is "hidden", i.e. there are no links to it, so the user must go to the URL
 * directly using their browser.
 *
 * @author Ian Springer
 */
public class TestTopView extends AbstractSectionedLeftNavigationView {

    public static final ViewName VIEW_ID = new ViewName("Test");

    // view IDs for Inventory section
    private static final ViewName INVENTORY_SECTION_VIEW_ID = new ViewName("Inventory");
    private static final ViewName PAGE_RESOURCE_SELECTOR = new ViewName("ResourceSelector");
    private static final ViewName PAGE_TYPE_TREE = new ViewName("TypeTree");
    private static final ViewName PAGE_SEARCH_BAR = new ViewName("SearchBar");

    // view IDs for Configuration section
    private static final ViewName CONFIGURATION_SECTION_VIEW_ID = new ViewName("Configuration");
    private static final ViewName PAGE_CONFIG_EDITOR = new ViewName("ConfigEditor");
    private static final ViewName PAGE_READONLY_CONFIG_EDITOR = new ViewName("ReadOnlyConfigEditor");
    private static final ViewName PAGE_GROUP_CONFIG_EDITOR = new ViewName("GroupConfigEditor");
    private static final ViewName PAGE_READONLY_GROUP_CONFIG_EDITOR = new ViewName("ReadOnlyGroupConfigEditor");

    // view IDs for Server Access section
    private static final ViewName SERVERACCESS_SECTION_VIEW_ID = new ViewName("ServerAccess");
    private static final ViewName PAGE_REMOTE_SERVICE_STATISTICS = new ViewName("RemoteServiceStatistics");
    private static final ViewName PAGE_SQL = new ViewName("SQL");
    private static final ViewName PAGE_HIBERNATE = new ViewName("Hibernate");
    private static final ViewName PAGE_ENTITY_BROWSER = new ViewName("EntityBrowser");
    private static final ViewName PAGE_ADMIN_CONTROL = new ViewName("AdminControl");
    private static final ViewName PAGE_EMAIL = new ViewName("EmailTest");
    private static final ViewName PAGE_AGENT = new ViewName("AgentConnectivityTest");
    private static final ViewName PAGE_USER_PREFERENCES = new ViewName("UserPreferences");

    // view IDs for Misc section
    private static final ViewName MISC_SECTION_VIEW_ID = new ViewName("Misc");
    private static final ViewName PAGE_MESSAGE_CENTER_TEST = new ViewName("MessageCenterTest");
    private static final ViewName PAGE_NUMBER_FORMAT_TEST = new ViewName("NumberFormatTest");

    public TestTopView() {
        super(VIEW_ID.getName());
    }

    protected Canvas defaultView() {
        EnhancedVLayout vLayout = new EnhancedVLayout();
        vLayout.setWidth100();

        // TODO: Help icon.
        TitleBar titleBar = new TitleBar(MSG.view_testTop_title());
        vLayout.addMember(titleBar);

        Label label = new Label(MSG.view_testTop_description());
        label.setPadding(10);
        vLayout.addMember(label);

        return vLayout;
    }

    @Override
    protected List<NavigationSection> getNavigationSections() {
        List<NavigationSection> sections = new ArrayList<NavigationSection>();

        NavigationSection inventorySection = buildInventorySection();
        sections.add(inventorySection);

        NavigationSection configurationSection = buildConfigurationSection();
        sections.add(configurationSection);

        NavigationSection serverAccessSection = buildServerAccessSection();
        sections.add(serverAccessSection);

        NavigationSection miscSection = buildMiscSection();
        sections.add(miscSection);

        return sections;
    }

    private NavigationSection buildInventorySection() {
        NavigationItem resourceSelectorItem = new NavigationItem(PAGE_RESOURCE_SELECTOR, null, new ViewFactory() {
            public Canvas createView() {
                return new ResourceSelector();
            }
        });

        NavigationItem typeTreeItem = new NavigationItem(PAGE_TYPE_TREE, null, new ViewFactory() {
            public Canvas createView() {
                return new ResourceTypeTreeView(true);
            }
        });

        NavigationItem searchBarItem = new NavigationItem(PAGE_SEARCH_BAR, null, new ViewFactory() {
            public Canvas createView() {
                return new TestSearchBarView();
            }
        });

        return new NavigationSection(INVENTORY_SECTION_VIEW_ID, resourceSelectorItem, typeTreeItem, searchBarItem);
    }

    private NavigationSection buildConfigurationSection() {
        NavigationItem configEditorItem = new NavigationItem(PAGE_CONFIG_EDITOR, null, new ViewFactory() {
            public Canvas createView() {
                return new TestConfigurationView();
            }
        });

        NavigationItem readOnlyConfigEditorItem = new NavigationItem(PAGE_READONLY_CONFIG_EDITOR, null,
            new ViewFactory() {
                public Canvas createView() {
                    return new TestReadOnlyConfigurationView();
                }
            });

        NavigationItem groupConfigEditorItem = new NavigationItem(PAGE_GROUP_CONFIG_EDITOR, null, new ViewFactory() {
            public Canvas createView() {
                return new TestGroupConfigurationView();
            }
        });

        NavigationItem readOnlyGroupConfigEditorItem = new NavigationItem(PAGE_READONLY_GROUP_CONFIG_EDITOR, null,
            new ViewFactory() {
                public Canvas createView() {
                    return new TestReadOnlyGroupConfigurationView();
                }
            });

        return new NavigationSection(CONFIGURATION_SECTION_VIEW_ID, configEditorItem, readOnlyConfigEditorItem,
            groupConfigEditorItem, readOnlyGroupConfigEditorItem);
    }

    private NavigationSection buildServerAccessSection() {
        NavigationItem userPrefsItem = new NavigationItem(PAGE_USER_PREFERENCES, null, new ViewFactory() {
            public Canvas createView() {
                return new TestUserPreferencesView();
            }
        });

        NavigationItem remoteServiceStatisticsItem = new NavigationItem(PAGE_REMOTE_SERVICE_STATISTICS, null,
            new ViewFactory() {
                public Canvas createView() {
                    return new TestRemoteServiceStatisticsView();
                }
            });

        NavigationItem sqlItem = new NavigationItem(PAGE_SQL, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/sql.jsp");
            }
        });

        NavigationItem hibernateItem = new NavigationItem(PAGE_HIBERNATE, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/hibernate.jsp");
            }
        });

        NavigationItem entityBrowserItem = new NavigationItem(PAGE_ENTITY_BROWSER, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/browser.jsp");
            }
        });

        NavigationItem adminControlItem = new NavigationItem(PAGE_ADMIN_CONTROL, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/control.jsp");
            }
        });

        NavigationItem emailItem = new NavigationItem(PAGE_EMAIL, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/email.jsp");
            }
        });

        NavigationItem agentItem = new NavigationItem(PAGE_AGENT, null, new ViewFactory() {
            public Canvas createView() {
                return new FullHTMLPane("/admin/test/agent.jsp");
            }
        });

        return new NavigationSection(SERVERACCESS_SECTION_VIEW_ID, remoteServiceStatisticsItem, sqlItem, hibernateItem,
            entityBrowserItem, adminControlItem, emailItem, agentItem, userPrefsItem);
    }

    private NavigationSection buildMiscSection() {
        NavigationItem messageCenterItem = new NavigationItem(PAGE_MESSAGE_CENTER_TEST, null, new ViewFactory() {
            public Canvas createView() {
                return new TestMessageCenterView();
            }
        });

        NavigationItem numberFormatItem = new NavigationItem(PAGE_NUMBER_FORMAT_TEST, null, new ViewFactory() {
            public Canvas createView() {
                return new TestNumberFormatView();
            }
        });

        return new NavigationSection(MISC_SECTION_VIEW_ID, messageCenterItem, numberFormatItem);
    }

}
