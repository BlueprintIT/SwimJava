/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.jdom.Element;

public class RemoteDir extends RemoteEntry
{
	private Map dirs = new HashMap();
	private Map files = new HashMap();
	
	RemoteDir(SwimInterface swim, String path, String version)
	{
		super(swim,path,version);
		refresh();
	}
	
	RemoteDir(SwimInterface swim, String path, String version, Element el)
	{
		super(swim,path,version);
		refresh(el);
	}
	
	public Iterator files()
	{
		return (new LinkedList(files.values())).iterator();
	}
	
	public Iterator dirs()
	{
		return (new LinkedList(dirs.values())).iterator();
	}
	
	public void refresh(Element element)
	{
		super.refresh(element);
		Collection cache = new ArrayList();
		
		cache.addAll(dirs.values());
		Iterator it = element.getChildren("dir").iterator();
		while (it.hasNext())
		{
			Element el = (Element)it.next();
			String sub = el.getAttributeValue("name");
			if (dirs.containsKey(sub))
			{
				RemoteDir dir = (RemoteDir)dirs.get(sub);
				dir.refresh(el);
				cache.remove(dir);
			}
			else
			{
				RemoteDir dir = new RemoteDir(swim,path+"/"+sub,version,el);
				dirs.put(sub,dir);
			}
		}
		it = cache.iterator();
		while (it.hasNext())
		{
			dirs.values().remove(it.next());
		}
		cache.clear();
		
		cache.addAll(files.values());
		it = element.getChildren("file").iterator();
		while (it.hasNext())
		{
			Element el = (Element)it.next();
			String sub = el.getAttributeValue("name");
			if (files.containsKey(sub))
			{
				RemoteFile file = (RemoteFile)files.get(sub);
				file.refresh(el);
				cache.remove(file);
			}
			else
			{
				RemoteFile file = new RemoteFile(swim,path+"/"+sub,version,el);
				files.put(sub,file);
			}
		}
		it = cache.iterator();
		while (it.hasNext())
		{
			files.values().remove(it.next());
		}
	}
	
	public boolean exists()
	{
		return true;
	}
}
