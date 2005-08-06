package com.blueprintit.swim;

import java.awt.event.ActionEvent;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;

import com.blueprintit.errors.ErrorReporter;
import com.blueprintit.htmlkit.WebEditEditorKit;
import com.blueprintit.xui.InterfaceEvent;
import com.blueprintit.xui.InterfaceListener;
import com.blueprintit.xui.UserInterface;

public class PageBrowser implements InterfaceListener
{
	private class Page implements Comparable
	{
		private String container;
		private String id;
		private String title;
		private HTMLDocument preview;
		
		public Page(Element element)
		{
			id=element.getAttributeValue("id");
			container=element.getParentElement().getAttributeValue("id");
			title=element.getAttributeValue("title");
		}
		
		public String toString()
		{
			return title;
		}
		
		public String getResource()
		{
			return container+"/page/"+id;
		}
		
		public StyledDocument getPreview()
		{
			if (preview==null)
			{
				try
				{
					Request request = swim.getRequest("preview",getResource());
					URL url = request.encode();
					preview = (HTMLDocument)editorKit.createDefaultDocument();
					preview.setBase(url);
					editorKit.read(request.openReader(),preview,0);
				}
				catch (Exception e)
				{
					log.error("Unable to retrieve preview.",e);
				}
			}
			return preview;
		}

		public int compareTo(Object o)
		{
			if (o instanceof Page)
			{
				return title.compareTo(((Page)o).title);
			}
			else if (o instanceof String)
			{
				return title.compareTo(o.toString());
			}
			else
			{
				throw new IllegalArgumentException("Can only compare a page to another page or a string");
			}
		}
	}
	
	private Logger log = Logger.getLogger(this.getClass());

	private SwimInterface swim;
	private Document list;
	private HTMLEditorKit editorKit;
	
	public JDialog dialog;
	public JList pageList;
	public JEditorPane editorPane;

	public Action okAction = new AbstractAction("OK") {
		public void actionPerformed(ActionEvent e)
		{
			dialog.setVisible(false);
		}
	};
	
	public Action cancelAction = new AbstractAction("Cancel") {
		public void actionPerformed(ActionEvent e)
		{
			pageList.setSelectedValue(null,false);
			dialog.setVisible(false);
		}
	};

	public PageBrowser(SwimInterface swim)
	{
		this.swim=swim;
	}
	
	public String choosePage(String current)
	{
		try
		{
			Request request = swim.getRequest("list","");
			SAXBuilder builder = new SAXBuilder();
			list=builder.build(request.encode());

			UserInterface ui = new UserInterface(this);
			for (int i=pageList.getModel().getSize()-1; i>=0; i--)
			{
				Page page = (Page)pageList.getModel().getElementAt(i);
				if (page.getResource().equals(current))
				{
					pageList.setSelectedValue(page,true);
					break;
				}
			}
			ui.showModal();
			Page page = (Page)pageList.getSelectedValue();
			if (page!=null)
			{
				return page.getResource();
			}
			return null;
		}
		catch (Exception e)
		{
			log.error("Unable to load site list",e);
			ErrorReporter.sendErrorReport(
					"Error loading site details","The pages could not be retrieved. The server could be down or misconfigured.",
					"Swim","WebEdit","Could not load site list",e);
			return null;
		}		
	}
	
	public String choosePage()
	{
		return choosePage(null);
	}

	public void interfaceCreated(InterfaceEvent ev)
	{
		editorKit = new WebEditEditorKit();
		editorPane.setEditorKit(editorKit);
		ArrayList elements = new ArrayList();
		Iterator it = list.getRootElement().getDescendants(new ElementFilter("page"));
		while (it.hasNext())
		{
			Element el = (Element)it.next();
			elements.add(new Page(el));
		}
		Collections.sort(elements);
		DefaultListModel model = new DefaultListModel();
		it = elements.iterator();
		while (it.hasNext())
		{
			model.addElement(it.next());
		}
		pageList.setModel(model);
		pageList.addListSelectionListener(new ListSelectionListener() {

			public void valueChanged(ListSelectionEvent ev)
			{
				Page page = (Page)pageList.getSelectedValue();
				editorPane.setDocument(page.getPreview());
			}
		});
	}
}
