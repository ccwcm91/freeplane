/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
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
package org.freeplane.main.application;

import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import org.freeplane.core.Compat;
import org.freeplane.core.controller.Controller;
import org.freeplane.core.frame.IMapSelectionListener;
import org.freeplane.core.model.MapModel;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.ui.UIBuilder;
import org.freeplane.core.ui.components.FreeplaneMenuBar;
import org.freeplane.core.url.UrlManager;
import org.freeplane.n3.nanoxml.XMLParseException;

/**
 * This class manages a list of the maps that were opened last. It aims to
 * provide persistence for the last recent maps. Maps should be shown in the
 * format:"mode\:key",ie."Mindmap\:/home/joerg/freeplane.mm"
 */
public class LastOpenedList implements IMapSelectionListener {
	/**
	 * Contains Restore strings.
	 */
	final private List lastOpenedList = new LinkedList();
	final private int maxEntries;
	/**
	 * Contains Restore string => map name (map.toString()).
	 */
	final private Map mRestorableToMapName = new HashMap();

	LastOpenedList(final String restored, final int maxEntries) {
		this.maxEntries = maxEntries;
		load(restored);
	}

	public void afterMapChange(final MapModel oldMap, final MapModel newMap) {
		mapOpened(newMap);
	}

	public void afterMapClose(final MapModel oldMap) {
	}

	public void beforeMapChange(final MapModel oldMap, final MapModel newMap) {
	}

	public boolean isMapChangeAllowed(final MapModel oldMap, final MapModel newMap) {
		return true;
	}

	ListIterator listIterator() {
		return lastOpenedList.listIterator();
	}

	/**
	 *
	 */
	void load(final String data) {
		if (data != null) {
			final StringTokenizer token = new StringTokenizer(data, ";");
			while (token.hasMoreTokens()) {
				lastOpenedList.add(token.nextToken());
			}
		}
	}

	void mapOpened(final MapModel map) {
		if (map == null) {
			return;
		}
		final String restoreString = UrlManager.getController(map.getModeController()).getRestoreable(map);
		if (restoreString == null) {
			return;
		}
		if (lastOpenedList.contains(restoreString)) {
			lastOpenedList.remove(restoreString);
		}
		lastOpenedList.add(0, restoreString);
		mRestorableToMapName.put(restoreString, map.getTitle());
		while (lastOpenedList.size() > maxEntries) {
			lastOpenedList.remove(lastOpenedList.size() - 1);
		}
	}

	public void open(final Controller controller, final String restoreable) throws FileNotFoundException,
	        XMLParseException, MalformedURLException, IOException, URISyntaxException {
		final boolean changedToMapView = controller.getMapViewManager().tryToChangeToMapView(
		    (String) mRestorableToMapName.get(restoreable));
		if ((restoreable != null) && !(changedToMapView)) {
			final StringTokenizer token = new StringTokenizer(restoreable, ":");
			if (token.hasMoreTokens()) {
				final String mode = token.nextToken();
				if (controller.selectMode(mode)) {
					final String fileName = token.nextToken("").substring(1);
					controller.getModeController().getMapController().newMap(Compat.fileToUrl(new File(fileName)));
				}
			}
		}
	}

	/** fc, 8.8.2004: This method returns a string representation of this class. */
	String save() {
		String str = new String();
		for (final ListIterator it = listIterator(); it.hasNext();) {
			str = str.concat((String) it.next() + ";");
		}
		return str;
	}

	public void updateMenus(final Controller controller, final MenuBuilder menuBuilder) {
		menuBuilder.removeChildElements(FreeplaneMenuBar.FILE_MENU + "/last");
		boolean firstElement = true;
		for (final ListIterator it = listIterator(); it.hasNext();) {
			final String key = (String) it.next();
			final JMenuItem item = new JMenuItem(key);
			if (firstElement) {
				firstElement = false;
				item.setAccelerator(KeyStroke.getKeyStroke(ResourceController.getResourceController().getAdjustableProperty(
				    "keystroke_open_first_in_history")));
			}
			final ActionListener lastOpenedActionListener = new LastOpenedActionListener(controller, this);
			item.addActionListener(lastOpenedActionListener);
			menuBuilder.addMenuItem(FreeplaneMenuBar.FILE_MENU + "/last", item, UIBuilder.AS_CHILD);
		}
	}
}
