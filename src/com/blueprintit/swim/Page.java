/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import org.jdom.Element;

public class Page implements Comparable
{
	private String container;
	private String id;
	private String title;
	private SwimInterface swim;
	
	Page(SwimInterface swim, Element element)
	{
		this.swim=swim;
		id=element.getAttributeValue("id");
		container=element.getParentElement().getAttributeValue("id");
		title=element.getAttributeValue("title");
	}
	
	public String toString()
	{
		return title;
	}
	
	public String getTitle()
	{
		return title;
	}
	
	public String getResource()
	{
		return container+"/page/"+id;
	}
	
	public Request getPreviewRequest()
	{
		return swim.getRequest("preview",getResource());
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
