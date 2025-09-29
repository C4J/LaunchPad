package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  Simple Transferable for String Data
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class StringSelectionTransferable extends StringSelection
{
	public StringSelectionTransferable(String data)
	{
		super(data);
	}

	@Override
	public DataFlavor[] getTransferDataFlavors()
	{
		return new DataFlavor[] { DataFlavor.stringFlavor };
	}

	@Override
	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		return DataFlavor.stringFlavor.equals(flavor);
	}
}

