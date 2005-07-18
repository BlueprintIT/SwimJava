package com.blueprintit.htmlkit;

import java.io.IOException;
import java.io.StringReader;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit.Parser;
import javax.swing.text.html.HTMLEditorKit.ParserCallback;

import org.apache.log4j.Logger;

public class WebEditDocument extends HTMLDocument
{
	private Logger log = Logger.getLogger(this.getClass());

	public WebEditDocument(StyleSheet ss)
	{
		super(ss);
	}

	public HTML.Tag getParagraphTag(int offset)
	{
		Element el = super.getParagraphElement(offset);
		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		return tag;
	}
	
	public Element getRealParagraphElement(int offset)
	{
		Element el = super.getParagraphElement(offset);
		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		return el;
	}
	
	public void replaceAttributes(Element e, AttributeSet a, HTML.Tag tag)
	{
		if((e != null) && (a != null))
		{
			try
			{
				writeLock();
				int start = e.getStartOffset();
				DefaultDocumentEvent changes = new DefaultDocumentEvent(start, e.getEndOffset() - start, DocumentEvent.EventType.CHANGE);
				AttributeSet sCopy = a.copyAttributes();
				changes.addEdit(new AttributeUndoableEdit(e, sCopy, false));
				MutableAttributeSet attr = (MutableAttributeSet) e.getAttributes();
				Enumeration aNames = attr.getAttributeNames();
				Object value;
				Object aName;
				while (aNames.hasMoreElements())
				{
					aName = aNames.nextElement();
					value = attr.getAttribute(aName);
					if(value != null && !value.toString().equalsIgnoreCase(tag.toString()))
					{
						attr.removeAttribute(aName);
					}
				}
				attr.addAttributes(a);
				changes.end();
				fireChangedUpdate(changes);
				fireUndoableEditUpdate(new UndoableEditEvent(this, changes));
			}
			finally
			{
				writeUnlock();
			}
		}
	}

	public void insertString(int offset, String text, AttributeSet attrs) throws BadLocationException
	{
		Element para = getRealParagraphElement(offset);
		HTML.Tag tag = (Tag)para.getAttributes().getAttribute(StyleConstants.NameAttribute);
		Element el = getCharacterElement(offset);
		
		int pop=0;
		HTML.Tag eltag = (Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while ((el!=para)&&(eltag!=HTML.Tag.BODY))
		{
			if (eltag!=HTML.Tag.CONTENT)
				pop++; 
			el=el.getParentElement();
			eltag = (Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		if (el==para)
			pop++;
		
		boolean first = true;
		StringBuffer content = new StringBuffer();
		StringTokenizer tokenizer = new StringTokenizer(text,"\n",true);
		while (tokenizer.hasMoreTokens())
		{
			String part = tokenizer.nextToken();
			if (part.equals("\n"))
			{
				if (first)
				{
					first=false;
				}
				else
				{
					content.append("</"+tag.toString()+">\n");
				}
				content.append("<"+tag.toString()+">\n");
			}
			else
			{
				if (first)
				{
					super.insertString(offset,part,attrs);
					offset+=part.length();
				}
				else
				{
					content.append(part);
				}
			}
		}
		if (!first)
		{
			if (offset==para.getStartOffset())
			{
				content.insert(0,"<"+tag.toString()+">\n</"+tag.toString()+">\n");
			}
			content.append(getText(offset,para.getEndOffset()-offset));
			content.append("</"+tag.toString()+">");
			log.info("Adding "+content);
			log.info("Popping "+pop);
			remove(offset,para.getEndOffset()-offset);
			try
			{
				Parser p = getParser();

				ParserCallback receiver = getReader(offset, pop, 0, tag);
				Boolean ignoreCharset = (Boolean)getProperty("IgnoreCharsetDirective");
				p.parse(new StringReader(content.toString()), receiver,
						(ignoreCharset==null) ? false : ignoreCharset.booleanValue());
				receiver.flush();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	public java.util.Iterator getCharacterElementIterator(int start, int end)
	{
		if (end<start)
		{
			int temp=end;
			end=start;
			start=temp;
		}
		LinkedList elements = new LinkedList();
		Element element = getCharacterElement(start);
		elements.add(element);
		while (element.getEndOffset()<end)
		{
			int pos=element.getEndOffset();
			element=getCharacterElement(pos);
			elements.add(element);
		}
		return elements.iterator();
	}
	
	public java.util.Iterator getParagraphElementIterator(int start, int end)
	{
		if (end<start)
		{
			int temp=end;
			end=start;
			start=temp;
		}
		LinkedList elements = new LinkedList();
		Element element = getRealParagraphElement(start);
		elements.add(element);
		while (element.getEndOffset()<end)
		{
			int pos=element.getEndOffset();
			element=getParagraphElement(pos);
			elements.add(element);
		}
		return elements.iterator();
	}
}
