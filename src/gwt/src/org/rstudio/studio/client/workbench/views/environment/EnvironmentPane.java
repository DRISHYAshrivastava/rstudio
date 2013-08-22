/*
 * EnvironmentPane.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.workbench.views.environment;

import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.SuggestOracle;

import org.rstudio.core.client.DebugFilePosition;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.core.client.widget.SecondaryToolbar;
import org.rstudio.core.client.widget.Toolbar;
import org.rstudio.core.client.widget.ToolbarButton;
import org.rstudio.core.client.widget.ToolbarPopupMenu;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.icons.StandardIcons;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.ui.WorkbenchPane;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;
import org.rstudio.studio.client.workbench.views.environment.model.CallFrame;
import org.rstudio.studio.client.workbench.views.environment.model.EnvironmentServerOperations;
import org.rstudio.studio.client.workbench.views.environment.model.RObject;
import org.rstudio.studio.client.workbench.views.environment.view.EnvironmentObjects;
import org.rstudio.studio.client.workbench.views.packages.events.PackageStatusChangedEvent;
import org.rstudio.studio.client.workbench.views.packages.events.PackageStatusChangedHandler;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.ArrayList;

public class EnvironmentPane extends WorkbenchPane 
                             implements EnvironmentPresenter.Display,
                                        EnvironmentObjects.Observer,
                                        PackageStatusChangedHandler
{
   @Inject
   public EnvironmentPane(Commands commands,
                          EventBus eventBus,
                          GlobalDisplay globalDisplay,
                          EnvironmentServerOperations serverOperations,
                          Session session)
   {
      super("Environment");
      
      commands_ = commands;
      eventBus_ = eventBus;
      server_ = serverOperations;
      globalDisplay_ = globalDisplay;

      expandedObjects_ = new ArrayList<String>();
      scrollPosition_ = 0;
      isClientStateDirty_ = false;
      environments_ = 
            session.getSessionInfo().getEnvironmentState().environments();
      environmentName_ = 
            session.getSessionInfo().getEnvironmentState().environmentName();

      EnvironmentPaneResources.INSTANCE.environmentPaneStyle().ensureInjected();
      
      eventBus_.addHandler(PackageStatusChangedEvent.TYPE, this);

      ensureWidget();
   }

   // WorkbenchPane overrides -------------------------------------------------

   @Override
   protected Toolbar createMainToolbar()
   {
      Toolbar toolbar = new Toolbar();
      toolbar.addLeftWidget(commands_.loadWorkspace().createToolbarButton());
      toolbar.addLeftWidget(commands_.saveWorkspace().createToolbarButton());
      toolbar.addLeftSeparator();
      toolbar.addLeftWidget(createImportMenu());

      SearchWidget searchWidget = new SearchWidget(new SuggestOracle() {
         @Override
         public void requestSuggestions(Request request, Callback callback)
         {
            // no suggestions
            callback.onSuggestionsReady(
                  request,
                  new Response(new ArrayList<Suggestion>()));
         }
      });
      searchWidget.addValueChangeHandler(new ValueChangeHandler<String>() {
         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            objects_.setFilterText(event.getValue());
         }
      });

      toolbar.addLeftSeparator();
      toolbar.addLeftWidget(commands_.clearWorkspace().createToolbarButton());
      toolbar.addLeftSeparator();
      toolbar.addLeftWidget(commands_.refreshEnvironment().createToolbarButton());
      toolbar.addRightWidget(searchWidget);

      return toolbar;
   }

   @Override
   protected SecondaryToolbar createSecondaryToolbar()
   {
      SecondaryToolbar toolbar = new SecondaryToolbar();
      
      environmentMenu_ = new ToolbarPopupMenu();
      rebuildEnvironmentMenu();
      Label envLabel = new Label("Environment:");
      envLabel.addStyleName(
         EnvironmentPaneResources.INSTANCE.
            environmentPaneStyle().environmentNameLabel());
      toolbar.addLeftWidget(envLabel);
      environmentLabel_ = new Label(friendlyEnvironmentName());
      toolbar.addLeftPopupMenu(environmentLabel_, environmentMenu_);
      
      return toolbar;
   }

   @Override
   protected Widget createMainWidget()
   {
      objects_ = new EnvironmentObjects(this);
      return objects_;
   }

   // EnviromentPresenter.Display implementation ------------------------------

   @Override
   public void addObject(RObject object)
   {
      objects_.addObject(object);
   }

   @Override
   public void addObjects(JsArray<RObject> objects)
   {
      objects_.addObjects(objects);
   }
   
   @Override
   public void removeObject(String objectName)
   {
      objects_.removeObject(objectName);
   }
   
   @Override
   public void setContextDepth(int contextDepth)
   {
      objects_.setContextDepth(contextDepth);

      // if the environment we're about to show is nested, turn off the toolbar
      // commands that act on the global environment
      Boolean commandsEnabled = contextDepth == 0;
      commands_.loadWorkspace().setEnabled(commandsEnabled);
      commands_.saveWorkspace().setEnabled(commandsEnabled);
      commands_.clearWorkspace().setEnabled(commandsEnabled);
      commands_.importDatasetFromFile().setEnabled(commandsEnabled);
      commands_.importDatasetFromURL().setEnabled(commandsEnabled);
      dataImportButton_.setEnabled(commandsEnabled);
   }

   @Override
   public void clearObjects()
   {
      objects_.clearObjects();
      expandedObjects_.clear();
      scrollPosition_ = 0;
      isClientStateDirty_ = true;
   }

   @Override
   public void setEnvironmentName(String environmentName)
   {
      environmentName_ = environmentName;
      environmentLabel_.setText(friendlyEnvironmentName());
      objects_.setEnvironmentName(friendlyEnvironmentName());
   }

   @Override
   public void setCallFrames(JsArray<CallFrame> frameList)
   {
      objects_.setCallFrames(frameList);
   }

   @Override
   public int getScrollPosition()
   {
      return scrollPosition_;
   }

   @Override
   public void setScrollPosition(int scrollPosition)
   {
      objects_.setScrollPosition(scrollPosition);
   }

   @Override
   public void setExpandedObjects(JsArrayString objects)
   {
      objects_.setExpandedObjects(objects);
      expandedObjects_.clear();
      for (int idx = 0; idx < objects.length(); idx++)
      {
         expandedObjects_.add(objects.get(idx));
      }
   }

   @Override
   public String[] getExpandedObjects()
   {
      return expandedObjects_.toArray(new String[0]);
   }

   @Override
   public void changeContextDepth(int newDepth)
   {
      server_.setContextDepth(newDepth, new ServerRequestCallback<Void>()
      {
         @Override
         public void onError(ServerError error)
         {
            globalDisplay_.showErrorMessage("Error opening call frame", error.getUserMessage());
         }
      });
   }

   public boolean clientStateDirty()
   {
      return isClientStateDirty_;
   }

   public void setClientStateClean()
   {
      isClientStateDirty_ = false;
   }

   @Override
   public void resize()
   {
      objects_.onResize();
   }

   @Override
   public void setBrowserRange(DebugFilePosition range)
   {
      objects_.updateLineNumber(range.getLine());
   }

   // Event handlers ----------------------------------------------------------

   @Override
   public void onPackageStatusChanged(PackageStatusChangedEvent event)
   {
      // When a package is attached or detached, get the new list of 
      // environments from the server. We can't do this in the attach/detach
      // event itself since R runs the detach before actually removing the
      // environment from the search path. 
      server_.getEnvironmentNames(new ServerRequestCallback<JsArrayString>()
      {
         @Override
         public void onResponseReceived(JsArrayString response)
         {
            environments_ = response;
            rebuildEnvironmentMenu();
         }

         @Override
         public void onError(ServerError error)
         {
            
         }
      });
   }

   // EnviromentObjects.Observer implementation -------------------------------

   public void setPersistedScrollPosition(int scrollPosition)
   {
      scrollPosition_ = scrollPosition;
      isClientStateDirty_ = true;
   }

   public void setObjectExpanded(String objectName)
   {
      expandedObjects_.add(objectName);
      isClientStateDirty_ = true;
   }

   public void setObjectCollapsed(String objectName)
   {
      expandedObjects_.remove(objectName);
      isClientStateDirty_ = true;
   }

   public void viewObject(String objectName)
   {
      executeFunctionForObject("View", objectName);
   }

   // Private methods ---------------------------------------------------------

   private void executeFunctionForObject(String function, String objectName)
   {
      String editCode =
              function + "(" + StringUtil.toRSymbolName(objectName) + ")";
      SendToConsoleEvent event = new SendToConsoleEvent(editCode, true);
      eventBus_.fireEvent(event);
   }

   private Widget createImportMenu()
   {
      ToolbarPopupMenu menu = new ToolbarPopupMenu();
      menu.addItem(commands_.importDatasetFromFile().createMenuItem(false));
      menu.addItem(commands_.importDatasetFromURL().createMenuItem(false));
      dataImportButton_ = new ToolbarButton(
              "Import Dataset",
              StandardIcons.INSTANCE.import_dataset(),
              menu);
      return dataImportButton_;

   }
   
   private String friendlyEnvironmentName()
   {
      return friendlyNameOfEnvironment(environmentName_);
   }
   
   private String friendlyNameOfEnvironment(String name)
   {
      if (name.equals("R_GlobalEnv"))
         return "Global";
      else 
         return name;
   }
   
   private void rebuildEnvironmentMenu()
   {
      environmentMenu_.clearItems();
      for (int i = 0; i < environments_.length(); i++)
      {
         final String environment = environments_.get(i);
         environmentMenu_.addItem(new MenuItem(
               friendlyNameOfEnvironment(environment), 
               false,  // as HTML
               new Scheduler.ScheduledCommand()
               {
                  @Override
                  public void execute()
                  {
                     server_.setEnvironment(environment, 
                           new ServerRequestCallback<Void>()
                     {
                        @Override
                        public void onResponseReceived(Void v)
                        {
                           setEnvironmentName(environment);
                        }

                        @Override
                        public void onError(ServerError error)
                        {
                           
                        }
                     });
                  }
               }));
      }
   }

   private final Commands commands_;
   private final EventBus eventBus_;
   private final GlobalDisplay globalDisplay_;
   private final EnvironmentServerOperations server_;

   private ToolbarButton dataImportButton_;
   private ToolbarPopupMenu environmentMenu_;
   private Label environmentLabel_;
   private EnvironmentObjects objects_;

   private ArrayList<String> expandedObjects_;
   private int scrollPosition_;
   private boolean isClientStateDirty_;
   private JsArrayString environments_;
   private String environmentName_;
}
