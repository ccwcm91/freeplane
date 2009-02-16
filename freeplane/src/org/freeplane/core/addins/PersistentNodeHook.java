/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.core.addins;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IElementDOMHandler;
import org.freeplane.core.io.IExtensionElementWriter;
import org.freeplane.core.io.ITreeWriter;
import org.freeplane.core.modecontroller.MapController;
import org.freeplane.core.modecontroller.ModeController;
import org.freeplane.core.model.NodeModel;
import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.ActionDescriptor;
import org.freeplane.core.ui.SelectableAction;
import org.freeplane.core.undo.IUndoableActor;
import org.freeplane.n3.nanoxml.IXMLElement;
import org.freeplane.n3.nanoxml.XMLElement;

public abstract class PersistentNodeHook implements IExtension {
	public abstract class HookAction extends AFreeplaneAction {
		public HookAction() {
			super(controller);
		}

		public void actionPerformed(final ActionEvent e) {
			final NodeModel[] nodes = getNodes();
			final boolean activeForSelection = isActiveForSelection();
			for (int i = 0; i < nodes.length; i++) {
				final NodeModel node = nodes[i];
				if (node.containsExtension(getExtensionClass()) != activeForSelection) {
					continue;
				}
				undoableToggleHook(node);
			}
		}
	}

	@SelectableAction(checkOnNodeChange = true)
	private class SelectableHookAction extends HookAction {
		/**
		 * 
		 */
		private static final long serialVersionUID = 4427219027418034535L;

		public SelectableHookAction() {
			super();
		}

		@Override
		public void setSelected() {
			setSelected(isActiveForSelection());
		}

		public String getName() {
			// TODO rladstaetter 15.02.2009 this action is not annotated and thus no name can be derived?
			return getClass().getSimpleName();
		}
	}

	private final class ToggleHookActor implements IUndoableActor {
		IExtension extension;
		private final NodeModel node;

		private ToggleHookActor(final NodeModel node, final IExtension extension) {
			this.node = node;
			this.extension = extension != null ? extension : node.getExtension(getExtensionClass());
		}

		public void act() {
			if (extension != null && node.containsExtension(extension)) {
				remove(node, extension);
			}
			else {
				if (extension == null) {
					extension = createExtension(node);
				}
				add(node, extension);
			}
		}

		public String getDescription() {
			return getHookName();
		}

		public void undo() {
			act();
		}
	}

	protected class XmlReader implements IElementDOMHandler {
		public Object createElement(final Object parent, final String tag, final IXMLElement attributes) {
			if (attributes == null) {
				return null;
			}
			if (!getHookName().equals(attributes.getAttribute("NAME", null))) {
				return null;
			}
			return parent;
		}

		public void endElement(final Object parent, final String tag, final Object userObject,
		                       final IXMLElement lastBuiltElement) {
			if (getHookAnnotation().onceForMap()) {
				final IXMLElement parentNodeElement = lastBuiltElement.getParent().getParent();
				if (parentNodeElement == null || parentNodeElement.getName().equals("node")) {
					return;
				}
			}
			if (selectableHookAction != null) {
				selectableHookAction.setEnabled(true);
			}
			final NodeModel node = (NodeModel) userObject;
			add(node, createExtension(node, lastBuiltElement));
		}
	}

	protected class XmlWriter implements IExtensionElementWriter {
		public void writeContent(final ITreeWriter writer, final Object object, final IExtension extension)
		        throws IOException {
			final XMLElement element = new XMLElement("hook");
			saveExtension(extension, element);
			writer.addElement(null, element);
		}
	}

	private final HookAction selectableHookAction;
	final private Controller controller;
	private final ModeController modeController;

	public PersistentNodeHook(final ModeController modeController) {
		super();
		this.modeController = modeController;
		controller = modeController.getController();
		final ActionDescriptor actionAnnotation = getActionAnnotation();
		if (actionAnnotation != null) {
			selectableHookAction = createHookAction();
			if (selectableHookAction != null) {
				registerAction(selectableHookAction, actionAnnotation);
			}
		}
		else {
			selectableHookAction = null;
		}
		final MapController mapController = modeController.getMapController();
		mapController.getReadManager().addElementHandler("hook", createXmlReader());
		mapController.getWriteManager().addExtensionElementWriter(getExtensionClass(), createXmlWriter());
	}

	protected void add(final NodeModel node, final IExtension extension) {
		assert (getExtensionClass().equals(extension.getClass()));
		node.addExtension(extension);
		getModeController().getMapController().nodeChanged(node);
	}

	protected HookAction createHookAction() {
		return new SelectableHookAction();
	}

	protected IExtension createExtension(final NodeModel node) {
		return createExtension(node, null);
	}

	protected IExtension createExtension(final NodeModel node, final IXMLElement element) {
		return this;
	}

	protected XmlReader createXmlReader() {
		return new XmlReader();
	}

	protected XmlWriter createXmlWriter() {
		return new XmlWriter();
	}

	protected ActionDescriptor getActionAnnotation() {
		final ActionDescriptor annotation = getClass().getAnnotation(ActionDescriptor.class);
		return annotation;
	}

	protected Class getExtensionClass() {
		return getClass();
	}

	private NodeHookDescriptor getHookAnnotation() {
		final NodeHookDescriptor annotation = getClass().getAnnotation(NodeHookDescriptor.class);
		return annotation;
	}

	protected String getHookName() {
		return getHookAnnotation().hookName();
	}

	protected ModeController getModeController() {
		return modeController;
	}

	protected NodeModel[] getNodes() {
		if (getHookAnnotation().onceForMap()) {
			return getRootNode();
		}
		return getSelectedNodes();
	}

	protected NodeModel[] getRootNode() {
		final NodeModel[] nodes = new NodeModel[1];
		nodes[0] = controller.getMap().getRootNode();
		return nodes;
	}

	protected NodeModel[] getSelectedNodes() {
		final List<NodeModel> selection = controller.getSelection().getSelection();
		final int size = selection.size();
		final NodeModel[] nodes = new NodeModel[size];
		final Iterator<NodeModel> iterator = selection.iterator();
		int i = 0;
		while (iterator.hasNext()) {
			nodes[i++] = iterator.next();
		}
		return nodes;
	}

	protected boolean isActive(final NodeModel nodeModel) {
		if (!nodeModel.isRoot() && getHookAnnotation().onceForMap()) {
			return isActive(nodeModel.getMap().getRootNode());
		}
		return nodeModel.containsExtension(getExtensionClass());
	}

	protected boolean isActiveForSelection() {
		final NodeModel[] nodes = getNodes();
		for (int i = 0; i < nodes.length; i++) {
			final NodeModel nodeModel = nodes[i];
			if (nodeModel.containsExtension(getExtensionClass())) {
				return true;
			}
		}
		return false;
	}

	protected void registerAction(final AFreeplaneAction action) {
		registerAction(action, action.getClass().getAnnotation(ActionDescriptor.class));
	}

	protected void registerAction(final AFreeplaneAction action, final ActionDescriptor actionAnnotation) {
		modeController.putAction(actionAnnotation.name(), action);
		getModeController().getUserInputListenerFactory().getMenuBuilder().addAction(action, actionAnnotation);
	}

	protected void remove(final NodeModel node, final IExtension extension) {
		node.removeExtension(extension);
		getModeController().getMapController().nodeChanged(node);
	}

	protected void saveExtension(final IExtension extension, final IXMLElement element) {
		element.setAttribute("NAME", getHookName());
	}

	public void undoableActivateHook(final NodeModel node, final IExtension extension) {
		undoableToggleHook(node, extension);
	}

	public void undoableToggleHook(final NodeModel node) {
		undoableToggleHook(node, null);
	}

	private void undoableToggleHook(final NodeModel node, final IExtension extension) {
		final IUndoableActor actor = new ToggleHookActor(node, extension);
		getModeController().execute(actor);
	}
}
