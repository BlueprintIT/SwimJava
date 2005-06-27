package com.blueprintit.swim;

import java.io.IOException;

import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.blueprintit.xui.InterfaceEvent;
import com.blueprintit.xui.InterfaceListener;
import com.blueprintit.xui.UserInterface;

public class PageBrowser implements InterfaceListener
{
	private class Page
	{
		private String container;
		private String id;
		private String title;
		private String preview;
		
		public Page(Element element)
		{
			id=element.getAttribute("id");
			container=((Element)element.getParentNode()).getAttribute("id");
			title=element.getAttribute("title");
		}
		
		public String toString()
		{
			return title;
		}
		
		public String getResource()
		{
			return container+"/page/"+id;
		}
		
		public String getPreview()
		{
			if (preview==null)
			{
				try
				{
					preview=swim.getResource(getResource()+"/content/block.html",null);
				}
				catch (IOException e)
				{
					log.error("Unable to retrieve preview.",e);
				}
			}
			return preview;
		}
	}
	
	private Logger log = Logger.getLogger(this.getClass());

	private SwimInterface swim;
	private Document list;
	
	public JList pageList;
	public JEditorPane editorPane;

	public PageBrowser(SwimInterface swim)
	{
		this.swim=swim;
	}
	
	public String choosePage(String current) throws Exception
	{
		try
		{
			Request request = new Request(swim,"list","");
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			list=builder.parse(request.encode().toString());

			UserInterface ui = new UserInterface(this);
			ui.showModal();
			return "";
		}
		catch (Exception e)
		{
			log.error("Unable to load site list",e);
			return null;
		}		
	}
	
	public String choosePage() throws Exception
	{
		return choosePage(null);
	}

	public void interfaceCreated(InterfaceEvent ev)
	{
		DefaultListModel model = new DefaultListModel();
		NodeList items = list.getElementsByTagName("page");
		for (int i=0; i<items.getLength(); i++)
		{
			Element el = (Element)items.item(i);
			model.addElement(new Page(el));
		}
		pageList.setModel(model);
		pageList.addListSelectionListener(new ListSelectionListener() {

			public void valueChanged(ListSelectionEvent ev)
			{
				Page page = (Page)pageList.getSelectedValue();
				editorPane.setText(page.getPreview());
			}
		});
	}
}
