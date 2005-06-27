/*
 * $HeadURL$
 * $LastChangedBy$
 * $Date$
 * $Revision$
 */
package com.blueprintit.swim;

import java.util.Iterator;
import java.util.Map;
import java.util.Hashtable;
import java.net.URL;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;

public class Request
{
	private Logger log = Logger.getLogger(this.getClass());

	private SwimInterface swim;
	private String method;
	private String resource;
	private Map query;
	
	private Request(SwimInterface swim)
	{
		this.swim=swim;
		query = new Hashtable();
	}
	
	public Request(SwimInterface swim, String method, String resource)
	{
		this(swim);
		this.method=method;
		if (resource.startsWith("/"))
		{
			resource=resource.substring(1);
		}
		this.resource=resource;
	}
	
	public Request(SwimInterface swim, String method, String resource, Map params)
	{
		this(swim,method,resource);
	}
	
	public Map getQuery()
	{
		return query;
	}
	
	public void addParameter(String name, String value)
	{
		query.put(name,value);
	}
	
	public static Request decode(SwimInterface swim, URL url)
	{
		Request request = new Request(swim);
		return request;
	}
	
	public static String URLEncode(String str)
	{
		if (str==null)
			return str;
		StringBuffer sb = new StringBuffer(str.length()*3);
		try
		{
			char c;
			for (int i = 0; i<str.length(); i++)
			{
				c = str.charAt(i);
				if (c=='&')
				{
					sb.append("&amp;");
				}
				else if (c==' ')
				{
					sb.append('+');
				}
				else if ((c>=','&&c<=';')||(c>='A'&&c<='Z')||(c>='a'&&c<='z')||c=='_'||c=='?')
				{
					sb.append(c);
				}
				else
				{
					sb.append('%');
					if (c>15)
					{ // is it a non-control char, ie. >x0F so 2 chars
						sb.append(Integer.toHexString((int) c)); // just add % and the
																											// string
					}
					else
					{
						sb.append("0"+Integer.toHexString((int) c));
						// otherwise need to add a leading 0
					}
				}
			}

		}
		catch (Exception ex)
		{
			return (null);
		}
		return (sb.toString());
	}
	
	private String generateQuery()
	{
		if (query.size()>0)
		{
			StringBuffer text = new StringBuffer();
			Iterator it = query.entrySet().iterator();
			while (it.hasNext())
			{
				Map.Entry entry = (Map.Entry)it.next();
				text.append(URLEncode(entry.getKey().toString())+"="+URLEncode(entry.getValue().toString())+"&");
			}
			text.delete(text.length()-1,text.length());
			return text.toString();
		}
		else
		{
			return null;
		}
	}
	
	public URL encode()
	{
		String full=swim.getURL().toString()+"/"+method+"/"+resource;
		String query=generateQuery();
		if (query!=null)
		{
			full+="?"+query;
		}
		try
		{
			return new URL(full);
		}
		catch (MalformedURLException e)
		{
			log.error("Could not build url from "+full);
			throw new IllegalArgumentException(e);
		}
	}
}
