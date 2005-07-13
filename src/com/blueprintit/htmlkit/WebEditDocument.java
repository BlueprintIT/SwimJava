package com.blueprintit.htmlkit;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.html.HTML.Tag;

import org.apache.log4j.Logger;

public class WebEditDocument extends HTMLDocument
{
	private Logger log = Logger.getLogger(this.getClass());

	public WebEditDocument(StyleSheet ss)
	{
		super(ss);
	}

	public Element getParagraphElement(int offset)
	{
		Element el = super.getParagraphElement(offset);
		HTML.Tag tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		if (tag==HTML.Tag.IMPLIED)
		{
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		return el;
	}
	
	public void insertString(int offset, String text, AttributeSet attrs) throws BadLocationException
	{
		Element para = getParagraphElement(offset);
		Element el = getCharacterElement(offset);
		if ((el.getStartOffset()==offset)&&(offset>0))
		{
			el=getCharacterElement(offset-1);
		}
		HTML.Tag tag = (Tag) el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		int pop=1;
		while (el!=para)
		{
			if (tag!=HTML.Tag.CONTENT)
				pop++; 
			el=el.getParentElement();
			tag = (HTML.Tag)el.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		boolean first = true;
		StringBuffer content = new StringBuffer();
		StringTokenizer tokenizer = new StringTokenizer(text,"\n",true);
		int count=0;
		while (tokenizer.hasMoreTokens())
		{
			String part = tokenizer.nextToken();
			if (part.equals("\n"))
			{
				if (first)
				{
					first=false;
					content.append("<"+tag.toString()+">");
				}
				else
				{
					content.append("</"+tag.toString()+">");
					content.append("<"+tag.toString()+">");
				}
			}
			else
			{
				if (first)
				{
					super.insertString(offset,part,attrs);
				}
				else
				{
					content.append(part);
					count++;
				}
			}
		}
		content.append("</"+tag.toString()+">");
		log.info("Adding "+content.toString());
		log.info("Popping "+pop);
		try
		{
			getParser().parse(new StringReader(content.toString()),getReader(offset,pop,0,tag),true);
		}
		catch (IOException e)
		{
			log.error(e);
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
		Element element = getParagraphElement(start);
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
