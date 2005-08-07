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
	
	public static class IncreaseListLevelAction extends StyledTextAction
	{
		private HTML.Tag ltype;
		private Logger log = Logger.getLogger(this.getClass());
		
		public IncreaseListLevelAction(String name, HTML.Tag type)
		{
			super(name);
			this.ltype=type;
		}
		
		private boolean isListBlock(Element el)
		{
			Object o = el.getAttributes().getAttribute(StyleConstants.NameAttribute);
			if (o!=null)
			{
				if ((o==HTML.Tag.UL)||(o==HTML.Tag.OL))
				{
					return true;
				}
			}
			return false;
		}
		
		private List buildListStack(Element el)
		{
			List base;
			Element par = el.getParentElement();
			if (par!=null)
			{
				base=buildListStack(par);
			}
			else
			{
				base = new LinkedList();
			}
			if (isListBlock(el))
			{
				base.add(el);
			}
			return base;
		}
		
		public void actionPerformed(ActionEvent ev)
		{
			MutableAttributeSet newattrs = new SimpleAttributeSet();
			MutableAttributeSet liattrs = new SimpleAttributeSet();
			MutableAttributeSet piattrs = new SimpleAttributeSet();
			
			newattrs.addAttribute(StyleConstants.NameAttribute,ltype);
			liattrs.addAttribute(StyleConstants.NameAttribute,HTML.Tag.LI);
			piattrs.addAttribute(StyleConstants.NameAttribute,HTML.Tag.IMPLIED);
			
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
			List specs = new LinkedList();
			Iterator it = document.getParagraphElementIterator(caret.getDot(),caret.getMark());
			Element el = (Element)it.next();
			int offset=el.getStartOffset();
			List stack = buildListStack(el);
			for (int i=0; i<stack.size(); i++)
			{
				Element par = (Element)stack.get(i);
				if (par.getStartOffset()==el.getStartOffset())
				{
					log.info("Start element "+par.getName());
					specs.add(new ElementSpec(par.getAttributes(),ElementSpec.StartTagType));
				}
			}
			boolean ingenerated=true;
			log.info("Add new list");
			specs.add(new ElementSpec(newattrs,ElementSpec.StartTagType));
			log.info("Start list item");
			specs.add(new ElementSpec(liattrs,ElementSpec.StartTagType));
			specs.add(new ElementSpec(piattrs,ElementSpec.StartTagType));
			int n = el.getElementCount();
			for (int i=0; i<n; i++)
			{
				Element content = el.getElement(i);
				specs.add(new ElementSpec(content.getAttributes(),ElementSpec.ContentType,content.getEndOffset()-content.getStartOffset()));
			}
			specs.add(new ElementSpec(null,ElementSpec.EndTagType));
			specs.add(new ElementSpec(null,ElementSpec.EndTagType));
			log.info("End list item");
			while (it.hasNext())
			{
				el = (Element)it.next();
				List newstack = buildListStack(el);
				int i=0;
				while ((i<stack.size())&&(i<newstack.size()))
				{
					if (stack.get(i)!=newstack.get(i))
						break;
					i++;
				}
				if (i<stack.size())
				{
					log.info("Ending generated list");
					specs.add(new ElementSpec(null,ElementSpec.EndTagType));
					ingenerated=false;
					for (int j=stack.size()-1; j>=i; j--)
					{
						log.info("End element");
						specs.add(new ElementSpec(null,ElementSpec.EndTagType));
					}
				}
				if (i<newstack.size())
				{
					if (ingenerated)
					{
						log.info("Ending generated list");
						specs.add(new ElementSpec(null,ElementSpec.EndTagType));
						ingenerated=false;
					}
					while (i<newstack.size())
					{
						Element par = (Element)newstack.get(i);
						log.info("Start element "+par.getName());
						specs.add(new ElementSpec(par.getAttributes(),ElementSpec.StartTagType));
						i++;
					}
				}
				if (!ingenerated)
				{
					log.info("Add new list");
					specs.add(new ElementSpec(newattrs,ElementSpec.StartTagType));
					ingenerated=true;
				}
				log.info("Start list item");
				specs.add(new ElementSpec(liattrs,ElementSpec.StartTagType));
				specs.add(new ElementSpec(piattrs,ElementSpec.StartTagType));
				n = el.getElementCount();
				for (i=0; i<n; i++)
				{
					Element content = el.getElement(i);
					specs.add(new ElementSpec(content.getAttributes(),ElementSpec.ContentType,content.getEndOffset()-content.getStartOffset()));
				}
				specs.add(new ElementSpec(null,ElementSpec.EndTagType));
				specs.add(new ElementSpec(null,ElementSpec.EndTagType));
				log.info("End list item");
				stack=newstack;
			}
			if (ingenerated)
			{
				log.info("Ending generated list");
				specs.add(new ElementSpec(null,ElementSpec.EndTagType));
				ingenerated=false;
			}
			for (int i=stack.size()-1; i>=0; i--)
			{
				Element par = (Element)stack.get(i);
				if (par.getEndOffset()==el.getEndOffset())
				{
					log.info("End element");
					specs.add(new ElementSpec(null,ElementSpec.EndTagType));
				}
			}
			ElementSpec[] results = (ElementSpec[])specs.toArray(new ElementSpec[0]);
			document.updateStructure(offset,results);
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
	
	public Document createDefaultDocument()
	{
		StyleSheet styles = getStyleSheet();
		StyleSheet ss = new StyleSheet();

		ss.addStyleSheet(styles);

		HTMLDocument doc = new WebEditDocument(ss);
		doc.setParser(getParser());
		doc.setAsynchronousLoadPriority(4);
		doc.setTokenThreshold(100);
		return doc;
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
