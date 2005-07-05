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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;

public class Request
{
	private class ResourceOutputStream extends OutputStream
	{
		private OutputStream out;
		private HttpURLConnection connection;

		public ResourceOutputStream(HttpURLConnection connection) throws IOException
		{
			this.connection=connection;
			connection.setDoOutput(true);
			this.out=connection.getOutputStream();
		}

		public void write(int b) throws IOException
		{
			out.write(b);
		}
		
		public void close() throws IOException
		{
			out.close();
			connection.connect();
			connection.getInputStream().close();
		}
	}
	
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
	
	public Request(SwimInterface swim, String resource)
	{
		this(swim,"view",resource);
	}
	
	public Request(SwimInterface swim, String method, String resource, Map params)
	{
		this(swim,method,resource);
		query.putAll(params);
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
			// TODO handle this error?
			log.error("Could not build url from "+full);
			return null;
		}
	}

	public Reader openReader() throws IOException
	{
		return new InputStreamReader(openInputStream());
	}
	
	public Writer openWriter() throws IOException
	{
		return new OutputStreamWriter(openOutputStream());
	}
	
	public InputStream openInputStream() throws IOException
	{
		return openConnection("GET").getInputStream();
	}
	
	public OutputStream openOutputStream() throws IOException
	{
		HttpURLConnection connection = openConnection("PUT");
		return new ResourceOutputStream(connection);
	}
	
	private HttpURLConnection openConnection(String method) throws IOException
	{
		URL url = encode();
		log.info("Opening connection to "+url.toString());
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setRequestMethod(method);
		return connection;
	}
}
