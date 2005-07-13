/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.htmlkit;

import java.io.IOException;
import java.io.Writer;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
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
			return view;
		}
	}
	
  private ViewFactory defaultFactory = new WebEditFactory();
	
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
