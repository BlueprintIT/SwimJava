/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.htmlkit;

import java.io.IOException;
import java.io.Writer;
import java.util.Enumeration;

import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLWriter;

import org.apache.log4j.Logger;

public class WebEditHTMLWriter extends HTMLWriter
{
	private Logger log = Logger.getLogger(this.getClass());

	public WebEditHTMLWriter(Writer w, HTMLDocument doc)
	{
		super(w,doc);
	}
	
	public WebEditHTMLWriter(Writer w, HTMLDocument doc, int pos, int len)
	{
		super(w,doc,pos,len);
	}

  /**
	 * Writes out the attribute set. Ignores all attributes with a key of type
	 * HTML.Tag, attributes with a key of type StyleConstants, and attributes with
	 * a key of type HTML.Attribute.ENDTAG.
	 * 
	 * @param attr
	 *          an AttributeSet
	 * @exception IOException
	 *              on any I/O error
	 * 
	 */
  protected void writeAttributes(AttributeSet attr) throws IOException
  {
  	MutableAttributeSet conv = new SimpleAttributeSet(attr);
		
  	StringBuffer style = new StringBuffer();
		Enumeration names = conv.getAttributeNames();
		while (names.hasMoreElements())
		{
			Object name = names.nextElement();
			if (name instanceof CSS.Attribute)
			{
				style.append(name.toString()+": "+conv.getAttribute(name)+"; ");
				conv.removeAttribute(name);
			}
		}
		if (style.length()>0)
		{
			style.delete(style.length()-2,style.length());
			write(" style=\""+style.toString()+"\"");
		}
		if (conv.getAttributeCount()>0)
		{
			super.writeAttributes(conv);
		}
  }
}
