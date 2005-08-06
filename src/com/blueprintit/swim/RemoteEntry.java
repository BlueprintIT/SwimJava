/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import org.jdom.Element;

public abstract class RemoteEntry
{
	protected SwimInterface swim;
	protected String path;
	protected String name;
	protected String version;
	
	RemoteEntry(SwimInterface swim, String path, String version)
	{
		this.swim=swim;
		this.version=version;
		this.path=path;
	}
	
	protected Element loadList()
	{
		return swim.loadList(path,version);
	}
	
	public void refresh()
	{
		refresh(loadList());
	}
	
	public void refresh(Element element)
	{
		name = element.getAttributeValue("name");
	}
	
	public String getPath()
	{
		return path;
	}
	
	public String getName()
	{
		return name;
	}
	
	public String toString()
	{
		return name;
	}
	
	public abstract boolean exists();
}
