/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.htmlkit;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.DefaultStyledDocument.ElementSpec;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;

import org.apache.log4j.Logger;

public class WebEditEditorKit extends HTMLEditorKit
{
	private Logger log = Logger.getLogger(this.getClass());

	private class WebEditFactory extends HTMLEditorKit.HTMLFactory
	{
		public View create(Element el)
		{
			View view = super.create(el);
			if (view instanceof ParagraphView)
			{
				return new WebEditParagraphView(el);
			}
			else if (view instanceof ImageView)
			{
				return new AnchorView((ImageView)view);
			}
			return view;
		}
	}
	
  private ViewFactory defaultFactory = new WebEditFactory();
	
  public static class FloatLeftAction extends StyledTextAction
  {
  	public FloatLeftAction(String name)
  	{
  		super(name);
  	}

  	public void actionPerformed(ActionEvent e)
		{
			MutableAttributeSet attrs = new SimpleAttributeSet();
			attrs.addAttribute(CSS.Attribute.FLOAT,"left");
			JEditorPane pane = getEditor(e);
			setCharacterAttributes(pane,attrs,false);
		}
  }
  
  public static class FloatRightAction extends StyledTextAction
  {
  	public FloatRightAction(String name)
  	{
  		super(name);
  	}

  	public void actionPerformed(ActionEvent e)
		{
			MutableAttributeSet attrs = new SimpleAttributeSet();
			attrs.addAttribute(CSS.Attribute.FLOAT,"right");
			JEditorPane pane = getEditor(e);
			setCharacterAttributes(pane,attrs,false);
		}
  }
  
  public static class FloatNoneAction extends StyledTextAction
  {
  	public FloatNoneAction(String name)
  	{
  		super(name);
  	}

  	public void actionPerformed(ActionEvent e)
		{
			JEditorPane pane = getEditor(e);
			WebEditDocument doc = (WebEditDocument)pane.getDocument();
			int start = pane.getSelectionStart();
			doc.removeCharacterAttribute(start,pane.getSelectionEnd()-start,CSS.Attribute.FLOAT);
		}
  }
  
  public static abstract class ListMutateAction extends StyledTextAction
  {
		private Logger log = Logger.getLogger(this.getClass());

		protected class Paragraph
		{
			public List stack = new LinkedList();
			public MutableAttributeSet attrs;
			public HTML.Tag tag;
			public ElementSpec[] content;
		}
		
		public ListMutateAction(String name)
  	{
  		super(name);
  	}
  	
  	private boolean isListElement(Element el)
  	{
  		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
  		if ((tag==HTML.Tag.UL)||(tag==HTML.Tag.OL))
  		{
  			return true;
  		}
  		return false;
  	}
  	
  	private List buildListStack(Element el)
  	{
  		if (el==null)
  		{
  			return new LinkedList();
  		}
  		List result = buildListStack(el.getParentElement());
  		if (isListElement(el))
  		{
  			result.add(el.getAttributes());
  		}
  		return result;
  	}
  	
  	private Paragraph buildParagraph(Element el)
  	{
  		Paragraph p = new Paragraph();
  		p.attrs = new SimpleAttributeSet(el.getAttributes());
  		p.tag=(HTML.Tag)p.attrs.getAttribute(StyleConstants.NameAttribute);
  		int n = el.getElementCount();
  		p.content = new ElementSpec[n];
  		for (int i=0; i<n; i++)
  		{
  			Element leaf = el.getElement(i);
  			int len = leaf.getEndOffset()-leaf.getStartOffset();
  			p.content[i] = new ElementSpec(leaf.getAttributes(),ElementSpec.ContentType,len);
  		}
  		Element par = el;
  		while (par!=null)
  		{
  			if (isListElement(par))
  			{
  				p.stack.add(0,par);
  			}
  			par=par.getParentElement();
  		}
  		return p;
  	}
  	
  	protected abstract void mutateList(List paragraphs);
  	
		public void actionPerformed(ActionEvent e)
		{
			JEditorPane editorPane = getEditor(e);
			WebEditDocument document = (WebEditDocument)editorPane.getDocument();
			WebEditEditorKit editorKit = (WebEditEditorKit)editorPane.getEditorKit();
			Caret caret = editorPane.getCaret();
			int start = caret.getDot();
			int end = caret.getMark();
			if (end<start)
			{
				int temp = end;
				end=start;
				start=temp;
			}
			try
			{
				Element el = document.getParagraphElement(start);
				int rlstart = el.getStartOffset();
				while (el!=null)
				{
					if (isListElement(el))
					{
						rlstart=Math.min(rlstart,el.getStartOffset());
					}
					el=el.getParentElement();
				}
				el = document.getParagraphElement(end);
				int rlend = el.getEndOffset();
				while (el!=null)
				{
					if (isListElement(el))
					{
						rlend=Math.max(rlend,el.getStartOffset());
					}
					el=el.getParentElement();
				}

				List leadin = new ArrayList();
				List paragraphs = new ArrayList();
				List leadout = new ArrayList();
				
				List current = leadin;
				Iterator it = document.getParagraphElementIterator(rlstart,rlend);
				while (it.hasNext())
				{
					el = (Element)it.next();
					if (el.getStartOffset()>end)
					{
						current=leadout;
					}
					else if (el.getEndOffset()>start)
					{
						current=paragraphs;
					}
					
					current.add(buildParagraph(el));
				}
				mutateList(paragraphs);
				leadin.addAll(paragraphs);
				leadin.addAll(leadout);
				List specs = new LinkedList();
			}
			catch (Exception ex)
			{
				log.error(ex);
				ex.printStackTrace();
			}
		}
  }
  
  public static class IncreaseListAction extends ListMutateAction
  {
  	public IncreaseListAction()
  	{
  		super("increase-list-level");
  	}
  	
  	protected void mutateList(List paragraphs)
  	{
  	}
  }
  
	public static class ToggleListAction extends StyledTextAction
	{
		private HTML.Tag ltype;
		private Logger log = Logger.getLogger(this.getClass());
		
		public ToggleListAction(String name, HTML.Tag type)
		{
			super(name);
			this.ltype=type;
		}
		
		public void actionPerformed(ActionEvent ev)
		{
			JEditorPane editorPane = getEditor(ev);
			WebEditDocument document = (WebEditDocument)editorPane.getDocument();
			WebEditEditorKit editorKit = (WebEditEditorKit)editorPane.getEditorKit();
			Caret caret = editorPane.getCaret();
			int start = caret.getDot();
			int end = caret.getMark();
			if (end<start)
			{
				int temp = end;
				end=start;
				start=temp;
			}
			StringBuffer list = new StringBuffer();
			Iterator it = document.getParagraphElementIterator(caret.getDot(),caret.getMark());
			Element el = (Element)it.next();
			start=el.getStartOffset();
			HTML.Tag parent;
			HTML.Tag type = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
			if (type==HTML.Tag.LI)
			{
				type=HTML.Tag.P;
				parent=type;
			}
			else
			{
				type=HTML.Tag.LI;
				parent=ltype;
				list.append("<"+parent.toString()+">\n");
			}
			do
			{
				try
				{
					String paragraph = document.getText(el.getStartOffset(),el.getEndOffset()-el.getStartOffset());
					list.append("<"+type.toString()+">");
					list.append(paragraph.trim());
					list.append("</"+type.toString()+">\n");
				}
				catch (BadLocationException e)
				{
					log.error(e);
				}
				if (it.hasNext())
				{
					el = (Element)it.next();
				}
				else
				{
					break;
				}
			} while (true);
			end=el.getEndOffset();
			if (type==HTML.Tag.LI)
			{
				list.append("</"+parent.toString()+">");
			}
			log.info(list);
			try
			{
				document.remove(start,end-start);
				el = document.getCharacterElement(start);
				if ((el.getStartOffset()==start)&&(start>0))
				{
					el=document.getCharacterElement(start-1);
				}
				HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
				int pop=0;
				while ((tag!=HTML.Tag.BODY)&&((parent==HTML.Tag.P)||(tag!=parent)))
				{
					log.info(tag);
					if (tag!=HTML.Tag.CONTENT)
						pop++; 
					el=el.getParentElement();
					tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
				}
				if (tag!=HTML.Tag.BODY)
				{
					parent=type;
				}
				try
				{
					editorKit.insertHTML(document,start,list.toString(),pop,0,parent);
				}
				catch (IOException e)
				{
					log.error(e);
				}
				editorPane.setSelectionStart(start);
				editorPane.setSelectionEnd(start);
			}
			catch (BadLocationException e)
			{
				log.error(e);
			}
		}
	}
	
	public static class ToggleOrderedListAction extends ToggleListAction
	{
		public ToggleOrderedListAction()
		{
			super("ordered-list",HTML.Tag.OL);
		}
	}
	
	public static class ToggleUnorderedListAction extends ToggleListAction
	{
		public ToggleUnorderedListAction()
		{
			super("unordered-list",HTML.Tag.UL);
		}
	}
	
	public WebEditEditorKit()
	{
		super();
	}
	

	public WebEditDocument createDefaultDocument(String bodyid)
	{
		StyleSheet styles = getStyleSheet();
		StyleSheet ss = new WebEditStyleSheet();

		ss.addStyleSheet(styles);

		WebEditDocument doc = new WebEditDocument(ss,bodyid);
		doc.setParser(getParser());
		doc.setAsynchronousLoadPriority(4);
		doc.setTokenThreshold(100);
		return doc;
	}

	public Document createDefaultDocument()
	{
		return createDefaultDocument(null);
	}
	
  public void write(Writer out, Document doc, int pos, int len) throws IOException, BadLocationException
  {
		if (doc instanceof HTMLDocument)
		{
	    WebEditHTMLWriter w = new WebEditHTMLWriter(out, (HTMLDocument)doc, pos, len);
	    w.write();
		}
		else
		{
			super.write(out,doc,pos,len);
		}
  }

	public ViewFactory getViewFactory()
	{
		return defaultFactory;
	}
	
	public void install(JEditorPane pane)
	{
		log.info("Installing editor kit");
		super.install(pane);
		pane.setTransferHandler(new WebEditTransferHandler());
	}
}
