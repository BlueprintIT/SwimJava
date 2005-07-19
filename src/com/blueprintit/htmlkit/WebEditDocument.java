package com.blueprintit.htmlkit;

import java.util.LinkedList;
import java.util.List;

import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;

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
	
	public void removeCharacterAttribute(int offset, int length, Object attribute)
	{
		try
		{
			writeLock();
			DefaultDocumentEvent changes = new DefaultDocumentEvent(offset, length, DocumentEvent.EventType.CHANGE);

			// split elements that need it
			buffer.change(offset, length, changes);

			int lastEnd = Integer.MAX_VALUE;
			for (int pos = offset; pos<(offset+length); pos = lastEnd)
			{
				Element run = getCharacterElement(pos);
				lastEnd = run.getEndOffset();
				if (pos==lastEnd)
				{
					// offset + length beyond length of document, bail.
					break;
				}
				MutableAttributeSet attr = (MutableAttributeSet) run.getAttributes();
				MutableAttributeSet sCopy = new SimpleAttributeSet(attr);
				sCopy.removeAttribute(attribute);
				changes.addEdit(new AttributeUndoableEdit(run, sCopy, true));
				attr.removeAttribute(attribute);
			}
			changes.end();
			fireChangedUpdate(changes);
			fireUndoableEditUpdate(new UndoableEditEvent(this, changes));
		}
		finally
		{
			writeUnlock();
		}
	}

	private void insertNewlineSpecs(List specs, List stack)
	{
		Element paragraph;
		for (int j = 0; j<stack.size(); j++)
		{
			log.info("End tag");
			specs.add(new ElementSpec(null,ElementSpec.EndTagType));
		}
		for (int j = stack.size(); --j>=0;)
		{
			log.info("Start tag");
			paragraph = (Element)stack.get(j);
			specs.add(new ElementSpec(paragraph.getAttributes(),ElementSpec.StartTagType));
		}
	}
	
  protected void insertUpdate(DefaultDocumentEvent chng, AttributeSet attr)
	{
		int offset = chng.getOffset();
		int length = chng.getLength();
  	log.info("Insert at "+offset+" length "+length);
		List stack = new LinkedList();
		boolean fracture=false;
		Element paragraph = getParagraphElement(offset+length);
  	log.info("Element at "+paragraph.getStartOffset()+" "+paragraph.getEndOffset());
		if (offset>paragraph.getStartOffset())
		{
			fracture=true;
		}
		stack.add(paragraph);
		HTML.Tag tag = (HTML.Tag)paragraph.getAttributes().getAttribute(StyleConstants.NameAttribute);
		while (tag==HTML.Tag.IMPLIED)
		{
			paragraph=paragraph.getParentElement();
			stack.add(paragraph);
			tag = (HTML.Tag)paragraph.getAttributes().getAttribute(StyleConstants.NameAttribute);
		}
		List specs = new LinkedList();
		
		Segment s = new Segment();
		s.setPartialReturn(false);
		ElementSpec spec;
		try
		{
			getText(offset,length,s);
			int start = s.offset;
			int end = s.count+s.offset;
			int lastend = start;
			
			if (!fracture)
			{
				insertNewlineSpecs(specs,stack);
			}
			for (int i = start; i<end; i++)
			{
				if (s.array[i]=='\n')
				{
					if (lastend<i)
					{
						log.info("Adding content");
						spec = new ElementSpec(attr,ElementSpec.ContentType,i-lastend);
						if (lastend==start)
						{
							log.info("Joining with previous");
							spec.setDirection(ElementSpec.JoinPreviousDirection);
						}
						specs.add(spec);
					}
					log.info("Adding newline content");
					specs.add(new ElementSpec(attr,ElementSpec.ContentType,1));
					insertNewlineSpecs(specs,stack);
					lastend=i+1;
				}
			}
			
			if (specs.size()>0)
			{
				int pos=specs.size()-1;
				spec = (ElementSpec)specs.get(pos);
				while (spec.getType()==ElementSpec.StartTagType)
				{
					if (fracture)
					{
						log.info("Fracturing");
						spec.setDirection(ElementSpec.JoinFractureDirection);
					}
					else
					{
						log.info("Joining");
						spec.setDirection(ElementSpec.JoinNextDirection);
					}
					pos--;
					spec = (ElementSpec)specs.get(pos);
				}
			}
			
			if ((lastend<end)&&(specs.size()>0))
			{
				log.info("Adding content");
				spec = new ElementSpec(attr,ElementSpec.ContentType,end-lastend);
				spec.setDirection(ElementSpec.JoinNextDirection);
				specs.add(spec);
			}
			
			if (specs.size()>0)
			{
				ElementSpec[] results = (ElementSpec[])specs.toArray(new ElementSpec[0]);
				buffer.insert(offset,length,results,chng);
			}
		}
		catch (BadLocationException e)
		{
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
			element=getRealParagraphElement(pos);
			elements.add(element);
		}
		return elements.iterator();
	}
}
