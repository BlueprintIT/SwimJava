package com.blueprintit.htmlkit;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.io.Reader;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.TransferHandler;
import javax.swing.text.BadLocationException;

import org.apache.log4j.Logger;

public class WebEditTransferHandler extends TransferHandler
{
	private Logger log = Logger.getLogger(this.getClass());

	public WebEditTransferHandler()
	{
	}

	private DataFlavor getPreferredDataFlavor(DataFlavor[] flavors)
	{
    for (int i = 0; i<flavors.length; i++)
		{
    	log.info(flavors[i].getMimeType());
		}
    for (int i = 0; i<flavors.length; i++)
		{
			String mime = flavors[i].getMimeType();
			if (mime.startsWith("text/plain"))
			{
				return flavors[i];
			}
			else if (mime.startsWith("application/x-java-jvm-local-objectref")
								&&flavors[i].getRepresentationClass()==java.lang.String.class)
			{
				return flavors[i];
			}
			else if (flavors[i].equals(DataFlavor.stringFlavor))
			{
				return flavors[i];
			}
		}
		return null;
	}

	private String importFromReader(Reader in) throws IOException
	{
		int nch;
		int last;
		boolean lastWasCR = false;
		char[] buff = new char[1024];
		StringBuffer sbuff = new StringBuffer();

		while ((nch = in.read(buff, 0, buff.length))!=-1)
		{
			if (sbuff==null)
			{
				sbuff = new StringBuffer(nch);
			}
			last = 0;
			for (int counter = 0; counter<nch; counter++)
			{
				switch (buff[counter])
				{
					case '\r':
						if (lastWasCR)
						{
							if (counter==0)
							{
								sbuff.append('\n');
							}
							else
							{
								buff[counter-1] = '\n';
							}
						}
						else
						{
							lastWasCR = true;
						}
						break;
					case '\n':
						if (lastWasCR)
						{
							if (counter>(last+1))
							{
								sbuff.append(buff, last, counter-last-1);
							}
							// else nothing to do, can skip \r, next write will
							// write \n
							lastWasCR = false;
							last = counter;
						}
						break;
					default:
						if (lastWasCR)
						{
							if (counter==0)
							{
								sbuff.append('\n');
							}
							else
							{
								buff[counter-1] = '\n';
							}
							lastWasCR = false;
						}
						break;
				}
			}
			if (last<nch)
			{
				if (lastWasCR)
				{
					if (last<(nch-1))
					{
						sbuff.append(buff, last, nch-last-1);
					}
				}
				else
				{
					sbuff.append(buff, last, nch-last);
				}
			}
		}
		if (lastWasCR)
		{
			sbuff.append('\n');
		}
		return sbuff.toString();
	}

	public boolean importData(JComponent comp, Transferable t)
	{
		JEditorPane pane = (JEditorPane)comp;
		DataFlavor flavor = getPreferredDataFlavor(t.getTransferDataFlavors());
		if (flavor!=null)
		{
			try
			{
				String text = importFromReader(flavor.getReaderForText(t));
				pane.replaceSelection(text);
				return true;
			}
			catch (Exception e)
			{
				log.warn(e);
				return false;
			}
		}
		else
		{
			return false;
		}
	}

	public boolean canImport(JComponent comp, DataFlavor[] transferFlavors)
	{
		return getPreferredDataFlavor(transferFlavors)!=null;
	}

	protected Transferable createTransferable(JComponent comp)
	{
		JEditorPane pane = (JEditorPane)comp;
		int start = pane.getSelectionStart();
		int end = pane.getSelectionEnd();
		
		if (start==end)
			return null;
		
		if (end<start)
		{
			int temp=end;
			end=start;
			start=temp;
		}
		try
		{
			String text = pane.getText(start,end-start);
			return new StringSelection(text);
		}
		catch (BadLocationException e)
		{
			log.error(e);
			return null;
		}
	}

	protected void exportDone(JComponent comp, Transferable data, int action)
	{
		JEditorPane pane = (JEditorPane)comp;
		if (action==MOVE)
		{
			int start = pane.getSelectionStart();
			int end = pane.getSelectionEnd();
			
			if (start!=end)
			{
				if (end<start)
				{
					int temp=end;
					end=start;
					start=temp;
				}
				try
				{
					pane.getDocument().remove(start,end-start);
				}
				catch (BadLocationException e)
				{
					log.error(e);
				}
			}
		}
	}

	public int getSourceActions(JComponent comp)
	{
		return COPY_OR_MOVE;
	}

	public Icon getVisualRepresentation(Transferable t)
	{
		return null;
	}
}
