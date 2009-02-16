/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is created by Dimitry Polivaev in 2008.
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
package org.freeplane.features.controller.print;

import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.util.Tools;

/**
 * @author Dimitry Polivaev
 */
public class PrintController implements IExtension {
	public static PrintController getController(final Controller controller) {
		return (PrintController) controller.getExtension(PrintController.class);
	}

	public static void install(final Controller controller) {
		controller.addExtension(PrintController.class, new PrintController(controller));
	}

	private PageFormat pageFormat = null;
	final private Controller controller;
	final private PageAction pageAction;
	final private PrintAction printAction;
	final private PrintDirectAction printDirectAction;
	final private PrintPreviewAction printPreviewAction;
	private PrinterJob printerJob = null;
	private boolean printingAllowed;

	public PrintController(final Controller controller) {
		super();
		this.controller = controller;
		printAction = new PrintAction(controller, this, true);
		printDirectAction = new PrintDirectAction(controller, this);
		printPreviewAction = new PrintPreviewAction(controller, this);
		pageAction = new PageAction(this);
		controller.putAction(printAction);
		controller.putAction(printDirectAction);
		controller.putAction(printPreviewAction);
		controller.putAction(pageAction);
		printingAllowed = true;
	}

	boolean acquirePrinterJobAndPageFormat() {
		if (printerJob == null) {
			try {
				printerJob = PrinterJob.getPrinterJob();
			}
			catch (final SecurityException ex) {
				printAction.setEnabled(false);
				printDirectAction.setEnabled(false);
				printPreviewAction.setEnabled(false);
				pageAction.setEnabled(false);
				printingAllowed = false;
				return false;
			}
		}
		if (pageFormat == null) {
			pageFormat = printerJob.defaultPage();
			if (Tools.safeEquals(ResourceController.getResourceController().getProperty("page_orientation"), "landscape")) {
				pageFormat.setOrientation(PageFormat.LANDSCAPE);
			}
			else if (Tools.safeEquals(ResourceController.getResourceController().getProperty("page_orientation"), "portrait")) {
				pageFormat.setOrientation(PageFormat.PORTRAIT);
			}
			else if (Tools.safeEquals(ResourceController.getResourceController().getProperty("page_orientation"),
			    "reverse_landscape")) {
				pageFormat.setOrientation(PageFormat.REVERSE_LANDSCAPE);
			}
		}
		return true;
	}

	public Controller getController() {
		return controller;
	}

	public PageFormat getPageFormat() {
		return pageFormat;
	}

	PrinterJob getPrinterJob() {
		return printerJob;
	}

	public boolean isEnabled() {
		return printingAllowed;
	}

	void setPageFormat(final PageFormat pageFormat) {
		this.pageFormat = pageFormat;
	}

	void setPrinterJob(final PrinterJob printerJob) {
		this.printerJob = printerJob;
	}
}
