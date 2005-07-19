/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import java.io.BufferedReader;
import java.io.Writer;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

public class SwimInterface
{
	private Logger log = Logger.getLogger(this.getClass());

	private URL url;
	
	public SwimInterface(URL url)
	{
		log.info("Created interface to "+url);
		this.url=url;
	}
	
	public Request getRequest(String resource)
	{
		return new Request(this,resource);
	}
	
	public Request getRequest(String method, String resource)
	{
		return new Request(this,method,resource);
	}
	
	public Request getRequest(String method, String resource, Map params)
	{
		return new Request(this,method,resource,params);
	}
	
	public RemoteEntry getEntry(String path, String version)
	{
		Element list = loadList(path,version);
		if (list.getName().equals("dir"))
		{
			return new RemoteDir(this,path,version,list);
		}
		else
		{
			return new RemoteFile(this,path,version,list);
		}
	}
	
	Element loadList(String path, String version)
	{
		try
		{
			Request request = getRequest("list",path);
			request.getQuery().put("version",version);
			SAXBuilder builder = new SAXBuilder();
			Document document = builder.build(request.encode());
			return document.getRootElement();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public URL getURL()
	{
		return url;
	}
	
	public PageBrowser getPageBrowser()
	{
		return new PageBrowser(this);
	}
	
	public String getResource(String path, String version) throws IOException
	{
		Request request = getRequest(path);
		if (version!=null)
				request.addParameter("version",version);
		BufferedReader reader = new BufferedReader(request.openReader());
		StringBuffer result = new StringBuffer();
		String line=reader.readLine();
		while (line!=null)
		{
			result.append(line);
			line=reader.readLine();
		}
		reader.close();
		return result.toString();
	}
	
	public void setResource(String path, String version, String data) throws IOException
	{
		Request request = getRequest(path);
		if (version!=null)
				request.addParameter("version",version);
		Writer writer = request.openWriter();
		writer.write(data);
		writer.close();
	}
}
