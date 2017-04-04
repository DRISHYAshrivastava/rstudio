/*
 * ObjectExplorerEditingTargetWidget.java
 *
 * Copyright (C) 2009-17 by RStudio, Inc.
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
package org.rstudio.studio.client.workbench.views.source.editors.explorer.view;

import org.rstudio.core.client.widget.SearchWidget;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.workbench.views.source.editors.explorer.ObjectExplorerServerOperations;
import org.rstudio.studio.client.workbench.views.source.editors.explorer.model.ObjectExplorerHandle;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SuggestOracle;

public class ObjectExplorerEditingTargetWidget extends Composite
{
   public ObjectExplorerEditingTargetWidget(ObjectExplorerHandle handle)
   {
      RStudioGinjector.INSTANCE.injectMembers(this);
      
      panel_ = new DockLayoutPanel(Unit.PX);
      grid_ = new ObjectExplorerDataGrid(handle);
      footer_ = new FlowPanel();
      
      cbAttributes_ = new CheckBox();
      filterWidget_ = new SearchWidget(new SuggestOracle()
      {
         @Override
         public void requestSuggestions(Request request, Callback callback)
         {
         }
      });
      
      initFooter();
      initPanel();
      initWidget(panel_);
   }
   
   public void onActivate()
   {
      grid_.redraw();
   }
   
   public void onDeactivate()
   {
      // TODO
   }
   
   private void initFooter()
   {
      FlowPanel panel = new FlowPanel();
      
      cbAttributes_.setText("Show Attributes");
      cbAttributes_.addValueChangeHandler(new ValueChangeHandler<Boolean>()
      {
         @Override
         public void onValueChange(ValueChangeEvent<Boolean> event)
         {
            grid_.toggleShowAttributes(cbAttributes_.getValue());
         }
      });
      cbAttributes_.addStyleName(RES.styles().checkbox());
      panel.add(cbAttributes_);
      
      filterWidget_.addValueChangeHandler(new ValueChangeHandler<String>()
      {
         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            grid_.setFilter(event.getValue());
         }
      });
      filterWidget_.addStyleName(RES.styles().filter());
      panel.add(filterWidget_);
      
      footer_.addStyleName(RES.styles().footer());
      footer_.add(panel);
   }
   
   private void initPanel()
   {
      panel_.setSize("100%", "100%");
      panel_.addSouth(footer_, 24);
      panel_.add(grid_);
   }
   
   private final DockLayoutPanel panel_;
   private final FlowPanel footer_;
   private final ObjectExplorerDataGrid grid_;
   
   private final CheckBox cbAttributes_;
   private final SearchWidget filterWidget_;
   
   // Injected ----
   private ObjectExplorerServerOperations server_;
   
   // Resources, etc ----
   public interface Resources extends ClientBundle
   {
      @Source("ObjectExplorerEditingTargetWidget.css")
      Styles styles();
   }
   
   public interface Styles extends CssResource
   {
      String footer();
      String checkbox();
      String filter();
   }
   
   private static final Resources RES = GWT.create(Resources.class);
   
   static {
      RES.styles().ensureInjected();
   }
   
}
