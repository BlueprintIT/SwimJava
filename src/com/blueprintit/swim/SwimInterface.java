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
