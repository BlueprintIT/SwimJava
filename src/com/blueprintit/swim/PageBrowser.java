package com.blueprintit.swim;

import java.net.URL;

import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.blueprintit.htmlkit.WebEditEditorKit;
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
		private HTMLDocument preview;
		
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
	}
	
	private Logger log = Logger.getLogger(this.getClass());

	private SwimInterface swim;
	private Document list;
	private HTMLEditorKit editorKit;
	
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
			Request request = swim.getRequest("list","");
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			list=builder.parse(request.encode().toString());

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
		editorKit = new WebEditEditorKit();
		editorPane.setEditorKit(editorKit);
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
				editorPane.setDocument(page.getPreview());
			}
		});
	}
}
