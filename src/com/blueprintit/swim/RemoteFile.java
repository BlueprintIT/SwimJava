/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import org.jdom.Element;

public class RemoteFile extends RemoteEntry
{
	private boolean exists = false;
	private int size = -1;

	RemoteFile(SwimInterface swim, String path, String version)
	{
		super(swim,path,version);
		refresh();
	}
	
	RemoteFile(SwimInterface swim, String path, String version, Element el)
	{
		super(swim,path,version);
		refresh(el);
	}
	
	public void refresh(Element element)
	{
		super.refresh(element);
		exists=Boolean.valueOf(element.getAttributeValue("exists")).booleanValue();
		if (element.getAttribute("size")!=null)
			size=Integer.parseInt(element.getAttributeValue("size"));
	}
	
	public void delete()
	{
		Request del = swim.getRequest("delete",path);
		del.addParameter("version","temp");
		try
		{
			del.openInputStream().close();
			size=-1;
			exists=false;
		}
		catch (Exception e)
		{
		}
	}
	
	public int getSize()
	{
		return size;
	}
	
	public boolean exists()
	{
		return exists;
	}
}
